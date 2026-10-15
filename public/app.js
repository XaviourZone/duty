/**
 * Automated Duty Roster Management System
 * Standalone Client-Side Application for GitHub Pages & Local Hosting
 *
 * Implements:
 * - Two independent duty routines (Normal & Sunday Routine)
 * - Cross-routine consecutive-duty rest rules (min 2 days gap)
 * - Punishment duty exceptions with recorded reasons
 * - Leave management with prefix, suffix, Monday leave rule, and return priority
 * - Fair duty distribution (new joiners not overloaded)
 * - Duty swapping with live rest validation
 * - Officer joining and transfer management
 * - Client-side Excel template generation, import, and export (via SheetJS)
 * - Persistent browser storage (localStorage)
 */

(function () {
  'use strict';

  // --- Constants & Seed Data ---
  const STORAGE_KEY = 'NAVY_DUTY_ROSTER_DB_V1';
  const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  // Initial Seed Dataset (Military Station Sample)
  const INITIAL_SEED_DATA = {
    settings: {
      admin_password_hash: 'admin@123' // Plain or hashed; easily changeable
    },
    officers: [
      { id: 1, service_id: 'IC-101', name: 'Arjun Mehta', rank: 'Lt Cdr', status: 'ACTIVE', joining_date: '2025-01-01', transfer_date: null, normal_duty_count: 24, sunday_duty_count: 10 },
      { id: 2, service_id: 'IC-102', name: 'Vikram Malhotra', rank: 'Lt', status: 'ACTIVE', joining_date: '2025-02-15', transfer_date: null, normal_duty_count: 22, sunday_duty_count: 9 },
      { id: 3, service_id: 'IC-103', name: 'Sneha Rao', rank: 'Lt Cdr', status: 'ACTIVE', joining_date: '2025-03-01', transfer_date: null, normal_duty_count: 25, sunday_duty_count: 11 },
      { id: 4, service_id: 'IC-104', name: 'Rajesh Nair', rank: 'Lt', status: 'ACTIVE', joining_date: '2025-04-10', transfer_date: null, normal_duty_count: 21, sunday_duty_count: 9 },
      { id: 5, service_id: 'IC-105', name: 'Pooja Sharma', rank: 'Lt', status: 'ACTIVE', joining_date: '2025-05-01', transfer_date: null, normal_duty_count: 20, sunday_duty_count: 8 },
      { id: 6, service_id: 'IC-106', name: 'Rohan Gupta', rank: 'Sub Lt', status: 'ACTIVE', joining_date: '2026-09-15', transfer_date: null, normal_duty_count: 1, sunday_duty_count: 1 }, // New joiner with low count
      { id: 7, service_id: 'IC-107', name: 'Manoj Deshmukh', rank: 'Lt Cdr', status: 'ACTIVE', joining_date: '2025-06-01', transfer_date: null, normal_duty_count: 19, sunday_duty_count: 8 },
      { id: 8, service_id: 'IC-108', name: 'Amitav Ghosh', rank: 'Lt', status: 'ACTIVE', joining_date: '2025-07-01', transfer_date: null, normal_duty_count: 18, sunday_duty_count: 7 },
      { id: 9, service_id: 'IC-109', name: 'Kiran Bedi', rank: 'Lt Cdr', status: 'TRANSFERRED_OUT', joining_date: '2024-01-01', transfer_date: '2026-10-01', normal_duty_count: 35, sunday_duty_count: 15 } // Transferred out officer
    ],
    holidays: [
      { id: 1, holiday_date: '2026-10-20', description: 'National Defence Day Holiday' },
      { id: 2, holiday_date: '2026-11-04', description: 'Navy Day Observance' }
    ],
    leaves: [
      {
        id: 1,
        officer_id: 4, // IC-104
        leave_start: '2026-10-12',
        leave_end: '2026-10-16',
        prefix_start: '2026-10-10',
        prefix_end: '2026-10-11',
        suffix_start: '2026-10-17',
        suffix_end: '2026-10-18',
        return_date: '2026-10-19',
        remarks: 'Annual Leave with Prefix & Suffix weekend'
      },
      {
        id: 2,
        officer_id: 5, // IC-105
        leave_start: '2026-10-26', // Starts on Monday -> Sunday Oct 25 blocked per Rule 5.3
        leave_end: '2026-10-30',
        prefix_start: '2026-10-24',
        prefix_end: '2026-10-25',
        suffix_start: null,
        suffix_end: null,
        return_date: '2026-11-02',
        remarks: 'Leave starting Monday Oct 26 (Preceding Sunday Oct 25 restricted)'
      }
    ],
    roster_weeks: [
      {
        id: 1,
        week_start: '2026-10-05',
        week_end: '2026-10-11',
        status: 'FINALIZED',
        finalized_at: '2026-10-04 18:00:00',
        notes: 'Finalized base roster'
      }
    ],
    duty_assignments: [
      { id: 1, roster_week_id: 1, duty_date: '2026-10-05', routine_type: 'NORMAL', day_name: 'Monday', is_holiday: 0, holiday_name: null, assigned_officer_id: 1, original_officer_id: 1, swapped_with_officer_id: null, is_swap: 0, swap_reason: null, is_punishment: 0, punishment_reason: null, is_manual_override: 0, is_return_priority: 0 },
      { id: 2, roster_week_id: 1, duty_date: '2026-10-06', routine_type: 'NORMAL', day_name: 'Tuesday', is_holiday: 0, holiday_name: null, assigned_officer_id: 2, original_officer_id: 2, swapped_with_officer_id: null, is_swap: 0, swap_reason: null, is_punishment: 0, punishment_reason: null, is_manual_override: 0, is_return_priority: 0 },
      { id: 3, roster_week_id: 1, duty_date: '2026-10-07', routine_type: 'NORMAL', day_name: 'Wednesday', is_holiday: 0, holiday_name: null, assigned_officer_id: 3, original_officer_id: 3, swapped_with_officer_id: null, is_swap: 0, swap_reason: null, is_punishment: 0, punishment_reason: null, is_manual_override: 0, is_return_priority: 0 },
      { id: 4, roster_week_id: 1, duty_date: '2026-10-08', routine_type: 'NORMAL', day_name: 'Thursday', is_holiday: 0, holiday_name: null, assigned_officer_id: 7, original_officer_id: 7, swapped_with_officer_id: null, is_swap: 0, swap_reason: null, is_punishment: 0, punishment_reason: null, is_manual_override: 0, is_return_priority: 0 },
      { id: 5, roster_week_id: 1, duty_date: '2026-10-09', routine_type: 'NORMAL', day_name: 'Friday', is_holiday: 0, holiday_name: null, assigned_officer_id: 8, original_officer_id: 8, swapped_with_officer_id: null, is_swap: 0, swap_reason: null, is_punishment: 0, punishment_reason: null, is_manual_override: 0, is_return_priority: 0 },
      { id: 6, roster_week_id: 1, duty_date: '2026-10-10', routine_type: 'SUNDAY', day_name: 'Saturday', is_holiday: 0, holiday_name: null, assigned_officer_id: 2, original_officer_id: 2, swapped_with_officer_id: null, is_swap: 0, swap_reason: null, is_punishment: 0, punishment_reason: null, is_manual_override: 0, is_return_priority: 0 },
      { id: 7, roster_week_id: 1, duty_date: '2026-10-11', routine_type: 'SUNDAY', day_name: 'Sunday', is_holiday: 0, holiday_name: null, assigned_officer_id: 7, original_officer_id: 7, swapped_with_officer_id: null, is_swap: 0, swap_reason: null, is_punishment: 0, punishment_reason: null, is_manual_override: 0, is_return_priority: 0 }
    ],
    duty_history: [
      { id: 1, duty_date: '2026-10-05', officer_id: 1, routine_type: 'NORMAL', is_punishment: 0, punishment_reason: null, roster_week_id: 1 },
      { id: 2, duty_date: '2026-10-06', officer_id: 2, routine_type: 'NORMAL', is_punishment: 0, punishment_reason: null, roster_week_id: 1 },
      { id: 3, duty_date: '2026-10-07', officer_id: 3, routine_type: 'NORMAL', is_punishment: 0, punishment_reason: null, roster_week_id: 1 },
      { id: 4, duty_date: '2026-10-08', officer_id: 7, routine_type: 'NORMAL', is_punishment: 0, punishment_reason: null, roster_week_id: 1 },
      { id: 5, duty_date: '2026-10-09', officer_id: 8, routine_type: 'NORMAL', is_punishment: 0, punishment_reason: null, roster_week_id: 1 },
      { id: 6, duty_date: '2026-10-10', officer_id: 2, routine_type: 'SUNDAY', is_punishment: 0, punishment_reason: null, roster_week_id: 1 },
      { id: 7, duty_date: '2026-10-11', officer_id: 7, routine_type: 'SUNDAY', is_punishment: 0, punishment_reason: null, roster_week_id: 1 }
    ]
  };

  // --- Storage Engine ---
  class StorageEngine {
    static load() {
      try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) {
          const fresh = JSON.parse(JSON.stringify(INITIAL_SEED_DATA));
          StorageEngine.save(fresh);
          return fresh;
        }
        return JSON.parse(raw);
      } catch (e) {
        console.error('Failed to parse storage, re-initializing seed', e);
        const fresh = JSON.parse(JSON.stringify(INITIAL_SEED_DATA));
        StorageEngine.save(fresh);
        return fresh;
      }
    }

    static save(data) {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
      } catch (e) {
        console.error('Failed to save to localStorage', e);
      }
    }

    static reset() {
      const fresh = JSON.parse(JSON.stringify(INITIAL_SEED_DATA));
      StorageEngine.save(fresh);
      return fresh;
    }
  }

  // --- Date Math Helpers ---
  function dateDiffDays(dateStrA, dateStrB) {
    const dA = new Date(dateStrA + 'T00:00:00Z');
    const dB = new Date(dateStrB + 'T00:00:00Z');
    return Math.round((dA.getTime() - dB.getTime()) / (1000 * 60 * 60 * 24));
  }

  function addDays(dateStr, days) {
    const d = new Date(dateStr + 'T00:00:00Z');
    d.setUTCDate(d.getUTCDate() + days);
    return d.toISOString().split('T')[0];
  }

  function isDateInRange(targetDate, startDate, endDate) {
    if (!startDate || !endDate) return false;
    return targetDate >= startDate && targetDate <= endDate;
  }

  function getMondayOfWeek(dateStr) {
    const d = new Date(dateStr + 'T00:00:00Z');
    const day = d.getUTCDay(); // 0 is Sunday, 1 is Monday...
    const diff = day === 0 ? -6 : 1 - day;
    d.setUTCDate(d.getUTCDate() + diff);
    return d.toISOString().split('T')[0];
  }

  function formatDisplayDate(dateStr) {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    if (parts.length !== 3) return dateStr;
    const year = parts[0];
    const month = parseInt(parts[1], 10);
    const day = parseInt(parts[2], 10);
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return `${day} ${months[month - 1]} ${year}`;
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // --- Validation & Eligibility Engine ---
  function checkOfficerEligibility(db, officerId, targetDate, isPunishment = false, currentAssignmentId = null) {
    const errors = [];
    const warnings = [];

    const officer = db.officers.find(o => o.id === parseInt(officerId, 10));
    if (!officer) {
      return { valid: false, errors: ['Officer does not exist.'], warnings: [] };
    }

    // 1. Joining Date
    if (targetDate < officer.joining_date) {
      errors.push(`Officer ${officer.name} has not yet joined on ${targetDate} (Joining date: ${officer.joining_date}).`);
    }

    // 2. Transfer Date
    if (officer.status === 'TRANSFERRED_OUT' && officer.transfer_date && targetDate >= officer.transfer_date) {
      errors.push(`Officer ${officer.name} is transferred out as of ${officer.transfer_date} (Unavailable on ${targetDate}).`);
    }

    // 3. Leaves, Prefix, Suffix
    const officerLeaves = db.leaves.filter(l => l.officer_id === officer.id);
    for (const leave of officerLeaves) {
      if (isDateInRange(targetDate, leave.leave_start, leave.leave_end)) {
        errors.push(`Officer ${officer.name} is on LEAVE from ${leave.leave_start} to ${leave.leave_end}.`);
      }
      if (leave.prefix_start && leave.prefix_end && isDateInRange(targetDate, leave.prefix_start, leave.prefix_end)) {
        errors.push(`Officer ${officer.name} is unavailable during PREFIX holidays (${leave.prefix_start} to ${leave.prefix_end}).`);
      }
      if (leave.suffix_start && leave.suffix_end && isDateInRange(targetDate, leave.suffix_start, leave.suffix_end)) {
        errors.push(`Officer ${officer.name} is unavailable during SUFFIX holidays (${leave.suffix_start} to ${leave.suffix_end}).`);
      }

      // Monday Leave rule (Rule 5.3): Preceding Sunday blocked
      const leaveStartDate = new Date(leave.leave_start + 'T00:00:00Z');
      if (leaveStartDate.getUTCDay() === 1) { // 1 = Monday
        const precedingSunday = addDays(leave.leave_start, -1);
        if (targetDate === precedingSunday) {
          errors.push(`Officer ${officer.name} begins leave on Monday (${leave.leave_start}) and cannot be assigned on the preceding Sunday (${precedingSunday}).`);
        }
      }
    }

    // 4. Consecutive-duty restriction:
    // An officer must not be assigned duty on consecutive calendar days (very next calendar day, diff = 1).
    // Officer on Friday can be on Sunday (diff = 2), but not Saturday (diff = 1).
    if (!isPunishment) {
      const minDate = addDays(targetDate, -2);
      const maxDate = addDays(targetDate, 2);

      // Check current duty assignments
      for (const a of db.duty_assignments) {
        if (a.assigned_officer_id === officer.id && a.duty_date !== targetDate && a.duty_date >= minDate && a.duty_date <= maxDate) {
          if (currentAssignmentId && a.id === parseInt(currentAssignmentId, 10)) continue;
          const diff = Math.abs(dateDiffDays(targetDate, a.duty_date));
          if (diff === 1) {
            errors.push(
              `Consecutive duty conflict: Officer ${officer.name} already has duty on ${a.duty_date} (Duty on consecutive calendar day; not permitted).`
            );
          }
        }
      }

      // Check historical duties
      for (const h of db.duty_history) {
        if (h.officer_id === officer.id && h.duty_date !== targetDate && h.duty_date >= minDate && h.duty_date <= maxDate) {
          const already = db.duty_assignments.some(a => a.duty_date === h.duty_date && a.assigned_officer_id === officer.id);
          if (!already) {
            const diff = Math.abs(dateDiffDays(targetDate, h.duty_date));
            if (diff === 1) {
              errors.push(
                `Consecutive duty conflict: Officer ${officer.name} performed historical duty on ${h.duty_date} (Duty on consecutive calendar day; not permitted).`
              );
            }
          }
        }
      }
    } else {
      warnings.push(`Punishment duty assigned to Officer ${officer.name}. Consecutive rest rules bypassed per Rule 2.`);
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  function validateWeek(db, weekId) {
    const week = db.roster_weeks.find(w => w.id === weekId);
    if (!week) return { valid: false, errors: ['Roster week not found.'], warnings: [] };

    const assignments = db.duty_assignments
      .filter(a => a.roster_week_id === weekId)
      .sort((a, b) => a.duty_date.localeCompare(b.duty_date));

    const errors = [];
    const warnings = [];

    if (assignments.length === 0) {
      return { valid: false, errors: ['No duty assignments found for this week.'], warnings: [] };
    }

    for (const a of assignments) {
      if (!a.assigned_officer_id) {
        errors.push(`Duty on ${a.duty_date} (${a.day_name}) has no assigned officer.`);
        continue;
      }

      const check = checkOfficerEligibility(db, a.assigned_officer_id, a.duty_date, a.is_punishment === 1, a.id);
      if (!check.valid) {
        check.errors.forEach(err => errors.push(`[${a.duty_date} ${a.day_name}] ${err}`));
      }
      check.warnings.forEach(w => warnings.push(`[${a.duty_date} ${a.day_name}] ${w}`));

      if (a.is_punishment === 1 && (!a.punishment_reason || !a.punishment_reason.trim())) {
        errors.push(`[${a.duty_date}] Punishment duty must have a recorded reason.`);
      }
    }

    // Returning officers notice
    const returningLeaves = db.leaves.filter(l => l.return_date >= week.week_start && l.return_date <= week.week_end);
    for (const ret of returningLeaves) {
      const officer = db.officers.find(o => o.id === ret.officer_id);
      const assigned = assignments.find(a => a.duty_date === ret.return_date && a.assigned_officer_id === ret.officer_id);
      if (!assigned && officer) {
        const check = checkOfficerEligibility(db, officer.id, ret.return_date, false);
        if (check.valid) {
          warnings.push(`Return-to-duty notice: Officer ${officer.name} returned on ${ret.return_date} but was not assigned duty.`);
        } else {
          warnings.push(`Return-to-duty conflict: Officer ${officer.name} returned on ${ret.return_date} but could not be assigned: ${check.errors.join('; ')}`);
        }
      }
    }

    return { valid: errors.length === 0, errors, warnings };
  }

  // --- Scheduler Engine ---
  function classifyDateRoutine(db, dateStr) {
    const holiday = db.holidays.find(h => h.holiday_date === dateStr);
    const d = new Date(dateStr + 'T00:00:00Z');
    const dayIndex = d.getUTCDay();
    const dayName = DAY_NAMES[dayIndex];

    if (holiday) {
      return { routine_type: 'SUNDAY', is_holiday: 1, holiday_name: holiday.description, day_name: dayName };
    }
    if (dayIndex === 0 || dayIndex === 6) {
      return { routine_type: 'SUNDAY', is_holiday: 0, holiday_name: null, day_name: dayName };
    }
    return { routine_type: 'NORMAL', is_holiday: 0, holiday_name: null, day_name: dayName };
  }

  function getLastDutyDateInRoutine(db, officerId, routineType, beforeDate) {
    let latest = null;

    // Check duty_assignments
    for (const a of db.duty_assignments) {
      if (a.assigned_officer_id === officerId && a.routine_type === routineType && a.duty_date < beforeDate) {
        if (!latest || a.duty_date > latest) latest = a.duty_date;
      }
    }

    // Check duty_history
    for (const h of db.duty_history) {
      if (h.officer_id === officerId && h.routine_type === routineType && h.duty_date < beforeDate) {
        if (!latest || h.duty_date > latest) latest = h.duty_date;
      }
    }

    return latest;
  }

  function getOfficerRoutineDutyCount(db, officerId, routineType, beforeDate) {
    const officer = db.officers.find(o => o.id === officerId);
    if (!officer) return 0;
    const baseCount = routineType === 'NORMAL' ? officer.normal_duty_count : officer.sunday_duty_count;

    let unfinalizedCount = 0;
    for (const a of db.duty_assignments) {
      if (a.assigned_officer_id === officerId && a.routine_type === routineType && a.duty_date < beforeDate) {
        const week = db.roster_weeks.find(w => w.id === a.roster_week_id);
        if (week && week.status === 'DRAFT') unfinalizedCount++;
      }
    }

    return baseCount + unfinalizedCount;
  }

  function selectBestOfficer(db, dutyDate, routineType, currentAssignmentId = null) {
    // 1. All active officers on dutyDate
    const candidates = db.officers.filter(o => {
      if (dutyDate < o.joining_date) return false;
      if (o.status === 'TRANSFERRED_OUT' && o.transfer_date && dutyDate >= o.transfer_date) return false;
      return true;
    });

    if (candidates.length === 0) return { officer: null, isReturnPriority: false };

    // 2. Filter by eligibility
    const eligible = candidates.filter(o => {
      const check = checkOfficerEligibility(db, o.id, dutyDate, false, currentAssignmentId);
      return check.valid;
    });

    if (eligible.length === 0) return { officer: null, isReturnPriority: false };

    // 3. Return from leave priority
    const returningLeaves = db.leaves.filter(l => l.return_date === dutyDate);
    const returningIds = new Set(returningLeaves.map(l => l.officer_id));
    const eligibleReturning = eligible.filter(o => returningIds.has(o.id));

    if (eligibleReturning.length > 0) {
      eligibleReturning.sort((a, b) => {
        const lastA = getLastDutyDateInRoutine(db, a.id, routineType, dutyDate) || a.joining_date;
        const lastB = getLastDutyDateInRoutine(db, b.id, routineType, dutyDate) || b.joining_date;
        return lastA.localeCompare(lastB);
      });
      return { officer: eligibleReturning[0], isReturnPriority: true };
    }

    // 4. Fair rotation scoring:
    // Pacing benchmarks new joiners to their joining date so they aren't repeatedly assigned
    const scored = eligible.map(officer => {
      const lastDuty = getLastDutyDateInRoutine(db, officer.id, routineType, dutyDate);
      const routineCount = getOfficerRoutineDutyCount(db, officer.id, routineType, dutyDate);
      const daysSinceDuty = lastDuty ? dateDiffDays(dutyDate, lastDuty) : dateDiffDays(dutyDate, officer.joining_date);

      return { officer, lastDuty, routineCount, daysSinceDuty };
    });

    scored.sort((a, b) => {
      const dayDiff = b.daysSinceDuty - a.daysSinceDuty;
      if (Math.abs(dayDiff) > 2) return dayDiff;
      if (a.routineCount !== b.routineCount) return a.routineCount - b.routineCount;
      return a.officer.id - b.officer.id;
    });

    return { officer: scored[0].officer, isReturnPriority: false };
  }

  function generateWeekRoster(db, weekStartDate, forceAll = false) {
    const monday = getMondayOfWeek(weekStartDate);
    const sunday = addDays(monday, 6);

    let week = db.roster_weeks.find(w => w.week_start === monday);
    if (!week) {
      const maxId = db.roster_weeks.reduce((max, w) => Math.max(max, w.id), 0);
      week = {
        id: maxId + 1,
        week_start: monday,
        week_end: sunday,
        status: 'DRAFT',
        finalized_at: null,
        notes: null
      };
      db.roster_weeks.push(week);
    }

    for (let i = 0; i < 7; i++) {
      const currentDate = addDays(monday, i);
      const dateClass = classifyDateRoutine(db, currentDate);

      let assignment = db.duty_assignments.find(a => a.roster_week_id === week.id && a.duty_date === currentDate);
      if (!assignment) {
        const maxAssignId = db.duty_assignments.reduce((max, a) => Math.max(max, a.id), 0);
        assignment = {
          id: maxAssignId + 1,
          roster_week_id: week.id,
          duty_date: currentDate,
          routine_type: dateClass.routine_type,
          day_name: dateClass.day_name,
          is_holiday: dateClass.is_holiday,
          holiday_name: dateClass.holiday_name,
          assigned_officer_id: null,
          original_officer_id: null,
          swapped_with_officer_id: null,
          is_swap: 0,
          swap_reason: null,
          is_punishment: 0,
          punishment_reason: null,
          is_manual_override: 0,
          is_return_priority: 0
        };
        db.duty_assignments.push(assignment);
      } else {
        assignment.routine_type = dateClass.routine_type;
        assignment.is_holiday = dateClass.is_holiday;
        assignment.holiday_name = dateClass.holiday_name;
        assignment.day_name = dateClass.day_name;
      }

      if (!forceAll && (assignment.is_manual_override || assignment.is_punishment || assignment.is_swap)) {
        continue;
      }

      const selection = selectBestOfficer(db, currentDate, dateClass.routine_type, assignment.id);
      const assignedId = selection.officer ? selection.officer.id : null;

      assignment.assigned_officer_id = assignedId;
      assignment.original_officer_id = assignment.original_officer_id || assignedId;
      assignment.is_return_priority = selection.isReturnPriority ? 1 : 0;
      assignment.is_manual_override = 0;
      assignment.is_swap = 0;
      assignment.is_punishment = 0;
      assignment.swap_reason = null;
      assignment.swapped_with_officer_id = null;
    }

    StorageEngine.save(db);
    return week;
  }

  function finalizeWeekRoster(db, weekId) {
    const week = db.roster_weeks.find(w => w.id === weekId);
    if (!week) throw new Error('Week not found.');

    const assignments = db.duty_assignments.filter(a => a.roster_week_id === weekId);
    for (const a of assignments) {
      if (!a.assigned_officer_id) {
        throw new Error(`Cannot finalize: Date ${a.duty_date} has no assigned officer.`);
      }

      // Add to duty_history
      const existingHistoryIndex = db.duty_history.findIndex(h => h.duty_date === a.duty_date);
      const historyItem = {
        id: existingHistoryIndex >= 0 ? db.duty_history[existingHistoryIndex].id : db.duty_history.length + 1,
        duty_date: a.duty_date,
        officer_id: a.assigned_officer_id,
        routine_type: a.routine_type,
        is_punishment: a.is_punishment,
        punishment_reason: a.punishment_reason,
        roster_week_id: weekId
      };

      if (existingHistoryIndex >= 0) {
        db.duty_history[existingHistoryIndex] = historyItem;
      } else {
        db.duty_history.push(historyItem);
      }

      // Increment officer duty counts
      const officer = db.officers.find(o => o.id === a.assigned_officer_id);
      if (officer) {
        if (a.routine_type === 'NORMAL') {
          officer.normal_duty_count++;
        } else {
          officer.sunday_duty_count++;
        }
      }
    }

    week.status = 'FINALIZED';
    week.finalized_at = new Date().toISOString().replace('T', ' ').substring(0, 19);

    StorageEngine.save(db);
    return { success: true };
  }

  // --- Client-side Excel Operations via SheetJS ---
  function downloadExcelTemplate() {
    if (typeof XLSX === 'undefined') {
      alert('Excel library (SheetJS) is loading. Please check your internet connection or reload.');
      return;
    }

    const wb = XLSX.utils.book_new();

    const officersData = [
      { Service_ID: 'IC-1001', Name: 'A. Sharma', Rank: 'Lt Cdr', Status: 'ACTIVE', Joining_Date: '2026-01-01', Transfer_Date: '', Normal_Duty_Count: 14, Sunday_Duty_Count: 6 },
      { Service_ID: 'IC-1002', Name: 'R. Verma', Rank: 'Lt', Status: 'ACTIVE', Joining_Date: '2026-01-15', Transfer_Date: '', Normal_Duty_Count: 12, Sunday_Duty_Count: 5 },
      { Service_ID: 'IC-1003', Name: 'S. Nair', Rank: 'Lt Cdr', Status: 'ACTIVE', Joining_Date: '2026-02-01', Transfer_Date: '', Normal_Duty_Count: 15, Sunday_Duty_Count: 6 },
      { Service_ID: 'IC-1004', Name: 'K. Patel', Rank: 'Lt', Status: 'ACTIVE', Joining_Date: '2026-03-01', Transfer_Date: '', Normal_Duty_Count: 10, Sunday_Duty_Count: 4 },
      { Service_ID: 'IC-1005', Name: 'V. Singh', Rank: 'Sub Lt', Status: 'ACTIVE', Joining_Date: '2026-09-01', Transfer_Date: '', Normal_Duty_Count: 2, Sunday_Duty_Count: 1 },
      { Service_ID: 'IC-1006', Name: 'M. Rao', Rank: 'Lt', Status: 'ACTIVE', Joining_Date: '2026-02-15', Transfer_Date: '', Normal_Duty_Count: 11, Sunday_Duty_Count: 5 },
      { Service_ID: 'IC-1007', Name: 'P. Joshi', Rank: 'Lt', Status: 'TRANSFERRED_OUT', Joining_Date: '2025-10-01', Transfer_Date: '2026-10-01', Normal_Duty_Count: 24, Sunday_Duty_Count: 10 }
    ];
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(officersData), 'Officers');

    const leavesData = [
      { Service_ID: 'IC-1004', Leave_Start: '2026-10-19', Leave_End: '2026-10-23', Prefix_Start: '2026-10-17', Prefix_End: '2026-10-18', Suffix_Start: '2026-10-24', Suffix_End: '2026-10-25', Return_Date: '2026-10-26', Remarks: 'Annual leave' }
    ];
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(leavesData), 'Leaves');

    const holidaysData = [
      { Holiday_Date: '2026-10-20', Description: 'Navratri Holiday' },
      { Holiday_Date: '2026-11-04', Description: 'Diwali Holiday' }
    ];
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(holidaysData), 'Holidays');

    const historyData = [
      { Duty_Date: '2026-10-05', Service_ID: 'IC-1001', Routine_Type: 'NORMAL', Is_Punishment: 0, Punishment_Reason: '' }
    ];
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(historyData), 'Historical_Duties');

    XLSX.writeFile(wb, 'officer_duty_roster_template.xlsx');
  }

  function exportCurrentRosterToExcel(db, weekId) {
    if (typeof XLSX === 'undefined') {
      alert('Excel library (SheetJS) is loading. Please reload the page.');
      return;
    }

    const week = db.roster_weeks.find(w => w.id === weekId);
    if (!week) return;

    const assignments = db.duty_assignments
      .filter(a => a.roster_week_id === weekId)
      .sort((a, b) => a.duty_date.localeCompare(b.duty_date));

    const rosterExportRows = assignments.map(a => {
      const officer = db.officers.find(o => o.id === a.assigned_officer_id);
      const swapOfficer = db.officers.find(o => o.id === a.swapped_with_officer_id);

      let swapDetails = '';
      if (a.is_swap && swapOfficer) {
        swapDetails = `Swapped with ${swapOfficer.rank} ${swapOfficer.name} - ${a.swap_reason || ''}`;
      }

      let remarks = '';
      if (a.is_punishment) {
        remarks = `Punishment Duty (${a.punishment_reason || ''})`;
      } else if (a.is_return_priority) {
        remarks = 'Return from Leave Priority';
      }

      return {
        Date: a.duty_date,
        Day: a.day_name,
        Routine: a.routine_type,
        Holiday: a.is_holiday ? (a.holiday_name || 'Declared Holiday') : '',
        Assigned_Officer: officer ? `${officer.rank} ${officer.name}` : 'Unassigned',
        Service_ID: officer ? officer.service_id : '',
        Swap_Details: swapDetails,
        Remarks: remarks
      };
    });

    const officersExportRows = db.officers.map(o => ({
      Service_ID: o.service_id,
      Name: o.name,
      Rank: o.rank,
      Status: o.status,
      Joining_Date: o.joining_date,
      Transfer_Date: o.transfer_date || '',
      Normal_Duty_Count: o.normal_duty_count,
      Sunday_Duty_Count: o.sunday_duty_count,
      Total_Duty_Count: o.normal_duty_count + o.sunday_duty_count
    }));

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(rosterExportRows), 'Duty_Roster');
    XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(officersExportRows), 'Officers_Summary');

    XLSX.writeFile(wb, `duty_roster_${week.week_start}_to_${week.week_end}.xlsx`);
  }

  function importExcelWorkbookBuffer(db, arrayBuffer) {
    if (typeof XLSX === 'undefined') {
      throw new Error('SheetJS is not loaded.');
    }

    const wb = XLSX.read(arrayBuffer, { type: 'array' });
    const results = {
      officersCreated: 0,
      officersUpdated: 0,
      leavesImported: 0,
      holidaysImported: 0,
      historyImported: 0
    };

    // 1. Process Officers
    if (wb.Sheets['Officers']) {
      const rows = XLSX.utils.sheet_to_json(wb.Sheets['Officers']);
      for (const row of rows) {
        const serviceId = String(row.Service_ID || row.service_id || '').trim();
        if (!serviceId) continue;

        const name = String(row.Name || row.name || '').trim();
        const rank = String(row.Rank || row.rank || 'Officer').trim();
        const status = String(row.Status || row.status || 'ACTIVE').trim().toUpperCase();
        const joiningDate = String(row.Joining_Date || row.joining_date || '2026-01-01').trim();
        const transferDate = row.Transfer_Date ? String(row.Transfer_Date).trim() : null;
        const normalCount = parseInt(row.Normal_Duty_Count ?? row.normal_duty_count ?? 0, 10);
        const sundayCount = parseInt(row.Sunday_Duty_Count ?? row.sunday_duty_count ?? 0, 10);

        const existing = db.officers.find(o => o.service_id === serviceId);
        if (existing) {
          existing.name = name || existing.name;
          existing.rank = rank || existing.rank;
          existing.status = status || existing.status;
          existing.joining_date = joiningDate || existing.joining_date;
          existing.transfer_date = transferDate !== undefined ? transferDate : existing.transfer_date;
          // Never reset or diminish duty counts
          existing.normal_duty_count = Math.max(existing.normal_duty_count, isNaN(normalCount) ? 0 : normalCount);
          existing.sunday_duty_count = Math.max(existing.sunday_duty_count, isNaN(sundayCount) ? 0 : sundayCount);
          results.officersUpdated++;
        } else {
          const newId = db.officers.reduce((max, o) => Math.max(max, o.id), 0) + 1;
          db.officers.push({
            id: newId,
            service_id: serviceId,
            name: name,
            rank: rank,
            status: status,
            joining_date: joiningDate,
            transfer_date: transferDate,
            normal_duty_count: isNaN(normalCount) ? 0 : normalCount,
            sunday_duty_count: isNaN(sundayCount) ? 0 : sundayCount
          });
          results.officersCreated++;
        }
      }
    }

    // 2. Process Holidays
    if (wb.Sheets['Holidays']) {
      const rows = XLSX.utils.sheet_to_json(wb.Sheets['Holidays']);
      for (const row of rows) {
        const holidayDate = String(row.Holiday_Date || row.holiday_date || '').trim();
        const desc = String(row.Description || row.description || 'Declared Holiday').trim();
        if (holidayDate) {
          const existing = db.holidays.find(h => h.holiday_date === holidayDate);
          if (existing) {
            existing.description = desc;
          } else {
            const newId = db.holidays.reduce((max, h) => Math.max(max, h.id), 0) + 1;
            db.holidays.push({ id: newId, holiday_date: holidayDate, description: desc });
          }
          results.holidaysImported++;
        }
      }
    }

    // 3. Process Leaves
    if (wb.Sheets['Leaves']) {
      const rows = XLSX.utils.sheet_to_json(wb.Sheets['Leaves']);
      for (const row of rows) {
        const serviceId = String(row.Service_ID || row.service_id || '').trim();
        const officer = db.officers.find(o => o.service_id === serviceId);
        if (!officer) continue;

        const leaveStart = String(row.Leave_Start || row.leave_start || '').trim();
        const leaveEnd = String(row.Leave_End || row.leave_end || '').trim();
        const returnDate = String(row.Return_Date || row.return_date || '').trim() || addDays(leaveEnd, 1);

        if (leaveStart && leaveEnd) {
          const newId = db.leaves.reduce((max, l) => Math.max(max, l.id), 0) + 1;
          db.leaves.push({
            id: newId,
            officer_id: officer.id,
            leave_start: leaveStart,
            leave_end: leaveEnd,
            prefix_start: row.Prefix_Start ? String(row.Prefix_Start).trim() : null,
            prefix_end: row.Prefix_End ? String(row.Prefix_End).trim() : null,
            suffix_start: row.Suffix_Start ? String(row.Suffix_Start).trim() : null,
            suffix_end: row.Suffix_End ? String(row.Suffix_End).trim() : null,
            return_date: returnDate,
            remarks: row.Remarks ? String(row.Remarks).trim() : ''
          });
          results.leavesImported++;
        }
      }
    }

    StorageEngine.save(db);
    return results;
  }

  // --- UI Controller & Event Binding ---
  document.addEventListener('DOMContentLoaded', () => {
    let db = StorageEngine.load();
    let currentWeekStart = getMondayOfWeek(new Date().toISOString().split('T')[0]);
    let currentWeek = null;
    let isWatchkeeper = sessionStorage.getItem('WATCHKEEPER_AUTH') === 'true';

    // DOM Elements
    const btnWatchkeeperPortal = document.getElementById('btnWatchkeeperPortal');
    const watchkeeperBtnText = document.getElementById('watchkeeperBtnText');
    const watchkeeperBtnIcon = document.getElementById('watchkeeperBtnIcon');
    const authStatusIndicator = document.getElementById('authStatusIndicator');
    const watchkeeperPanel = document.getElementById('watchkeeperPanel');
    const thActions = document.getElementById('thActions');

    const rosterWeekTitle = document.getElementById('rosterWeekTitle');
    const rosterStatusBadge = document.getElementById('rosterStatusBadge');
    const currentWeekRange = document.getElementById('currentWeekRange');
    const rosterTableBody = document.getElementById('rosterTableBody');
    const rosterConflictBanner = document.getElementById('rosterConflictBanner');
    const finalizedTimestamp = document.getElementById('finalizedTimestamp');

    const statNormalCount = document.getElementById('statNormalCount');
    const statSundayCount = document.getElementById('statSundayCount');
    const statHolidayCount = document.getElementById('statHolidayCount');

    const btnPrevWeek = document.getElementById('btnPrevWeek');
    const btnNextWeek = document.getElementById('btnNextWeek');
    const btnCurrentWeek = document.getElementById('btnCurrentWeek');
    const btnExportExcel = document.getElementById('btnExportExcel');
    const btnDownloadTemplate = document.getElementById('btnDownloadTemplate');
    const btnExportFullExcel = document.getElementById('btnExportFullExcel');

    const btnValidateRoster = document.getElementById('btnValidateRoster');
    const btnRecalculateRoster = document.getElementById('btnRecalculateRoster');
    const btnFinalizeRoster = document.getElementById('btnFinalizeRoster');
    const btnForceRegenerate = document.getElementById('btnForceRegenerate');
    const btnOpenSwapModal = document.getElementById('btnOpenSwapModal');

    // Theme Toggle Controller
    const btnThemeToggle = document.getElementById('btnThemeToggle');
    const themeToggleIcon = document.getElementById('themeToggleIcon');
    let currentTheme = localStorage.getItem('NAVY_THEME') || 'light';

    function applyTheme(theme) {
      document.documentElement.setAttribute('data-theme', theme);
      if (themeToggleIcon) {
        themeToggleIcon.textContent = theme === 'dark' ? '☀️' : '🌙';
      }
      localStorage.setItem('NAVY_THEME', theme);
    }
    applyTheme(currentTheme);

    if (btnThemeToggle) {
      btnThemeToggle.addEventListener('click', () => {
        currentTheme = currentTheme === 'dark' ? 'light' : 'dark';
        applyTheme(currentTheme);
        showToast(`Theme switched to ${currentTheme === 'dark' ? 'Dark Sapphire' : 'Crisp Executive Light'} mode`, 'info');
      });
    }

    // Modals
    const modalLogin = document.getElementById('modalLogin');
    const formLogin = document.getElementById('formLogin');
    const loginPassword = document.getElementById('loginPassword');
    const loginErrorMsg = document.getElementById('loginErrorMsg');

    const modalEditAssignment = document.getElementById('modalEditAssignment');
    const formEditAssignment = document.getElementById('formEditAssignment');
    const editAssignmentId = document.getElementById('editAssignmentId');
    const editDateDisplay = document.getElementById('editDateDisplay');
    const selectEditOfficer = document.getElementById('selectEditOfficer');
    const checkIsPunishment = document.getElementById('checkIsPunishment');
    const punishmentReasonGroup = document.getElementById('punishmentReasonGroup');
    const punishmentReasonInput = document.getElementById('punishmentReasonInput');

    const modalSwap = document.getElementById('modalSwap');
    const formSwap = document.getElementById('formSwap');
    const selectSwapSlot1 = document.getElementById('selectSwapSlot1');
    const selectSwapSlot2 = document.getElementById('selectSwapSlot2');
    const swapReasonInput = document.getElementById('swapReasonInput');
    const swapValidationFeedback = document.getElementById('swapValidationFeedback');

    const modalAddOfficer = document.getElementById('modalAddOfficer');
    const formAddOfficer = document.getElementById('formAddOfficer');
    const btnOpenAddOfficer = document.getElementById('btnOpenAddOfficer');

    const modalTransferOut = document.getElementById('modalTransferOut');
    const formTransferOut = document.getElementById('formTransferOut');
    const transferOfficerId = document.getElementById('transferOfficerId');
    const transferOfficerName = document.getElementById('transferOfficerName');
    const transferDateInput = document.getElementById('transferDateInput');

    const modalAddLeave = document.getElementById('modalAddLeave');
    const formAddLeave = document.getElementById('formAddLeave');
    const btnOpenAddLeave = document.getElementById('btnOpenAddLeave');
    const leaveOfficerSelect = document.getElementById('leaveOfficerSelect');

    const modalAddHoliday = document.getElementById('modalAddHoliday');
    const formAddHoliday = document.getElementById('formAddHoliday');
    const btnOpenAddHoliday = document.getElementById('btnOpenAddHoliday');

    const formChangePassword = document.getElementById('formChangePassword');
    const btnLogoutWatchkeeper = document.getElementById('btnLogoutWatchkeeper');
    const btnResetSampleData = document.getElementById('btnResetSampleData');

    const excelDropZone = document.getElementById('excelDropZone');
    const excelFileInput = document.getElementById('excelFileInput');
    const importResultBox = document.getElementById('importResultBox');

    function showToast(message, type = 'info') {
      const container = document.getElementById('toastContainer');
      const toast = document.createElement('div');
      toast.className = `toast toast-${type}`;
      let icon = 'ℹ️';
      if (type === 'success') icon = '✅';
      if (type === 'error') icon = '❌';
      if (type === 'warning') icon = '⚠️';

      toast.innerHTML = `<span>${icon}</span><span style="flex: 1;">${message}</span>`;
      container.appendChild(toast);

      setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transition = 'opacity 0.3s';
        setTimeout(() => toast.remove(), 300);
      }, 4000);
    }

    function openModal(modal) {
      if (modal) modal.classList.add('active');
    }

    function closeModal(modal) {
      if (modal) modal.classList.remove('active');
    }

    document.querySelectorAll('[data-close]').forEach(btn => {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        closeModal(document.getElementById(btn.getAttribute('data-close')));
      });
    });

    window.addEventListener('click', (e) => {
      if (e.target.classList.contains('modal-overlay')) closeModal(e.target);
    });

    // Tab switching
    document.querySelectorAll('.tab-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

        btn.classList.add('active');
        const targetId = btn.getAttribute('data-tab');
        const targetContent = document.getElementById(targetId);
        if (targetContent) targetContent.classList.add('active');

        if (targetId === 'tab-officers') renderOfficersTable();
        if (targetId === 'tab-leaves') renderLeavesTable();
        if (targetId === 'tab-holidays') renderHolidaysTable();
        if (targetId === 'tab-history') renderHistoryTables();
      });
    });

    function updateAuthUI() {
      if (isWatchkeeper) {
        authStatusIndicator.style.display = 'inline-flex';
        watchkeeperBtnText.textContent = 'Watchkeeper Panel';
        watchkeeperBtnIcon.textContent = '🛡️';
        watchkeeperPanel.style.display = 'block';
        thActions.style.display = 'table-cell';
      } else {
        authStatusIndicator.style.display = 'none';
        watchkeeperBtnText.textContent = 'Watchkeeper Login';
        watchkeeperBtnIcon.textContent = '🔒';
        watchkeeperPanel.style.display = 'none';
        thActions.style.display = 'none';
      }
      renderRosterView();
    }

    // Render Main Roster View
    function renderRosterView() {
      // Ensure week exists
      currentWeek = db.roster_weeks.find(w => w.week_start === currentWeekStart);
      if (!currentWeek) {
        currentWeek = generateWeekRoster(db, currentWeekStart, false);
      }

      rosterWeekTitle.textContent = `Duty Roster: Week of ${formatDisplayDate(currentWeek.week_start)}`;
      currentWeekRange.textContent = `${formatDisplayDate(currentWeek.week_start)} — ${formatDisplayDate(currentWeek.week_end)}`;

      if (currentWeek.status === 'FINALIZED') {
        rosterStatusBadge.className = 'badge badge-finalized';
        rosterStatusBadge.textContent = 'FINALIZED';
        finalizedTimestamp.textContent = `Finalized & Locked on ${currentWeek.finalized_at || 'Recent'}`;
      } else {
        rosterStatusBadge.className = 'badge badge-draft';
        rosterStatusBadge.textContent = 'DRAFT';
        finalizedTimestamp.textContent = 'Status: Unfinalized Draft (Pending Watchkeeper Finalization)';
      }

      const assignments = db.duty_assignments
        .filter(a => a.roster_week_id === currentWeek.id)
        .sort((a, b) => a.duty_date.localeCompare(b.duty_date));

      let normalCount = 0;
      let sundayCount = 0;
      let holidayCount = 0;

      rosterTableBody.innerHTML = '';

      assignments.forEach((a, idx) => {
        if (a.routine_type === 'NORMAL') normalCount++;
        if (a.routine_type === 'SUNDAY') sundayCount++;
        if (a.is_holiday) holidayCount++;

        const officer = db.officers.find(o => o.id === a.assigned_officer_id);
        const swapOfficer = db.officers.find(o => o.id === a.swapped_with_officer_id);

        const tr = document.createElement('tr');
        tr.style.animation = `fadeInSlideUp 0.35s cubic-bezier(0.16, 1, 0.3, 1) ${idx * 0.04}s both`;

        let holidayTag = a.is_holiday
          ? `<div style="font-size: 0.75rem; color: var(--purple); font-weight: 600;">★ ${escapeHtml(a.holiday_name || 'Declared Holiday')}</div>`
          : '';

        let routineBadgeClass = 'badge-normal';
        let routineText = 'Normal Routine';
        if (a.is_holiday) {
          routineBadgeClass = 'badge-holiday';
          routineText = 'Sunday Routine (Holiday)';
        } else if (a.routine_type === 'SUNDAY') {
          routineBadgeClass = 'badge-sunday';
          routineText = 'Sunday Routine';
        }

        let officerHtml = '';
        if (officer) {
          officerHtml = `
            <div class="officer-name-title">${escapeHtml(officer.rank)} ${escapeHtml(officer.name)}</div>
            <div class="officer-meta"><span>ID: ${escapeHtml(officer.service_id)}</span></div>
          `;
        } else {
          officerHtml = `<div style="color: var(--danger); font-weight: 600;">⚠️ Unassigned / Slot Empty</div>`;
        }

        let specialHtml = '';
        if (a.is_swap && swapOfficer) {
          specialHtml += `
            <div class="officer-swap-notice">
              ⇄ ${escapeHtml(officer ? `${officer.rank} ${officer.name}` : '')} (swapped with ${escapeHtml(swapOfficer.rank)} ${escapeHtml(swapOfficer.name)})
              <div style="font-size: 0.75rem; color: var(--text-muted);">${escapeHtml(a.swap_reason || '')}</div>
            </div>
          `;
        }
        if (a.is_punishment) {
          specialHtml += `<div class="punishment-notice">⚖️ Punishment Duty: ${escapeHtml(a.punishment_reason || 'Disciplinary')}</div>`;
        }
        if (a.is_return_priority && !a.is_swap && !a.is_punishment) {
          specialHtml += `<span class="badge badge-return" style="font-size: 0.7rem;">Return from Leave Priority</span>`;
        }
        if (!specialHtml) specialHtml = `<span style="color: var(--text-muted); font-size: 0.8rem;">Ordinary Allocation</span>`;

        let actionHtml = '';
        if (isWatchkeeper) {
          if (currentWeek.status === 'FINALIZED') {
            actionHtml = `<span style="font-size: 0.75rem; color: var(--text-muted);">Week Locked</span>`;
          } else {
            actionHtml = `
              <button class="btn btn-secondary btn-sm btn-edit-slot" data-id="${a.id}">
                ✏️ Reassign / Discipline
              </button>
            `;
          }
        }

        tr.innerHTML = `
          <td class="day-badge-col">
            <div class="day-title">${escapeHtml(a.day_name)}</div>
            <div class="date-subtitle">${formatDisplayDate(a.duty_date)}</div>
            ${holidayTag}
          </td>
          <td class="routine-col"><span class="badge ${routineBadgeClass}">${routineText}</span></td>
          <td class="officer-col">${officerHtml}</td>
          <td class="status-col">${specialHtml}</td>
          ${isWatchkeeper ? `<td class="action-col">${actionHtml}</td>` : ''}
        `;

        rosterTableBody.appendChild(tr);
      });

      statNormalCount.textContent = normalCount;
      statSundayCount.textContent = sundayCount;
      statHolidayCount.textContent = holidayCount;

      // Attach reassign buttons
      document.querySelectorAll('.btn-edit-slot').forEach(btn => {
        btn.addEventListener('click', () => {
          const assignId = parseInt(btn.getAttribute('data-id'), 10);
          openEditModal(assignId);
        });
      });

      // Validation check
      const validation = validateWeek(db, currentWeek.id);
      renderConflictBanner(validation);
    }

    function renderConflictBanner(validation) {
      if (!validation.valid && validation.errors.length > 0) {
        rosterConflictBanner.className = 'conflict-card error';
        rosterConflictBanner.style.display = 'block';
        let html = `<strong>⚠️ Mandatory Roster Conflicts Detected:</strong><ul class="conflict-list">`;
        validation.errors.forEach(err => html += `<li>${escapeHtml(err)}</li>`);
        html += `</ul>`;
        rosterConflictBanner.innerHTML = html;
      } else if (validation.warnings.length > 0) {
        rosterConflictBanner.className = 'conflict-card warning';
        rosterConflictBanner.style.display = 'block';
        let html = `<strong>ℹ️ Scheduling Notices:</strong><ul class="conflict-list">`;
        validation.warnings.forEach(w => html += `<li>${escapeHtml(w)}</li>`);
        html += `</ul>`;
        rosterConflictBanner.innerHTML = html;
      } else {
        rosterConflictBanner.className = 'conflict-card success';
        rosterConflictBanner.style.display = 'block';
        rosterConflictBanner.innerHTML = `<strong>✅ Roster Compliant:</strong> All consecutive rest restrictions, leave rules, and routine counts satisfied.`;
        setTimeout(() => {
          if (rosterConflictBanner.classList.contains('success')) {
            rosterConflictBanner.style.display = 'none';
          }
        }, 5000);
      }
    }

    // Edit Assignment Modal
    function openEditModal(assignId) {
      const a = db.duty_assignments.find(item => item.id === assignId);
      if (!a) return;

      editAssignmentId.value = a.id;
      editDateDisplay.textContent = `${a.day_name}, ${formatDisplayDate(a.duty_date)}`;

      selectEditOfficer.innerHTML = `<option value="">-- Select Eligible Officer --</option>`;
      db.officers.forEach(o => {
        const isSelected = o.id === a.assigned_officer_id;
        const statusSuffix = o.status === 'TRANSFERRED_OUT' ? ' [TRANSFERRED]' : '';
        selectEditOfficer.innerHTML += `
          <option value="${o.id}" ${isSelected ? 'selected' : ''}>
            ${escapeHtml(o.rank)} ${escapeHtml(o.name)} (${escapeHtml(o.service_id)})${statusSuffix}
          </option>
        `;
      });

      checkIsPunishment.checked = a.is_punishment === 1;
      punishmentReasonGroup.style.display = a.is_punishment === 1 ? 'block' : 'none';
      punishmentReasonInput.value = a.punishment_reason || '';

      openModal(modalEditAssignment);
    }

    checkIsPunishment.addEventListener('change', () => {
      punishmentReasonGroup.style.display = checkIsPunishment.checked ? 'block' : 'none';
    });

    formEditAssignment.addEventListener('submit', (e) => {
      e.preventDefault();
      const assignId = parseInt(editAssignmentId.value, 10);
      const officerId = selectEditOfficer.value ? parseInt(selectEditOfficer.value, 10) : null;
      const isPunishment = checkIsPunishment.checked;
      const punishmentReason = punishmentReasonInput.value;

      const a = db.duty_assignments.find(item => item.id === assignId);
      if (!a) return;

      if (officerId) {
        const check = checkOfficerEligibility(db, officerId, a.duty_date, isPunishment, a.id);
        if (!check.valid && !isPunishment) {
          alert('Assignment violates duty rules:\n• ' + check.errors.join('\n• '));
          return;
        }
      }

      a.assigned_officer_id = officerId;
      a.is_manual_override = 1;
      a.is_punishment = isPunishment ? 1 : 0;
      a.punishment_reason = isPunishment ? (punishmentReason || 'Disciplinary assignment') : null;

      StorageEngine.save(db);
      closeModal(modalEditAssignment);
      showToast('Assignment updated successfully.', 'success');
      renderRosterView();
    });

    // Duty Swap Modal & Execution
    btnOpenSwapModal.addEventListener('click', () => {
      if (!currentWeek) return;
      const assignments = db.duty_assignments.filter(a => a.roster_week_id === currentWeek.id);

      selectSwapSlot1.innerHTML = '';
      selectSwapSlot2.innerHTML = '';
      swapValidationFeedback.style.display = 'none';

      assignments.forEach(a => {
        const officer = db.officers.find(o => o.id === a.assigned_officer_id);
        const optText = `${a.day_name} (${formatDisplayDate(a.duty_date)}) - ${officer ? `${officer.rank} ${officer.name}` : 'Unassigned'}`;
        selectSwapSlot1.innerHTML += `<option value="${a.id}">${optText}</option>`;
        selectSwapSlot2.innerHTML += `<option value="${a.id}">${optText}</option>`;
      });

      if (selectSwapSlot2.options.length > 1) {
        selectSwapSlot2.selectedIndex = 1;
      }

      openModal(modalSwap);
    });

    formSwap.addEventListener('submit', (e) => {
      e.preventDefault();
      const a1Id = parseInt(selectSwapSlot1.value, 10);
      const a2Id = parseInt(selectSwapSlot2.value, 10);
      const reason = swapReasonInput.value || 'Mutual duty swap';

      if (a1Id === a2Id) {
        alert('Cannot swap an assignment with itself.');
        return;
      }

      const a1 = db.duty_assignments.find(a => a.id === a1Id);
      const a2 = db.duty_assignments.find(a => a.id === a2Id);
      if (!a1 || !a2) return;

      const o1Id = a1.assigned_officer_id;
      const o2Id = a2.assigned_officer_id;
      if (!o1Id || !o2Id) {
        alert('Both dates must have assigned officers to perform a swap.');
        return;
      }

      // Check Officer 1 on Date 2
      const check1 = checkOfficerEligibility(db, o1Id, a2.duty_date, false, a1.id);
      // Check Officer 2 on Date 1
      const check2 = checkOfficerEligibility(db, o2Id, a1.duty_date, false, a2.id);

      const swapErrors = [];
      if (!check1.valid) {
        const o1 = db.officers.find(o => o.id === o1Id);
        swapErrors.push(`Swap invalid for ${o1.name} on ${a2.duty_date}: ${check1.errors.join('; ')}`);
      }
      if (!check2.valid) {
        const o2 = db.officers.find(o => o.id === o2Id);
        swapErrors.push(`Swap invalid for ${o2.name} on ${a1.duty_date}: ${check2.errors.join('; ')}`);
      }

      if (swapErrors.length > 0) {
        swapValidationFeedback.className = 'conflict-card error';
        swapValidationFeedback.style.display = 'block';
        let html = `<strong>❌ Swap Rejected by Duty Rest Engine:</strong><ul class="conflict-list">`;
        swapErrors.forEach(err => html += `<li>${escapeHtml(err)}</li>`);
        html += `</ul>`;
        swapValidationFeedback.innerHTML = html;
        return;
      }

      // Execute swap
      a1.assigned_officer_id = o2Id;
      a1.swapped_with_officer_id = o1Id;
      a1.is_swap = 1;
      a1.swap_reason = reason;
      a1.is_manual_override = 1;

      a2.assigned_officer_id = o1Id;
      a2.swapped_with_officer_id = o2Id;
      a2.is_swap = 1;
      a2.swap_reason = reason;
      a2.is_manual_override = 1;

      StorageEngine.save(db);
      closeModal(modalSwap);
      showToast('Duty swap successfully recorded and validated!', 'success');
      renderRosterView();
    });

    // Recalculate & Force Regenerate
    btnRecalculateRoster.addEventListener('click', () => {
      if (!confirm('Recalculate rotation slots for this week? (Manual overrides and swaps will be preserved).')) return;
      generateWeekRoster(db, currentWeekStart, false);
      showToast('Roster recalculated while preserving manual changes and swaps.', 'success');
      renderRosterView();
    });

    btnForceRegenerate.addEventListener('click', () => {
      if (!confirm('WARNING: This will wipe all manual edits and swaps for this week and regenerate a fresh draft. Proceed?')) return;
      generateWeekRoster(db, currentWeekStart, true);
      showToast('Roster completely regenerated.', 'success');
      renderRosterView();
    });

    // Check Conflicts
    btnValidateRoster.addEventListener('click', () => {
      if (!currentWeek) return;
      const validation = validateWeek(db, currentWeek.id);
      renderConflictBanner(validation);
      if (validation.valid) {
        showToast('All duty rules and rest restrictions satisfied.', 'success');
      } else {
        showToast('Roster has conflicts preventing finalization.', 'warning');
      }
    });

    // Finalize Roster
    btnFinalizeRoster.addEventListener('click', () => {
      if (!currentWeek) return;
      const validation = validateWeek(db, currentWeek.id);
      if (!validation.valid) {
        alert('Cannot finalize roster: Mandatory duty rules are violated:\n• ' + validation.errors.join('\n• '));
        return;
      }

      if (!confirm('Are you sure you want to finalize this roster? This will lock the week, increment official duty counts, and establish it as official history.')) return;

      finalizeWeekRoster(db, currentWeek.id);
      showToast('Roster finalized and published successfully!', 'success');
      renderRosterView();
    });

    // Week Navigation
    btnPrevWeek.addEventListener('click', () => {
      currentWeekStart = addDays(currentWeekStart, -7);
      renderRosterView();
    });

    btnNextWeek.addEventListener('click', () => {
      currentWeekStart = addDays(currentWeekStart, 7);
      renderRosterView();
    });

    btnCurrentWeek.addEventListener('click', () => {
      currentWeekStart = getMondayOfWeek(new Date().toISOString().split('T')[0]);
      renderRosterView();
    });

    // Excel Export / Download
    btnExportExcel.addEventListener('click', () => {
      if (!currentWeek) return;
      exportCurrentRosterToExcel(db, currentWeek.id);
    });

    btnExportFullExcel.addEventListener('click', () => {
      if (!currentWeek) return;
      exportCurrentRosterToExcel(db, currentWeek.id);
    });

    btnDownloadTemplate.addEventListener('click', () => {
      downloadExcelTemplate();
    });

    // Excel Drag & Drop Import
    excelDropZone.addEventListener('click', () => excelFileInput.click());

    excelDropZone.addEventListener('dragover', (e) => {
      e.preventDefault();
      excelDropZone.classList.add('dragover');
    });

    excelDropZone.addEventListener('dragleave', () => {
      excelDropZone.classList.remove('dragover');
    });

    excelDropZone.addEventListener('drop', (e) => {
      e.preventDefault();
      excelDropZone.classList.remove('dragover');
      if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
        handleExcelFile(e.dataTransfer.files[0]);
      }
    });

    excelFileInput.addEventListener('change', () => {
      if (excelFileInput.files && excelFileInput.files.length > 0) {
        handleExcelFile(excelFileInput.files[0]);
      }
    });

    function handleExcelFile(file) {
      if (!file.name.endsWith('.xlsx')) {
        alert('Please upload a valid .xlsx file.');
        return;
      }

      importResultBox.style.display = 'block';
      importResultBox.innerHTML = `<span style="color: var(--cyan-accent);">Parsing Excel workbook...</span>`;

      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const r = importExcelWorkbookBuffer(db, e.target.result);
          importResultBox.innerHTML = `
            <div style="background: rgba(16, 185, 129, 0.15); border: 1px solid rgba(16, 185, 129, 0.3); padding: 0.75rem; border-radius: 4px; color: #a7f3d0;">
              <strong>✅ Excel Import Succeeded:</strong><br>
              • New Officers Created: <strong>${r.officersCreated}</strong><br>
              • Existing Officers Synchronized: <strong>${r.officersUpdated}</strong> (duty counts preserved)<br>
              • Leaves Imported: <strong>${r.leavesImported}</strong><br>
              • Declared Holidays: <strong>${r.holidaysImported}</strong>
            </div>
          `;
          showToast('Excel synchronization complete without count reset.', 'success');
          renderRosterView();
        } catch (err) {
          importResultBox.innerHTML = `<div style="color: var(--danger);">Import Error: ${err.message}</div>`;
        }
      };
      reader.readAsArrayBuffer(file);
    }

    // Watchkeeper Auth Actions
    btnWatchkeeperPortal.addEventListener('click', () => {
      if (isWatchkeeper) {
        watchkeeperPanel.scrollIntoView({ behavior: 'smooth' });
      } else {
        loginErrorMsg.style.display = 'none';
        loginPassword.value = '';
        openModal(modalLogin);
      }
    });

    formLogin.addEventListener('submit', (e) => {
      e.preventDefault();
      const entered = loginPassword.value;
      const expected = db.settings.admin_password_hash || 'admin@123';

      if (entered === expected) {
        isWatchkeeper = true;
        sessionStorage.setItem('WATCHKEEPER_AUTH', 'true');
        closeModal(modalLogin);
        updateAuthUI();
        showToast('Welcome, Watchkeeper. Administrative controls enabled.', 'success');
        watchkeeperPanel.scrollIntoView({ behavior: 'smooth' });
      } else {
        loginErrorMsg.textContent = 'Invalid Watchkeeper password.';
        loginErrorMsg.style.display = 'block';
      }
    });

    btnLogoutWatchkeeper.addEventListener('click', () => {
      isWatchkeeper = false;
      sessionStorage.removeItem('WATCHKEEPER_AUTH');
      updateAuthUI();
      showToast('Logged out of Watchkeeper panel.', 'info');
    });

    formChangePassword.addEventListener('submit', (e) => {
      e.preventDefault();
      const curr = document.getElementById('currentPassword').value;
      const next = document.getElementById('newPassword').value;
      const expected = db.settings.admin_password_hash || 'admin@123';

      if (curr !== expected) {
        alert('Current password is incorrect.');
        return;
      }
      db.settings.admin_password_hash = next;
      StorageEngine.save(db);
      formChangePassword.reset();
      showToast('Watchkeeper password updated successfully.', 'success');
    });

    btnResetSampleData.addEventListener('click', () => {
      if (!confirm('Are you sure you want to reset all data back to the factory military sample database?')) return;
      db = StorageEngine.reset();
      showToast('System reset to sample dataset.', 'info');
      renderRosterView();
    });

    // Officers Tab Management
    function renderOfficersTable() {
      const tbody = document.getElementById('officersTableBody');
      tbody.innerHTML = '';

      db.officers.forEach(o => {
        const tr = document.createElement('tr');
        const isTransferred = o.status === 'TRANSFERRED_OUT';
        const statusBadge = isTransferred
          ? `<span class="badge badge-punishment">TRANSFERRED</span>`
          : `<span class="badge badge-finalized">ACTIVE</span>`;

        let actionHtml = '';
        if (!isTransferred) {
          actionHtml = `<button class="btn btn-secondary btn-sm btn-transfer-out" data-id="${o.id}">Transfer Out</button>`;
        } else {
          actionHtml = `<span style="font-size: 0.75rem; color: var(--text-muted);">Transferred</span>`;
        }

        tr.innerHTML = `
          <td><strong>${escapeHtml(o.service_id)}</strong></td>
          <td>${escapeHtml(o.rank)} ${escapeHtml(o.name)}</td>
          <td>${statusBadge}</td>
          <td>${o.joining_date || '-'}</td>
          <td>${o.transfer_date || '-'}</td>
          <td><strong style="color: #60a5fa;">${o.normal_duty_count}</strong></td>
          <td><strong style="color: #fbbf24;">${o.sunday_duty_count}</strong></td>
          <td><strong>${o.normal_duty_count + o.sunday_duty_count}</strong></td>
          <td>${actionHtml}</td>
        `;
        tbody.appendChild(tr);
      });

      document.querySelectorAll('.btn-transfer-out').forEach(btn => {
        btn.addEventListener('click', () => {
          const oId = parseInt(btn.getAttribute('data-id'), 10);
          const officer = db.officers.find(o => o.id === oId);
          if (!officer) return;
          transferOfficerId.value = officer.id;
          transferOfficerName.textContent = `${officer.rank} ${officer.name}`;
          transferDateInput.value = new Date().toISOString().split('T')[0];
          openModal(modalTransferOut);
        });
      });
    }

    btnOpenAddOfficer.addEventListener('click', () => {
      formAddOfficer.reset();
      document.getElementById('newJoiningDate').value = new Date().toISOString().split('T')[0];
      openModal(modalAddOfficer);
    });

    formAddOfficer.addEventListener('submit', (e) => {
      e.preventDefault();
      const serviceId = document.getElementById('newServiceId').value.trim();
      const name = document.getElementById('newName').value.trim();
      const rank = document.getElementById('newRank').value;
      const joiningDate = document.getElementById('newJoiningDate').value;
      const normalCount = parseInt(document.getElementById('newNormalCount').value || 0, 10);
      const sundayCount = parseInt(document.getElementById('newSundayCount').value || 0, 10);

      if (db.officers.some(o => o.service_id === serviceId)) {
        alert(`Officer with Service ID ${serviceId} already exists.`);
        return;
      }

      const newId = db.officers.reduce((max, o) => Math.max(max, o.id), 0) + 1;
      db.officers.push({
        id: newId,
        service_id: serviceId,
        name: name,
        rank: rank,
        status: 'ACTIVE',
        joining_date: joiningDate,
        transfer_date: null,
        normal_duty_count: normalCount,
        sunday_duty_count: sundayCount
      });

      StorageEngine.save(db);
      closeModal(modalAddOfficer);
      showToast('Officer joined successfully!', 'success');
      renderOfficersTable();
      renderRosterView();
    });

    formTransferOut.addEventListener('submit', (e) => {
      e.preventDefault();
      const oId = parseInt(transferOfficerId.value, 10);
      const transferDate = transferDateInput.value;
      const officer = db.officers.find(o => o.id === oId);
      if (!officer) return;

      officer.status = 'TRANSFERRED_OUT';
      officer.transfer_date = transferDate;

      StorageEngine.save(db);
      closeModal(modalTransferOut);
      showToast(`Officer ${officer.name} marked as transferred out as of ${transferDate}.`, 'success');
      renderOfficersTable();
      renderRosterView();
    });

    // Leaves Tab Management
    function renderLeavesTable() {
      const tbody = document.getElementById('leavesTableBody');
      tbody.innerHTML = '';

      if (db.leaves.length === 0) {
        tbody.innerHTML = `<tr><td colspan="7" style="text-align: center; color: var(--text-muted);">No leave records found.</td></tr>`;
        return;
      }

      db.leaves.forEach(l => {
        const officer = db.officers.find(o => o.id === l.officer_id);
        const prefix = (l.prefix_start && l.prefix_end) ? `${l.prefix_start} to ${l.prefix_end}` : 'None';
        const suffix = (l.suffix_start && l.suffix_end) ? `${l.suffix_start} to ${l.suffix_end}` : 'None';

        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><strong>${officer ? `${escapeHtml(officer.rank)} ${escapeHtml(officer.name)}` : 'Unknown'}</strong></td>
          <td>${l.leave_start} to ${l.leave_end}</td>
          <td>${prefix}</td>
          <td>${suffix}</td>
          <td><strong style="color: #34d399;">${l.return_date}</strong></td>
          <td>${escapeHtml(l.remarks || '-')}</td>
          <td><button class="btn btn-secondary btn-sm btn-delete-leave" data-id="${l.id}" style="color: #f87171;">Delete</button></td>
        `;
        tbody.appendChild(tr);
      });

      document.querySelectorAll('.btn-delete-leave').forEach(btn => {
        btn.addEventListener('click', () => {
          if (!confirm('Delete this leave record?')) return;
          const lId = parseInt(btn.getAttribute('data-id'), 10);
          db.leaves = db.leaves.filter(l => l.id !== lId);
          StorageEngine.save(db);
          showToast('Leave record removed.', 'info');
          renderLeavesTable();
          renderRosterView();
        });
      });
    }

    btnOpenAddLeave.addEventListener('click', () => {
      leaveOfficerSelect.innerHTML = `<option value="">-- Select Active Officer --</option>`;
      db.officers.filter(o => o.status === 'ACTIVE').forEach(o => {
        leaveOfficerSelect.innerHTML += `<option value="${o.id}">${escapeHtml(o.rank)} ${escapeHtml(o.name)} (${escapeHtml(o.service_id)})</option>`;
      });
      formAddLeave.reset();
      openModal(modalAddLeave);
    });

    formAddLeave.addEventListener('submit', (e) => {
      e.preventDefault();
      const officerId = parseInt(leaveOfficerSelect.value, 10);
      const leaveStart = document.getElementById('leaveStart').value;
      const leaveEnd = document.getElementById('leaveEnd').value;
      const prefixStart = document.getElementById('prefixStart').value || null;
      const prefixEnd = document.getElementById('prefixEnd').value || null;
      const suffixStart = document.getElementById('suffixStart').value || null;
      const suffixEnd = document.getElementById('suffixEnd').value || null;
      const returnDate = document.getElementById('leaveReturnDate').value;
      const remarks = document.getElementById('leaveRemarks').value;

      if (leaveEnd < leaveStart) {
        alert('Leave End Date cannot be before Leave Start Date.');
        return;
      }

      const newId = db.leaves.reduce((max, l) => Math.max(max, l.id), 0) + 1;
      db.leaves.push({
        id: newId,
        officer_id: officerId,
        leave_start: leaveStart,
        leave_end: leaveEnd,
        prefix_start: prefixStart,
        prefix_end: prefixEnd,
        suffix_start: suffixStart,
        suffix_end: suffixEnd,
        return_date: returnDate,
        remarks: remarks
      });

      StorageEngine.save(db);
      closeModal(modalAddLeave);
      showToast('Leave recorded successfully.', 'success');
      renderLeavesTable();
      renderRosterView();
    });

    // Holidays Tab Management
    function renderHolidaysTable() {
      const tbody = document.getElementById('holidaysTableBody');
      tbody.innerHTML = '';

      if (db.holidays.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted);">No declared holidays.</td></tr>`;
        return;
      }

      db.holidays.forEach(h => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
          <td><strong>${h.holiday_date}</strong></td>
          <td>${escapeHtml(h.description)}</td>
          <td><span class="badge badge-holiday">Sunday Routine</span></td>
          <td><button class="btn btn-secondary btn-sm btn-delete-holiday" data-id="${h.id}" style="color: #f87171;">Delete</button></td>
        `;
        tbody.appendChild(tr);
      });

      document.querySelectorAll('.btn-delete-holiday').forEach(btn => {
        btn.addEventListener('click', () => {
          if (!confirm('Delete this declared holiday?')) return;
          const hId = parseInt(btn.getAttribute('data-id'), 10);
          db.holidays = db.holidays.filter(h => h.id !== hId);
          StorageEngine.save(db);
          showToast('Holiday deleted.', 'info');
          renderHolidaysTable();
          renderRosterView();
        });
      });
    }

    btnOpenAddHoliday.addEventListener('click', () => {
      formAddHoliday.reset();
      openModal(modalAddHoliday);
    });

    formAddHoliday.addEventListener('submit', (e) => {
      e.preventDefault();
      const holidayDate = document.getElementById('holidayDate').value;
      const description = document.getElementById('holidayDesc').value.trim();

      const existing = db.holidays.find(h => h.holiday_date === holidayDate);
      if (existing) {
        existing.description = description;
      } else {
        const newId = db.holidays.reduce((max, h) => Math.max(max, h.id), 0) + 1;
        db.holidays.push({ id: newId, holiday_date: holidayDate, description: description });
      }

      StorageEngine.save(db);
      closeModal(modalAddHoliday);
      showToast('Holiday added into Sunday Routine calendar.', 'success');
      renderHolidaysTable();
      renderRosterView();
    });

    // History & Swaps Log Tab
    function renderHistoryTables() {
      const swapsTbody = document.getElementById('swapsLogTableBody');
      const historyTbody = document.getElementById('historyLogTableBody');

      swapsTbody.innerHTML = '';
      const swappedAssignments = db.duty_assignments.filter(a => a.is_swap === 1);
      if (swappedAssignments.length === 0) {
        swapsTbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted);">No swaps recorded yet.</td></tr>`;
      } else {
        swappedAssignments.forEach(s => {
          const actualO = db.officers.find(o => o.id === s.assigned_officer_id);
          const origO = db.officers.find(o => o.id === s.swapped_with_officer_id);
          const tr = document.createElement('tr');
          tr.innerHTML = `
            <td><strong>${s.duty_date}</strong></td>
            <td>${actualO ? `${escapeHtml(actualO.rank)} ${escapeHtml(actualO.name)}` : 'Unknown'}</td>
            <td style="color: #38bdf8;">${origO ? `${escapeHtml(origO.rank)} ${escapeHtml(origO.name)}` : 'Other'}</td>
            <td>${escapeHtml(s.swap_reason || '-')}</td>
          `;
          swapsTbody.appendChild(tr);
        });
      }

      historyTbody.innerHTML = '';
      if (db.duty_history.length === 0) {
        historyTbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: var(--text-muted);">No finalized duty history found.</td></tr>`;
      } else {
        const sortedHistory = [...db.duty_history].sort((a, b) => b.duty_date.localeCompare(a.duty_date));
        sortedHistory.forEach(h => {
          const officer = db.officers.find(o => o.id === h.officer_id);
          const tr = document.createElement('tr');
          const isPunishment = h.is_punishment === 1;
          const remarks = isPunishment ? `<span class="badge badge-punishment">Punishment (${escapeHtml(h.punishment_reason || '')})</span>` : 'Ordinary Duty';
          const routineBadge = h.routine_type === 'NORMAL' ? `<span class="badge badge-normal">Normal</span>` : `<span class="badge badge-sunday">Sunday</span>`;

          tr.innerHTML = `
            <td><strong>${h.duty_date}</strong></td>
            <td>${officer ? `${escapeHtml(officer.rank)} ${escapeHtml(officer.name)} (${escapeHtml(officer.service_id)})` : 'Unknown'}</td>
            <td>${routineBadge}</td>
            <td>${remarks}</td>
          `;
          historyTbody.appendChild(tr);
        });
      }
    }

    // Startup Initialization
    updateAuthUI();
  });
})();
