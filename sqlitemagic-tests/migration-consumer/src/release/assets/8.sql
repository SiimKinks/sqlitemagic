DELETE FROM migration_profile
ALTER TABLE migration_profile RENAME TO migration_profile_
CREATE TABLE IF NOT EXISTS migration_profile (id INTEGER PRIMARY KEY, contact_city TEXT DEFAULT NULL, contact_phone TEXT DEFAULT NULL, contact_postal_code TEXT DEFAULT NULL, email TEXT NOT NULL DEFAULT '', rating REAL NOT NULL DEFAULT 0.0, avatar BLOB DEFAULT NULL, handle TEXT NOT NULL UNIQUE)
DROP TABLE IF EXISTS migration_profile_
