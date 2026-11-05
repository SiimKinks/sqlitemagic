# Expressions

Values, predicates, NULL, arithmetic, aggregates, windows, conditionals, text helpers, CAST, row values, raw fragments
and declared functions. Examples use the schema in [SKILL.md](../SKILL.md#example-schema).

## Contents

- [Types of expressions](#types-of-expressions)
- [Values](#values)
- [Predicates](#predicates)
- [NULL](#null)
- [Arithmetic](#arithmetic)
- [Aggregates](#aggregates)
- [Window functions](#window-functions)
- [Conditionals](#conditionals)
- [Text, CAST and collation](#text-cast-and-collation)
- [Row values](#row-values)
- [Raw fragments and functions](#raw-fragments-and-functions)

## Types of expressions

Every field and expression is a `SqlExpression<R, N, T>`: `R` the decoded type used for compatibility checks, `N`
whether SQL can make it NULL (`Required` or `SqlNullable`), `T` the Kotlin value it reads. Let Kotlin infer these; when
a type must be written, copy the IDE's inferred type. The checks they enable:

- an operand must have the receiver's decoded type: `BOOK.TITLE IS AUTHOR.NAME` compiles, `BOOK.TITLE IS BOOK.PAGES`
  does not;
- a host value must be the field's input type (`BOOK.PAGES GREATER_THAN 300`, an `Int`; convert with `toInt()`/
  `toLong()` first);
- a selected value's `T` decides the result type, so nullable results are visible in Kotlin.

Any two numeric expressions compare natively (`BOOK.ID IS BOOK.PAGES`, `Long` with `Int`), through the top-level
extension: `import com.siimkinks.sqlitemagic.IS` (likewise `GREATER_THAN`, `IN`, `BETWEEN`, …). Same-type comparisons
are members and need no import.

## Values

| Write | Meaning |
| --- | --- |
| `BOOK.PAGES GREATER_THAN 300` | a host value where SQL compares it: bound as `?` through the field's encoder |
| `BOOK.STATUS IS BookStatus.PUBLISHED` | transformed host values encode through their transformer |
| `"Anonymous".asValue()` | a standalone bound value expression, where SQL needs an expression |
| `BookStatus.DRAFT.asValue()` | a transformed standalone value (generated conversion) |
| `"Anonymous".asLiteral()` | an escaped inline SQL literal instead of `?` |
| `NULL` | an inline SQL NULL |

- Pass host values directly to comparisons, `IN`, `BETWEEN`, `LIKE`, `SET`, `NULLIF`'s second operand and simple-CASE
  `WHEN`s. Use `asValue()` for the other positions: `IFNULL`/`COALESCE` alternatives, `THEN`/`ELSE` results, a
  projected constant, the left operand of arithmetic, `CONCAT` with a non-String.
- Values bind by storage class: `Long`/`Int`/`Short`/`Boolean` as INTEGER, `Double`/`Float` as REAL, `String` as TEXT,
  `ByteArray` as BLOB. `"10"` is not `10`: pass the intended type.
- Prefer `asValue()` over `asLiteral()`: literals change the SQL text per value, which defeats statement caching.
  Literals are for constants that never vary; inside view definitions every value is rendered as a literal anyway.
- `value.asValue(SqlType.X)` binds through an explicit descriptor, for host types with several possible encodings.

## Predicates

| SQL | DSL (infix / dot) |
| --- | --- |
| `a = b`, `a != b` | `a IS b`, `a IS_NOT b` / `a.IS(b)` |
| `<`, `<=`, `>`, `>=` | `LESS_THAN`, `LESS_OR_EQUAL`, `GREATER_THAN`, `GREATER_OR_EQUAL` |
| `IN (…)`, `NOT IN` | `a IN listOf(…)`, `a IN arrayOf(…)`, `a.IN(1L, 2L)`, `a IN query`; `NOT_IN` |
| `BETWEEN x AND y` | `a BETWEEN x AND y`, `a.BETWEEN(x).AND(y)`; `NOT_BETWEEN` |
| `LIKE`, `GLOB` | `a LIKE "K%"`, `a LIKE "50!%%" ESCAPE "!"`; `NOT_LIKE`, `GLOB`, `NOT_GLOB` |
| `IS NULL`, `IS NOT NULL` | `a.IS_NULL()`, `a.IS_NOT_NULL()` |
| `AND`, `OR`, `NOT` | `p AND q`, `p OR q`, `p AND_NOT q`, `p OR_NOT q`, `!p` |
| `EXISTS (…)` | `EXISTS(query)`, `NOT_EXISTS(query)` |

- `IS` means SQL `=`: comparing with NULL yields NULL, never true. Test NULL with `IS_NULL()`; host values passed to
  `IS` must be non-null.
- A `Boolean` field is itself a predicate: `WHERE (BOOK_FLAGS.ARCHIVED)`, `WHERE (!BOOK_FLAGS.ARCHIVED)`.
- Ordered comparisons, `BETWEEN` and `LIKE` work on any type; transformed values compare by their stored form.
- `BETWEEN` returns an unfinished range: parenthesize it as the right operand of a Boolean operator,
  `p AND (BOOK.PAGES BETWEEN 100 AND 300)`.
- `IN` binds one variable per element. Very large lists hit SQLite's variable limit (999 before SQLite 3.32): select
  the IDs with a subquery (`IN query`) or chunk the list.
- `LIKE` is case-insensitive for ASCII only (and for all letters with ICU on the framework driver); `GLOB` is
  case-sensitive. A pattern with a leading wildcard (`"%term%"`) scans the whole table; a prefix pattern (`"term%"`)
  can use an index on the column.
- Kotlin's infix calls all have the same precedence and associate left: parenthesize every comparison inside `AND`/
  `OR`, and every `AND` inside `OR`. The generated SQL follows the Kotlin grouping exactly.

## NULL

- Nullable columns, outer-joined fields (`.nullable()`), `asScalar()`, `/`, `%`, `MIN`, `MAX`, `AVG`, `SUM`, `TOTAL`,
  `GROUP_CONCAT`, `NULLIF` and `CASE` without `ELSE` can be NULL, and their `T` is nullable.
- `x.nullable()` lifts any expression to nullable without changing its SQL; needed for `NOT NULL` fields read from
  the NULL-extended side of an outer join, and to make compound arms agree.
- Turn NULL into a value in SQL with `IFNULL(x, fallback.asValue())` or `COALESCE(a, b, …)` when the query result
  should be non-null, or in Kotlin with `?:` when only the display needs it.
- A required (`NOT NULL`) value that SQLite nevertheless returns as NULL, for example from raw SQL read as non-null,
  throws `SQLException` when the row is read; it never silently becomes `0` or `""`.

## Arithmetic

`+`, `-`, `*`, `/`, `%` and unary `-` on numeric expressions, with SQLite's result:

```kotlin
val nextPage = BOOK.PAGES + 1                     // Number: Long for INTEGER results, Double for REAL
val half = BOOK.PAGES / 2                         // Number?: a zero divisor gives NULL
val exactHalf = CAST(BOOK.PAGES AS REAL) / 2.0    // Number?: REAL division
```

- Results decode as `Number` per value (`Long` for INTEGER, `Double` for REAL); `/` and `%` are nullable. Integer
  division stays integer, as in SQLite: `BOOK.PAGES / 2` reads `18` for 37. Cast to REAL first for a fractional
  result, as `exactHalf` does.
- A field takes right-hand plain numbers of its own type (`BOOK.PAGES + 1`, not `+ 1.5`); use `asValue()` for another
  type (`BOOK.PAGES + 1.5.asValue()`) and for a number on the left (`100.asValue() - BOOK.PAGES`).
- Read a fixed type by converting in Kotlin (`toLong()`), or with `readAs(SqlType.LONG)` on a one-value query.
- `SET (BOOK.PAGES to BOOK.PAGES + 1)` accepts any numeric expression for a numeric column, a non-null one for a
  `NOT NULL` column: wrap a nullable one (`/`, `%`) in `IFNULL`.

## Aggregates

| Function | Result |
| --- | --- |
| `COUNT()`, `COUNT(x)`, `COUNT(DISTINCT(x))` | `Long`, never NULL (`COUNT(x)` skips NULLs) |
| `SUM(x)` | `Number?`: NULL for no rows, exact integers, throws on integer overflow |
| `TOTAL(x)` | `Double?`: `0.0` for no rows, never overflows |
| `AVG(x)` | `Double?` |
| `MIN(x)`, `MAX(x)` | `x`'s type, nullable |
| `GROUP_CONCAT(x)`, `GROUP_CONCAT(x, ", ")`, `GROUP_CONCAT(DISTINCT(x))` | `String?` |

```kotlin
val stats = SELECT
  .COLUMNS(BOOK.AUTHOR, COUNT(), IFNULL(MAX(BOOK.PAGES), 0.asValue()))
  .FROM(BOOK)
  .GROUP_BY(BOOK.AUTHOR)

val longCount = COUNT() FILTER_WHERE (BOOK.PAGES GREATER_THAN 500)
```

- Aggregates go in `COLUMNS`, `HAVING` and `ORDER_BY`; without `GROUP_BY` they reduce the whole result to one row.
  SQLite reports misuse elsewhere.
- Use `SUM` when exact integer totals matter and handle `null`; use `TOTAL` for a never-null floating sum.
- `FILTER_WHERE (predicate)` restricts one aggregate's rows (SQLite 3.30: bundled driver or API 31+); a `CASE` inside
  the aggregate works on every version.
- Aggregate results compare with host values: `HAVING (COUNT() GREATER_THAN 3L)`.
- `DISTINCT(x)` is only an aggregate argument; a distinct projection is `SELECT.DISTINCT(…)`.

## Window functions

SQLite 3.25 (bundled driver or API 30+):

```kotlin
val byAuthor = PARTITION_BY(BOOK.AUTHOR).ORDER_BY(BOOK.PAGES.DESC())
val rank = ROW_NUMBER() OVER byAuthor
val longestFirst = SELECT
  .COLUMNS(BOOK.TITLE, rank AS "rank")
  .FROM(BOOK)

val runningPages = SUM(BOOK.PAGES) OVER ORDER_BY(BOOK.ID.ASC()).ROWS_BETWEEN(UNBOUNDED_PRECEDING).AND(CURRENT_ROW)
```

- `ROW_NUMBER()`, `RANK()`, `DENSE_RANK()` (required `Long`) and any aggregate take `OVER(spec)` or `OVER()`.
- Specs start with `PARTITION_BY(…)`, `ORDER_BY(…)` (the top-level function, imported) or `ROWS_BETWEEN(start)`;
  frame bounds are `UNBOUNDED_PRECEDING`, `n.PRECEDING`, `CURRENT_ROW`, `n.FOLLOWING`, `UNBOUNDED_FOLLOWING`.
- Window values cannot appear in `WHERE`: filter on them from a derived source or CTE that selects them.

## Conditionals

```kotlin
val displayName = IFNULL(AUTHOR.NICKNAME, AUTHOR.NAME)
val label = COALESCE(AUTHOR.NICKNAME, AUTHOR.NAME, "Unknown".asValue())
val noEmpty = NULLIF(AUTHOR.NICKNAME, "")

val size = CASE
  .WHEN(BOOK.PAGES GREATER_THAN 500)
  .THEN("long".asValue())
  .WHEN(BOOK.PAGES GREATER_THAN 150)
  .THEN("medium".asValue())
  .ELSE("short".asValue())
  .END()

val statusLabel = CASE(BOOK.STATUS)
  .WHEN(BookStatus.PUBLISHED)
  .THEN("Published".asValue())
  .ELSE("Draft".asValue())
  .END()
```

- `IFNULL` and `COALESCE` are non-null when an alternative is non-null (for `COALESCE`, the first non-null within the
  first nine); `COALESCE` takes any number of alternatives.
- Alternatives and `THEN`/`ELSE` values are expressions (`asValue()`); `NULLIF`'s second operand and simple-CASE
  `WHEN` matches are host values encoded like comparisons.
- All alternatives must decode the same way (or all be native numbers); mixing a `String` with a `Long` is rejected.
- `CASE` without `ELSE` is nullable; `END()` completes it. `THEN` and `ELSE` are top-level extensions, imported in dot
  and infix form alike (`(CASE WHEN (p) THEN a ELSE b).END()`).

## Text, CAST and collation

| Function | Example |
| --- | --- |
| `LOWER`, `UPPER`, `LENGTH`, `ABS` | `LOWER(AUTHOR.NAME)` |
| `SUBSTRING(x, start, length)` | `SUBSTRING(BOOK.TITLE, 1, 20)` (1-based, renders `substr`) |
| `TRIM(x)`, `TRIM(x, chars)` | `TRIM(AUTHOR.NAME)` |
| `REPLACE(x, from, to)` | `REPLACE(BOOK.TITLE, "-", " ")` |
| `FORMAT(pattern, args…)` | `FORMAT("%s (%d)", BOOK.TITLE, BOOK.PAGES)` (renders `printf`) |
| `CONCAT` (`\|\|`) | `AUTHOR.NAME CONCAT " – " CONCAT BOOK.TITLE` |
| `CAST(x AS type)` | `CAST(BOOK.ID AS TEXT)`; types `INTEGER`, `REAL`, `TEXT`, `BLOB`, `NUMERIC` |
| `COLLATE` | `AUTHOR.NAME COLLATE NOCASE`; `BINARY`, `RTRIM`, `collation("LOCALIZED")` |
| alias | `expression AS "name"` |

- Text helpers are top-level calls as in SQL; there are no receiver forms (`AUTHOR.NAME.lower()` does not exist).
- `LOWER`/`UPPER`/`NOCASE` fold ASCII only, except `LOWER`/`UPPER`/`LIKE` with ICU on the framework driver. For
  locale-aware sorting on the framework driver use `collation("LOCALIZED")`; otherwise sort in Kotlin with a
  `Collator`.
- Comparing `LOWER(x) IS LOWER(y)` cannot use an index; for frequent case-insensitive lookups store a normalized
  column and index it.

## Row values

`row(a, b, …)` compares tuples in one predicate (SQLite 3.15: bundled driver or API 26+):
`row(BOOK.TITLE, BOOK.ID) GREATER_THAN row(lastTitle, lastId)`. Elements are expressions or native host values;
custom types go through `asValue()`.

## Raw fragments and functions

When the DSL lacks a construct, write a raw fragment with its result type and bound arguments:

```kotlin
val recent = "${BOOK.ID} > (SELECT max(id) - ? FROM book)".expr(SqlType.BOOLEAN, 100L)
  .observes(BOOK)

val ROUND = scalarFunction("round").readAs(SqlType.DOUBLE.nullable)
val averageRounded = ROUND(AVG(BOOK.PAGES), 1)

val INSTR = scalarFunction("instr").readAs(SqlType.LONG.nullable)
val hasColon = INSTR(BOOK.TITLE, ":") GREATER_THAN 0L
```

- `"sql".expr(SqlType.X, args…)`: the text is used as an operand, parenthesized; `?` placeholders bind the arguments
  in order (native values: `10L`, not `"10"`). `SqlType` gives the result: `BOOLEAN` for predicates, `LONG`, `INT`,
  `STRING`, `DOUBLE`, `BYTE_ARRAY`, `NUMBER` (read-only, `Long` or `Double` per value), generated
  `SqlType.<TRANSFORMED>`, and `.nullable` of each.
- Interpolating a generated field renders its quoted, qualified name (`"${BOOK.TITLE}"` → `"book"."title"`, or the
  alias of an aliased occurrence). Never interpolate values.
- A raw fragment is assumed to read only the query's own tables. Declare others with `.observes(TABLE, …)` so observed
  queries re-run when they change.
- `scalarFunction(name).readAs(type)` declares an SQLite built-in the DSL does not wrap (`round`, `instr`, `date`,
  `strftime`, `json_extract`, `random`, …) or a function the driver registers; call it like a function with
  expressions or host values. Declare it once as a top-level or object property. `.observes(…)` declares tables the
  function reads.
- Prefer a typed expression whenever one exists: raw text is not checked, and SQLite reports its mistakes only when
  the statement is prepared.
