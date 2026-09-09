CREATE INDEX added_index ON index_add_drop_change (added_value)
DROP INDEX removed_index
DROP INDEX changed_index
CREATE UNIQUE INDEX changed_index ON index_add_drop_change (changed_value)
CREATE TABLE index_rebuild_new (id INTEGER PRIMARY KEY, value TEXT, rebuilt_value TEXT DEFAULT '')
INSERT INTO index_rebuild_new (id, value) SELECT id, value FROM index_rebuild
DROP TABLE index_rebuild
ALTER TABLE index_rebuild_new RENAME TO index_rebuild
CREATE INDEX unchanged_rebuild_index ON index_rebuild (value)
CREATE INDEX rebuilt_new_index ON index_rebuild (rebuilt_value)
