const XLSX = require('xlsx');
const db = require('./db');

/**
 * Format JS date or Excel date to YYYY-MM-DD
 */
function normalizeDateStr(val) {
  if (!val) return null;
  if (typeof val === 'number') {
    // Excel serial number
    const date = new Date(Math.round((val - 25569) * 86400 * 1000));
    return date.toISOString().split('T')[0];
  }
  if (val instanceof Date) {
    return val.toISOString().split('T')[0];
  }
  const str = String(val).trim();
  // Check if standard YYYY-MM-DD
  if (/^\d{4}-\d{2}-\d{2}$/.test(str)) {
    return str;
  }
  // Try parsing
  const parsed = new Date(str);
  if (!isNaN(parsed.getTime())) {
    return parsed.toISOString().split('T')[0];
  }
  return str;
}

/**
 * Generate a comprehensive Excel template workbook with sample data.
 */
function generateTemplateWorkbook() {
  const wb = XLSX.utils.book_new();

  // 1. Officers Sheet
  const officersData = [
    {
      Service_ID: 'IC-1001',
      Name: 'A. Sharma',
      Rank: 'Lt Cdr',
      Status: 'ACTIVE',
      Joining_Date: '2026-01-01',
      Transfer_Date: '',
      Normal_Duty_Count: 14,
      Sunday_Duty_Count: 6
    },
    {
      Service_ID: 'IC-1002',
      Name: 'R. Verma',
      Rank: 'Lt',
      Status: 'ACTIVE',
      Joining_Date: '2026-01-15',
      Transfer_Date: '',
      Normal_Duty_Count: 12,
      Sunday_Duty_Count: 5
    },
    {
      Service_ID: 'IC-1003',
      Name: 'S. Nair',
      Rank: 'Lt Cdr',
      Status: 'ACTIVE',
      Joining_Date: '2026-02-01',
      Transfer_Date: '',
      Normal_Duty_Count: 15,
      Sunday_Duty_Count: 6
    },
    {
      Service_ID: 'IC-1004',
      Name: 'K. Patel',
      Rank: 'Lt',
      Status: 'ACTIVE',
      Joining_Date: '2026-03-01',
      Transfer_Date: '',
      Normal_Duty_Count: 10,
      Sunday_Duty_Count: 4
    },
    {
      Service_ID: 'IC-1005',
      Name: 'V. Singh',
      Rank: 'Sub Lt',
      Status: 'ACTIVE',
      Joining_Date: '2026-09-01',
      Transfer_Date: '',
      Normal_Duty_Count: 2,
      Sunday_Duty_Count: 1
    },
    {
      Service_ID: 'IC-1006',
      Name: 'M. Rao',
      Rank: 'Lt',
      Status: 'ACTIVE',
      Joining_Date: '2026-02-15',
      Transfer_Date: '',
      Normal_Duty_Count: 11,
      Sunday_Duty_Count: 5
    },
    {
      Service_ID: 'IC-1007',
      Name: 'P. Joshi',
      Rank: 'Lt',
      Status: 'TRANSFERRED_OUT',
      Joining_Date: '2025-10-01',
      Transfer_Date: '2026-10-01',
      Normal_Duty_Count: 24,
      Sunday_Duty_Count: 10
    }
  ];
  const wsOfficers = XLSX.utils.json_to_sheet(officersData);
  XLSX.utils.book_append_sheet(wb, wsOfficers, 'Officers');

  // 2. Leaves Sheet
  const leavesData = [
    {
      Service_ID: 'IC-1004',
      Leave_Start: '2026-10-19',
      Leave_End: '2026-10-23',
      Prefix_Start: '2026-10-17',
      Prefix_End: '2026-10-18',
      Suffix_Start: '2026-10-24',
      Suffix_End: '2026-10-25',
      Return_Date: '2026-10-26',
      Remarks: 'Annual leave with suffix weekend'
    }
  ];
  const wsLeaves = XLSX.utils.json_to_sheet(leavesData);
  XLSX.utils.book_append_sheet(wb, wsLeaves, 'Leaves');

  // 3. Holidays Sheet
  const holidaysData = [
    {
      Holiday_Date: '2026-10-20',
      Description: 'Navratri / Dussehra Holiday'
    },
    {
      Holiday_Date: '2026-11-08',
      Description: 'Diwali Holiday'
    }
  ];
  const wsHolidays = XLSX.utils.json_to_sheet(holidaysData);
  XLSX.utils.book_append_sheet(wb, wsHolidays, 'Holidays');

  // 4. Historical Duties Sheet
  const historyData = [
    {
      Duty_Date: '2026-10-05',
      Service_ID: 'IC-1001',
      Routine_Type: 'NORMAL',
      Is_Punishment: 0,
      Punishment_Reason: ''
    },
    {
      Duty_Date: '2026-10-06',
      Service_ID: 'IC-1002',
      Routine_Type: 'NORMAL',
      Is_Punishment: 0,
      Punishment_Reason: ''
    },
    {
      Duty_Date: '2026-10-07',
      Service_ID: 'IC-1003',
      Routine_Type: 'NORMAL',
      Is_Punishment: 0,
      Punishment_Reason: ''
    },
    {
      Duty_Date: '2026-10-08',
      Service_ID: 'IC-1004',
      Routine_Type: 'NORMAL',
      Is_Punishment: 0,
      Punishment_Reason: ''
    },
    {
      Duty_Date: '2026-10-09',
      Service_ID: 'IC-1006',
      Routine_Type: 'NORMAL',
      Is_Punishment: 0,
      Punishment_Reason: ''
    },
    {
      Duty_Date: '2026-10-10',
      Service_ID: 'IC-1005',
      Routine_Type: 'SUNDAY',
      Is_Punishment: 0,
      Punishment_Reason: ''
    },
    {
      Duty_Date: '2026-10-11',
      Service_ID: 'IC-1001',
      Routine_Type: 'SUNDAY',
      Is_Punishment: 0,
      Punishment_Reason: ''
    }
  ];
  const wsHistory = XLSX.utils.json_to_sheet(historyData);
  XLSX.utils.book_append_sheet(wb, wsHistory, 'Historical_Duties');

  return wb;
}

