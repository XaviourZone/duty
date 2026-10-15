const db = require('./db');
const { addDays, dateDiffDays, checkOfficerEligibility } = require('./validator');

const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

/**
 * Get the Monday of the week containing dateStr (YYYY-MM-DD)
 */
function getMondayOfWeek(dateStr) {
  const d = new Date(dateStr + 'T00:00:00Z');
  const day = d.getUTCDay(); // 0 is Sunday, 1 is Monday...
  const diff = day === 0 ? -6 : 1 - day; // If Sunday, go back 6 days to Monday
  d.setUTCDate(d.getUTCDate() + diff);
  return d.toISOString().split('T')[0];
}

/**
 * Classify a calendar date into 'NORMAL' or 'SUNDAY' routine, checking declared holidays.
 */
function classifyDateRoutine(dateStr) {
  const holiday = db.prepare('SELECT description FROM holidays WHERE holiday_date = ?').get(dateStr);
  const d = new Date(dateStr + 'T00:00:00Z');
  const dayIndex = d.getUTCDay();
  const dayName = DAY_NAMES[dayIndex];

  if (holiday) {
    return {
      routine_type: 'SUNDAY',
      is_holiday: 1,
      holiday_name: holiday.description,
      day_name: dayName
    };
  }

  if (dayIndex === 0 || dayIndex === 6) {
    // Saturday or Sunday
    return {
      routine_type: 'SUNDAY',
      is_holiday: 0,
      holiday_name: null,
      day_name: dayName
    };
  }

  // Monday through Friday
  return {
    routine_type: 'NORMAL',
    is_holiday: 0,
    holiday_name: null,
    day_name: dayName
  };
}

/**
 * Find the most recent duty date for an officer in a specific routine.
 * Looks across duty_assignments (finalized or earlier in draft) and duty_history.
 */
function getLastDutyDateInRoutine(officerId, routineType, beforeDate) {
  // Check duty_assignments before this date
  const rowAssigned = db.prepare(`
    SELECT da.duty_date
    FROM duty_assignments da
    JOIN roster_weeks rw ON da.roster_week_id = rw.id
    WHERE da.assigned_officer_id = ?
      AND da.routine_type = ?
      AND da.duty_date < ?
    ORDER BY da.duty_date DESC
    LIMIT 1
  `).get(officerId, routineType, beforeDate);

  // Check duty_history before this date
  const rowHistory = db.prepare(`
    SELECT duty_date
    FROM duty_history
    WHERE officer_id = ?
      AND routine_type = ?
      AND duty_date < ?
    ORDER BY duty_date DESC
    LIMIT 1
  `).get(officerId, routineType, beforeDate);

  let candidateDate = null;
  if (rowAssigned && rowHistory) {
    candidateDate = rowAssigned.duty_date > rowHistory.duty_date ? rowAssigned.duty_date : rowHistory.duty_date;
  } else if (rowAssigned) {
    candidateDate = rowAssigned.duty_date;
  } else if (rowHistory) {
    candidateDate = rowHistory.duty_date;
  }

  return candidateDate;
}

/**
 * Calculate duty count in a routine for an officer up to beforeDate.
 */
function getOfficerRoutineDutyCount(officerId, routineType, beforeDate) {
  const officer = db.prepare('SELECT normal_duty_count, sunday_duty_count FROM officers WHERE id = ?').get(officerId);
  if (!officer) return 0;

  const baseCount = routineType === 'NORMAL' ? officer.normal_duty_count : officer.sunday_duty_count;

  // Count unfinalized or newly assigned duties in drafts before this date
  const draftDuties = db.prepare(`
    SELECT COUNT(*) as cnt
    FROM duty_assignments da
    JOIN roster_weeks rw ON da.roster_week_id = rw.id
    WHERE da.assigned_officer_id = ?
      AND da.routine_type = ?
      AND da.duty_date < ?
      AND rw.status = 'DRAFT'
  `).get(officerId, routineType, beforeDate);

  return baseCount + (draftDuties ? draftDuties.cnt : 0);
}

/**
 * Select the best eligible officer for a given duty date and routine.
 * Handles:
 * 1. Return-to-duty priority (Section 5 Rule 2 & 4).
 * 2. Independent rotation & fairness (Section 4 Rule 3 & Section 6).
 * 3. Prevention of new joiner over-allocation.
 */
