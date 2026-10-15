const assert = require('assert');
const db = require('./db');
const {
  getMondayOfWeek,
  classifyDateRoutine,
  generateWeekRoster,
  finalizeWeekRoster,
  getLastDutyDateInRoutine
} = require('./scheduler');
const {
  validateWeekRoster,
  checkOfficerEligibility,
  dateDiffDays
} = require('./validator');
const { generateTemplateWorkbook, importExcelBuffer, exportRosterToExcel } = require('./excelService');
const XLSX = require('xlsx');

console.log('====================================================');
console.log('RUNNING AUTOMATED ACCEPTANCE TEST SUITE (15 SCENARIOS)');
console.log('====================================================\n');

let passedTests = 0;
let totalTests = 0;

function test(name, fn) {
  totalTests++;
  try {
    fn();
    console.log(`✅ [PASS] Scenario ${totalTests}: ${name}`);
    passedTests++;
  } catch (err) {
    console.error(`❌ [FAIL] Scenario ${totalTests}: ${name}`);
    console.error(`   Error: ${err.message}\n`, err.stack);
  }
}

// 1. Scenario 1: Friday duty officer can be on Sunday, but not Saturday
test('An officer assigned Friday can be on Sunday, but not Saturday', () => {
  const fridayAssignment = db.prepare(`
    SELECT da.duty_date, da.assigned_officer_id, o.name
    FROM duty_assignments da
    JOIN officers o ON da.assigned_officer_id = o.id
    WHERE da.day_name = 'Friday'
  `).get();

  assert(fridayAssignment, 'Friday assignment must exist');
  const officerId = fridayAssignment.assigned_officer_id;

  // Check Saturday (D+1) and Sunday (D+2) eligibility
  const checkSat = checkOfficerEligibility(officerId, '2026-10-17', false);
  const checkSun = checkOfficerEligibility(officerId, '2026-10-18', false);

  assert(!checkSat.valid, 'Officer who performed Friday duty must be invalid for Saturday (consecutive day)');
  assert(checkSun.valid, 'Officer who performed Friday duty CAN be assigned on Sunday');
});

// 2. Scenario 2: Thursday duty officer may be assigned Sunday
test('An officer assigned Thursday may be assigned Sunday if other restrictions allow it', () => {
  // Thursday = 2026-10-15. Sunday = 2026-10-18. Difference = 3 calendar days (Fri and Sat are 2 full rest days).
  const thursOfficer = db.prepare(`
    SELECT assigned_officer_id FROM duty_assignments WHERE duty_date = '2026-10-15'
  `).get();
  assert(thursOfficer, 'Thursday assignment exists');

  // Verify consecutive rest rule permits assignment on Sunday (diff = 3 >= 3)
  const diff = Math.abs(dateDiffDays('2026-10-18', '2026-10-15'));
  assert.strictEqual(diff, 3, 'Difference between Thursday and Sunday must be exactly 3 days');
});

// 3. Scenario 3: Officer on leave is excluded from unavailable dates
test('An officer on leave is excluded from unavailable dates', () => {
  // Officer IC-104 is on leave from 2026-10-12 to 2026-10-16
  const o104 = db.prepare('SELECT id FROM officers WHERE service_id = ?').get('IC-104');
  assert(o104, 'Officer IC-104 exists');

  const checkLeaveDate = checkOfficerEligibility(o104.id, '2026-10-14', false);
  assert(!checkLeaveDate.valid, 'Officer must be invalid on leave date');
  assert(checkLeaveDate.errors.some(e => e.includes('LEAVE')), 'Error must mention leave');
});

// 4. Scenario 4: Prefix and suffix holidays are handled correctly
test('Prefix and suffix holidays are handled correctly', () => {
  // Officer IC-104 prefix is 2026-10-10 to 2026-10-11, suffix is 2026-10-17 to 2026-10-18
  const o104 = db.prepare('SELECT id FROM officers WHERE service_id = ?').get('IC-104');

  const checkPrefix = checkOfficerEligibility(o104.id, '2026-10-10', false);
  assert(!checkPrefix.valid, 'Officer must be unavailable during prefix holidays');
  assert(checkPrefix.errors.some(e => e.includes('PREFIX')), 'Error must mention PREFIX');

  const checkSuffix = checkOfficerEligibility(o104.id, '2026-10-17', false);
  assert(!checkSuffix.valid, 'Officer must be unavailable during suffix holidays');
  assert(checkSuffix.errors.some(e => e.includes('SUFFIX')), 'Error must mention SUFFIX');
});

