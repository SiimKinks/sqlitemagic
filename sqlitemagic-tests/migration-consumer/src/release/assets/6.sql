DROP INDEX IF EXISTS main."migration_entry_account_reference"
DROP INDEX IF EXISTS main."migration_preference_lookup"
DROP INDEX IF EXISTS main."migration_person_slug"
ALTER TABLE migration_preference RENAME TO migration_setting
ALTER TABLE migration_entry RENAME TO migration_entry_
CREATE TABLE IF NOT EXISTS migration_entry (id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT DEFAULT '' REFERENCES migration_account(id) ON DELETE CASCADE, reference TEXT DEFAULT '', person INTEGER DEFAULT NULL, amount REAL DEFAULT 0.0, status TEXT DEFAULT 'posted')
INSERT INTO migration_entry (id,account,reference,person,amount,status) SELECT id,account,reference,person,amount,status FROM migration_entry_
DROP TABLE IF EXISTS migration_entry_
CREATE UNIQUE INDEX IF NOT EXISTS main."migration_entry_account_reference" ON "migration_entry" ("account", "reference")
CREATE INDEX IF NOT EXISTS main."migration_preference_lookup" ON "migration_setting" ("key", "value")
CREATE UNIQUE INDEX IF NOT EXISTS main."migration_person_slug_unique" ON "migration_person" ("slug")
UPDATE migration_entry SET status='posted' WHERE status='pending'