function selectBestOfficer(dutyDate, routineType, currentAssignmentId = null) {
  // 1. Fetch all officers active on this date
  const allOfficers = db.prepare(`
    SELECT * FROM officers
    WHERE joining_date <= ?
      AND (status = 'ACTIVE' OR (status = 'TRANSFERRED_OUT' AND transfer_date > ?))
  `).all(dutyDate, dutyDate);

  if (allOfficers.length === 0) {
    return { officer: null, reason: 'No active officers found for this date.' };
  }

  // 2. Check eligibility for each officer (Rest, Leaves, Monday leave rule)
  const eligibleOfficers = [];
  for (const officer of allOfficers) {
    const check = checkOfficerEligibility(officer.id, dutyDate, false, currentAssignmentId);
    if (check.valid) {
      eligibleOfficers.push(officer);
    }
  }

  if (eligibleOfficers.length === 0) {
    return { officer: null, reason: 'All active officers have conflicts (rest, leave, or transfers).' };
  }

  // 3. Check for return-from-leave officers on dutyDate
  const returningLeaves = db.prepare(`
    SELECT officer_id FROM leaves
    WHERE return_date = ?
  `).all(dutyDate);

  const returningOfficerIds = new Set(returningLeaves.map(l => l.officer_id));
  const eligibleReturningOfficers = eligibleOfficers.filter(o => returningOfficerIds.has(o.id));

  if (eligibleReturningOfficers.length > 0) {
    // If multiple officers return on the same day, prioritize the one who hasn't had duty longest
    eligibleReturningOfficers.sort((a, b) => {
      const lastA = getLastDutyDateInRoutine(a.id, routineType, dutyDate) || a.joining_date;
      const lastB = getLastDutyDateInRoutine(b.id, routineType, dutyDate) || b.joining_date;
      return lastA.localeCompare(lastB); // oldest date first
    });

    return {
      officer: eligibleReturningOfficers[0],
      isReturnPriority: true,
      reason: 'Return-to-duty allocation priority.'
    };
  }

  // 4. Score each eligible officer for fair rotation
  // Scores prioritize:
  // a) Days since last duty in this routine (rotation sequence)
  // b) Pacing / count balance (avoiding unfair burden while respecting join date)
  const scored = eligibleOfficers.map(officer => {
    const lastDuty = getLastDutyDateInRoutine(officer.id, routineType, dutyDate);
    const routineCount = getOfficerRoutineDutyCount(officer.id, routineType, dutyDate);

    // Days since last duty in this routine.
    // If officer never had a duty in this routine, their tenure baseline is their joining date.
    // This prevents a newly joined officer from jumping ahead of someone who has served 4 weeks without duty.
    let daysSinceDuty;
    if (lastDuty) {
      daysSinceDuty = dateDiffDays(dutyDate, lastDuty);
    } else {
      // Benchmark against joining date
      daysSinceDuty = dateDiffDays(dutyDate, officer.joining_date);
    }

    return {
      officer,
      lastDuty,
      routineCount,
      daysSinceDuty
    };
  });

  // Sort: Largest daysSinceDuty first (longest wait since last duty in this routine).
  // If tied or close (within 2 days difference), lower routineCount breaks the tie.
  scored.sort((a, b) => {
    const dayDiff = b.daysSinceDuty - a.daysSinceDuty;
    if (Math.abs(dayDiff) > 2) {
      return dayDiff;
    }
    // Secondary: lower duty count
    if (a.routineCount !== b.routineCount) {
      return a.routineCount - b.routineCount;
    }
    // Tie-breaker: officer ID
    return a.officer.id - b.officer.id;
  });

  return {
    officer: scored[0].officer,
    isReturnPriority: false,
    reason: `Fair rotation: ${scored[0].daysSinceDuty} days since last ${routineType} duty (count: ${scored[0].routineCount})`
  };
}

/**
 * Generate or recalculate a roster for a specified week starting on Monday.
 * Preserves manual edits, swaps, and punishments if regenerateOnlyDraft is true.
 */