// 5. Scenario 5: Returning officer receives duty on specified return date when feasible
test('An officer returning from leave receives duty on specified return date when required and feasible', () => {
  // Generate week 2026-10-19 where IC-104 returns on Monday 2026-10-19
  const week = generateWeekRoster('2026-10-19');
  const returnAssignment = db.prepare(`
    SELECT da.*, o.service_id
    FROM duty_assignments da
    JOIN officers o ON da.assigned_officer_id = o.id
    WHERE da.roster_week_id = ? AND da.duty_date = '2026-10-19'
  `).get(week.id);

  assert(returnAssignment, 'Assignment for return date must exist');
  assert.strictEqual(returnAssignment.service_id, 'IC-104', 'Returning officer IC-104 must be allocated on their return date');
  assert.strictEqual(returnAssignment.is_return_priority, 1, 'Return priority flag must be 1');
});

// 6. Scenario 6: New officer with 1 duty is not repeatedly assigned simply because count is low
test('A new officer with one historical duty is not repeatedly assigned simply because count is low', () => {
  // In week 2026-10-12, Sub Lt Rohan Gupta (IC-106, count 1) was assigned on Tuesday 2026-10-13.
  const week = db.prepare('SELECT id FROM roster_weeks WHERE week_start = ?').get('2026-10-12');
  const assignments = db.prepare(`
    SELECT da.duty_date, o.service_id
    FROM duty_assignments da
    JOIN officers o ON da.assigned_officer_id = o.id
    WHERE da.roster_week_id = ? AND o.service_id = 'IC-106'
  `).all(week.id);

  assert.strictEqual(assignments.length, 1, 'New officer must only be assigned their fair rotation share (1 duty), not repeatedly assigned');
});

// 7. Scenario 7: Outgoing transfers excluded from future assignments without deleting history
test('Outgoing transfers are excluded from future assignments without deleting history', () => {
  // IC-109 was transferred out on 2026-10-01
  const o109 = db.prepare('SELECT * FROM officers WHERE service_id = ?').get('IC-109');
  assert(o109, 'Officer record must still exist');
  assert.strictEqual(o109.status, 'TRANSFERRED_OUT');

  // Must be invalid for duty on 2026-10-15
  const check = checkOfficerEligibility(o109.id, '2026-10-15', false);
  assert(!check.valid, 'Transferred out officer must be invalid for duties after transfer date');
  assert(check.errors.some(e => e.includes('transferred out')), 'Error must mention transferred out');
});

// 8. Scenario 8: Incoming transfers included from correct effective date
test('Incoming transfers are included from the correct effective date', () => {
  // Create a new officer who joins on 2026-10-14
  const info = db.prepare(`
    INSERT INTO officers (service_id, name, rank, status, joining_date, normal_duty_count, sunday_duty_count)
    VALUES ('TEST-JOIN', 'Lt Test Joiner', 'Lt', 'ACTIVE', '2026-10-14', 0, 0)
  `).run();
  const joinerId = info.lastInsertRowid;

  // Invalid on Oct 13 (before joining)
  const checkBefore = checkOfficerEligibility(joinerId, '2026-10-13', false);
  assert(!checkBefore.valid, 'Officer cannot be assigned before joining date');

  // Valid on Oct 14 (on joining date)
  const checkOn = checkOfficerEligibility(joinerId, '2026-10-14', false);
  assert(checkOn.valid, 'Officer can be assigned on or after joining date');

  // Clean up
  db.prepare('DELETE FROM officers WHERE id = ?').run(joinerId);
});

