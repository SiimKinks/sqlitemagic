# Legacy → new expressions

Values, raw SQL fragments, predicates, aggregates, text helpers and arithmetic. Query shapes are in
[queries.md](queries.md).

Examples assume generated `AUTHOR` and `BOOK` tables with `Author` and `Book` models; names are illustrative.
"Legacy" covers both of the old DSL's forms: Kotlin infix (`SELECT FROM AUTHOR WHERE (…)`) and the Java-style
fluent builders (`Select.from(AUTHOR).where(…)`). "New" shows the infix form and the dot-call form where they
differ. Keep whichever style the call site already uses.

## Contents

- [Values and raw SQL fragments](#values-and-raw-sql-fragments)
- [Predicates](#predicates)
- [Aggregates, text helpers and arithmetic](#aggregates-text-helpers-and-arithmetic)

## Values and raw SQL fragments

| Legacy | New |
| --- | --- |
| `42.asColumn`, `Select.asColumn(42)`, `"x".asColumn` as a standalone value | `42.asValue()` (binds `?`) or `42.asLiteral()` (inlines) |
| `X IS asColumn(false)`, `X IS Select.asColumn(v)` | `X IS false`, `X IS v`: comparisons take host values directly |
| `Select.asColumn(202).`is`(202)` | `202.asValue() IS 202` |
| `5.asColumn - 3` | `5.asValue() - 3` |
| `Select.asColumn(value.externalToken)` | `value.externalToken.asValue()` (generated conversion) or `.asValue(SqlType.EXTERNAL_TOKEN)` |
| `Select.asRawColumn("printf('%s', …)")` | `"printf('%s', …)".expr(SqlType.STRING)` |
| `"author.age > 17".expr`, `Expr.raw("…")` | `"author.age > 17".expr(SqlType.BOOLEAN)` |
| `"author.name = ?".expr("Ada")`, `Expr.raw(sql, "10")` | `"author.name = ?".expr(SqlType.BOOLEAN, "Ada")`, `sql.expr(SqlType.BOOLEAN, 10L)` |

- Raw arguments are native values bound by storage class: pass `10L`, `true`, `1.5`, not their strings.
- `SqlType` names the result: `BOOLEAN` for predicates, `LONG`, `STRING`, `DOUBLE`, `BYTE_ARRAY`, `NUMBER`
  (read-only, Long or Double per value), generated transformer types, and `.nullable` forms.
- A generated field interpolated into raw text renders its quoted qualified name: `"${AUTHOR.NAME} LIKE ?"` becomes
  `"author"."name" LIKE ?`; an aliased occurrence renders its alias.
- A raw fragment observes the query's own tables by default; `.observes(BOOK)` declares tables its text reads
  beyond them (at least one table). Typed expressions have no `observes`.
- Result alternatives (`IFNULL`/`COALESCE` alternatives, `CASE` `THEN`/`ELSE`) take expressions:
  `IFNULL(AUTHOR.NAME, "Unknown".asValue())`.
- `NULL` is an inline SQL NULL. `Float`/`Double` NaN is rejected when encoded.

## Predicates

| Legacy (fluent) | Legacy (infix) | New |
| --- | --- | --- |
| `` x.`is`(v) ``, `x.isNot(v)` | `x IS v`, `x IS_NOT v` | `x IS v`, `x.IS(v)`; `IS_NOT` |
| `x.greaterThan(v)`, `greaterOrEqual`, `lessThan`, `lessOrEqual` | `GREATER_THAN`, … | `x GREATER_THAN v`, `x.GREATER_THAN(v)`, … |
| `` x.`in`(list) ``, `x.notIn(1, 2)` | `x IN list` | `x IN list`, `x IN arrayOf(…)`, `x.IN(1L, 2L)`; `NOT_IN` |
| `x.like(p)`, `notLike`, `glob`, `notGlob` | `x LIKE p` | `x LIKE p`, `x.LIKE(p)`; `NOT_LIKE`, `GLOB`, `NOT_GLOB` |
| `x.between(a).and(b)` | `x BETWEEN (a AND b)` | `x BETWEEN a AND b`, `x.BETWEEN(a).AND(b)` |
| `x.notBetween(a).and(b)` | `x NOT_BETWEEN (a AND b)` | `x NOT_BETWEEN a AND b` |
| `x.isNull()`, `x.isNotNull()` | — | `x.IS_NULL()`, `x.IS_NOT_NULL()` |
| `p.and(q)`, `or`, `andNot`, `orNot`, `p.not()` | `p AND q`, … | `p AND q`, `p.AND(q)`; `OR`, `AND_NOT`, `OR_NOT`, `!p` |

- An operand must have the receiver's decoded type, and a host value the field's input type (`intValue.toLong()` for
  a `Long` ID), as in legacy. Legacy matched numeric fields through a shared `Number` type, so the member
  `AUTHOR.ID.is(AUTHOR.AGE)` (Long with Int) compiled without an import. Comparing numeric expressions of different
  types (`AUTHOR.ID IS AUTHOR.AGE`, `AUTHOR.ID.IS(intValue.asValue())`) now needs the imported extension,
  `import com.siimkinks.sqlitemagic.IS` (likewise `GREATER_THAN`, `IN`, `BETWEEN`, …), in fluent form too.
- Legacy `column.toExpr()` made a predicate of a Boolean column; a Boolean field is now itself a predicate,
  `WHERE(TODO_ITEM.COMPLETED)`. Compare other fields explicitly.
- Host values are non-null; compare with NULL through `IS_NULL()`/`IS_NOT_NULL()`.
- Typed `IN` operands are varargs only: `AUTHOR.ID.IN(OTHER.ID, 5L.asValue())`.
- Ordered comparisons and ranges now work on text and custom encodings too; `LIKE … ESCAPE "!"` is available.
- Comparing with a query: `x IS (select)` becomes `x IS select.asScalar()`; `x IN select` keeps its shape.
- `BETWEEN` returns an unfinished range: parenthesize it as the right operand of a Boolean operator,
  `p AND (x BETWEEN 1L AND 2L)`. Ranges no longer need `import com.siimkinks.sqlitemagic.AND`.

## Aggregates, text helpers and arithmetic

| Legacy | New |
| --- | --- |
| `Select.count()`, `Select.count(x)` | `COUNT()`, `COUNT(x)` |
| `Select.countDistinct(x)`, `Select.avgDistinct(x)`, … | `COUNT(DISTINCT(x))`, `AVG(DISTINCT(x))`, … |
| `Select.avg(x)`, `Select.min(x)`, `Select.max(x)` | `AVG(x)`, `MIN(x)`, `MAX(x)` |
| `Select.sumDistinct(x)` (rendered `total(DISTINCT x)`) | `TOTAL(DISTINCT(x))` |
| `Select.groupConcat(x, ", ")` | `GROUP_CONCAT(x, ", ")` |
| `Select.groupConcatDistinct(x)`; with a separator | `GROUP_CONCAT(DISTINCT(x))`; none with a separator, which SQLite rejects |
| `Select.sum(x)` (rendered `total(x)`) | `TOTAL(x)` to keep legacy results, or `SUM(x)`; see [behavior](behavior.md#legacy-sum-was-total) |
| `Select.lower(x)`, `upper`, `length`, `abs` | `LOWER(x)`, `UPPER(x)`, `LENGTH(x)`, `ABS(x)` |
| `Select.format("%s %d", a, b)` | `FORMAT("%s %d", a, b)` |
| `x.substring(2)`, `x.substring(2, 5)` | `SUBSTRING(x, 2)`, `SUBSTRING(x, 2, 5)` |
| `x.trim()`, `x.trim("ab")` | `TRIM(x)`, `TRIM(x, "ab")` |
| `x.replace("a" with "b")` | `REPLACE(x, "a", "b")` |
| `concat(a, b, c)`, `a concat b` | `a CONCAT b CONCAT c` (renders `||`) |
| `x.toNotNullable()` | removed: `IFNULL(x, fallback.asValue())`/`COALESCE(…)`, or handle `null` in Kotlin |

- `MIN`, `MAX`, `AVG` and `GROUP_CONCAT` results are nullable now, whatever the input column
  ([behavior](behavior.md#aggregates-read-null-for-empty-input)).
- New helpers with no legacy form: `IFNULL`, `COALESCE`, `NULLIF`, `CASE`, `CAST(x AS TEXT)`, `COLLATE`,
  `row(…)` comparisons, window functions (`ROW_NUMBER() OVER …`), `FILTER_WHERE`, `scalarFunction(name)`.
- Arithmetic (`+ - * / %`, unary `-`) reads SQLite's result as `Number` (`Long` or `Double` per value) instead of
  `Double`; `/` and `%` results are nullable. Convert with `toLong()`/`toDouble()`, or choose a fixed reader with
  `readAs(SqlType.LONG)`/`readAs(SqlType.DOUBLE)` on a scalar query. A field takes plain numbers of its own type:
  `TABLE.INTEGER * 2.5` is rejected (as before); use `TABLE.INTEGER * 2.5.asValue()`.
