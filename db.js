const Database = require('better-sqlite3');
const path = require('path');
const fs = require('fs');
const bcrypt = require('bcryptjs');

const dataDir = path.join(__dirname, 'data');
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}

const dbPath = path.join(dataDir, 'roster.db');
const db = new Database(dbPath);

// Enable WAL mode and foreign keys for performance and integrity
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

// Initialize schema
db.exec(`
  CREATE TABLE IF NOT EXISTS settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS officers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    service_id TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    rank TEXT DEFAULT 'Officer',
    status TEXT NOT NULL DEFAULT 'ACTIVE', -- 'ACTIVE', 'TRANSFERRED_OUT'
    joining_date TEXT NOT NULL,            -- YYYY-MM-DD
    transfer_date TEXT,                    -- YYYY-MM-DD (effective date if transferred out)
    normal_duty_count INTEGER NOT NULL DEFAULT 0,
    sunday_duty_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
  );

  CREATE TABLE IF NOT EXISTS holidays (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    holiday_date TEXT UNIQUE NOT NULL,     -- YYYY-MM-DD
    description TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS leaves (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    officer_id INTEGER NOT NULL,
    type TEXT NOT NULL DEFAULT 'LEAVE',    -- 'LEAVE', 'TY_DUTY'
    leave_start TEXT NOT NULL,             -- YYYY-MM-DD
    leave_end TEXT NOT NULL,               -- YYYY-MM-DD
    prefix_start TEXT,                     -- YYYY-MM-DD
    prefix_end TEXT,                       -- YYYY-MM-DD
    suffix_start TEXT,                     -- YYYY-MM-DD
    suffix_end TEXT,                       -- YYYY-MM-DD
    return_date TEXT NOT NULL,             -- YYYY-MM-DD
    remarks TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY(officer_id) REFERENCES officers(id) ON DELETE CASCADE
  );

  CREATE TABLE IF NOT EXISTS roster_weeks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    week_start TEXT UNIQUE NOT NULL,       -- Monday YYYY-MM-DD
    week_end TEXT NOT NULL,                 -- Sunday YYYY-MM-DD
    status TEXT NOT NULL DEFAULT 'DRAFT',  -- 'DRAFT', 'FINALIZED'
    finalized_at TEXT,
    notes TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
  );

  CREATE TABLE IF NOT EXISTS duty_assignments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    roster_week_id INTEGER NOT NULL,
    duty_date TEXT NOT NULL,               -- YYYY-MM-DD
    routine_type TEXT NOT NULL,            -- 'NORMAL', 'SUNDAY'
    day_name TEXT NOT NULL,                -- 'Monday', 'Tuesday', ...
    is_holiday INTEGER NOT NULL DEFAULT 0,
    holiday_name TEXT,
    assigned_officer_id INTEGER,
    original_officer_id INTEGER,
    swapped_with_officer_id INTEGER,
    is_swap INTEGER NOT NULL DEFAULT 0,
    swap_reason TEXT,
    is_punishment INTEGER NOT NULL DEFAULT 0,
    punishment_reason TEXT,
    is_manual_override INTEGER NOT NULL DEFAULT 0,
    is_return_priority INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY(roster_week_id) REFERENCES roster_weeks(id) ON DELETE CASCADE,
    FOREIGN KEY(assigned_officer_id) REFERENCES officers(id),
    FOREIGN KEY(original_officer_id) REFERENCES officers(id),
    FOREIGN KEY(swapped_with_officer_id) REFERENCES officers(id),
    UNIQUE(roster_week_id, duty_date)
  );

  CREATE TABLE IF NOT EXISTS duty_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    duty_date TEXT UNIQUE NOT NULL,        -- YYYY-MM-DD
    officer_id INTEGER NOT NULL,
    routine_type TEXT NOT NULL,            -- 'NORMAL', 'SUNDAY'
    is_punishment INTEGER NOT NULL DEFAULT 0,
    punishment_reason TEXT,
    roster_week_id INTEGER,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    FOREIGN KEY(officer_id) REFERENCES officers(id)
  );
`);

// Initialize Watchkeeper default password: admin@123
const adminPassRow = db.prepare('SELECT value FROM settings WHERE key = ?').get('admin_password');
if (!adminPassRow) {
  const hash = bcrypt.hashSync('admin@123', 10);
  db.prepare('INSERT INTO settings (key, value) VALUES (?, ?)').run('admin_password', hash);
}

// Migration: Ensure type column exists on leaves table
try {
  db.exec("ALTER TABLE leaves ADD COLUMN type TEXT NOT NULL DEFAULT 'LEAVE'");
} catch (e) {
  // column already exists
}

module.exports = db;