/**
 * Import and update database from uploaded Excel workbook buffer.
 * Preserves historical counts and updates existing officers by unique identifier.
 */
function importExcelBuffer(buffer) {
  const wb = XLSX.read(buffer, { type: 'buffer' });
  const results = {
    officersCreated: 0,
    officersUpdated: 0,
    leavesImported: 0,
    holidaysImported: 0,
    historyImported: 0,
    errors: []
  };

  const importTx = db.transaction(() => {
    // 1. Process Officers
    if (wb.Sheets['Officers']) {
      const rows = XLSX.utils.sheet_to_json(wb.Sheets['Officers']);
      for (const row of rows) {
        const serviceId = String(row.Service_ID || row.service_id || row['Service ID'] || '').trim();
        if (!serviceId) continue;

        const name = String(row.Name || row.name || '').trim();
        const rank = String(row.Rank || row.rank || 'Officer').trim();
        const status = String(row.Status || row.status || 'ACTIVE').trim().toUpperCase();
        const joiningDate = normalizeDateStr(row.Joining_Date || row.joining_date || '2026-01-01');
        const transferDate = normalizeDateStr(row.Transfer_Date || row.transfer_date);
        const normalCount = parseInt(row.Normal_Duty_Count ?? row.normal_duty_count ?? 0, 10);
        const sundayCount = parseInt(row.Sunday_Duty_Count ?? row.sunday_duty_count ?? 0, 10);

        const existing = db.prepare('SELECT * FROM officers WHERE service_id = ?').get(serviceId);
        if (existing) {
          // Do not overwrite duty counts with a lower value; preserve duty history
          const updatedNormal = Math.max(existing.normal_duty_count, isNaN(normalCount) ? 0 : normalCount);
          const updatedSunday = Math.max(existing.sunday_duty_count, isNaN(sundayCount) ? 0 : sundayCount);

          db.prepare(`
            UPDATE officers
            SET name = ?, rank = ?, status = ?, joining_date = ?, transfer_date = ?,
                normal_duty_count = ?, sunday_duty_count = ?
            WHERE id = ?
          `).run(
            name || existing.name,
            rank || existing.rank,
            status || existing.status,
            joiningDate || existing.joining_date,
            transferDate || existing.transfer_date,
            updatedNormal,
            updatedSunday,
            existing.id
          );
          results.officersUpdated++;
        } else {
          db.prepare(`
            INSERT INTO officers (service_id, name, rank, status, joining_date, transfer_date, normal_duty_count, sunday_duty_count)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
          `).run(
            serviceId,
            name,
            rank,
            status,
            joiningDate,
            transferDate,
            isNaN(normalCount) ? 0 : normalCount,
            isNaN(sundayCount) ? 0 : sundayCount
          );
          results.officersCreated++;
        }
      }
    }

    // 2. Process Holidays
    if (wb.Sheets['Holidays']) {
      const rows = XLSX.utils.sheet_to_json(wb.Sheets['Holidays']);
      for (const row of rows) {
        const holidayDate = normalizeDateStr(row.Holiday_Date || row.holiday_date);
        const desc = String(row.Description || row.description || 'Declared Holiday').trim();
        if (holidayDate) {
          db.prepare(`
            INSERT INTO holidays (holiday_date, description)
            VALUES (?, ?)
            ON CONFLICT(holiday_date) DO UPDATE SET description = excluded.description
          `).run(holidayDate, desc);
          results.holidaysImported++;
        }
      }
    }

    // 3. Process Leaves
    if (wb.Sheets['Leaves']) {
      const rows = XLSX.utils.sheet_to_json(wb.Sheets['Leaves']);
      for (const row of rows) {
        const serviceId = String(row.Service_ID || row.service_id || '').trim();
        const officer = db.prepare('SELECT id FROM officers WHERE service_id = ?').get(serviceId);
        if (!officer) {
          results.errors.push(`Leave import error: Officer with Service ID ${serviceId} not found.`);
          continue;
        }

        const leaveStart = normalizeDateStr(row.Leave_Start || row.leave_start);
        const leaveEnd = normalizeDateStr(row.Leave_End || row.leave_end);
        const prefixStart = normalizeDateStr(row.Prefix_Start || row.prefix_start);
        const prefixEnd = normalizeDateStr(row.Prefix_End || row.prefix_end);
        const suffixStart = normalizeDateStr(row.Suffix_Start || row.suffix_start);
        const suffixEnd = normalizeDateStr(row.Suffix_End || row.suffix_end);
        const returnDate = normalizeDateStr(row.Return_Date || row.return_date) || (leaveEnd ? normalizeDateStr(addDaysStr(leaveEnd, 1)) : null);
        const remarks = String(row.Remarks || row.remarks || '').trim();

        if (leaveStart && leaveEnd && returnDate) {
          db.prepare(`
            INSERT INTO leaves (officer_id, leave_start, leave_end, prefix_start, prefix_end, suffix_start, suffix_end, return_date, remarks)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
          `).run(officer.id, leaveStart, leaveEnd, prefixStart, prefixEnd, suffixStart, suffixEnd, returnDate, remarks);
          results.leavesImported++;
        }
      }
    }

    // 4. Process Historical Duties
    if (wb.Sheets['Historical_Duties']) {
      const rows = XLSX.utils.sheet_to_json(wb.Sheets['Historical_Duties']);
      for (const row of rows) {
        const dutyDate = normalizeDateStr(row.Duty_Date || row.duty_date);
        const serviceId = String(row.Service_ID || row.service_id || '').trim();
        const routineType = String(row.Routine_Type || row.routine_type || 'NORMAL').trim().toUpperCase();
        const isPunishment = parseInt(row.Is_Punishment ?? row.is_punishment ?? 0, 10) === 1 ? 1 : 0;
        const punishmentReason = String(row.Punishment_Reason || row.punishment_reason || '').trim();

        if (dutyDate && serviceId) {
          const officer = db.prepare('SELECT id FROM officers WHERE service_id = ?').get(serviceId);
          if (officer) {
            db.prepare(`
              INSERT INTO duty_history (duty_date, officer_id, routine_type, is_punishment, punishment_reason)
              VALUES (?, ?, ?, ?, ?)
              ON CONFLICT(duty_date) DO UPDATE SET
                officer_id = excluded.officer_id,
                routine_type = excluded.routine_type,
                is_punishment = excluded.is_punishment,
                punishment_reason = excluded.punishment_reason
            `).run(dutyDate, officer.id, routineType, isPunishment, punishmentReason);
            results.historyImported++;
          }
        }
      }
    }
  });

  importTx();
  return results;
}

