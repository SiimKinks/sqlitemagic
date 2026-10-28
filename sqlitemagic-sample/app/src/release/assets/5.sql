ALTER TABLE todo_list RENAME TO todo_list_
DELETE FROM todo_item WHERE list IS NULL
ALTER TABLE todo_item RENAME TO todo_item_
CREATE TABLE IF NOT EXISTS todo_list (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL DEFAULT '')
INSERT INTO todo_list (id,name) SELECT id,IFNULL(name, '') FROM todo_list_
CREATE TABLE IF NOT EXISTS todo_item (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL DEFAULT '', list INTEGER NOT NULL REFERENCES todo_list(id) ON DELETE CASCADE, completed INTEGER NOT NULL DEFAULT 0)
INSERT INTO todo_item (id,title,list,completed) SELECT id,IFNULL(title, ''),list,IFNULL(completed, 0) FROM todo_item_
DROP TABLE IF EXISTS todo_item_
DROP TABLE IF EXISTS todo_list_
