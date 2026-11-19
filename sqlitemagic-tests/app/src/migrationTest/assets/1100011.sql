DROP TRIGGER IF EXISTS main."changed_note_fts_ai"
DROP TRIGGER IF EXISTS main."changed_note_fts_ad"
DROP TRIGGER IF EXISTS main."changed_note_fts_au"
DROP TABLE IF EXISTS main."changed_note_fts"
CREATE VIRTUAL TABLE IF NOT EXISTS main."changed_note_fts" USING fts4("title", "body")
CREATE TRIGGER IF NOT EXISTS main."changed_note_fts_ai" AFTER INSERT ON "changed_note" BEGIN INSERT INTO "changed_note_fts"(docid, "title", "body") VALUES (new."id", new."title", new."body"); END
CREATE TRIGGER IF NOT EXISTS main."changed_note_fts_ad" AFTER DELETE ON "changed_note" BEGIN DELETE FROM "changed_note_fts" WHERE docid = old."id"; END
CREATE TRIGGER IF NOT EXISTS main."changed_note_fts_au" AFTER UPDATE OF "id", "title", "body", rowid, oid, _rowid_ ON "changed_note" WHEN old."id" IS NOT new."id" OR old."title" IS NOT new."title" OR old."body" IS NOT new."body" BEGIN DELETE FROM "changed_note_fts" WHERE docid = old."id"; INSERT INTO "changed_note_fts"(docid, "title", "body") VALUES (new."id", new."title", new."body"); END
INSERT INTO main."changed_note_fts"(docid, "title", "body") SELECT "id", "title", "body" FROM main."changed_note"
CREATE VIRTUAL TABLE IF NOT EXISTS main."added_note_fts" USING fts4("body")
CREATE TRIGGER IF NOT EXISTS main."added_note_fts_ai" AFTER INSERT ON "added_note" BEGIN INSERT INTO "added_note_fts"(docid, "body") VALUES (new."id", new."body"); END
CREATE TRIGGER IF NOT EXISTS main."added_note_fts_ad" AFTER DELETE ON "added_note" BEGIN DELETE FROM "added_note_fts" WHERE docid = old."id"; END
CREATE TRIGGER IF NOT EXISTS main."added_note_fts_au" AFTER UPDATE OF "id", "body", rowid, oid, _rowid_ ON "added_note" WHEN old."id" IS NOT new."id" OR old."body" IS NOT new."body" BEGIN DELETE FROM "added_note_fts" WHERE docid = old."id"; INSERT INTO "added_note_fts"(docid, "body") VALUES (new."id", new."body"); END
INSERT INTO main."added_note_fts"(docid, "body") SELECT "id", "body" FROM main."added_note"
