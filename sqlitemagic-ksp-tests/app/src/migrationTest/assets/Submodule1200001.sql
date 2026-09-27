CREATE TABLE persistent_submodule_readback AS SELECT id, value FROM submodule_persistent_value
DROP TABLE submodule_persistent_value
CREATE TABLE submodule_persistent_value (id TEXT PRIMARY KEY, value TEXT NOT NULL)
INSERT INTO submodule_persistent_value (id, value) SELECT id, value FROM persistent_submodule_readback
DROP TABLE persistent_submodule_readback
