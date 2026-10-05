CREATE TABLE IF NOT EXISTS migration_account (id TEXT PRIMARY KEY, label TEXT UNIQUE) WITHOUT ROWID
CREATE TABLE IF NOT EXISTS migration_entry (id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT DEFAULT '' REFERENCES migration_account(id) ON DELETE CASCADE, reference TEXT DEFAULT '', person INTEGER DEFAULT NULL, amount INTEGER DEFAULT 0, memo TEXT DEFAULT NULL)
CREATE TABLE IF NOT EXISTS migration_preference (key TEXT UNIQUE, value TEXT DEFAULT '')
CREATE TABLE IF NOT EXISTS migration_profile (id INTEGER PRIMARY KEY, contact_city TEXT DEFAULT NULL, contact_phone TEXT DEFAULT NULL, email TEXT DEFAULT '', rating REAL DEFAULT 0.0, avatar BLOB DEFAULT NULL)
CREATE TABLE IF NOT EXISTS migration_region (id TEXT PRIMARY KEY, label TEXT DEFAULT '')
CREATE UNIQUE INDEX IF NOT EXISTS main."migration_entry_account_reference" ON "migration_entry" ("account", "reference")
CREATE INDEX IF NOT EXISTS main."migration_entry_memo" ON "migration_entry" ("memo")
CREATE INDEX IF NOT EXISTS main."migration_preference_lookup" ON "migration_preference" ("key", "value")
