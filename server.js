const express = require('express');
const cookieParser = require('cookie-parser');
const multer = require('multer');
const bcrypt = require('bcryptjs');
const path = require('path');
const fs = require('fs');

const db = require('./db');
const {
  getMondayOfWeek,
  classifyDateRoutine,
  generateWeekRoster,
  finalizeWeekRoster
} = require('./scheduler');
const {
  validateWeekRoster,
  checkOfficerEligibility,
  addDays
} = require('./validator');
const {
  generateTemplateWorkbook,
  importExcelBuffer,
  exportRosterToExcel
} = require('./excelService');
const { seedDatabase } = require('./seedData');

const XLSX = require('xlsx');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(cookieParser('duty-roster-secret-key-naval'));
app.use(express.static(__dirname));
app.use(express.static(path.join(__dirname, 'public')));

const upload = multer({ storage: multer.memoryStorage() });

// --- Auth Middleware ---
function requireWatchkeeper(req, res, next) {
  const token = req.signedCookies.watchkeeper_session;
  if (token === 'authorized_watchkeeper') {
    return next();
  }
  return res.status(401).json({ error: 'Watchkeeper authentication required.' });
}

// ==========================================
// PUBLIC ENDPOINTS
// ==========================================

// Auth Status
app.get('/api/auth/status', (req, res) => {
  const isAuth = req.signedCookies.watchkeeper_session === 'authorized_watchkeeper';
  res.json({ authenticated: isAuth });
});

// Watchkeeper Login
app.post('/api/auth/login', (req, res) => {
  const { password } = req.body;
  if (!password) {
    return res.status(400).json({ error: 'Password is required.' });
  }

  const row = db.prepare('SELECT value FROM settings WHERE key = ?').get('admin_password');
  if (!row) {
    return res.status(500).json({ error: 'Admin password not configured.' });
  }

  const isValid = bcrypt.compareSync(password, row.value);
  if (!isValid) {
    return res.status(401).json({ error: 'Invalid Watchkeeper password.' });
  }

  res.cookie('watchkeeper_session', 'authorized_watchkeeper', {
    signed: true,
    httpOnly: true,
    maxAge: 24 * 60 * 60 * 1000, // 24 hours
    sameSite: 'lax'
  });

  res.json({ success: true, message: 'Watchkeeper login successful.' });
});

// Watchkeeper Logout
app.post('/api/auth/logout', (req, res) => {
  res.clearCookie('watchkeeper_session');
  res.json({ success: true, message: 'Logged out successfully.' });
});

// Get Roster for current or specified week
app.get('/api/roster/view', (req, res) => {
  try {
    let weekStart = req.query.week_start;
    if (!weekStart) {
      // Default to current date's Monday
      const todayStr = new Date().toISOString().split('T')[0];
      weekStart = getMondayOfWeek(todayStr);
    } else {
      weekStart = getMondayOfWeek(weekStart);
    }

    let week = db.prepare('SELECT * FROM roster_weeks WHERE week_start = ?').get(weekStart);

    // If no roster exists for this week yet, generate draft
    if (!week) {
      week = generateWeekRoster(weekStart);
    }

    const assignments = db.prepare(`
      SELECT da.*,
             o.name as officer_name,
             o.rank as officer_rank,
             o.service_id as officer_service_id,
             orig.name as orig_officer_name,
             orig.rank as orig_officer_rank,
             swp.name as swap_officer_name,
             swp.rank as swap_officer_rank,
             swp.service_id as swap_officer_service_id
      FROM duty_assignments da
      LEFT JOIN officers o ON da.assigned_officer_id = o.id
      LEFT JOIN officers orig ON da.original_officer_id = orig.id
      LEFT JOIN officers swp ON da.swapped_with_officer_id = swp.id
      WHERE da.roster_week_id = ?
      ORDER BY da.duty_date ASC
    `).all(week.id);

    // Run quick validation summary
    const validation = validateWeekRoster(week.id);

    res.json({
      week,
      assignments,
      validation: {
        valid: validation.valid,
        errors: validation.errors,
        warnings: validation.warnings
      }
    });
  } catch (err) {
    console.error('Error fetching roster:', err);
    res.status(500).json({ error: err.message });
  }
});

