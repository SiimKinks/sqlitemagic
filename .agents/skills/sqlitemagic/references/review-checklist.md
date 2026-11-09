# Review checklist

Walk this over SqliteMagic code you wrote or review. Each item names what to look for and the fix.

## Correctness

- [ ] **Discarded query or builder calls.** `query.WHERE(p)`, `query.usingConnection(c)`, `book.insert()` without using
  the result change nothing. Assign or chain; every builder call returns a new value.
- [ ] **Missing terminal.** An entity operation or statement without `execute()`, `await()` or a subscribed
  `observe()` never runs. An RxJava write `Single` that nobody subscribes to never runs; one subscribed twice runs
  twice.
- [ ] **Kotlin precedence.** Every comparison inside `AND`/`OR` and every `BETWEEN` on the right of a Boolean
  operator is parenthesized; `p AND q OR r` means `(p AND q) OR r`, left to right.
- [ ] **NULL comparisons.** No `x IS null`-style intent: use `IS_NULL()`/`IS_NOT_NULL()`. `x IS_NOT v` excludes rows
  where `x` is NULL; add `OR (x.IS_NULL())` when they should match.
- [ ] **Outer joins.** Non-null fields read through `LEFT_JOIN` (or nullable relationships) are selected with
  `.nullable()` (a whole table with `all().nullable()`), and the DTO parameter is nullable; `COUNT(x)` rather than
  `COUNT()` counts matches of a `LEFT_JOIN`.
- [ ] **Ambiguous automatic joins.** A field of a table reached by several relationships (`book.author`,
  `book.editor`) is joined explicitly through an alias.
- [ ] **Aliases and computed values are created once** in a `val` and reused, not re-created at each use.
- [ ] **Nullable results handled.** `takeFirst().execute()` is `T?`; aggregates other than `COUNT` and `/`, `%`,
  `asScalar()` are nullable. Handled with `?:`, `IFNULL`/`COALESCE`, or `checkNotNull(…) { "why" }` — never `!!`.
- [ ] **`count()` meaning.** It counts the rows the query returns (after `GROUP_BY`, `DISTINCT`, `LIMIT`); per-group
  counts select `COUNT()` with `GROUP_BY`.
- [ ] **`SUM` vs `TOTAL`.** `SUM` is `null` for no rows and exact; `TOTAL` is `0.0` and floating.
- [ ] **Integer division.** `INTEGER / INTEGER` truncates; cast to `REAL` for fractions.
- [ ] **Partial entities and shallow reads are not written back blindly.** Objects from `mapTo(TABLE)` or with
  ID-only related objects are written with `persist().ignoreNullValues()` only when the missing properties read
  `null`; ID-only related objects with non-null Kotlin defaults are read fully, or the columns change with an UPDATE.
- [ ] **`insert()` of an object graph with existing related rows.** It silently duplicates rows with auto-increment
  IDs and fails on app-assigned or unique ones; use `persist()`, or `handleRecursively = false`.
- [ ] **Exceptions.** Expected conflicts use a conflict algorithm or catch `android.database.SQLException`, not
  `SQLiteConstraintException` (framework driver only). Pre-SQL `IllegalArgumentException`s are fixed in the query, not
  caught.
- [ ] **Transactions.** Multi-step writes that must be atomic are in one transaction; `inTransaction` blocks do not
  call suspend functions; `withTransaction` blocks do not call `runBlocking` or collect observed queries; no network or
  UI waits inside.
- [ ] **Raw SQL.** Values are bound arguments, never interpolated; arguments have their storage type (`10L`, not
  `"10"`); `observes(…)`/`notifies(…)` declare every table read or changed; views' dotted column names are quoted.
- [ ] **Feature versions.** Window functions, `FILTER_WHERE`, `RIGHT`/`FULL` joins, row values and newer SQL functions
  are used only with the bundled driver or a `minSdk` whose SQLite has them.

## Models and schema

- [ ] Non-null properties wherever a value is required; nullable only for real absence.
- [ ] Auto-increment IDs are `@Id val id: Long? = null`; client-assigned IDs say `autoIncrement = false`.
- [ ] Enums and other custom types have one transformer pair; enums are stored by name or explicit code.
- [ ] `onDeleteCascade` on child relationships whose rows must die with the parent; cycles broken with
  `handleRecursively = false` (which keeps that relationship's `onDeleteCascade`: the parent row is written first).
- [ ] Every `onDeleteCascade` column leads an index; indexes on foreign keys and columns that frequent queries filter
  and sort by; no speculative indexes.
- [ ] A schema change to a released app comes with a raised `@Database(version)`, the generated release script, its
  reviewed warnings and a migration test ([migrations.md](migrations.md)).
- [ ] No hand edits to generated code, `db/` state or debug scripts.

## Efficiency

- [ ] No `execute().size`, `execute().isEmpty()`, `execute().first()` where `count()`, `takeFirst()` or `EXISTS` fits.
- [ ] No filtering, sorting, grouping or pagination in Kotlin over a full result.
- [ ] No query per item in a loop; joins, `IN query` or subquery columns instead.
- [ ] Selections match what the code reads; entity reads only where the object is needed; `queryDeep()` only where
  related values are read.
- [ ] Writes of many objects use bulk operations or one transaction; column changes use UPDATE statements.
- [ ] Hot repeated queries keep a compiled plan with parameters; `asLiteral()` only for true constants.
- [ ] Observed queries are narrow, built once (not per recomposition or per item), and not combined with manual
  debouncing in the data layer.

## Style

- [ ] SQL words in SQL order, uppercase DSL calls; one style per call site (infix statement in parentheses with the
  terminal after, or a dot chain with one call per line); new statements follow the file's existing style, and infix
  where the file has none.
- [ ] Queries live next to their model (companion object) or in a repository, as functions returning queries; callers
  choose the terminal.
- [ ] Host values passed directly; `asValue()` only where an expression is required.
- [ ] Types inferred; no hand-written `SqlExpression<…>`/`SelectQuery<…>` types unless a public signature needs one.
- [ ] Blocking terminals only off the main thread; the project's reactive adapter for UI-facing code.
