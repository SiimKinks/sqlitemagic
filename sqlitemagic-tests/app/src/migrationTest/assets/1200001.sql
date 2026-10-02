ALTER TABLE query_composition_author RENAME TO query_composition_author_legacy
CREATE TABLE query_composition_author (id INTEGER PRIMARY KEY, name TEXT NOT NULL)
INSERT INTO query_composition_author (id, name) SELECT id, legacy_name FROM query_composition_author_legacy
DROP TABLE query_composition_author_legacy
