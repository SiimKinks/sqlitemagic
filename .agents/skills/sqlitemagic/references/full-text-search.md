# Full-text search

Full-text indexes are declared on a `@Table` and searched with `MATCH` through a generated index object. FTS5 needs the
bundled driver; FTS4 works on both drivers.

## Contents

- [Declare an index](#declare-an-index)
- [Search](#search)

## Declare an index

`@FullTextIndex` on a `@Table` and `@FullText` on each column to search declare an SQLite FTS5 index, or FTS4 with
`version = FullTextVersion.FTS4`. The library creates it with the schema as an external-content FTS table named
`<table>_fts`, which reads the text from the table, and keeps it in sync with generated triggers; the application
writes only the table.

```kotlin
@Table
@FullTextIndex(tokenize = "porter unicode61")
data class Article(
  @Id val id: Long? = null,
  @FullText val title: String,
  @FullText val body: String,
  val author: String
)
```

- The table needs a stable row ID: an `@Id` stored as `INTEGER`, or `@RowId val rowId: Long? = null` beside a text
  or other non-INTEGER `@Id`. `TEMPORARY` and `WITHOUT_ROWID` tables cannot have one.
- Any persisted column can be indexed, including embedded and transformed columns (indexed in their stored form);
  `@FullText` alone persists a property under `persistAll = false`. Columns follow property declaration order.
- All but one of a table's indexes need a name: `@FullTextIndex("article_titles")` and
  `@FullText("article_titles")`; a property can belong to several.
- `@FullText` inside an embedded value joins an index only in tables that declare it, so one embedded type can serve
  tables with and without that index. On a table's own property, `@FullText` of an undeclared index is a compile
  error.
- Options: `tokenize` (`unicode61` by default, `porter unicode61` for English stemming), `prefix = [2, 3]` (prefix
  indexes for prefix queries), `detail = FullTextDetail.COLUMN` or `NONE` (smaller index; phrase and `NEAR` queries
  fail, with `NONE` column filters too), `columnSize = false` (no stored token counts), and
  `@FullText(role = FullTextRole.UNINDEXED)` for a column stored in the FTS table but not searched. SQLite reports an
  invalid tokenizer when the schema is created.
- FTS5 needs the bundled driver: the framework driver has no FTS5 module, and opening the database fails with
  `no such module: fts5`. Apps on the framework driver use FTS4, which both drivers support:

  ```kotlin
  @Table
  @FullTextIndex(version = FullTextVersion.FTS4, tokenize = "unicode61")
  data class Memo(
    @Id val id: Long? = null,
    @FullText val text: String,
    @FullText(role = FullTextRole.LANGUAGE_ID) val language: Int
  )
  ```

- FTS4's default tokenizer, `simple`, folds the case of ASCII letters only; `tokenize = "unicode61"` handles other text,
  `porter` stems English but cannot be combined with `unicode61` as in FTS5. FTS4 quotes each tokenizer argument:
  `tokenize = """unicode61 "remove_diacritics=2""""`. `icu` works on the framework driver only (the bundled one fails
  with `unknown tokenizer: icu`). `prefix = [2, 3]` works as in FTS5.
- FTS4 options: `matchinfo = FullTextMatchinfo.FTS3` (smaller index; `MATCHINFO` formats other than `p`, `c` and `x`
  fail), `order = FullTextOrder.DESC` (matches in descending row ID order; on API 24 `integrityCheck()` reports such an
  index malformed although its queries are correct), `UNINDEXED`, and `@FullText(role = FullTextRole.LANGUAGE_ID)` on
  one column, whose value SQLite reads as an integer: it splits the index by it, and a search matches language 0 unless
  it also compares `MEMO_FTS.LANGUAGE` ([Search](#search)). `detail` and `columnSize` are compile errors on FTS4, the
  FTS4 options on FTS5.
- Indexed columns cannot be named like the index, nor `rank` or `rowid` in FTS5 or `docid` in FTS4. A table with an
  FTS4 index cannot have a column named `rowid` other than its row ID, since FTS4 reads the row ID by that name.
- A schema with a full-text index runs every library connection with `PRAGMA recursive_triggers = ON`, so rows that
  `CONFLICT_REPLACE` replaces leave the index. Connections the library did not open (the `sqlite3` shell, Database
  Inspector) run without it.
- Entity updates re-index a row only when its row ID or an indexed column changed.
- Search it with the generated index object, `ArticleFtsTable.Companion.ARTICLE_FTS` (the index name in upper snake
  case): `SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH query)` ([Search](#search)).
- Migrations keep the index in step with the model and its table. A removed or changed index is dropped before the
  table statements. An added or changed index, or one whose table is rebuilt or renamed, is created after them and
  refilled from every row of the table, with a build warning for each refill of an existing table
  ([migrations.md](migrations.md#changing-models-safely)).

## Search

A table with a `@FullTextIndex` ([Declare an index](#declare-an-index)) is searched through its generated index
object, `ARTICLE_FTS`. `MATCH` takes a query string in the syntax of the index's version (FTS5 or FTS4), as a
`String`, a `String` expression or a parameter.

```kotlin
val hits = (SELECT FROM ARTICLE WHERE (ARTICLE_FTS MATCH "sqlite OR room") ORDER_BY ARTICLE_FTS.RANK.ASC())
  .execute()
val inTitles = (SELECT COLUMN ARTICLE.TITLE FROM ARTICLE WHERE (ARTICLE_FTS.TITLE MATCH "sqlite"))
  .execute()
```

```sql
SELECT "article"."id", "article"."title", "article"."body", "article"."author" FROM "main"."article"
INNER JOIN "main"."article_fts" ON "article_fts"."rowid" = "article"."id"
WHERE "article_fts"."article_fts" MATCH ? ORDER BY "article_fts"."rank" ASC
```

- `ARTICLE_FTS MATCH q` searches every indexed column; `ARTICLE_FTS.TITLE MATCH q` only that column.
  `ARTICLE_FTS.RANK` is the match rank (`Double?`, lower is better, NULL in a query that does not search the index);
  `ARTICLE_FTS.ROWID` has the type of the table's row ID: its INTEGER `@Id`, or `Long` for `@RowId`, compared with
  `NOTE.ROW_ID`.
- A query that names the index without joining it gets an `INNER JOIN` placed directly after its table, before the
  joins of relationships, so SQLite scans the index first and reads rows and related entities by key. `count()` keeps
  the join. Queries observe the table, which is what changes the index.
- A subquery that selects from the table joins the index itself; a subquery of another table reads the index of the
  enclosing query, which must search it too.
- The join needs the table as a source written once, in `FROM` or an explicit join. A table written several times
  (aliases, self-joins) or reached only through a relationship is rejected before SQL: join it with
  `INNER_JOIN(ARTICLE)`, or join the index explicitly, `INNER_JOIN(ARTICLE_FTS).ON(ARTICLE_FTS.ROWID IS ARTICLE.ID)`.
  An aliased index (`ARTICLE_FTS AS "found"`) is never joined automatically.
- The index has no entity, and write statements do not take it. Select its columns from it with
  `SELECT.COLUMNS(ARTICLE_FTS.ROWID, ARTICLE_FTS.TITLE).FROM(ARTICLE_FTS)`, and update or delete matching rows
  through their row IDs: `DELETE FROM ARTICLE WHERE (ARTICLE.ID IN (SELECT COLUMN ARTICLE_FTS.ROWID FROM ARTICLE_FTS
  WHERE (ARTICLE_FTS MATCH query)))`.
- SQLite reports a malformed query string (`fts5: syntax error near …`, FTS4 `malformed MATCH expression`) when the
  query runs, as it does for `MATCH` under `OR` or in an explicit `LEFT_JOIN`.
- `ARTICLE_FTS.terms(input)` turns free text such as user input into a query that never fails: each term between
  whitespace (or NUL) becomes a quoted phrase (`"sqlite" "OR" "room"`, so operators and quotes are searched as text),
  every term must match, and blank input matches nothing. `terms(text = input, prefix = true)` also matches words
  that begin with the last term, for search as you type (`"sqlite" "ro"*`). FTS4's `terms` writes the prefix as
  `"ro*"`, reads `*` and quotes inside a term as word breaks, since FTS4 cannot quote them in a phrase, and leaves out
  terms without a letter or digit, since FTS4 matches no row for a phrase without words.

```kotlin
val results = (
    SELECT.COLUMNS(
        ARTICLE.ID,
        HIGHLIGHT(ARTICLE_FTS.TITLE, "<b>", "</b>"),
        SNIPPET(ARTICLE_FTS.BODY, "<b>", "</b>", "…", 10)
    )
        FROM ARTICLE
        WHERE (ARTICLE_FTS MATCH ARTICLE_FTS.terms(text = input, prefix = true))
        ORDER_BY BM25(ARTICLE_FTS.TITLE to 10.0).ASC()
        LIMIT 20
    )
  .execute()
```

```sql
SELECT "article"."id", HIGHLIGHT("article_fts"."article_fts", 0, ?, ?),
  SNIPPET("article_fts"."article_fts", 1, ?, ?, ?, ?) FROM "main"."article"
INNER JOIN "main"."article_fts" ON "article_fts"."rowid" = "article"."id"
WHERE "article_fts"."article_fts" MATCH ? ORDER BY BM25("article_fts"."article_fts", ?) ASC LIMIT 20
```

- `HIGHLIGHT(column, open, close)` is the column's text with every match marked; `SNIPPET(column, open, close,
  ellipsis, tokens)` a fragment of at most `tokens` (up to 64) around the matches, and `SNIPPET(ARTICLE_FTS, …)` one
  from the best matching column. The column functions read `String`, `String?` for a nullable column;
  `SNIPPET(ARTICLE_FTS, …)` reads `String?`. The library passes the column's position in the index and binds the
  other arguments, weights included.
- `BM25(ARTICLE_FTS)` equals `RANK` under `MATCH`; `BM25(ARTICLE_FTS.TITLE to 10.0, ARTICLE_FTS.BODY to 0.5)`
  weighs columns (unnamed ones weigh 1.0), so matches in heavier columns rank better. Weighing a column twice or
  columns of different indexes (an alias counts as another) or a NaN weight fails before SQL.
- In a query that does not search their index with `MATCH`, `HIGHLIGHT` and `SNIPPET` return the unmarked text
  (`SNIPPET(ARTICLE_FTS, …)` from the first column, NULL when it is NULL) and `BM25` 0, while `RANK` is NULL. On a
  row that a `LEFT_JOIN` of the index fills with NULLs, SQLite fails every function with "no such cursor" (FTS4:
  "illegal first argument to …").
- An FTS4 index has no `RANK`, `BM25` or `HIGHLIGHT` (compile errors). `SNIPPET` works on it as on FTS5, without
  marks or text outside a search, and two FTS4 functions read the matches: `OFFSETS(MEMO_FTS)`, four integers per
  match (column, query term, byte offset, byte size) as `String`, and `MATCHINFO(MEMO_FTS)`, the `pcx` statistics as
  a `ByteArray` of 32-bit integers in the device's byte order (`MATCHINFO(MEMO_FTS, "pcnalx")` picks others). Both
  are empty outside a search. A language-ID column is a plain field of the index object; compare it to search
  another language:

  ```kotlin
  val memos = (
      SELECT.COLUMNS(MEMO, SNIPPET(MEMO_FTS.TEXT, "<b>", "</b>", "…", 10), OFFSETS(MEMO_FTS))
          FROM MEMO
          WHERE ((MEMO_FTS MATCH MEMO_FTS.terms(text = input, prefix = true)) AND (MEMO_FTS.LANGUAGE IS 1))
      )
    .execute()
  ```

- Maintenance statements on the index object, executed like other statements: `ARTICLE_FTS.rebuild()` refills the
  index from the table (needed only after something bypassed the triggers; observers of the table re-run),
  `optimize()` merges its segments, and `integrityCheck()` fails with SQLite's error ("database disk image is
  malformed") when the index differs from the table's rows.
