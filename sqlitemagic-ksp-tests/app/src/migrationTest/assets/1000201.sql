ALTER TABLE author RENAME TO author_
ALTER TABLE magazine RENAME TO magazine_
ALTER TABLE complex_object_with_same_leafs RENAME TO complex_object_with_same_leafs_
CREATE TABLE IF NOT EXISTS author (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT DEFAULT NULL, boxed_boolean INTEGER DEFAULT NULL, primitive_boolean INTEGER DEFAULT 0)
INSERT INTO author (id,boxed_boolean,primitive_boolean) SELECT id,boxed_boolean,primitive_boolean FROM author_
CREATE TABLE IF NOT EXISTS magazine (id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT DEFAULT NULL, related_entity INTEGER DEFAULT NULL REFERENCES author(id) ON DELETE CASCADE, count INTEGER DEFAULT NULL)
INSERT INTO magazine (id) SELECT id FROM magazine_
CREATE TABLE IF NOT EXISTS complex_object_with_same_leafs (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT DEFAULT '', simple_value INTEGER DEFAULT NULL, entity_with_relationship INTEGER DEFAULT NULL, simple_value_duplicate INTEGER DEFAULT NULL)
INSERT INTO complex_object_with_same_leafs (id,name,simple_value,simple_value_duplicate) SELECT id,name,simple_value,simple_value_duplicate FROM complex_object_with_same_leafs_
DROP TABLE IF EXISTS complex_object_with_same_leafs_
DROP TABLE IF EXISTS magazine_
DROP TABLE IF EXISTS author_
ALTER TABLE author RENAME TO simple_mutable_entity
ALTER TABLE magazine RENAME TO entity_with_relationship
