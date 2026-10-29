# Legacy → new queries

Imports, construction order, SELECT shapes, joins, subqueries and compounds. Expressions inside queries are in
[expressions.md](expressions.md); raw SQL, UPDATE/DELETE, views and declared types are in
[statements-and-types.md](statements-and-types.md).

Examples assume generated `AUTHOR` and `BOOK` tables with `Author` and `Book` models; names are illustrative.
"Legacy" covers both of the old DSL's forms: Kotlin infix (`SELECT FROM AUTHOR WHERE (…)`) and the Java-style
fluent builders (`Select.from(AUTHOR).where(…)`). "New" shows the infix form and the dot-call form where they
differ. Keep whichever style the call site already uses.

## Contents

- [Imports](#imports)
- [Construction order](#construction-order)
- [Entity queries](#entity-queries)
- [Column selections, rows and partial entities](#column-selections-rows-and-partial-entities)
- [Ordering, grouping and pagination](#ordering-grouping-and-pagination)
- [Joins and aliases](#joins-and-aliases)
- [Subqueries, derived sources and compounds](#subqueries-derived-sources-and-compounds)

## Imports

User code still imports from `com.siimkinks.sqlitemagic`.

- Operations of the one type they start or modify are members now, and other legacy top-level names are gone. Delete
  these imports when they no longer resolve ("Unresolved reference"): `FROM`, `WHERE`, `GROUP_BY`, `HAVING`, `LIMIT`,
  `OFFSET`, `ON`, `USING`, `AS`, `COLUMN`, `COLUMNS`, `RAW`, `TABLE`, `LIKE`, `NOT_LIKE`, `GLOB`, `NOT_GLOB`, the join
  words (`INNER_JOIN`, `LEFT_JOIN`, `CROSS_JOIN`, `NATURAL_…`, `JOIN`, `LEFT_OUTER_JOIN`), `WITH_ARGS`,
  `WITH_CONFLICT_ALGORITHM`, `asColumn`, `asRawColumn`, `concat` and `with`.
- `ORDER_BY`, `SET`, `DISTINCT` and the comparison words (`IS`, `IS_NOT`, `IN`, `NOT_IN`, `BETWEEN`, `NOT_BETWEEN`,
  `GREATER_THAN`, `GREATER_OR_EQUAL`, `LESS_THAN`, `LESS_OR_EQUAL`) still resolve. Same-type comparisons are members
  and need no import; the imported forms serve numeric expressions of different types (`AUTHOR.ID IS AUTHOR.AGE`, in
  fluent form too), a `SET` with a numeric expression of another type, window specs and aggregate `DISTINCT(x)`.
  Remove the imports the compiler or IDE reports as unused.
- Still imported: Boolean operators `AND`, `OR`, `AND_NOT`, `OR_NOT` and `not` (`!p`); the arithmetic operators
  `plus`, `minus`, `times`, `div`, `rem` and `unaryMinus`, which were legacy members; compounds `UNION`, `UNION_ALL`,
  `INTERSECT`, `EXCEPT`; `CONCAT`; top-level SQL functions (`COUNT`, `SUM`, `TOTAL`, `AVG`, `MIN`, `MAX`, `GROUP_CONCAT`,
  `LOWER`, `UPPER`, `LENGTH`, `ABS`, `SUBSTRING`, `TRIM`, `REPLACE`, `FORMAT`, `IFNULL`, `COALESCE`, `NULLIF`,
  `CAST`, `EXISTS`, `NOT_EXISTS`); and the extensions `queryDeep`, `mapTo`, `readAs`, `asScalar`, `asValue`,
  `asLiteral` and `expr`; every `observe()` comes from `com.siimkinks.sqlitemagic.rxjava2`.
- Legacy `Select.Companion.count`, `Select.Companion.asColumn` and similar companion imports go away with the calls.

The sample app's view ([Views](statements-and-types.md#views)) changed its imports like this; `IS` and `ORDER_BY`
became unused because the view now calls only their member forms:

```diff
 import com.siimkinks.sqlitemagic.AND
-import com.siimkinks.sqlitemagic.AS
-import com.siimkinks.sqlitemagic.COLUMN
-import com.siimkinks.sqlitemagic.COLUMNS
-import com.siimkinks.sqlitemagic.FROM
-import com.siimkinks.sqlitemagic.IS
-import com.siimkinks.sqlitemagic.ORDER_BY
+import com.siimkinks.sqlitemagic.COUNT
 import com.siimkinks.sqlitemagic.SELECT
-import com.siimkinks.sqlitemagic.Select.Companion.asColumn
-import com.siimkinks.sqlitemagic.Select.Companion.count
-import com.siimkinks.sqlitemagic.WHERE
+import com.siimkinks.sqlitemagic.asScalar
```

## Construction order

```kotlin
// Legacy: builder calls mutated one shared builder
val query = Select.from(BOOK).queryDeep()
if (connection != null) query.usingConnection(connection)
query.where(BOOK.TITLE.`is`("Dune")).limit(10).execute()

// New: immutable values; clauses in SQL order, then final configuration, then a terminal
var query = SELECT.FROM(BOOK)
    .WHERE(BOOK.TITLE IS "Dune")
    .LIMIT(10)
    .queryDeep()
if (connection != null) query = query.usingConnection(connection)
query.execute()
```

- Clause order: `SELECT` [`DISTINCT`] selection `FROM` source, joins (each with its optional `ON`/`USING`), `WHERE`,
  `GROUP_BY`, `HAVING`, `ORDER_BY`, `LIMIT`, `OFFSET`. Each stage offers only what SQL allows next.
- Final configuration after the last clause: `queryDeep()`, `readAs(…)`, `mapTo(…)`, `usingConnection(…)`. No
  clause follows them. Put `mapTo` after `readAs`: a later `readAs` replaces the whole reader, dropping an earlier
  mapping. `usingConnection` goes before or after the others.
- Terminals: `execute()`, `takeFirst()`, `count()`, `toCursor()`, `compile()`, and `observe()` from the
  `sqlitemagic-rxjava2` adapter.
- `LIMIT`/`OFFSET` take `Int` or `Long`.

## Entity queries

```kotlin
// Legacy
Select.from(AUTHOR).where(AUTHOR.NAME.`is`("Ada")).execute()
(SELECT FROM AUTHOR WHERE (AUTHOR.NAME IS "Ada")).execute()
Select.from(BOOK).where(…).orderBy(…).limit(10).queryDeep().execute()
Select.distinct().from(AUTHOR)
Select.all().from(AUTHOR)                                             // SELECT *

// New
SELECT.FROM(AUTHOR).WHERE(AUTHOR.NAME.IS("Ada")).execute()
(SELECT FROM AUTHOR WHERE (AUTHOR.NAME IS "Ada")).execute()          // unchanged
SELECT.FROM(BOOK).WHERE(…).ORDER_BY(…).LIMIT(10).queryDeep().execute()
SELECT.DISTINCT.FROM(AUTHOR)                                          // infix: SELECT.DISTINCT FROM AUTHOR
SELECT.FROM(AUTHOR)                                                   // the entity query names its columns
```

- `SELECT.FROM(table)` is always the entity query (`List<Author>`), for aliases and generic `Table<T>` helpers too.
- `queryDeep()` moves after the last clause, needs `import com.siimkinks.sqlitemagic.queryDeep`, and exists only on
  entity queries; `SELECT.COLUMN(…).FROM(…).queryDeep()` no longer compiles.
- The entity `DISTINCT` needs the dot: `SELECT DISTINCT FROM AUTHOR` does not compile.

## Column selections, rows and partial entities

```kotlin
// Legacy
Select.column(AUTHOR.NAME).from(AUTHOR)                         // List<String…>
SELECT COLUMN AUTHOR.NAME FROM AUTHOR
Select.columns(AUTHOR.ID, AUTHOR.NAME).from(AUTHOR)             // List<Author>, partially filled
SELECT COLUMNS arrayOf(AUTHOR.ID, AUTHOR.NAME) FROM AUTHOR      // List<Author>
Select.columns(BOOK.all(), AUTHOR.all()).from(BOOK)             // List<Book>
SELECT DISTINCT arrayOf(AUTHOR.ID, AUTHOR.NAME) FROM AUTHOR

// New
SELECT.COLUMN(AUTHOR.NAME).FROM(AUTHOR)
SELECT COLUMN AUTHOR.NAME FROM AUTHOR                           // unchanged
SELECT.COLUMNS(AUTHOR.ID, AUTHOR.NAME).FROM(AUTHOR)             // List<Row2<Long, String>>
SELECT.COLUMNS(AUTHOR.ID, AUTHOR.NAME).FROM(AUTHOR).mapTo(AUTHOR)              // List<Author>
SELECT.COLUMNS(BOOK.all(), AUTHOR.all()).FROM(BOOK).mapTo(BOOK)                // List<Book>
SELECT.DISTINCT(AUTHOR.ID, AUTHOR.NAME).FROM(AUTHOR).mapTo(AUTHOR)
```

- `COLUMNS` and `DISTINCT` take the values as arguments, never an array, and need the dot. Two to nine plain
  expressions read `Row2`…`Row9` (properties `first`…`ninth`, destructurable). A single value, a `table.all()` list
  among them, or more than nine values read `SelectedRow`, read with `row[selectedExpression]`; select one value with
  `COLUMN`.
- Mapping decides the result, after every clause: `.mapTo(AUTHOR)` for a partial generated model, `.mapTo(::Dto)` for
  a constructor, `.mapTo { row -> … }` for a wide row. A legacy partial-entity selection always needs `.mapTo(TABLE)`.
- A partial entity reads plain fields of the mapped table or of tables its relationships reach. A computed value
  rejects `mapTo(TABLE)`; read it from a wide row:

```kotlin
// Legacy: a computed prefix before the view's columns
Select.columns(Select.asColumn(99) AS "prefix", VIEW.all()).from(VIEW)
// New
val prefix = 99L.asValue() AS "prefix"
SELECT.COLUMNS(prefix, VIEW.all()).FROM(VIEW).mapTo { row -> Prefixed(row[prefix], …) }
```

- A view's `all()` holds its scalar columns only. A view with a required table- or view-typed property cannot be
  mapped partially; read it whole with `SELECT FROM VIEW`.

## Ordering, grouping and pagination

| Legacy | New |
| --- | --- |
| `x.asc()`, `x.desc()` | `x.ASC()`, `x.DESC()` (members, no import) |
| `.orderBy(a.asc(), b.desc())` | `.ORDER_BY(a.ASC(), b.DESC())` |
| `ORDER_BY arrayOf(a.asc(), b.asc())` | `ORDER_BY arrayOf(a.ASC(), b.ASC())` |
| `.groupBy(a, b)`, `GROUP_BY arrayOf(a, b)` | `.GROUP_BY(a, b)`, `GROUP_BY arrayOf(a, b)` |
| `.having(p)` | `.HAVING(p)`, `HAVING p` |
| `.limit(n).offset(m)` | `.LIMIT(n).OFFSET(m)`, `LIMIT n OFFSET m` |

`takeFirst()` keeps an explicit `LIMIT`/`OFFSET` as written instead of rejecting a limit other than 1.

## Joins and aliases

```kotlin
// Legacy
Select.from(AUTHOR).leftJoin(BOOK.on(AUTHOR.ID.`is`(BOOK.AUTHOR)))
SELECT FROM AUTHOR LEFT_JOIN (BOOK ON (AUTHOR.ID IS BOOK.AUTHOR))
SELECT FROM AUTHOR INNER_JOIN (peer USING arrayOf(peer.ID))

// New: ON/USING follows the joined source
SELECT.FROM(AUTHOR).LEFT_JOIN(BOOK).ON(AUTHOR.ID IS BOOK.AUTHOR)
SELECT FROM AUTHOR LEFT_JOIN BOOK ON (AUTHOR.ID IS BOOK.AUTHOR)
SELECT FROM AUTHOR INNER_JOIN peer USING peer.ID        // several: USING arrayOf(a, b), or .USING(a, b)
```

| Legacy join | New |
| --- | --- |
| `JOIN`, `.join(…)` (a comma join) | `CROSS_JOIN` |
| `LEFT_OUTER_JOIN`, `.leftOuterJoin(…)` | `LEFT_JOIN` |
| `NATURAL_LEFT_OUTER_JOIN`, `.naturalLeftOuterJoin(…)` | `NATURAL_LEFT_JOIN` |
| `NATURAL_INNER_JOIN`, `.naturalInnerJoin(…)` | `NATURAL_JOIN` |
| `.innerJoin`, `.leftJoin`, `.crossJoin`, `.naturalJoin`, `.naturalLeftJoin`, `.naturalCrossJoin` | uppercase dot-calls |

- New: `RIGHT_JOIN`, `FULL_JOIN`, `NATURAL_RIGHT_JOIN`, `NATURAL_FULL_JOIN` (SQLite 3.39+, Android API 34+).
- Aliases: `` AUTHOR.`as`("a") `` and `` x.`as`("n") `` become `AUTHOR AS "a"` (`AUTHOR.AS("a")`) and `x AS "n"`.
- Each `AS` call creates a new occurrence, identified by instance rather than by name; legacy compared tables by alias
  name, so re-creating `AUTHOR.as("a")` used to work. Alias once into a value and use it for the source and its fields:
  `val b = BOOK AS "b"`, then `SELECT FROM b WHERE (b.TITLE IS "Dune")`. A field of another instance is rejected
  before SQL even when the alias text matches.
- Parenthesize an inline alias before a later infix call, `SELECT FROM (BOOK AS "b")`: without the parentheses
  `AS "b"` aliases the query `SELECT FROM BOOK` as a derived source.
- `AUTHOR.ID.inTable(alias)` becomes the alias's own field: `val peer = AUTHOR AS "peer"`, then `peer.ID`.
- A NOT NULL field selected from the NULL-extended side of an outer join needs `.nullable()`:
  `SELECT.COLUMN(BOOK.TITLE.nullable()).FROM(AUTHOR).LEFT_JOIN(BOOK).ON(…)` reads `String?`. Without it the query
  is rejected before SQL runs ([behavior](behavior.md#outer-joined-values-must-be-lifted)).
- A selected field of an unjoined table joins automatically only when exactly one relationship path reaches it;
  otherwise join the intended path explicitly through an alias value: `val editor = AUTHOR AS "editor"`, then
  `LEFT_JOIN editor ON (BOOK.EDITOR IS editor.ID)`.

## Subqueries, derived sources and compounds

| Legacy | New |
| --- | --- |
| `(SELECT COLUMN count() FROM T WHERE (…)).toColumn("n")` | `(SELECT COLUMN COUNT() FROM T WHERE (…)).asScalar() AS "n"` |
| `x IS (Select.column(Y.V) FROM Y)` | `x IS (SELECT COLUMN Y.V FROM Y).asScalar()` |
| `SET (X.V to (Select.column(Y.V) FROM Y))`, `set(X.V, select)` | `SET (X.V to (SELECT COLUMN Y.V FROM Y).asScalar())` |
| `query.toTable("b")`, then `SELECT FROM b` | `val b = query AS "b"` (or `query` unaliased), then `SELECT FROM b` |
| a derived source's column by its table field | `b[selected]`, with the exact expression instance the inner query selected |
| — | `EXISTS(query)`, `NOT_EXISTS(query)` (prefix, required `Boolean`) |
| `a.union(b)`, `unionAll`, `intersect`, `except` | `a UNION b`, `a.UNION(b)`; `UNION_ALL`, `INTERSECT`, `EXCEPT` |
| `compound.orderBy(…)` | `compound.ORDER_BY(…)` |

- `asScalar()` is the explicit conversion for a one-column query used as a value: comparisons, projections, SET values,
  function arguments. `IN query` consumes all rows and needs no conversion.
- `asScalar()` is SQL-nullable (zero rows give NULL), so a NOT NULL `SET` target needs a fallback:
  `SET (X.V to IFNULL(query.asScalar(), 0L.asValue()))`.
- Capture a derived source's selected expression once and reuse that instance for `b[…]`:

```kotlin
val nextAge = (AUTHOR.AGE + 1) AS "next_age"
val next = SELECT.COLUMNS(AUTHOR.ID, nextAge).FROM(AUTHOR) AS "next"
SELECT.COLUMN(next[AUTHOR.ID]).FROM(next).WHERE(next[nextAge] IS 36)
```

- A compound's right arm must have the left arm's result type or a subtype; map after compounding,
  `(ids UNION ids).mapTo(::AuthorId)`. Put an arm that may hold NULL first, or lift its columns with `.nullable()`.
- A nested query cannot choose a connection; call `usingConnection` on the outermost query only.
- Correlation follows SQL: a nested query's field of a table it does not itself use refers to the enclosing query.
  Alias the inner occurrence to read another row ([behavior](behavior.md#nested-queries-resolve-names-as-sql-does)).
