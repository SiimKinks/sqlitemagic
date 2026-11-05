# UPDATE, DELETE and raw SQL

Statements that change rows chosen by a condition, and whole raw SQL for what the DSL does not express. Saving whole
objects is in [entity-operations.md](entity-operations.md).

## Contents

- [UPDATE](#update)
- [DELETE](#delete)
- [Statement terminals](#statement-terminals)
- [Raw SELECT](#raw-select)
- [Raw UPDATE and DELETE](#raw-update-and-delete)
- [What has no DSL](#what-has-no-dsl)

## UPDATE

```kotlin
val renamed: Int = (
    UPDATE
        TABLE BOOK
        SET (BOOK.TITLE to "Dune")
        WHERE (BOOK.ID IS bookId)
    )
  .execute()

UPDATE
  .TABLE(BOOK)
  .SET(BOOK.PAGES, BOOK.PAGES + 1)
  .SET(BOOK.STATUS, BookStatus.PUBLISHED)
  .WHERE(BOOK.AUTHOR IS authorId)
  .execute()
```

- `SET` takes the column's input type, an expression of the same decoded type, or any numeric expression for a numeric
  column; that numeric `SET` and the arithmetic operators are top-level imports (`com.siimkinks.sqlitemagic.SET`,
  `…plus`). A `NOT NULL` column accepts only non-null values and expressions; wrap a nullable expression in
  `IFNULL`/`COALESCE`. `SET (BOOK.EDITOR to null)` works because the column is nullable; `SET_NULL BOOK.EDITOR` writes
  NULL to any column and lets SQLite enforce constraints.
- Chain several `SET`s; each must target the updated table itself: a column of another table does not compile, and
  one of an aliased copy makes `SET` throw `IllegalArgumentException`.
- Values can come from the row itself (`BOOK.PAGES + 1`) or from a correlated scalar subquery, `query.asScalar()`,
  wrapped in `IFNULL(…, fallback.asValue())` for a `NOT NULL` column since a subquery can return no row.
- Conflict algorithm in SQL order: `UPDATE OR CONFLICT_IGNORE TABLE BOOK SET (…)`,
  `UPDATE.OR(CONFLICT_REPLACE).TABLE(…)` with `android.database.sqlite.SQLiteDatabase.CONFLICT_*`.
- `WHERE` is optional, as in SQL: without it every row changes. The target is the plain table (no alias), every field
  must be the target's, and nothing joins automatically; reach other tables through subqueries (`IN query`, `EXISTS`,
  `asScalar()`).

## DELETE

```kotlin
val removed: Int = (DELETE FROM BOOK WHERE (BOOK.STATUS IS BookStatus.DRAFT)).execute()

DELETE
  .FROM(BOOK)
  .WHERE(BOOK.AUTHOR NOT_IN (SELECT COLUMN AUTHOR.ID FROM AUTHOR))
  .execute()
```

`DELETE FROM BOOK` without `WHERE` deletes every row. Deletes cascade where relationships declare `onDeleteCascade`.

## Statement terminals

| Terminal | Result |
| --- | --- |
| `execute()` | affected row count, `Int` (blocking) |
| `compile()` | `CompiledMutation`, `execute()` repeatedly |
| `bind(param TO value, …)` | the statement with parameter values, before `compile`/`execute` |
| `usingConnection(connection)` | after the last clause |
| coroutines `await()` | `Int` |
| RxJava `observe()` | cold `Single<Int>`; add `subscribeOn(Schedulers.io())` |

- Statements are immutable values like queries; `@CheckResult` makes lint flag a discarded one.
- A successful statement that changed rows notifies observers of its table (after the commit inside a transaction).
- One statement that changes many rows beats a loop of entity operations: one round trip, one notification.

## Raw SELECT

For SQL the DSL cannot express (recursive CTEs, `PRAGMA`, SQLite features newer than the DSL), run
the whole statement raw, bind its arguments and declare what it reads:

```kotlin
data class TitleMatch(val id: Long, val title: String)

val matches: List<TitleMatch> = SELECT
  .RAW("SELECT id, title FROM book WHERE title LIKE ? ESCAPE '!' ORDER BY title", "${prefix.escapeLike()}%")
  .observes(BOOK)
  .readAs(SqlType.LONG, SqlType.STRING)
  .mapTo(::TitleMatch)
  .execute()

val pages: Long? = SELECT.RAW("SELECT max(pages) FROM book")
  .readAs(SqlType.LONG.nullable)
  .takeFirst()
  .execute()

val columns = SELECT.RAW("PRAGMA table_info(book)").useRows { rows ->
  buildList {
    while (rows.step()) {
      add(rows.read(1, SqlType.STRING))
    }
  }
}
```

- `SELECT.RAW(sql, args…)` binds the arguments to `?` in order as native values; infix `SELECT RAW "…"` is for text
  without arguments.
- `.observes(TABLE, …)` declares the tables it reads: then `observe()` is a live stream like a typed query's. Without
  it, `observe()` runs once (`Single`/`Maybe`, or the coroutine `await()`). Raw text that reads no tables declares
  none.
- `readAs(type)` reads one column, `readAs(a, b, …)` up to nine columns as `Row2`…`Row9`, by position. A field can
  lend its reader and nullness: `readAs(BOOK.STATUS)`. Each execution checks that the column count matches. Then
  `mapTo(::Dto)`, `execute()`, `takeFirst()`, `useRows`, `compile()`, `observe()`.
- Without `readAs`, read rows inside `useRows { rows -> }`: `rows.step()` advances (forward only),
  `rows.read(index, SqlType.X)`, `rows.isNull(index)`, `rows.columnType(index)`, `rows.columnCount`,
  `rows.columnName(index)`. Rows exist only inside the block.
- Raw SQL names tables and columns itself. Interpolate generated fields for typo-proof names only in typed raw
  fragments ([expressions.md](expressions.md#raw-fragments-and-functions)); in whole raw statements write plain SQL.
- Reading a view through raw SQL quotes its dotted column names: `SELECT "author.name" FROM book_card`
  ([views.md](views.md#columns-and-names)).

## Raw UPDATE and DELETE

```kotlin
UPDATE.RAW("UPDATE book SET pages = pages + ? WHERE author = ?", 10, authorId)
  .notifies(BOOK)
  .execute()

DELETE.RAW("DELETE FROM book WHERE author = ? AND pages < ?", authorId, 10)
  .notifies(BOOK)
  .execute()
```

- `.notifies(TABLE, …)` names the generated tables the statement changes; undeclared changes notify nothing. Tables
  without generated code cannot be observed.
- A conflict clause goes into the text (`UPDATE OR IGNORE …`). There is no raw INSERT; rows are inserted through
  entity operations.
- Terminals as for typed statements: `execute()`, `compile()`, `usingConnection`, `await()`, `observe()`.

## What has no DSL

| Need | Use |
| --- | --- |
| Insert rows | entity `insert()`/bulk insert; no INSERT statement or `INSERT … SELECT` |
| Native UPSERT (`ON CONFLICT DO UPDATE`), `RETURNING` | entity `persist()`; read back with a query |
| Recursive CTE, reading a `PRAGMA` | raw SELECT |
| `ATTACH`, setting a `PRAGMA` | `configureConnection`, which runs on every connection; a raw statement reaches one connection only |
| DDL (tables, indexes, triggers) | models and annotations; triggers and virtual tables are not supported ([migrations.md](migrations.md)) |
| `UPDATE … FROM`, `NULLS FIRST/LAST`, named windows | subqueries, `CASE` in `ORDER_BY`, or raw SQL |