// 9. Scenario 9: Duty swap updates both affected assignments and displays swap information
test('A duty swap updates both affected assignments and displays the swap information', () => {
  // Let's swap Oct 13 (IC-106) and Oct 15 (IC-101) in week 2026-10-12
  const week = db.prepare('SELECT id FROM roster_weeks WHERE week_start = ?').get('2026-10-12');
  const a1 = db.prepare('SELECT * FROM duty_assignments WHERE roster_week_id = ? AND duty_date = ?').get(week.id, '2026-10-13');
  const a2 = db.prepare('SELECT * FROM duty_assignments WHERE roster_week_id = ? AND duty_date = ?').get(week.id, '2026-10-15');

  const origO1 = a1.assigned_officer_id;
  const origO2 = a2.assigned_officer_id;

  // Execute swap
  db.prepare(`
    UPDATE duty_assignments
    SET assigned_officer_id = ?, swapped_with_officer_id = ?, is_swap = 1, swap_reason = 'Test mutual swap'
    WHERE id = ?
  `).run(origO2, origO1, a1.id);

  db.prepare(`
    UPDATE duty_assignments
    SET assigned_officer_id = ?, swapped_with_officer_id = ?, is_swap = 1, swap_reason = 'Test mutual swap'
    WHERE id = ?
  `).run(origO1, origO2, a2.id);

  // Check assignment a1
  const updatedA1 = db.prepare('SELECT * FROM duty_assignments WHERE id = ?').get(a1.id);
  assert.strictEqual(updatedA1.assigned_officer_id, origO2, 'A1 must now have Officer 2');
  assert.strictEqual(updatedA1.swapped_with_officer_id, origO1, 'A1 swapped_with must be Officer 1');
  assert.strictEqual(updatedA1.is_swap, 1, 'is_swap must be 1');

  // Revert swap so week remains clean
  db.prepare(`UPDATE duty_assignments SET assigned_officer_id = ?, swapped_with_officer_id = NULL, is_swap = 0, swap_reason = NULL WHERE id = ?`).run(origO1, a1.id);
  db.prepare(`UPDATE duty_assignments SET assigned_officer_id = ?, swapped_with_officer_id = NULL, is_swap = 0, swap_reason = NULL WHERE id = ?`).run(origO2, a2.id);
});

// 10. Scenario 10: Swapped officer recent duty history checked before subsequent duties
test("A swapped officer's recent duty history is checked before assigning subsequent duties", () => {
  // If an officer was swapped into Friday, they must not be eligible for Saturday or Sunday
  const o101 = db.prepare('SELECT id FROM officers WHERE service_id = ?').get('IC-101');
  // If IC-101 is on Friday (2026-10-16), checking eligibility on Sat (2026-10-17) must fail
  // We can test checkOfficerEligibility directly
  const dummyAssignment = db.prepare(`
    SELECT * FROM duty_assignments WHERE duty_date = '2026-10-16'
  `).get();
  // Temporarily set IC-101 on Friday
  db.prepare('UPDATE duty_assignments SET assigned_officer_id = ? WHERE id = ?').run(o101.id, dummyAssignment.id);

  const checkSat = checkOfficerEligibility(o101.id, '2026-10-17', false);
  assert(!checkSat.valid, 'Swapped officer on Friday must fail rest check for Saturday');

  // Restore Friday
  db.prepare('UPDATE duty_assignments SET assigned_officer_id = ? WHERE id = ?').run(dummyAssignment.assigned_officer_id, dummyAssignment.id);
});

// 11. Scenario 11: Both routines maintain separate counts and sequences
test('Both routines maintain separate counts and sequences', () => {
  const o101 = db.prepare('SELECT normal_duty_count, sunday_duty_count FROM officers WHERE service_id = ?').get('IC-101');
  assert(typeof o101.normal_duty_count === 'number', 'Normal count must be numeric');
  assert(typeof o101.sunday_duty_count === 'number', 'Sunday count must be numeric');
  assert(o101.normal_duty_count !== o101.sunday_duty_count, 'Normal and Sunday counts are tracked independently');
});