function addDaysStr(dateStr, days) {
  const d = new Date(dateStr + 'T00:00:00Z');
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().split('T')[0];
}

/**
 * Export current roster and full system data to Excel.
 */
function exportRosterToExcel(rosterWeekId) {
  const wb = XLSX.utils.book_new();

  // 1. Current Week Roster Sheet
  const assignments = db.prepare(`
    SELECT da.duty_date as Date,
           da.day_name as Day,
           da.routine_type as Routine,
           CASE WHEN da.is_holiday = 1 THEN da.holiday_name ELSE '' END as Holiday,
           COALESCE(o.rank || ' ' || o.name, 'Unassigned') as Assigned_Officer,
           o.service_id as Service_ID,
           CASE
             WHEN da.is_swap = 1 THEN 'Swapped with ' || COALESCE(so.rank || ' ' || so.name, 'Other') || ' - ' || COALESCE(da.swap_reason, '')
             ELSE ''
           END as Swap_Details,
           CASE
             WHEN da.is_punishment = 1 THEN 'Punishment Duty (' || COALESCE(da.punishment_reason, '') || ')'
             ELSE ''
           END as Remarks
    FROM duty_assignments da
    LEFT JOIN officers o ON da.assigned_officer_id = o.id
    LEFT JOIN officers so ON da.swapped_with_officer_id = so.id
    WHERE da.roster_week_id = ?
    ORDER BY da.duty_date ASC
  `).all(rosterWeekId);

  const wsRoster = XLSX.utils.json_to_sheet(assignments);
  XLSX.utils.book_append_sheet(wb, wsRoster, 'Duty_Roster');

  // 2. All Officers & Current Counts Sheet
  const officers = db.prepare(`
    SELECT service_id as Service_ID,
           name as Name,
           rank as Rank,
           status as Status,
           joining_date as Joining_Date,
           transfer_date as Transfer_Date,
           normal_duty_count as Normal_Duty_Count,
           sunday_duty_count as Sunday_Duty_Count,
           (normal_duty_count + sunday_duty_count) as Total_Duty_Count
    FROM officers
    ORDER BY status ASC, name ASC
  `).all();

  const wsOfficers = XLSX.utils.json_to_sheet(officers);
  XLSX.utils.book_append_sheet(wb, wsOfficers, 'Officers_Summary');

  return XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
}

module.exports = {
  generateTemplateWorkbook,
  importExcelBuffer,
  exportRosterToExcel
};
