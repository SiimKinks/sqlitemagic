DROP INDEX IF EXISTS main."migration_entry_account_reference"
DROP INDEX IF EXISTS main."migration_preference_lookup"
DROP INDEX IF EXISTS main."migration_person_slug_unique"
DELETE FROM migration_account WHERE label IS NULL
ALTER TABLE migration_account RENAME TO migration_account_
DELETE FROM migration_entry WHERE account IS NULL OR reference IS NULL
ALTER TABLE migration_entry RENAME TO migration_entry_
DELETE FROM migration_setting WHERE key IS NULL
ALTER TABLE migration_setting RENAME TO migration_setting_
ALTER TABLE migration_profile RENAME TO migration_profile_
ALTER TABLE migration_person RENAME TO migration_person_
DELETE FROM migration_region WHERE id IS NULL
ALTER TABLE migration_region RENAME TO migration_region_
CREATE TABLE IF NOT EXISTS migration_account (id TEXT PRIMARY KEY, label TEXT NOT NULL UNIQUE) WITHOUT ROWID
INSERT INTO migration_account (id,label) SELECT id,label FROM migration_account_
CREATE TABLE IF NOT EXISTS migration_entry (id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT NOT NULL REFERENCES migration_account(id) ON DELETE CASCADE, reference TEXT NOT NULL, person INTEGER DEFAULT NULL, amount REAL NOT NULL DEFAULT 0.0, status TEXT NOT NULL DEFAULT 'posted')
INSERT INTO migration_entry (id,account,reference,person,amount,status) SELECT id,account,reference,person,IFNULL(amount, 0.0),IFNULL(status, 'posted') FROM migration_entry_
CREATE TABLE IF NOT EXISTS migration_setting (key TEXT NOT NULL UNIQUE, value TEXT NOT NULL DEFAULT '')
INSERT INTO migration_setting (key,value) SELECT key,IFNULL(value, '') FROM migration_setting_
CREATE TABLE IF NOT EXISTS migration_profile (id INTEGER PRIMARY KEY, contact_city TEXT DEFAULT NULL, contact_phone TEXT DEFAULT NULL, contact_postal_code TEXT DEFAULT NULL, email TEXT NOT NULL DEFAULT '', rating REAL NOT NULL DEFAULT 0.0, avatar BLOB DEFAULT NULL)
INSERT INTO migration_profile (id,contact_city,contact_phone,contact_postal_code,email,rating,avatar) SELECT id,contact_city,contact_phone,contact_postal_code,IFNULL(email, ''),IFNULL(rating, 0.0),avatar FROM migration_profile_
CREATE TABLE IF NOT EXISTS migration_person (id INTEGER PRIMARY KEY, name TEXT NOT NULL DEFAULT '', name_length INTEGER DEFAULT NULL, slug TEXT DEFAULT NULL)
INSERT INTO migration_person (id,name,name_length,slug) SELECT id,IFNULL(name, ''),name_length,slug FROM migration_person_
CREATE TABLE IF NOT EXISTS migration_region (id TEXT NOT NULL PRIMARY KEY, label TEXT NOT NULL DEFAULT '', country TEXT NOT NULL DEFAULT 'global')
INSERT INTO migration_region (id,label,country) SELECT id,IFNULL(label, ''),IFNULL(country, 'global') FROM migration_region_
DROP TABLE IF EXISTS migration_region_
DROP TABLE IF EXISTS migration_person_
DROP TABLE IF EXISTS migration_profile_
DROP TABLE IF EXISTS migration_setting_
DROP TABLE IF EXISTS migration_entry_
DROP TABLE IF EXISTS migration_account_
CREATE UNIQUE INDEX IF NOT EXISTS main."migration_entry_account_reference" ON "migration_entry" ("account", "reference")
CREATE INDEX IF NOT EXISTS main."migration_preference_lookup" ON "migration_setting" ("key", "value")
CREATE UNIQUE INDEX IF NOT EXISTS main."migration_person_slug_unique" ON "migration_person" ("slug")
