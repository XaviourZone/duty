const db = require('./db');

/**
 * Helper to calculate difference in calendar days between two YYYY-MM-DD strings.
 */
function dateDiffDays(dateStrA, dateStrB) {
  const dA = new Date(dateStrA + 'T00:00:00Z');
  const dB = new Date(dateStrB + 'T00:00:00Z');
  const diffTime = dA.getTime() - dB.getTime();
  return Math.round(diffTime / (1000 * 60 * 60 * 24));
}

/**
 * Helper to add days to a YYYY-MM-DD string.
 */
function addDays(dateStr, days) {
  const d = new Date(dateStr + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().split('T')[0];
}

/**
 * Check if a date falls inside [startDate, endDate] inclusive
 */
function isDateInRange(dateStr, startStr, endStr) {
  if (!startStr || !endStr) return false;
  return dateStr >= startStr && dateStr <= endStr;
}

/**
 * Check officer availability on a specific date for an ordinary assignment.
 * Returns { valid: boolean, errors: string[], warnings: string[] }
 */
function checkOfficerEligibility(officerId, targetDate, isPunishment = false, currentAssignmentId = null) {
  const errors = [];
  const warnings = [];

  const officer = db.prepare('SELECT * FROM officers WHERE id = ?').get(officerId);
  if (!officer) {
    return { valid: false, errors: ['Officer does not exist.'], warnings: [] };
  }

  // 1. Check Joining Date
  if (targetDate < officer.joining_date) {
    errors.push(`Officer ${officer.name} has not yet joined on ${targetDate} (Joining date: ${officer.joining_date}).`);
  }

  // 2. Check Transfer Date
  if (officer.status === 'TRANSFERRED_OUT' && officer.transfer_date && targetDate >= officer.transfer_date) {
    errors.push(`Officer ${officer.name} is transferred out as of ${officer.transfer_date} (Unavailable on ${targetDate}).`);
  }

  // 3. Check Leaves, Ty Duty, Prefix, Suffix
  const leaves = db.prepare('SELECT * FROM leaves WHERE officer_id = ?').all(officerId);
  for (const leave of leaves) {
    const isTyDuty = leave.type === 'TY_DUTY';
    const typeLabel = isTyDuty ? 'TEMPORARY DUTY (Ty Duty)' : 'LEAVE';
    if (isDateInRange(targetDate, leave.leave_start, leave.leave_end)) {
      errors.push(`Officer ${officer.name} is on ${typeLabel} from ${leave.leave_start} to ${leave.leave_end}.`);
    }
    if (!isTyDuty) {
      if (leave.prefix_start && leave.prefix_end && isDateInRange(targetDate, leave.prefix_start, leave.prefix_end)) {
        errors.push(`Officer ${officer.name} is unavailable during PREFIX holidays (${leave.prefix_start} to ${leave.prefix_end}).`);
      }
      if (leave.suffix_start && leave.suffix_end && isDateInRange(targetDate, leave.suffix_start, leave.suffix_end)) {
        errors.push(`Officer ${officer.name} is unavailable during SUFFIX holidays (${leave.suffix_start} to ${leave.suffix_end}).`);
      }

      // Section 5 Rule 3: Leave beginning on Monday -> officer must not be assigned on the preceding Sunday!
      const leaveStartDate = new Date(leave.leave_start + 'T00:00:00Z');
      const dayOfWeek = leaveStartDate.getUTCDay(); // 1 = Monday
      if (dayOfWeek === 1) {
        const precedingSunday = addDays(leave.leave_start, -1);
        if (targetDate === precedingSunday) {
          errors.push(`Officer ${officer.name} begins leave on Monday (${leave.leave_start}) and cannot be assigned on the preceding Sunday (${precedingSunday}).`);
        }
      }
    }
  }

  // 4. Consecutive-duty restriction:
  // An officer must not be assigned duty on consecutive calendar days (very next calendar day, diff = 1).
  // Officer on Friday can be on Sunday (diff = 2), but not Saturday (diff = 1).
  // Exception: Punishment duty may bypass this restriction.
  if (!isPunishment) {
    // Find all assigned dates for this officer within 2 days before and 2 days after
    const nearbyDuties = db.prepare(`
      SELECT da.id, da.duty_date, da.routine_type, da.is_punishment, rw.status as week_status
      FROM duty_assignments da
      JOIN roster_weeks rw ON da.roster_week_id = rw.id
      WHERE da.assigned_officer_id = ?
        AND da.duty_date != ?
        AND da.duty_date >= ?
        AND da.duty_date <= ?
        ${currentAssignmentId ? 'AND da.id != ' + currentAssignmentId : ''}
    `).all(officerId, targetDate, addDays(targetDate, -2), addDays(targetDate, 2));

    for (const duty of nearbyDuties) {
      const diff = Math.abs(dateDiffDays(targetDate, duty.duty_date));
      if (diff === 1) {
        errors.push(
          `Consecutive duty conflict: Officer ${officer.name} already has duty on ${duty.duty_date} (Duty on consecutive calendar day; not permitted).`
        );
      }
    }

    // Also check historical duty records outside unfinalized weeks
    const historyDuties = db.prepare(`
      SELECT duty_date, routine_type
      FROM duty_history
      WHERE officer_id = ?
        AND duty_date != ?
        AND duty_date >= ?
        AND duty_date <= ?
    `).all(officerId, targetDate, addDays(targetDate, -2), addDays(targetDate, 2));

    for (const hd of historyDuties) {
      const alreadyInAssignments = nearbyDuties.some(d => d.duty_date === hd.duty_date);
      if (!alreadyInAssignments) {
        const diff = Math.abs(dateDiffDays(targetDate, hd.duty_date));
        if (diff === 1) {
          errors.push(
            `Consecutive duty conflict: Officer ${officer.name} performed historical duty on ${hd.duty_date} (Duty on consecutive calendar day; not permitted).`
          );
        }
      }
    }
  } else {
    warnings.push(`Punishment duty assigned to Officer ${officer.name}. Consecutive rest rules are bypassed per Rule 2.`);
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings
  };
}

/**
 * Validate an entire roster week before finalization or review.
 */
function validateWeekRoster(rosterWeekId) {
  const week = db.prepare('SELECT * FROM roster_weeks WHERE id = ?').get(rosterWeekId);
  if (!week) {
    return { valid: false, errors: ['Roster week not found.'], warnings: [] };
  }

  const assignments = db.prepare(`
    SELECT da.*, o.name as officer_name, o.rank as officer_rank, o.service_id
    FROM duty_assignments da
    LEFT JOIN officers o ON da.assigned_officer_id = o.id
    WHERE da.roster_week_id = ?
    ORDER BY da.duty_date ASC
  `).all(rosterWeekId);

  const errors = [];
  const warnings = [];

  // Check that every day in the week has an assignment
  if (assignments.length === 0) {
    errors.push('No duty assignments found for this week.');
    return { valid: false, errors, warnings };
  }

  // Check each assignment
  for (const a of assignments) {
    if (!a.assigned_officer_id) {
      errors.push(`Duty on ${a.duty_date} (${a.day_name}) has no assigned officer.`);
      continue;
    }

    const check = checkOfficerEligibility(
      a.assigned_officer_id,
      a.duty_date,
      a.is_punishment === 1,
      a.id
    );

    if (!check.valid) {
      check.errors.forEach(err => errors.push(`[${a.duty_date} ${a.day_name}] ${err}`));
    }
    check.warnings.forEach(w => warnings.push(`[${a.duty_date} ${a.day_name}] ${w}`));

    // If punishment duty, verify reason is provided
    if (a.is_punishment === 1 && (!a.punishment_reason || !a.punishment_reason.trim())) {
      errors.push(`[${a.duty_date}] Punishment duty must have a recorded reason.`);
    }

    // Check swap consistency
    if (a.is_swap === 1) {
      if (!a.swapped_with_officer_id) {
        warnings.push(`[${a.duty_date}] Swap recorded but swap partner officer is missing.`);
      }
    }
  }

  // Check returning officers on this week
  const returningOfficers = db.prepare(`
    SELECT l.*, o.name as officer_name
    FROM leaves l
    JOIN officers o ON l.officer_id = o.id
    WHERE l.return_date >= ? AND l.return_date <= ?
  `).all(week.week_start, week.week_end);

  for (const ret of returningOfficers) {
    const assignedOnReturn = assignments.find(
      a => a.duty_date === ret.return_date && a.assigned_officer_id === ret.officer_id
    );
    if (!assignedOnReturn) {
      // Check if they were ineligible or if another officer was placed
      const eligibility = checkOfficerEligibility(ret.officer_id, ret.return_date, false);
      if (eligibility.valid) {
        warnings.push(
          `Return-to-duty notice: Officer ${ret.officer_name} returned on ${ret.return_date} but was not assigned duty on that date.`
        );
      } else {
        warnings.push(
          `Return-to-duty conflict: Officer ${ret.officer_name} returned on ${ret.return_date} but could not be assigned due to restriction: ${eligibility.errors.join('; ')}`
        );
      }
    }
  }

  return {
    valid: errors.length === 0,
    errors,
    warnings,
    assignmentCount: assignments.length
  };
}

module.exports = {
  dateDiffDays,
  addDays,
  isDateInRange,
  checkOfficerEligibility,
  validateWeekRoster
};
