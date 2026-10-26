# Changes that compile but behave differently

Walk this list for every migrated query and for code around entity operations, views and loggers. Each item says how
to find affected code and what to do. Items marked **needs a decision** can be ported two ways; ask the user when
neither the code nor its tests show which result is intended.

## Contents

- [Queries are immutable](#queries-are-immutable)
- [Values bind natively](#values-bind-natively)
- [Raw fragments are grouped as operands](#raw-fragments-are-grouped-as-operands)
- [Arithmetic returns SQLite's number](#arithmetic-returns-sqlites-number)
- [Legacy sum was TOTAL](#legacy-sum-was-total)
- [Aggregates read null for empty input](#aggregates-read-null-for-empty-input)
- [count() counts the final result](#count-counts-the-final-result)
- [Outer-joined values must be lifted](#outer-joined-values-must-be-lifted)
- [Automatic joins follow one relationship path](#automatic-joins-follow-one-relationship-path)
- [Entity reads reuse only exact relationship joins](#entity-reads-reuse-only-exact-relationship-joins)
- [Partial entities check required properties before SQL](#partial-entities-check-required-properties-before-sql)
- [Nested queries resolve names as SQL does](#nested-queries-resolve-names-as-sql-does)
- [Compounds type by the left arm](#compounds-type-by-the-left-arm)
- [View columns have qualified names](#view-columns-have-qualified-names)
- [Entity operations throw SQLite's exceptions](#entity-operations-throw-sqlites-exceptions)
- [Smaller behavior changes](#smaller-behavior-changes)

## Queries are immutable

Legacy `usingConnection()` and `queryDeep()` mutated a shared builder, so a call whose result was dropped still
applied. Every new call returns a new query and a dropped result changes nothing. Clause calls are `@CheckResult`, so
Android Lint flags a dropped one; `usingConnection`, `queryDeep`, `mapTo` and `readAs` are not flagged.

- Find: statements that call `usingConnection`, `queryDeep`, `mapTo`, `readAs` or a clause on a query variable without
  using the result, such as `if (connection != null) select.usingConnection(connection)`.
- Do: assign or chain the result (`query = query.usingConnection(connection)`, or
  `connection?.let(query::usingConnection) ?: query`).

## Values bind natively

Legacy bound every argument as TEXT through `toString()`. Values now bind by storage class: `Long`/`Int`/`Short`/
`Boolean` as INTEGER, `Double`/`Float` as REAL, `String` as TEXT, `ByteArray` and `Byte` as BLOB, custom types through
their transformer. Raw arguments follow the same rule.

- Effect: SQLite converts a bound value only through the other operand's affinity. A column with INTEGER, REAL,
  NUMERIC or TEXT affinity converts it, so ordinary column comparisons match as before. Where the other operand has
  no affinity (function results, arithmetic, raw expressions, columns declared without a type or as BLOB), legacy
  compared a TEXT argument with the value and now compares the native value: `'10' = 10` is false, so such
  comparisons can start or stop matching.
- Find: raw expressions and raw queries whose arguments were strings standing for numbers (`withArgs("10")`,
  `expr("5")`); comparisons whose left side is a function, arithmetic or raw expression.
- Do: pass arguments of the intended storage type (`10L`, not `"10"`), and check the results of the comparisons above
  against real data.

## Raw fragments are grouped as operands

Legacy wrote a raw fragment's text verbatim inside the surrounding expression, so
`"a = 1 OR b = 2".expr AND (X IS 3)` rendered `(a = 1 OR b = 2 AND x = ?)`, which SQL reads as
`a = 1 OR (b = 2 AND x = ?)`. A raw operand now renders parenthesized, `(a = 1 OR b = 2) AND x = ?`, as the Kotlin
code reads.

- Find: raw predicate text containing `OR` (or another operator weaker than its neighbor) combined with typed
  predicates.
- Do: nothing when the Kotlin grouping was intended; otherwise restructure the predicate explicitly.

## Arithmetic returns SQLite's number

Legacy arithmetic typed every result as `Double`: `TABLE.INTEGER + 2` read `37.0`. SQLite computes the same values,
but results now decode per value under the static type `Number`: INTEGER as `Long`, REAL as `Double`. So
`TABLE.INTEGER + 2` reads `37L` and `TABLE.INTEGER / 2` reads `18L` for 36 (legacy `18.0`; SQLite divides integers
either way). `/` and `%` are nullable, since a zero divisor gives NULL. An integer overflow reads the REAL `Double`
SQLite returns. Unary minus no longer keeps the field's type.

- Find: `+ - * / %` and unary `-` on fields in projections, DTO constructors and code that formats or compares the
  result as `Double`.
- Do: accept `Number`/`Number?` and convert (`toLong()`, `toDouble()`), or choose a fixed reader with
  `readAs(SqlType.LONG)`/`readAs(SqlType.DOUBLE)` on a scalar query. Handle `null` from `/` and `%`.

## Legacy sum was TOTAL

**Needs a decision.** Legacy `Select.sum(x)` rendered SQL `total(x)`: `Double`, 0.0 for no rows, never overflowing.

- `TOTAL(x)` keeps that SQL. Its type is `Double?` (conflicting infinities can produce NULL), so add `?: 0.0` or
  `!!` where a `Double` was expected.
- `SUM(x)` is SQL's `sum(x)`: `Number?` (Long for integer input), `null` for no rows, and SQLite's "integer overflow"
  error.
- Default to `TOTAL` for a mechanical port; prefer `SUM` when exact integer sums matter and callers handle `null`.

## Aggregates read null for empty input

Legacy typed MIN, MAX, AVG and GROUP_CONCAT by their input column, so over a NOT NULL column they were non-null, and
an empty or all-NULL input read `0`, `0.0` or a `null` String despite the non-null type. Now MIN and MAX are
SQL-nullable (keeping the input's decoder), AVG reads `Double?` and GROUP_CONCAT `String?`; an empty input reads null.

- Effect where code still compiles: `?: 0` fallbacks start to apply, and a `takeFirst().observe().runQuery()` stream
  skips null results instead of emitting `0`.
- Find: MIN/MAX/AVG/GROUP_CONCAT projections, especially `takeFirst()` reads and their streams.
- Do: handle null, use `IFNULL(MAX(BOOK.PAGES), 0L.asValue())` where a default is wanted, or `runQueryOrDefault(0L)`
  on streams.

## count() counts the final result

Legacy `count()` replaced the select list with `count(*)` and kept the rest of the query: with GROUP BY it returned
the first group's count, DISTINCT was dropped so duplicates counted, LIMIT had no effect, and an OFFSET skipped the only
row, so the count failed. Now `count()` counts the rows the query returns, `SELECT count(*) FROM (query)`, including
DISTINCT, grouping and pagination.

- Find: `count()` on queries with GROUP_BY, DISTINCT, LIMIT or OFFSET.
- Do: count the query whose rows should be counted. For the legacy total, drop those clauses; for per-group counts,
  select `COUNT()` per group: `SELECT.COLUMNS(BOOK.AUTHOR, COUNT()).FROM(BOOK).GROUP_BY(BOOK.AUTHOR)`.

## Outer-joined values must be lifted

Legacy typed a NOT NULL field selected through `LEFT JOIN` as non-null and read SQL NULL into it. A selected value
whose Kotlin type is non-null but which an outer join can make NULL now fails before SQL runs, naming the field to
lift. The same applies to fields reached through a nullable relationship by an automatic join, and to RIGHT/FULL joins.

- Find: `LEFT_JOIN`/`leftJoin`/`LEFT_OUTER_JOIN`, `NATURAL_LEFT_JOIN` and automatically joined fields of nullable
  relationships, in selections, `mapTo` constructors and view definitions. These compile; only running the query
  (or its test) shows the rejection.
- Do: select `BOOK.TITLE.nullable()` and accept `String?`. Filters such as `WHERE (BOOK.ID.IS_NULL())` need nothing.
  A query's `count()` reads no values and needs no lift.

## Automatic joins follow one relationship path

Legacy joined related tables for selected fields along every path, so a table reached through two relationships
(`book.author`, `book.editor`) expanded one selected column into one output per path. A field of an unjoined table now
joins automatically only when exactly one relationship path reaches it; with none or several the query is rejected
before SQL, naming the paths. UPDATE and DELETE never join automatically.

- Find: queries that select, filter or order by fields of tables they do not join.
- Do: join the intended relationship explicitly, aliased where needed:
  `SELECT.COLUMN(editor.NAME.nullable()).FROM(BOOK).LEFT_JOIN(editor).ON(BOOK.EDITOR IS editor.ID)` with
  `val editor = AUTHOR AS "editor"`.

## Entity reads reuse only exact relationship joins

Legacy reused any same-table join whose ON or USING mentioned the foreign key, so a filtered join
(`LEFT_JOIN AUTHOR ON ((BOOK.AUTHOR IS AUTHOR.ID) AND (AUTHOR.NAME IS "Ada"))`) nulled the relationship of rows that
failed the filter; a bare join of the target, without ON, was reused too. Now a join supplies a relationship only when
its ON is exactly `fk IS id`, in either order and through any alias; other joins stay as written and the relationship
gets its own automatic join, so every book reads its real author.

- Find: entity queries (especially `queryDeep()`) that join a relationship's target with extra conditions or with no
  condition.
- Do: if code relied on the nulled relationship to filter, move the condition to `WHERE`.

## Partial entities check required properties before SQL

A partial entity (`SELECT.COLUMNS(…).FROM(T).mapTo(T)`, legacy `Select.columns(…)`) that misses a required property
used to fail with `SQLException` at the first row read, so a query returning no rows succeeded. It now throws
`IllegalArgumentException` before SQL, whatever the number of rows: `Book.title is required; select "book"."title"`.
A required relationship needs its foreign key, or its target's fields when the target cannot be built from its ID.
Nullable properties read null; mutable ones keep their initializers.

- Find: every migrated partial selection and view `all()` mapping.
- Do: select the named fields, or make the property nullable in the model.

## Nested queries resolve names as SQL does

Legacy joined a related table into a subquery when the subquery selected one of its fields, even when the enclosing
query used that table; fields in WHERE rendered as written, so correlated filters behave as before. Now a field of a
table the nested query does not itself use refers to the nearest enclosing query that has it, as in SQL; only when no
enclosing query has it does the nested query join it. A legacy
`UPDATE TABLE ENTITY SET (ENTITY.COUNT to (Select.column(ENTITY.COUNT) FROM OTHER))` joined `entity` inside the
subquery; the same query now reads the updated row's own `count`.

- Find: subqueries (`IN`, comparisons, `toColumn`, SET values, view columns) that select fields of tables the
  enclosing query uses.
- Do: when the nested query must read another row of a table the outer query also uses, alias the inner occurrence
  and join it inside the subquery.

## Compounds type by the left arm

- The right arm's result type must be a subtype of the left's; a `UNION` of scalars whose right arm may be NULL reads
  nullable (`List<String?>`). Legacy typed the compound by the left arm regardless.
- A right arm with its own `ORDER_BY`, `LIMIT` or `OFFSET` now renders as `SELECT * FROM (…)`, so only that arm is
  limited. Legacy rendered the clauses inline, where SQLite applied them to the whole compound.
- Find: compounds whose right arm is ordered or limited; code that relied on the whole result being limited.
- Do: put ORDER BY and LIMIT on the compound itself (`(a UNION b).ORDER_BY(…).LIMIT(1)`) to limit the whole result.

## View columns have qualified names

A view's columns are named by its definition: an `AS` alias, otherwise the field's qualified name (`book.title`,
`author.name`). Legacy named them `title` and `name` and matched `@ViewColumn` keys per row, falling back to the last
name segment and then to a position.

- `@ViewColumn` keys that name a selected field, alias or block keep working, now resolved once by name. A key that
  relied on the last-segment fallback must name the qualified column; a missing name now fails with SQLite's
  "no such column" when the view is queried instead of reading another position.
- Raw SQL over a view quotes the dotted names (`SELECT "author.name" FROM book_card`), or the definition aliases the
  output (`AUTHOR.NAME AS "name"`).
- Installed databases keep persistent views created by legacy code, with legacy column names, so reading them fails
  with "no such column" until a schema upgrade rebuilds them. Persistent views are created with the schema, and every
  upgrade drops and recreates all of them; temporary views are created on every open. Ship the migration with a
  database version bump. The runtime needs no SQL script for the new version (each version's script is optional),
  but the project's release or migration tooling may expect its own entries. Test opening and upgrading a database
  that legacy code created, reading the view.

## Entity operations throw SQLite's exceptions

`insert()` and `persist()` wrapped SQLite errors in `OperationFailedException`, and a bulk insert or persist conflict
returned `false`. Now single and bulk insert, persist, update and delete throw SQLite's exception as it is
(`SQLiteConstraintException`, an `android.database.SQLException`), roll back bulk work, and `observe()` emits it.

- `OperationFailedException` remains only for outcomes SQLite completes without an error: an `update().observe()` that
  changed no row, an insert that wrote no row under a non-ignoring conflict algorithm, a skipped relationship write and
  a relationship that resolved to a NULL ID. `CONFLICT_IGNORE` results are unchanged.
- An entity `observe()` resolves the default connection when subscribed, not when `observe()` is called; a `Single`
  built before setup no longer throws at the call.
- Each failing terminal logs one `Operation failed` error; entity transactions no longer log
  `Error while executing db transaction`.
- Find: `catch (… OperationFailedException …)`, `onError` handlers matching it, and uses of a bulk insert/persist
  `Boolean` result.
- Do: catch `SQLException` (or the specific constraint exception); treat a bulk conflict as an exception, not `false`.

## Smaller behavior changes

| Change | Action |
| --- | --- |
| Every non-zero stored Boolean reads `true` (legacy generated readers read only 1 as `true`) | none for data the library wrote; store 0 for false |
| A required scalar read of SQL NULL throws `android.database.SQLException`; legacy scalar reads returned `0`, `0.0` or `null` | select or read nullable where NULL is possible |
| A failed UPDATE/DELETE that reached SQLite notifies its table, so observers re-run | none |
| `count()` skips joins that only an entity read needs; its observer re-runs only for tables that can change the count | none |
| Values inside view definitions render as SQL literals; legacy failed view creation with bound arguments | drop `asColumn` workarounds |
| `Float`/`Double` NaN is rejected with `IllegalArgumentException` when encoded | handle NaN before building SQL values |
| `FastCursor.moveToNext()` past the last row moves to `position == count` | backward traversal after a failed forward move starts at the last row |
| An empty `IN` list renders `IN ()` instead of a bound `'0'` | none; results are the same |
| `takeFirst()` keeps explicit `LIMIT`/`OFFSET`; `LIMIT(0).takeFirst()` returns null | none |
| `REPLACE`, `TRIM`, `FORMAT` and `GROUP_CONCAT` arguments bind as `?` (literals only in views) | update tests asserting SQL text |
| Generated SQL quotes identifiers and qualifies tables (`"main"."book"`) | update tests asserting SQL text |
| `x AS ""` renders `AS ""`, a column named `""` (legacy dropped the alias) | drop empty aliases |
| Raw `UPDATE.RAW`/`DELETE.RAW` notify only tables declared with `notifies(…)` | declare tables observed elsewhere |
| The default downgrade drops the newer version's views as well as tables | none |
| Query execution time is no longer logged | time queries in the caller if needed |
| Entity result singletons (`EntityInsertResult.Ignored`, …) are data objects (`toString()` is `Ignored`) | none unless code parsed the old text |
