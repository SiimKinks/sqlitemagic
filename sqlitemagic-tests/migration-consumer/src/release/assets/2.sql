ALTER TABLE migration_person ADD COLUMN name_length INTEGER DEFAULT NULL
UPDATE migration_person SET name=upper(name), name_length=length(name)
