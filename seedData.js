const db = require('./db');
const { generateWeekRoster, finalizeWeekRoster } = require('./scheduler');

function seedDatabase() {
  const existingCount = db.prepare('SELECT COUNT(*) as cnt FROM officers').get().cnt;
  if (existingCount > 0) {
    console.log('Database already has officer data. Skipping seed.');
    return;
  }

  console.log('Seeding initial realistic military/naval officer duty roster data...');

  // 1. Insert Officers (including senior officers, new joiner, and transferred out officer)
  const officers = [
    { service_id: 'IC-101', name: 'Arjun Mehta', rank: 'Lt Cdr', status: 'ACTIVE', joining_date: '2025-01-01', transfer_date: null, normal_duty_count: 24, sunday_duty_count: 10 },
    { service_id: 'IC-102', name: 'Vikram Malhotra', rank: 'Lt', status: 'ACTIVE', joining_date: '2025-02-15', transfer_date: null, normal_duty_count: 22, sunday_duty_count: 9 },
    { service_id: 'IC-103', name: 'Sneha Rao', rank: 'Lt Cdr', status: 'ACTIVE', joining_date: '2025-03-01', transfer_date: null, normal_duty_count: 25, sunday_duty_count: 11 },
    { service_id: 'IC-104', name: 'Rajesh Nair', rank: 'Lt', status: 'ACTIVE', joining_date: '2025-04-10', transfer_date: null, normal_duty_count: 21, sunday_duty_count: 9 },
    { service_id: 'IC-105', name: 'Pooja Sharma', rank: 'Lt', status: 'ACTIVE', joining_date: '2025-05-01', transfer_date: null, normal_duty_count: 20, sunday_duty_count: 8 },
    { service_id: 'IC-106', name: 'Rohan Gupta', rank: 'Sub Lt', status: 'ACTIVE', joining_date: '2026-09-15', transfer_date: null, normal_duty_count: 1, sunday_duty_count: 1 }, // New joiner with low count
    { service_id: 'IC-107', name: 'Manoj Deshmukh', rank: 'Lt Cdr', status: 'ACTIVE', joining_date: '2025-06-01', transfer_date: null, normal_duty_count: 19, sunday_duty_count: 8 },
    { service_id: 'IC-108', name: 'Amitav Ghosh', rank: 'Lt', status: 'ACTIVE', joining_date: '2025-07-01', transfer_date: null, normal_duty_count: 18, sunday_duty_count: 7 },
    { service_id: 'IC-109', name: 'Kiran Bedi', rank: 'Lt Cdr', status: 'TRANSFERRED_OUT', joining_date: '2024-01-01', transfer_date: '2026-10-01', normal_duty_count: 35, sunday_duty_count: 15 } // Transferred out officer
  ];

  const insertOfficer = db.prepare(`
    INSERT INTO officers (service_id, name, rank, status, joining_date, transfer_date, normal_duty_count, sunday_duty_count)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `);

  const officerMap = {};
  for (const o of officers) {
    const res = insertOfficer.run(o.service_id, o.name, o.rank, o.status, o.joining_date, o.transfer_date, o.normal_duty_count, o.sunday_duty_count);
    officerMap[o.service_id] = res.lastInsertRowid;
  }

  // 2. Insert Declared Holidays
  const holidays = [
    { holiday_date: '2026-10-20', description: 'National Defence Day Holiday' },
    { holiday_date: '2026-11-04', description: 'Navy Day Observance' }
  ];
  const insertHoliday = db.prepare('INSERT INTO holidays (holiday_date, description) VALUES (?, ?)');
  for (const h of holidays) {
    insertHoliday.run(h.holiday_date, h.description);
  }

  // 3. Insert Leaves
  // Example 1: Officer IC-104 on leave with prefix (Oct 10-11) and suffix (Oct 17-18), returning to duty on Monday Oct 19
  // Example 2: Officer IC-105 begins leave on Monday Oct 26, so must NOT be assigned on Sunday Oct 25!
  const leaves = [
    {
      officer_id: officerMap['IC-104'],
      leave_start: '2026-10-12',
      leave_end: '2026-10-16',
      prefix_start: '2026-10-10',
      prefix_end: '2026-10-11',
      suffix_start: '2026-10-17',
      suffix_end: '2026-10-18',
      return_date: '2026-10-19',
      remarks: 'Casual Leave with Prefix & Suffix weekend'
    },
    {
      officer_id: officerMap['IC-105'],
      type: 'LEAVE',
      leave_start: '2026-10-26', // Starts on Monday
      leave_end: '2026-10-30',
      prefix_start: '2026-10-24',
      prefix_end: '2026-10-25',
      suffix_start: null,
      suffix_end: null,
      return_date: '2026-11-02',
      remarks: 'Leave starting on Monday Oct 26 (Restricts Sunday Oct 25 per Rule 5.3)'
    },
    {
      officer_id: officerMap['IC-108'],
      type: 'TY_DUTY',
      leave_start: '2026-10-21',
      leave_end: '2026-10-23',
      prefix_start: null,
      prefix_end: null,
      suffix_start: null,
      suffix_end: null,
      return_date: '2026-10-24',
      remarks: 'Temporary Duty (Ty Duty) at HQ'
    }
  ];

  const insertLeave = db.prepare(`
    INSERT INTO leaves (officer_id, type, leave_start, leave_end, prefix_start, prefix_end, suffix_start, suffix_end, return_date, remarks)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  `);
  for (const l of leaves) {
    insertLeave.run(l.officer_id, l.type || 'LEAVE', l.leave_start, l.leave_end, l.prefix_start, l.prefix_end, l.suffix_start, l.suffix_end, l.return_date, l.remarks);
  }

  // 4. Insert Previous Week Finalized Roster (Week: Oct 05 to Oct 11, 2026)
  // This provides actual previous assignments so current week generation continues smoothly
  const prevWeekMonday = '2026-10-05';
  const prevWeek = generateWeekRoster(prevWeekMonday, { forceRegenerateAll: true });

  // Explicitly set realistic previous week assignments to establish rotation history
  const prevDuties = [
    { date: '2026-10-05', officer_id: officerMap['IC-101'], type: 'NORMAL' },
    { date: '2026-10-06', officer_id: officerMap['IC-102'], type: 'NORMAL' },
    { date: '2026-10-07', officer_id: officerMap['IC-103'], type: 'NORMAL' },
    { date: '2026-10-08', officer_id: officerMap['IC-107'], type: 'NORMAL' }, // Thursday duty
    { date: '2026-10-09', officer_id: officerMap['IC-108'], type: 'NORMAL' }, // Friday duty -> rest Sat/Sun!
    { date: '2026-10-10', officer_id: officerMap['IC-102'], type: 'SUNDAY' }, // Saturday
    { date: '2026-10-11', officer_id: officerMap['IC-107'], type: 'SUNDAY' }  // Sunday (IC-107 had duty on Thu Oct 8 -> 2 rest days Fri/Sat -> Sun Oct 11 valid!)
  ];

  for (const pd of prevDuties) {
    db.prepare(`
      UPDATE duty_assignments
      SET assigned_officer_id = ?, original_officer_id = ?
      WHERE roster_week_id = ? AND duty_date = ?
    `).run(pd.officer_id, pd.officer_id, prevWeek.id, pd.date);
  }

  // Finalize the previous week
  finalizeWeekRoster(prevWeek.id);
  console.log('Previous week (2026-10-05 to 2026-10-11) finalized as historical base.');

  // 5. Generate Current Week Roster (Week: Oct 12 to Oct 18, 2026)
  const currentWeekMonday = '2026-10-12';
  const currentWeek = generateWeekRoster(currentWeekMonday, { forceRegenerateAll: true });
  console.log('Current week (2026-10-12 to 2026-10-18) draft generated.');

  console.log('Sample database seeding complete.');
}

if (require.main === module) {
  seedDatabase();
}

module.exports = { seedDatabase };
