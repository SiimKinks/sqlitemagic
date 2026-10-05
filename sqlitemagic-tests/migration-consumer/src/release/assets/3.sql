ALTER TABLE migration_person ADD COLUMN slug TEXT DEFAULT NULL
CREATE INDEX IF NOT EXISTS main."migration_person_slug" ON "migration_person" ("slug")
UPDATE migration_person SET slug=lower(name)