function generateWeekRoster(weekStartDate, options = {}) {
  const { forceRegenerateAll = false } = options;
  const monday = getMondayOfWeek(weekStartDate);
  const sunday = addDays(monday, 6);

  // Check or create roster_weeks entry
  let week = db.prepare('SELECT * FROM roster_weeks WHERE week_start = ?').get(monday);
  if (!week) {
    const info = db.prepare(`
      INSERT INTO roster_weeks (week_start, week_end, status)
      VALUES (?, ?, 'DRAFT')
    `).run(monday, sunday);
    week = db.prepare('SELECT * FROM roster_weeks WHERE id = ?').get(info.lastInsertRowid);
  }

  // Iterate over 7 days: Monday to Sunday
  for (let i = 0; i < 7; i++) {
    const currentDate = addDays(monday, i);
    const dateClass = classifyDateRoutine(currentDate);

    // Check existing assignment
    let assignment = db.prepare(`
      SELECT * FROM duty_assignments
      WHERE roster_week_id = ? AND duty_date = ?
    `).get(week.id, currentDate);

    if (!assignment) {
      // Create draft assignment record
      const insertInfo = db.prepare(`
        INSERT INTO duty_assignments (
          roster_week_id, duty_date, routine_type, day_name,
          is_holiday, holiday_name, assigned_officer_id, original_officer_id
        ) VALUES (?, ?, ?, ?, ?, ?, NULL, NULL)
      `).run(
        week.id,
        currentDate,
        dateClass.routine_type,
        dateClass.day_name,
        dateClass.is_holiday,
        dateClass.holiday_name
      );
      assignment = db.prepare('SELECT * FROM duty_assignments WHERE id = ?').get(insertInfo.lastInsertRowid);
    } else {
      // Update classification if holidays changed
      db.prepare(`
        UPDATE duty_assignments
        SET routine_type = ?, is_holiday = ?, holiday_name = ?, day_name = ?
        WHERE id = ?
      `).run(
        dateClass.routine_type,
        dateClass.is_holiday,
        dateClass.holiday_name,
        dateClass.day_name,
        assignment.id
      );
    }

    // If locked by manual override, punishment, or swap, and not forceRegenerateAll: keep as-is
    if (!forceRegenerateAll && (assignment.is_manual_override || assignment.is_punishment || assignment.is_swap)) {
      continue;
    }

    // Auto-assign best eligible officer
    const selection = selectBestOfficer(currentDate, dateClass.routine_type, assignment.id);
    const assignedId = selection.officer ? selection.officer.id : null;

    db.prepare(`
      UPDATE duty_assignments
      SET assigned_officer_id = ?,
          original_officer_id = COALESCE(original_officer_id, ?),
          is_return_priority = ?,
          is_manual_override = 0,
          is_swap = 0,
          is_punishment = 0,
          swap_reason = NULL,
          swapped_with_officer_id = NULL
      WHERE id = ?
    `).run(
      assignedId,
      assignedId,
      selection.isReturnPriority ? 1 : 0,
      assignment.id
    );
  }

  return week;
}

/**
 * Finalize a roster week.
 * Updates officer historical counts and permanently writes to duty_history.
 */
function finalizeWeekRoster(rosterWeekId) {
  const week = db.prepare('SELECT * FROM roster_weeks WHERE id = ?').get(rosterWeekId);
  if (!week) {
    throw new Error('Roster week not found.');
  }

  const assignments = db.prepare(`
    SELECT * FROM duty_assignments
    WHERE roster_week_id = ?
    ORDER BY duty_date ASC
  `).all(rosterWeekId);

  // Wrap in SQLite transaction
  const finalizeTx = db.transaction(() => {
    for (const a of assignments) {
      if (!a.assigned_officer_id) {
        throw new Error(`Cannot finalize: Date ${a.duty_date} has no assigned officer.`);
      }

      // Record in duty_history (replace if existing)
      db.prepare(`
        INSERT INTO duty_history (duty_date, officer_id, routine_type, is_punishment, punishment_reason, roster_week_id)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(duty_date) DO UPDATE SET
          officer_id = excluded.officer_id,
          routine_type = excluded.routine_type,
          is_punishment = excluded.is_punishment,
          punishment_reason = excluded.punishment_reason,
          roster_week_id = excluded.roster_week_id
      `).run(
        a.duty_date,
        a.assigned_officer_id,
        a.routine_type,
        a.is_punishment,
        a.punishment_reason,
        rosterWeekId
      );

      // Increment officer duty counts
      // Ordinary duties increment their respective routine count
      if (a.routine_type === 'NORMAL') {
        db.prepare('UPDATE officers SET normal_duty_count = normal_duty_count + 1 WHERE id = ?').run(a.assigned_officer_id);
      } else {
        db.prepare('UPDATE officers SET sunday_duty_count = sunday_duty_count + 1 WHERE id = ?').run(a.assigned_officer_id);
      }
    }

    db.prepare(`
      UPDATE roster_weeks
      SET status = 'FINALIZED',
          finalized_at = datetime('now', 'localtime')
      WHERE id = ?
    `).run(rosterWeekId);
  });

  finalizeTx();
  return { success: true, message: 'Roster week successfully finalized and archived to history.' };
}

module.exports = {
  DAY_NAMES,
  getMondayOfWeek,
  classifyDateRoutine,
  getLastDutyDateInRoutine,
  getOfficerRoutineDutyCount,
  selectBestOfficer,
  generateWeekRoster,
  finalizeWeekRoster
};
