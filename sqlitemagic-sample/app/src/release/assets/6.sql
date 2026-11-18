CREATE INDEX IF NOT EXISTS main."index_todo_item_list" ON "todo_item" ("list")
CREATE VIRTUAL TABLE IF NOT EXISTS main."todo_item_fts" USING fts5("title", content='todo_item', content_rowid='id', tokenize='porter unicode61')
CREATE TRIGGER IF NOT EXISTS main."todo_item_fts_ai" AFTER INSERT ON "todo_item" BEGIN INSERT INTO "todo_item_fts"(rowid, "title") VALUES (new."id", new."title"); END
CREATE TRIGGER IF NOT EXISTS main."todo_item_fts_ad" AFTER DELETE ON "todo_item" BEGIN INSERT INTO "todo_item_fts"("todo_item_fts", rowid, "title") VALUES ('delete', old."id", old."title"); END
CREATE TRIGGER IF NOT EXISTS main."todo_item_fts_au" AFTER UPDATE OF "id", "title", rowid, oid, _rowid_ ON "todo_item" WHEN old."id" IS NOT new."id" OR old."title" IS NOT new."title" BEGIN INSERT INTO "todo_item_fts"("todo_item_fts", rowid, "title") VALUES ('delete', old."id", old."title"); INSERT INTO "todo_item_fts"(rowid, "title") VALUES (new."id", new."title"); END
INSERT INTO main."todo_item_fts"("todo_item_fts") VALUES ('rebuild')
