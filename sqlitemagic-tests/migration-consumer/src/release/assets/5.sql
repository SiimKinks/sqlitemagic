DROP INDEX IF EXISTS main."migration_entry_account_reference"
DROP INDEX IF EXISTS main."migration_entry_memo"
DROP INDEX IF EXISTS main."migration_person_slug"
ALTER TABLE migration_entry RENAME TO migration_entry_
CREATE TABLE IF NOT EXISTS migration_entry (id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT DEFAULT '' REFERENCES migration_account(id) ON DELETE CASCADE, reference TEXT DEFAULT '', person INTEGER DEFAULT NULL, amount REAL DEFAULT 0.0, status TEXT DEFAULT 'pending')
INSERT INTO migration_entry (id,account,reference,person,amount) SELECT id,account,reference,person,amount FROM migration_entry_
DROP TABLE IF EXISTS migration_entry_
ALTER TABLE migration_profile RENAME TO migration_profile_
CREATE TABLE IF NOT EXISTS migration_profile (id INTEGER PRIMARY KEY, contact_city TEXT DEFAULT NULL, contact_phone TEXT DEFAULT NULL, contact_postal_code TEXT DEFAULT NULL, email TEXT DEFAULT '', rating REAL DEFAULT 0.0, avatar BLOB DEFAULT NULL)
INSERT INTO migration_profile (id,contact_city,contact_phone,email,rating,avatar) SELECT id,contact_city,contact_phone,email,rating,avatar FROM migration_profile_
DROP TABLE IF EXISTS migration_profile_
ALTER TABLE migration_region ADD COLUMN country TEXT DEFAULT 'global'
CREATE UNIQUE INDEX IF NOT EXISTS main."migration_entry_account_reference" ON "migration_entry" ("account", "reference")
CREATE UNIQUE INDEX IF NOT EXISTS main."migration_person_slug" ON "migration_person" ("slug")
