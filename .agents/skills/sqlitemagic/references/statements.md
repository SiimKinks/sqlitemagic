# INSERT, UPDATE, DELETE and raw SQL

Statements that insert rows of chosen columns or change rows chosen by a condition, and whole raw SQL for what the DSL
does not express. Saving whole objects is in [entity-operations.md](entity-operations.md).

## Contents

- [INSERT](#insert)
- [UPDATE](#update)
- [DELETE](#delete)
- [Statement terminals](#statement-terminals)
- [Raw SELECT](#raw-select)
- [Raw INSERT, UPDATE and DELETE](#raw-insert-update-and-delete)
- [What has no DSL](#what-has-no-dsl)

## INSERT

```kotlin
(INSERT INTO AUTHOR COLUMNS AUTHOR.NAME VALUES "Ada").execute()

val inserted: Int = INSERT
  .INTO(BOOK)
  .COLUMNS(BOOK.TITLE, BOOK.PAGES, BOOK.AUTHOR)
  .VALUES("Dune", 412, authorId)
  .VALUES("Emma", 474, authorId)
  .execute()

INSERT
  .INTO(BOOK)
  .COLUMNS(BOOK.TITLE, BOOK.PAGES, BOOK.AUTHOR)
  .VALUES(drafts.map { Row3(it.title, it.pages, authorId) })
  .execute()

INSERT
  .INTO(AUTHOR)
  .DEFAULT_VALUES()
  .execute()

// The rows of a query: one selected value per listed column, in order
val archived: Int = INSERT
  .INTO(ARCHIVED_BOOK)
  .COLUMNS(ARCHIVED_BOOK.TITLE, ARCHIVED_BOOK.PAGES)
  .SELECT(
    SELECT
      .COLUMNS(BOOK.TITLE, BOOK.PAGES)
      .FROM(BOOK)
      .WHERE(BOOK.PAGES GREATER_THAN 1000)
  )
  .execute()

// The new row's ID instead of the count: one VALUES row or DEFAULT_VALUES()
val bookId: Long? = INSERT
  .OR(CONFLICT_IGNORE)
  .INTO(BOOK)
  .COLUMNS(BOOK.TITLE, BOOK.PAGES, BOOK.AUTHOR)
  .VALUES("Dune", 412, authorId)
  .readRowId()
  .execute()

// More than nine columns: each row assigns the listed columns by name
fun InsertedRow<Event>.fill(tap: Tap) {
  this[EVENT.KIND] = "tap"
  this[EVENT.SCREEN] = tap.screen
  this[EVENT.AT] = tap.at
  this[EVENT.USER] = tap.userId
  this[EVENT.X] = tap.x
  this[EVENT.Y] = tap.y
  this[EVENT.OS] = "android"
  this[EVENT.OS_VERSION] = tap.osVersion
  this[EVENT.APP_VERSION] = tap.appVersion
  this[EVENT.LOCALE] = tap.locale
}

INSERT
  .INTO(EVENT)
  .COLUMNS(
    EVENT.KIND, EVENT.SCREEN, EVENT.AT, EVENT.USER, EVENT.X,
    EVENT.Y, EVENT.OS, EVENT.OS_VERSION, EVENT.APP_VERSION, EVENT.LOCALE
  )
  .VALUES {
    it.fill(tap)
    it[EVENT.USER] = SELECT.COLUMN(AUTHOR.ID).FROM(AUTHOR).WHERE(AUTHOR.NAME IS "Ada").asScalar()
  }
  .VALUES(queuedTaps, InsertedRow<Event>::fill)
  .execute()
```

- `COLUMNS` lists the columns of the target table; the others take their SQL defaults
  ([models.md](models.md#columns-nullability-and-defaults)), so a partial row needs no whole object. A required
  relationship, a `@Unique` column and a column of a unique index have no implicit default: leaving one out fails with
  `NOT NULL constraint failed`. A column of another table does not compile; one of an aliased copy makes `COLUMNS`
  throw `IllegalArgumentException`.
- Each `VALUES` call adds one row, and all rows go into one statement: `VALUES (?, ?), (?, ?)`. A row takes either
  host values of the columns' input types, encoded through their transformers as `SET` does (`null` writes NULL to a
  nullable column), or expressions only. Mix the two by turning host values into expressions with `asValue()`.
- An expression row takes each column's value type with any nullability: a parameter, a function, `asValue()`, or a
  scalar subquery (`query.asScalar()`, which is always nullable). A NULL into a `NOT NULL` column fails with SQLite's
  `NOT NULL constraint failed`. A wider number, such as `COUNT()` or arithmetic, states its read type with
  `readAs(SqlType.INT)`, which leaves the SQL unchanged.
- A list of rows uses `Row1`…`Row9`, one value per column: `VALUES(names.map(::Row1))` for one column. An empty list
  adds no rows; a statement whose rows all come from empty lists returns 0, notifies nothing and runs no SQL, so
  callers need no emptiness check. Each value binds one variable: below SQLite 3.32.0 (the framework driver before
  API 31) more than 999 variables fail with `too many SQL variables`, so split very large lists yourself.
- One to nine columns take their row values by position, typed per column. More than nine columns (or a spread
  array, `COLUMNS(*columns)`) take rows that assign every listed column by name: `VALUES { it[EVENT.KIND] = "tap" }`
  adds one row, and `VALUES(list) { row, element -> … }` one row per element, as list rows above. A row can mix
  host values and expressions, each assignment by the rules above, and assigning a column again keeps the last
  value. A row that leaves a listed column without a value or assigns an unlisted one makes `VALUES` throw
  `IllegalArgumentException`.
- Values cannot read the target table: a field in `VALUES` outside a subquery throws `IllegalArgumentException`.
- Conflict algorithm in SQL order: `INSERT OR CONFLICT_IGNORE INTO AUTHOR …`, `INSERT.OR(CONFLICT_REPLACE).INTO(…)`.
  The count is SQLite's `changes()`: ignored rows count nothing, and an `INSERT OR REPLACE` counts the inserted row,
  not the one it deleted.
- `DEFAULT_VALUES()` inserts one row of defaults.
- `SELECT(query)` after `COLUMNS` inserts the rows of a query, which selects one value per listed column in column
  order: `SELECT.COLUMN(…)` for one column, `SELECT.COLUMNS(…)` for more. Each selected expression follows the
  expression-row rules above: the column's value type with any nullability, and `readAs` for a wider number
  (`SELECT.COLUMN(COUNT().readAs(SqlType.INT))`). A query of another column count or value type does not compile. The
  types come from the selected expressions, so `mapTo` and `readAs` on the query change nothing: a query that reads
  DTOs elsewhere inserts as is. More than nine columns (or a spread array) take any query, such as a
  `SELECT.COLUMNS(…)` of as many columns; SQLite reports a different column count (`2 values for 10 columns`) and
  the selected types are not checked.
- The query may be a compound, read a subquery in `FROM`, or start with `WITH`. Only its SQL is used: bind its
  parameters on the INSERT with `bind`, and choose the connection on the INSERT; a query with `usingConnection` makes
  `SELECT` throw `IllegalArgumentException`. The count and notifications are as for `VALUES` rows; no rows selected
  count 0 and notify nothing. Neither `VALUES` nor `readRowId()` follows `SELECT(query)`.
- `readRowId()` turns a statement that inserts one row into one that returns the new row's ID, `Long?`, instead of
  the count: `null` when nothing was inserted, such as a row `CONFLICT_IGNORE` ignored. For an auto-increment `Long`
  ID it is the new ID. It is offered after a single `VALUES` row (typed, expression or wide `VALUES { … }`) and after
  `DEFAULT_VALUES()`, with or without `OR`. After a second `VALUES` or list rows it does not compile, since SQLite
  reports only the last row's ID. Call it before `bind` and `usingConnection`, which return the count statement. A
  `WITHOUT ROWID` table has no row ID: `readRowId()` throws `IllegalArgumentException`.

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
| `execute()` | inserted, updated or deleted row count, `Int` (blocking); after `readRowId()` the new row ID, `Long?` |
| `compile()` | `CompiledMutation`, `execute()` repeatedly; after `readRowId()` `CompiledRowIdInsert` |
| `bind(param TO value, …)` | the statement with parameter values, before `compile`/`execute` |
| `usingConnection(connection)` | after the last clause |
| coroutines `await()` | `Int`; after `readRowId()` `Long?` |
| RxJava `observe()` | cold `Single<Int>`; after `readRowId()` cold `Maybe<Long>`, empty when nothing was inserted; add `subscribeOn(Schedulers.io())` |

- Statements are immutable values like queries; `@CheckResult` makes lint flag a discarded one.
- A successful statement that changed rows notifies observers of its table (after the commit inside a transaction).
  `DELETE`, `UPDATE.OR(CONFLICT_REPLACE)` and `INSERT.OR(CONFLICT_REPLACE)` also notify the tables their deletes
  cascade into. A statement that fails after it starts running notifies too, since some rows may have changed.
- One statement that changes many rows beats a loop of entity operations: one round trip, one notification.
- A statement with list rows is prepared for each run, since every list size is its own SQL; other statements reuse
  the connection's prepared statement.

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
- A connection keeps the columns of `*` that it first prepared: name the columns of a table altered at runtime.
- Raw SQL names tables and columns itself. Interpolate generated fields for typo-proof names only in typed raw
  fragments ([expressions.md](expressions.md#raw-fragments-and-functions)); in whole raw statements write plain SQL.
- Reading a view through raw SQL quotes its dotted column names: `SELECT "author.name" FROM book_card`
  ([views.md](views.md#columns-and-names)).

## Raw INSERT, UPDATE and DELETE

```kotlin
INSERT.RAW("INSERT INTO book (title, pages, author) VALUES (?, ?, ?)", "Dune", 412, authorId)
  .notifies(BOOK)
  .execute()

UPDATE.RAW("UPDATE book SET pages = pages + ? WHERE author = ?", 10, authorId)
  .notifies(BOOK)
  .execute()

DELETE.RAW("DELETE FROM book WHERE author = ? AND pages < ?", authorId, 10)
  .notifies(BOOK)
  .execute()
```

- `.notifies(TABLE, …)` names the generated tables the statement changes, including the ones its deletes cascade
  into; undeclared changes notify nothing. Tables without generated code cannot be observed.
- A conflict clause goes into the text (`UPDATE OR IGNORE …`).
- Terminals as for typed statements: `execute()`, `compile()`, `usingConnection`, `await()`, `observe()`.

## What has no DSL

| Need | Use |
| --- | --- |
| Native UPSERT (`ON CONFLICT DO UPDATE`), `RETURNING` | entity `persist()`; read back with a query |
| Recursive CTE, reading a `PRAGMA` | raw SELECT |
| `ATTACH`, setting a `PRAGMA` | `configureConnection`, which runs on every connection; a raw statement reaches one connection only |
| DDL (tables, indexes, triggers) | models and annotations; triggers and virtual tables are not supported ([migrations.md](migrations.md)) |
| `UPDATE … FROM`, `NULLS FIRST/LAST`, named windows | subqueries, `CASE` in `ORDER_BY`, or raw SQL |