// List available roster weeks
app.get('/api/roster/weeks', (req, res) => {
  try {
    const weeks = db.prepare(`
      SELECT id, week_start, week_end, status, finalized_at, notes,
             (SELECT COUNT(*) FROM duty_assignments WHERE roster_week_id = roster_weeks.id) as duty_count
      FROM roster_weeks
      ORDER BY week_start DESC
    `).all();
    res.json(weeks);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// ==========================================
// PROTECTED WATCHKEEPER ENDPOINTS
// ==========================================

// Change Watchkeeper Password
app.post('/api/auth/change-password', requireWatchkeeper, (req, res) => {
  const { currentPassword, newPassword } = req.body;
  if (!newPassword || newPassword.length < 6) {
    return res.status(400).json({ error: 'New password must be at least 6 characters.' });
  }

  const row = db.prepare('SELECT value FROM settings WHERE key = ?').get('admin_password');
  if (!row || !bcrypt.compareSync(currentPassword, row.value)) {
    return res.status(400).json({ error: 'Current password is incorrect.' });
  }

  const hash = bcrypt.hashSync(newPassword, 10);
  db.prepare('UPDATE settings SET value = ? WHERE key = ?').run(hash, 'admin_password');
  res.json({ success: true, message: 'Watchkeeper password updated successfully.' });
});

// --- Officers Management ---
app.get('/api/officers', requireWatchkeeper, (req, res) => {
  try {
    const officers = db.prepare(`
      SELECT id, service_id, name, rank, status, joining_date, transfer_date,
             normal_duty_count, sunday_duty_count,
             (normal_duty_count + sunday_duty_count) as total_duty_count
      FROM officers
      ORDER BY status ASC, rank DESC, name ASC
    `).all();
    res.json(officers);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Add New Officer (Incoming transfer / joining)
app.post('/api/officers', requireWatchkeeper, (req, res) => {
  try {
    const { service_id, name, rank, status, joining_date, transfer_date, normal_duty_count, sunday_duty_count } = req.body;
    if (!service_id || !name || !joining_date) {
      return res.status(400).json({ error: 'Service ID, Name, and Joining Date are required.' });
    }

    const existing = db.prepare('SELECT id FROM officers WHERE service_id = ?').get(service_id.trim());
    if (existing) {
      return res.status(400).json({ error: `Officer with Service ID ${service_id} already exists.` });
    }

    const info = db.prepare(`
      INSERT INTO officers (service_id, name, rank, status, joining_date, transfer_date, normal_duty_count, sunday_duty_count)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      service_id.trim(),
      name.trim(),
      rank ? rank.trim() : 'Officer',
      status || 'ACTIVE',
      joining_date,
      transfer_date || null,
      parseInt(normal_duty_count || 0, 10),
      parseInt(sunday_duty_count || 0, 10)
    );

    res.json({ success: true, officerId: info.lastInsertRowid, message: 'Officer added successfully.' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Update Officer
app.put('/api/officers/:id', requireWatchkeeper, (req, res) => {
  try {
    const officerId = req.params.id;
    const { service_id, name, rank, status, joining_date, transfer_date, normal_duty_count, sunday_duty_count } = req.body;

    const officer = db.prepare('SELECT * FROM officers WHERE id = ?').get(officerId);
    if (!officer) {
      return res.status(404).json({ error: 'Officer not found.' });
    }

    db.prepare(`
      UPDATE officers
      SET service_id = ?, name = ?, rank = ?, status = ?, joining_date = ?, transfer_date = ?,
          normal_duty_count = ?, sunday_duty_count = ?
      WHERE id = ?
    `).run(
      service_id ? service_id.trim() : officer.service_id,
      name ? name.trim() : officer.name,
      rank ? rank.trim() : officer.rank,
      status || officer.status,
      joining_date || officer.joining_date,
      transfer_date !== undefined ? transfer_date : officer.transfer_date,
      normal_duty_count !== undefined ? parseInt(normal_duty_count, 10) : officer.normal_duty_count,
      sunday_duty_count !== undefined ? parseInt(sunday_duty_count, 10) : officer.sunday_duty_count,
      officerId
    );

    res.json({ success: true, message: 'Officer details updated successfully.' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Record Outgoing Transfer
app.post('/api/officers/:id/transfer-out', requireWatchkeeper, (req, res) => {
  try {
    const officerId = req.params.id;
    const { transfer_date } = req.body;
    if (!transfer_date) {
      return res.status(400).json({ error: 'Transfer date is required.' });
    }

    const officer = db.prepare('SELECT * FROM officers WHERE id = ?').get(officerId);
    if (!officer) {
      return res.status(404).json({ error: 'Officer not found.' });
    }

    db.prepare(`
      UPDATE officers
      SET status = 'TRANSFERRED_OUT', transfer_date = ?
      WHERE id = ?
    `).run(transfer_date, officerId);

    res.json({ success: true, message: `Officer ${officer.name} marked as transferred out as of ${transfer_date}. Historical records preserved.` });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// --- Leaves Management ---
app.get('/api/leaves', requireWatchkeeper, (req, res) => {
  try {
    const leaves = db.prepare(`
      SELECT l.*, o.name as officer_name, o.rank as officer_rank, o.service_id
      FROM leaves l
      JOIN officers o ON l.officer_id = o.id
      ORDER BY l.leave_start DESC
    `).all();
    res.json(leaves);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Add Leave
app.post('/api/leaves', requireWatchkeeper, (req, res) => {
  try {
    const { officer_id, leave_start, leave_end, prefix_start, prefix_end, suffix_start, suffix_end, return_date, remarks } = req.body;
    if (!officer_id || !leave_start || !leave_end || !return_date) {
      return res.status(400).json({ error: 'Officer, Leave Start, Leave End, and Return Date are required.' });
    }

    if (leave_end < leave_start) {
      return res.status(400).json({ error: 'Leave End Date cannot be before Leave Start Date.' });
    }

    db.prepare(`
      INSERT INTO leaves (officer_id, leave_start, leave_end, prefix_start, prefix_end, suffix_start, suffix_end, return_date, remarks)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    `).run(
      officer_id,
      leave_start,
      leave_end,
      prefix_start || null,
      prefix_end || null,
      suffix_start || null,
      suffix_end || null,
      return_date,
      remarks || null
    );

    res.json({ success: true, message: 'Leave recorded successfully.' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Delete Leave
app.delete('/api/leaves/:id', requireWatchkeeper, (req, res) => {
  try {
    db.prepare('DELETE FROM leaves WHERE id = ?').run(req.params.id);
    res.json({ success: true, message: 'Leave record removed.' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// --- Holidays Management ---
app.get('/api/holidays', requireWatchkeeper, (req, res) => {
  try {
    const holidays = db.prepare('SELECT * FROM holidays ORDER BY holiday_date ASC').all();
    res.json(holidays);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Add Holiday
app.post('/api/holidays', requireWatchkeeper, (req, res) => {
  try {
    const { holiday_date, description } = req.body;
    if (!holiday_date || !description) {
      return res.status(400).json({ error: 'Holiday date and description are required.' });
    }

    db.prepare(`
      INSERT INTO holidays (holiday_date, description)
      VALUES (?, ?)
      ON CONFLICT(holiday_date) DO UPDATE SET description = excluded.description
    `).run(holiday_date, description.trim());

    res.json({ success: true, message: 'Holiday saved into Sunday Routine calendar.' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Delete Holiday
app.delete('/api/holidays/:id', requireWatchkeeper, (req, res) => {
  try {
    db.prepare('DELETE FROM holidays WHERE id = ?').run(req.params.id);
    res.json({ success: true, message: 'Holiday deleted.' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// --- Roster Actions ---

// Regenerate / Recalculate Week
app.post('/api/roster/generate', requireWatchkeeper, (req, res) => {
  try {
    const { week_start, force_all } = req.body;
    if (!week_start) {
      return res.status(400).json({ error: 'week_start is required.' });
    }

    const week = generateWeekRoster(week_start, { forceRegenerateAll: !!force_all });
    const validation = validateWeekRoster(week.id);

    res.json({
      success: true,
      message: force_all ? 'Roster completely regenerated.' : 'Roster recalculated while preserving manual changes and swaps.',
      week,
      validation
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Edit or override single assignment
app.put('/api/roster/assignment/:id', requireWatchkeeper, (req, res) => {
  try {
    const assignmentId = req.params.id;
    const { officer_id, is_punishment, punishment_reason } = req.body;

    const assignment = db.prepare('SELECT * FROM duty_assignments WHERE id = ?').get(assignmentId);
    if (!assignment) {
      return res.status(404).json({ error: 'Assignment not found.' });
    }

    const week = db.prepare('SELECT status FROM roster_weeks WHERE id = ?').get(assignment.roster_week_id);
    if (week.status === 'FINALIZED') {
      return res.status(400).json({ error: 'Cannot modify an already finalized roster week.' });
    }

    // Validate eligibility unless punishment
    if (officer_id) {
      const check = checkOfficerEligibility(officer_id, assignment.duty_date, !!is_punishment, assignmentId);
      if (!check.valid && !is_punishment) {
        return res.status(400).json({
          error: 'Assignment violates duty rules.',
          details: check.errors
        });
      }
    }

    db.prepare(`
      UPDATE duty_assignments
      SET assigned_officer_id = ?,
          is_manual_override = 1,
          is_punishment = ?,
          punishment_reason = ?
      WHERE id = ?
    `).run(
      officer_id || null,
      is_punishment ? 1 : 0,
      is_punishment ? (punishment_reason ? punishment_reason.trim() : 'Disciplinary assignment') : null,
      assignmentId
    );

    res.json({ success: true, message: 'Assignment updated successfully.' });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Duty Swap: Exchanging duties between two assignments
app.post('/api/roster/swap', requireWatchkeeper, (req, res) => {
  try {
    const { assignment_id_1, assignment_id_2, reason } = req.body;
    if (!assignment_id_1 || !assignment_id_2) {
      return res.status(400).json({ error: 'Both assignment IDs are required to swap.' });
    }

    if (assignment_id_1 === assignment_id_2) {
      return res.status(400).json({ error: 'Cannot swap an assignment with itself.' });
    }

    const a1 = db.prepare('SELECT * FROM duty_assignments WHERE id = ?').get(assignment_id_1);
    const a2 = db.prepare('SELECT * FROM duty_assignments WHERE id = ?').get(assignment_id_2);

    if (!a1 || !a2) {
      return res.status(404).json({ error: 'One or both assignments not found.' });
    }

    const officer1Id = a1.assigned_officer_id;
    const officer2Id = a2.assigned_officer_id;

    if (!officer1Id || !officer2Id) {
      return res.status(400).json({ error: 'Both assignments must have an assigned officer to swap.' });
    }

    // Validate Officer 1 on Date 2
    const check1 = checkOfficerEligibility(officer1Id, a2.duty_date, false, a1.id);
    // Validate Officer 2 on Date 1
    const check2 = checkOfficerEligibility(officer2Id, a1.duty_date, false, a2.id);

    const swapErrors = [];
    if (!check1.valid) {
      const o1 = db.prepare('SELECT name FROM officers WHERE id = ?').get(officer1Id);
      swapErrors.push(`Swap invalid for ${o1.name} on ${a2.duty_date}: ${check1.errors.join('; ')}`);
    }
    if (!check2.valid) {
      const o2 = db.prepare('SELECT name FROM officers WHERE id = ?').get(officer2Id);
      swapErrors.push(`Swap invalid for ${o2.name} on ${a1.duty_date}: ${check2.errors.join('; ')}`);
    }

    if (swapErrors.length > 0) {
      return res.status(400).json({
        error: 'Swap violates rest or availability restrictions.',
        details: swapErrors
      });
    }

    // Perform swap in a transaction
    const swapTx = db.transaction(() => {
      // a1 gets officer2, swapped with officer1
      db.prepare(`
        UPDATE duty_assignments
        SET assigned_officer_id = ?,
            swapped_with_officer_id = ?,
            is_swap = 1,
            swap_reason = ?,
            is_manual_override = 1
        WHERE id = ?
      `).run(officer2Id, officer1Id, reason || 'Mutual duty swap', a1.id);

      // a2 gets officer1, swapped with officer2
      db.prepare(`
        UPDATE duty_assignments
        SET assigned_officer_id = ?,
            swapped_with_officer_id = ?,
            is_swap = 1,
            swap_reason = ?,
            is_manual_override = 1
        WHERE id = ?
      `).run(officer1Id, officer2Id, reason || 'Mutual duty swap', a2.id);
    });

    swapTx();

    res.json({
      success: true,
      message: 'Duty swap successfully recorded and validated.'
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Validate Week
app.post('/api/roster/validate/:week_id', requireWatchkeeper, (req, res) => {
  try {
    const validation = validateWeekRoster(req.params.week_id);
    res.json(validation);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Finalize Week
app.post('/api/roster/finalize/:week_id', requireWatchkeeper, (req, res) => {
  try {
    const weekId = req.params.week_id;
    const validation = validateWeekRoster(weekId);

    if (!validation.valid) {
      return res.status(400).json({
        error: 'Cannot finalize roster: Mandatory duty rules are violated.',
        details: validation.errors
      });
    }

    const result = finalizeWeekRoster(weekId);
    res.json(result);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// --- Excel Import, Export, & Template ---

// Download Excel Template
app.get('/api/excel/template', (req, res) => {
  try {
    const wb = generateTemplateWorkbook();
    const buffer = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
    res.setHeader('Content-Disposition', 'attachment; filename="officer_duty_roster_template.xlsx"');
    res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    res.send(buffer);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Export Roster to Excel
app.get('/api/excel/export/:week_id', (req, res) => {
  try {
    const weekId = req.params.week_id;
    const buffer = exportRosterToExcel(weekId);
    res.setHeader('Content-Disposition', `attachment; filename="duty_roster_week_${weekId}.xlsx"`);
    res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
    res.send(buffer);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Import Excel File
app.post('/api/excel/import', requireWatchkeeper, upload.single('excel_file'), (req, res) => {
  try {
    if (!req.file || !req.file.buffer) {
      return res.status(400).json({ error: 'Please upload an Excel file (.xlsx).' });
    }

    const results = importExcelBuffer(req.file.buffer);
    res.json({
      success: true,
      message: 'Excel data imported and synchronized successfully without resetting historical duty counts.',
      results
    });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Duty History & Changes Log
app.get('/api/history', requireWatchkeeper, (req, res) => {
  try {
    const history = db.prepare(`
      SELECT dh.*, o.name as officer_name, o.rank as officer_rank, o.service_id
      FROM duty_history dh
      JOIN officers o ON dh.officer_id = o.id
      ORDER BY dh.duty_date DESC
      LIMIT 100
    `).all();

    const swaps = db.prepare(`
      SELECT da.duty_date, da.routine_type, da.swap_reason,
             o.name as actual_officer_name, o.rank as actual_officer_rank,
             swp.name as swapped_with_name, swp.rank as swapped_with_rank
      FROM duty_assignments da
      JOIN officers o ON da.assigned_officer_id = o.id
      JOIN officers swp ON da.swapped_with_officer_id = swp.id
      WHERE da.is_swap = 1
      ORDER BY da.duty_date DESC
      LIMIT 50
    `).all();

    res.json({ history, swaps });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// Fallback to index.html
app.use((req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});

// Ensure seed data exists on startup
seedDatabase();

app.listen(PORT, () => {
  console.log(`Automated Duty Roster System running at http://localhost:${PORT}`);
});