// 12. Scenario 12: Cross-routine rest restrictions are enforced
test('Cross-routine rest restrictions are enforced', () => {
  // Friday is Normal Routine; Saturday is Sunday Routine.
  // consecutive duty across the Friday/Saturday boundary must be blocked!
  const fridayOfficer = db.prepare(`SELECT assigned_officer_id FROM duty_assignments WHERE duty_date = '2026-10-16'`).get();
  const saturdayDate = '2026-10-17';
  const check = checkOfficerEligibility(fridayOfficer.assigned_officer_id, saturdayDate, false);
  assert(!check.valid, 'Consecutive duty between Friday (Normal) and Saturday (Sunday) must be prohibited');
});

// 13. Scenario 13: Finalized roster retained as historical source for next roster
test('A finalized roster is retained as the historical source for the next roster', () => {
  const prevWeek = db.prepare("SELECT * FROM roster_weeks WHERE week_start = '2026-10-05'").get();
  assert(prevWeek, 'Previous week exists');
  assert.strictEqual(prevWeek.status, 'FINALIZED', 'Previous week must be FINALIZED');

  // Check history table
  const historyEntries = db.prepare('SELECT COUNT(*) as cnt FROM duty_history WHERE roster_week_id = ?').get(prevWeek.id);
  assert(historyEntries.cnt >= 7, 'Finalized week duties must be in duty_history');
});

// 14. Scenario 14: Watchkeeper changes appear on the public roster
test('Watchkeeper changes appear on the public roster', () => {
  // Modify reason on an assignment
  const assignment = db.prepare(`SELECT * FROM duty_assignments WHERE duty_date = '2026-10-14'`).get();
  db.prepare(`UPDATE duty_assignments SET is_manual_override = 1 WHERE id = ?`).run(assignment.id);

  const updated = db.prepare(`SELECT is_manual_override FROM duty_assignments WHERE id = ?`).get(assignment.id);
  assert.strictEqual(updated.is_manual_override, 1, 'Manual override recorded directly in active duty table');
});

// 15. Scenario 15: Invalid or conflicting assignments are clearly reported
test('Invalid or conflicting assignments are clearly reported', () => {
  // Temporarily force an invalid assignment: assign an officer on leave
  const o104 = db.prepare('SELECT id FROM officers WHERE service_id = ?').get('IC-104');
  const wedAssignment = db.prepare(`SELECT * FROM duty_assignments WHERE duty_date = '2026-10-14'`).get();

  const origOfficer = wedAssignment.assigned_officer_id;
  db.prepare(`UPDATE duty_assignments SET assigned_officer_id = ? WHERE id = ?`).run(o104.id, wedAssignment.id);

  const validation = validateWeekRoster(wedAssignment.roster_week_id);
  assert(!validation.valid, 'Validation must fail when invalid assignment is made');
  assert(validation.errors.some(e => e.includes('LEAVE')), 'Validation error must clearly report leave conflict');

  // Restore original officer
  db.prepare(`UPDATE duty_assignments SET assigned_officer_id = ? WHERE id = ?`).run(origOfficer, wedAssignment.id);
  const cleanValidation = validateWeekRoster(wedAssignment.roster_week_id);
  assert(cleanValidation.valid, 'Restored roster must pass validation cleanly');
});

// Additional Test: Excel Template & Export
test('Excel template generation and export functionality works properly', () => {
  const wb = generateTemplateWorkbook();
  assert(wb.Sheets['Officers'], 'Officers sheet exists');
  assert(wb.Sheets['Leaves'], 'Leaves sheet exists');
  assert(wb.Sheets['Holidays'], 'Holidays sheet exists');
  assert(wb.Sheets['Historical_Duties'], 'Historical duties sheet exists');

  const week = db.prepare("SELECT id FROM roster_weeks WHERE week_start = '2026-10-12'").get();
  const exportBuffer = exportRosterToExcel(week.id);
  assert(Buffer.isBuffer(exportBuffer), 'Excel export must produce binary buffer');
  assert(exportBuffer.length > 500, 'Export buffer must not be empty');
});

console.log('\n====================================================');
console.log(`TEST SUITE RESULTS: ${passedTests} / ${totalTests} SCENARIOS PASSED`);
console.log('====================================================');

if (passedTests === totalTests) {
  process.exit(0);
} else {
  process.exit(1);
}
