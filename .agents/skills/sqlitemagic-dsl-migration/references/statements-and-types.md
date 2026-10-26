# Legacy → new statements, views and types

Whole raw SQL, UPDATE/DELETE, views, compiled plans and declared types, loggers, generated code, and what was
removed. Query shapes are in [queries.md](queries.md).

Examples assume generated `AUTHOR` and `BOOK` tables with `Author` and `Book` models; names are illustrative.
"Legacy" covers both of the old DSL's forms: Kotlin infix (`SELECT FROM AUTHOR WHERE (…)`) and the Java-style
fluent builders (`Select.from(AUTHOR).where(…)`). "New" shows the infix form and the dot-call form where they
differ. Keep whichever style the call site already uses.

## Contents

- [Whole raw queries and statements](#whole-raw-queries-and-statements)
- [UPDATE and DELETE](#update-and-delete)
- [Views](#views)
- [Plans, observation and declared types](#plans-observation-and-declared-types)
- [Logger, generated code and models](#logger-generated-code-and-models)
- [Removed without replacement](#removed-without-replacement)

## Whole raw queries and statements

```kotlin
// Legacy
Select.raw("SELECT id, name FROM author WHERE id > ?").from(AUTHOR).withArgs("10").execute()   // Cursor
SELECT RAW "SELECT id FROM author WHERE id > ?" FROM AUTHOR WITH_ARGS arrayOf("10")
Update.table("book").set("count", "1").where("id = ?", "2").execute()
UPDATE TABLE "book" SET ("count" to "1") WHERE ("id=?" to arrayOf("2"))
Delete.from("legacy_log").where("id = ?", "5").execute()

// New
SELECT.RAW("SELECT id, name FROM author WHERE id > ?", 10L).observes(AUTHOR).toCursor().execute()
SELECT.RAW("SELECT id, name FROM author WHERE id > ?", 10L)
    .observes(AUTHOR)
    .readAs(SqlType.LONG, SqlType.STRING.nullable)
    .mapTo(::AuthorName)
    .execute()
UPDATE.RAW("UPDATE book SET count = ? WHERE id = ?", 1, 2L).notifies(BOOK).execute()
DELETE.RAW("DELETE FROM legacy_log WHERE id = ?", 5L).execute()
```

- Arguments go into `RAW` as native values; infix `SELECT RAW "SELECT 1"` remains for text without arguments.
- `.from(tables)`/`FROM tables` on a raw query becomes `.observes(tables)`. Without it, `observe()` runs once
  (`Single`/`Maybe`) instead of streaming. The raw `observe()` is an extension import.
- A raw query without `readAs` has no `execute()`; insert `.toCursor()` for a caller-owned cursor, or read typed rows
  by position with `readAs(…)` (a column count other than `readAs`'s width fails each execution).
- `UPDATE.RAW`/`DELETE.RAW` declare changed tables with `.notifies(TABLE)`; legacy's string table name is gone. A
  conflict algorithm goes into the text (`UPDATE OR REPLACE …`).
- A raw condition on a generated table stays typed: `WHERE ("pages < ?" to arrayOf("100"))` becomes
  `WHERE ("pages < ?".expr(SqlType.BOOLEAN, 100))`.

## UPDATE and DELETE

```kotlin
// Legacy
Update.table(BOOK).set(BOOK.TITLE, "Foo").where(BOOK.ID.`is`(77L)).execute()
UPDATE WITH_CONFLICT_ALGORITHM CONFLICT_IGNORE TABLE BOOK SET (BOOK.TITLE to "Dune")
Update.withConflictAlgorithm(CONFLICT_IGNORE).table(BOOK).setNullable(BOOK.SUBTITLE, null)
Delete.from(BOOK).where(BOOK.ID.isNot(77L).and(BOOK.TITLE.`is`("Foo"))).execute()

// New
UPDATE.TABLE(BOOK).SET(BOOK.TITLE, "Foo").WHERE(BOOK.ID IS 77L).execute()
UPDATE OR CONFLICT_IGNORE TABLE BOOK SET (BOOK.TITLE to "Dune")
UPDATE.OR(CONFLICT_IGNORE).TABLE(BOOK).SET(BOOK.SUBTITLE, null)
DELETE.FROM(BOOK).WHERE((BOOK.ID IS_NOT 77L) AND (BOOK.TITLE IS "Foo")).execute()
```

- `UPDATE TABLE BOOK SET (…) WHERE (…)` and `DELETE FROM BOOK WHERE (…)` keep their infix spelling.
- `setNullable(column, value)` becomes `SET(column, value)`; `SET` accepts null where the column's input type does.
- `SET_NULL BOOK.TITLE` (new) writes SQL NULL to any column, bypassing the encoder; SQLite decides the outcome.
- An expression value must decode to the target's type; any numeric expression fits a numeric target. A NOT NULL
  target rejects a nullable expression at compile time (including `/` and `%` results): wrap it in
  `IFNULL`/`COALESCE`, which replaces legacy `toNotNullable()`.
- Targets are unaliased: `UPDATE TABLE (BOOK AS "b")` is rejected when built. Every SET/WHERE field must belong to the
  target; a correlated subquery aliases its inner occurrence.
- `compile()` returns `CompiledMutation` (legacy `CompiledUpdate`/`CompiledDelete`); `execute()` is `Int` and
  `observe()` a cold `Single<Int>`, as before.

## Views

```kotlin
// Legacy
@ViewQuery
val QUERY = (
    SELECT COLUMNS arrayOf(
      TODO_LIST.all() AS "todo_list",
      (SELECT COLUMN count() FROM TODO_ITEM
          WHERE ((TODO_ITEM.COMPLETED IS asColumn(false)) AND (TODO_ITEM.LIST IS TODO_LIST.ID)))
        .toColumn("items_count")
    )
        FROM TODO_LIST
        ORDER_BY TODO_LIST.NAME.asc()
    )
  .compile()

// New
@ViewQuery
val QUERY = (
    SELECT.COLUMNS(
      TODO_LIST.all() AS "todo_list",
      (SELECT COLUMN COUNT() FROM TODO_ITEM
          WHERE ((TODO_ITEM.COMPLETED IS false) AND (TODO_ITEM.LIST IS TODO_LIST.ID)))
        .asScalar() AS "items_count"
    )
        FROM TODO_LIST
        ORDER_BY TODO_LIST.NAME.ASC()
    )
```

- `@ViewQuery` holds the `SelectQuery` itself; drop `.compile()` (a compiled value fails processing) and any
  `usingConnection`.
- Values in a definition render as SQL literals, so plain values replace `asColumn(…)` there. Raw expressions in a
  definition write constants into the text: SQLite rejects view parameters.
- View columns are named by qualified names (`book.title`) or `AS` aliases. The example's view columns change from
  `id`, `name` to `todo_list.id`, `todo_list.name`, so databases already installed need a schema upgrade before the
  view can be read; see [behavior](behavior.md#view-columns-have-qualified-names), which also covers raw SQL over
  views and `@ViewColumn` keys.

## Plans, observation and declared types

| Legacy | New |
| --- | --- |
| `CompiledSelect<T, S>` | `CompiledSelect<T>` |
| `CompiledFirstSelect<T, S>` | `CompiledFirst<T>` |
| `CompiledCountSelect<S>` | `CompiledCount` |
| `CompiledCursorSelect<T, S>` | `CompiledCursor<T>` (still has `getFromCurrentPosition(cursor)`) |
| `CompiledRawSelect`, `CompiledObservableRawSelect` | `CompiledRawCursor` from `SELECT.RAW(…).toCursor()`, or read plans after `readAs` (the new generic `CompiledRawSelect<T, O>` is the list plan after `readAs`, so a legacy annotation fails on type arguments) |
| `CompiledUpdate`, `CompiledDelete` | `CompiledMutation` |
| `compiled.takeFirst()`, `compiled.count()`, `compiled.toCursor()` | `query.takeFirst()`, `query.count()`, `query.toCursor()`: create each plan from the query |
| `Expr` | `SqlExpression<Boolean, SqlNullable, Boolean?>`; `IS_NULL()`: `SqlExpression<Boolean, Required, Boolean>` |
| `OrderingTerm` | `Ordering` |
| `Select.From<…>`, `Select.Where<…>`, … | inferred stage types, or `SelectQuery<T, S>` after final configuration |
| `NotNullable`, `Nullable` markers | `Required`, `SqlNullable` |
| `Column<T, R, ET, P, N>`, `NumericColumn<…>`, `UniqueNumericColumn<…>` | generated `Column<…>`/`UniqueColumn<…>` (seven type parameters); let them be inferred |
| `val starter: Select<Select.SelectN> = SELECT` | `val starter = SELECT` |
| `Query.Mapper` | removed: map with `readAs`/`mapTo`, or read `toCursor()` directly |

- `S` in `SelectQuery<T, S>` is the selection: `EntitySelection<Author>`, `ScalarSelection<Long, Required, Long>`
  (decoded type, SQL nullness, result), `RowSelection<Row2<Long, String>>`, `RowSelection<SelectedRow>`. When a type
  must be written, copy the IDE's inferred type rather than reconstructing it.
- Observation types and operators are unchanged: `ListQueryObservable`, `SingleItemQueryObservable`,
  `CountQueryObservable`, `runQuery()`, `runQueryOnce()`, `runQueryOrDefault(…)`, `isZero()`, `isNotZero()`.
- New: named parameters, `AUTHOR.NAME.asParameter("name")`, bound per plan with `compiled.bind(name TO "Ada")`.

## Logger, generated code and models

- `Logger`: delete overrides of `logQueryTime(…)`, `logWarning(message)` and the one-argument `logError(message)`
  ("overrides nothing"); keep `logDebug(message)` and `logError(message, throwable)`.
- Regenerate with the new processor. Generated DAOs now live in `com.siimkinks.sqlitemagic`; only code that calls a
  DAO directly (normally tests) changes its import.
- Java models, their persisted members and external transformer functions must be public.
- Hand-built `Table`/`Column` instances, generated readers and adapters are generated-code API: add
  `@file:OptIn(InternalSqliteMagicApi::class)`, or better, use generated tables.
- Entity `byColumn(…)` takes the generated unique column of the entity's own table, as before; a unique column of
  another table fails at the call, and a hand-written `Unique` no longer compiles.

## Removed without replacement

- Lowercase builders and companions: `Select.from/column/columns/distinct/raw`, `Update.table`, `Delete.from`,
  `.where`, `.orderBy`, … (use the uppercase forms above).
- `JOIN` and `LEFT_OUTER_JOIN` spellings, `toNotNullable()`, `inTable()`, `asRawColumn`, `WITH_ARGS`,
  `WITH_CONFLICT_ALGORITHM`, `toColumn`, `toTable`.
- String table and column names inside UPDATE/DELETE (`UPDATE TABLE "book"`, `SET ("title" to "x")`,
  `WHERE ("sql" to args)`): use whole raw statements or raw expressions.
- `Query.Mapper`, `GeneratedDatabase.columnForValue`, `Logger.logQueryTime`.
- Java-facing query API: port query-building Java code to Kotlin.
