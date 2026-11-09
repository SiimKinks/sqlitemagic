# Queries

SELECT shapes and their result types, clauses, joins, mapping, nesting, CTEs, compounds, terminals and compiled
plans. Expressions used inside queries are in [expressions.md](expressions.md). Examples use the schema in
[SKILL.md](../SKILL.md#example-schema).

## Contents

- [Shapes and results](#shapes-and-results)
- [Writing a query](#writing-a-query)
- [Entity queries](#entity-queries)
- [Columns, rows and mapping](#columns-rows-and-mapping)
- [Filtering, grouping, ordering, pagination](#filtering-grouping-ordering-pagination)
- [Joins and aliases](#joins-and-aliases)
- [Subqueries and derived sources](#subqueries-and-derived-sources)
- [Common table expressions](#common-table-expressions)
- [Compound queries](#compound-queries)
- [Terminals](#terminals)
- [Compiled plans and parameters](#compiled-plans-and-parameters)

## Shapes and results

| Query | `execute()` returns |
| --- | --- |
| `SELECT FROM BOOK` | `List<Book>` |
| `SELECT.DISTINCT FROM BOOK` | `List<Book>`, distinct rows |
| `SELECT COLUMN BOOK.TITLE FROM BOOK` | `List<String>` (the field's type; `List<String?>` for a nullable column) |
| `SELECT DISTINCT BOOK.PAGES FROM BOOK` | `List<Int>`, distinct values |
| `SELECT.COLUMNS(BOOK.ID, BOOK.TITLE).FROM(BOOK)` | `List<Row2<Long, String>>`; two to nine values give `Row2`…`Row9` |
| `SELECT.COLUMNS(BOOK.ID, AUTHOR.all()).FROM(BOOK)…` | `List<SelectedRow>`: any `all()` list or more than nine values |
| `….mapTo(::BookRow)` | `List<BookRow>`, constructor arguments checked against the selected types |
| `….mapTo { row -> … }` | your type, from a wide `SelectedRow` row |
| `SELECT.COLUMNS(AUTHOR.ID, AUTHOR.NAME).FROM(AUTHOR).mapTo(AUTHOR)` | `List<Author>`, partial entities: the unselected `nickname` reads `null` |
| `.takeFirst()` | the row type, nullable: `Book?`, `String?` |
| `.count()` | `Long`: the number of rows the query returns |

`Row2`…`Row9` are data classes with `first`…`ninth` and destructuring: `val (id, title) = row`.

## Writing a query

```kotlin
val longBooks = (
    SELECT
        FROM BOOK
        WHERE ((BOOK.PAGES GREATER_THAN 500) AND (BOOK.STATUS IS BookStatus.PUBLISHED))
        ORDER_BY BOOK.TITLE.ASC()
        LIMIT 20
    )
  .execute()

val sameQuery = SELECT
  .FROM(BOOK)
  .WHERE((BOOK.PAGES GREATER_THAN 500) AND (BOOK.STATUS IS BookStatus.PUBLISHED))
  .ORDER_BY(BOOK.TITLE.ASC())
  .LIMIT(20)
```

- Infix form: wrap the statement in parentheses, one clause per line, and call the terminal on the closing
  parenthesis. Dot form: one call per line. Both build the same immutable query.
- Clause arguments in infix form are parenthesized when they are expressions; a single field or value needs none
  (`LIMIT 20`, `ORDER_BY BOOK.TITLE.ASC()`). Several terms in infix form take an array:
  `ORDER_BY arrayOf(BOOK.STATUS.ASC(), BOOK.TITLE.ASC())`, `GROUP_BY arrayOf(…)`; the dot form takes varargs.
- After the last clause come, in any order, the final configuration calls `queryDeep()`, `mapTo(…)` (after `readAs`),
  `readAs(…)` and `usingConnection(…)`; then the terminal. No clause follows them.
- Every call returns a new query. Assign or chain the result; `query.WHERE(p)` alone changes nothing.

## Entity queries

`SELECT FROM BOOK` reads whole `Book` objects, related objects included:

- A related object whose model has required properties besides its ID is loaded in full through an automatic
  `LEFT JOIN`. One whose other constructor parameters are all nullable or defaulted is built from its stored ID alone
  (a *shallow* read); add `.queryDeep()` to load such objects in full, at every depth, in the same statement.
- `queryDeep()` follows only recursively handled relationships. A `handleRecursively = false` relationship is always
  read as ID-only; join its table and select the fields the code needs.
- Use `queryDeep()` when the code reads the related objects' values; leave it off when it only needs their IDs. Read
  in full a parent that will be written back: a shallow related object holds Kotlin defaults for its other properties,
  and writing the parent writes them ([entity-operations.md](entity-operations.md#related-objects)).
- The automatic joins reuse a join the query writes itself when its `ON` is exactly `fk IS id`, so filtering on the
  related table through such a join costs nothing extra: `SELECT FROM BOOK INNER_JOIN AUTHOR ON (BOOK.AUTHOR IS
  AUTHOR.ID) WHERE (AUTHOR.NAME IS "Ada")`. A join with extra conditions stays as written beside the automatic one;
  put the filter in `WHERE`.
- An entity read of a table that an outer join can NULL-extend (`RIGHT_JOIN`, `FULL_JOIN`) is rejected; select fields.

## Columns, rows and mapping

Select the values the code uses and map them where needed:

```kotlin
data class BookRow(val id: Long, val title: String, val pages: Int)

val rows: List<BookRow> = SELECT
  .COLUMNS(BOOK.ID, BOOK.TITLE, BOOK.PAGES)
  .FROM(BOOK)
  .ORDER_BY(BOOK.TITLE.ASC())
  .mapTo(::BookRow)
  .execute()

val titles: List<String> = (SELECT COLUMN BOOK.TITLE FROM BOOK).execute()
```

- `COLUMN` selects one value; `COLUMNS` two or more, as varargs (never an array). `SELECT.DISTINCT(a, b)` and
  `SELECT DISTINCT a` are the DISTINCT twins.
- `mapTo(::Dto)` maps typed rows of one to nine values to a constructor or function; parameter types must accept the
  selected types (a nullable value needs a nullable parameter). It allocates no intermediate `RowN`.
- `mapTo(BOOK)` builds partial entities from plain selected fields of that table and tables its relationships reach.
  A required property that is not selected is rejected before SQL (`Book.title is required; select "book"."title"`):
  every non-null property, also one with a Kotlin default (generated readers do not use constructor defaults), and a
  required relationship needs its target's fields unless the target builds from its ID alone. Nullable properties not
  selected read `null`. Partial entities are for reading; write them back only with
  `persist().ignoreNullValues()` or prefer a DTO.
- Wide selections (any `table.all()`, or more than nine values) read `SelectedRow`; read a value with
  `row[expression]`, using the exact instance that was selected (a not nullable column of `table.all().nullable()` is
  read with its `.nullable()`, as in `row[BOOK.TITLE.nullable()]`):

  ```kotlin
  data class AuthorWithCount(val author: Author, val bookCount: Long)

  val bookCount = COUNT(BOOK.ID) AS "book_count"
  val authorsWithCounts = SELECT
    .COLUMNS(AUTHOR.all(), bookCount)
    .FROM(AUTHOR)
    .LEFT_JOIN(BOOK)
    .ON(BOOK.AUTHOR IS AUTHOR.ID)
    .GROUP_BY(AUTHOR.ID)
    .mapTo { row ->
      AuthorWithCount(
        author = Author(
          id = row[AUTHOR.ID],
          name = row[AUTHOR.NAME],
          nickname = row[AUTHOR.NICKNAME]
        ),
        bookCount = row[bookCount]
      )
    }
  ```

  The `row` in `mapTo { }` is valid only during the call. Unmapped `SelectedRow` results own their values.
- `readAs(SqlType.LONG)` on a one-value query replaces its reader without changing the SQL, for example to read
  arithmetic (`Number`) as `Long`.

## Filtering, grouping, ordering, pagination

- `WHERE`, `HAVING`: one predicate each; combine with `AND`, `OR`, `AND_NOT`, `OR_NOT`, `!p`
  ([expressions.md](expressions.md#predicates)). Build optional filters as a list and combine them:

  ```kotlin
  fun String.escapeLike() = replace("!", "!!")
    .replace("%", "!%")
    .replace("_", "!_")

  fun search(title: String?, status: BookStatus?): SelectQuery<Book, *> {
    val filters = listOfNotNull(
      title?.let { BOOK.TITLE LIKE "${it.escapeLike()}%" ESCAPE "!" },
      status?.let { BOOK.STATUS IS it }
    )
    return when {
      filters.isEmpty() -> SELECT FROM BOOK
      else -> SELECT FROM BOOK WHERE filters.reduce { all, filter -> all AND filter }
    }
  }
  ```

  Escape `%`, `_` and the escape character in user input that goes into a `LIKE` pattern.
- `GROUP_BY` and `HAVING` with aggregates: `SELECT.COLUMNS(BOOK.AUTHOR, COUNT()).FROM(BOOK).GROUP_BY(BOOK.AUTHOR)
  .HAVING(COUNT() GREATER_THAN 3L)`.
- `ORDER_BY` takes orderings: `x.ASC()`, `x.DESC()`; case-insensitive text with `(AUTHOR.NAME COLLATE NOCASE).ASC()`.
  Order by a unique column last when pages must be stable: `ORDER_BY(BOOK.TITLE.ASC(), BOOK.ID.ASC())`.
- `LIMIT n`, then `OFFSET m` (`Int` or `Long`). OFFSET reads and discards the skipped rows; for deep pages, keyset
  pagination is faster:

  ```kotlin
  fun pageAfter(lastTitle: String, lastId: Long) = SELECT
    .FROM(BOOK)
    .WHERE((BOOK.TITLE GREATER_THAN lastTitle) OR ((BOOK.TITLE IS lastTitle) AND (BOOK.ID GREATER_THAN lastId)))
    .ORDER_BY(BOOK.TITLE.ASC(), BOOK.ID.ASC())
    .LIMIT(50)
  ```

  With SQLite 3.15+ (bundled driver, or API 26+) the predicate can be `row(BOOK.TITLE, BOOK.ID) GREATER_THAN
  row(lastTitle, lastId)`.

## Joins and aliases

`INNER_JOIN`, `LEFT_JOIN`, `CROSS_JOIN`, `NATURAL_JOIN`, `NATURAL_LEFT_JOIN`, `NATURAL_CROSS_JOIN`, plus `RIGHT_JOIN`,
`FULL_JOIN`, `NATURAL_RIGHT_JOIN`, `NATURAL_FULL_JOIN` (SQLite 3.39: bundled driver or API 34+). `ON (predicate)` or
`USING column` follows the joined source; both are optional.

```kotlin
data class BookLine(val title: String, val author: String, val editor: String?)

val editor = AUTHOR AS "editor"
val lines = SELECT
  .COLUMNS(BOOK.TITLE, AUTHOR.NAME, editor.NAME.nullable())
  .FROM(BOOK)
  .INNER_JOIN(AUTHOR)
  .ON(BOOK.AUTHOR IS AUTHOR.ID)
  .LEFT_JOIN(editor)
  .ON(BOOK.EDITOR IS editor.ID)
  .mapTo(::BookLine)
```

- **Aliases.** `AUTHOR AS "editor"` creates a new occurrence with its own fields (`editor.NAME`). Create it once in a
  `val` and use that value for the source and its fields; a second `AUTHOR AS "editor"` is a different occurrence, and
  mixing them is rejected. Parenthesize an inline alias in infix form: `SELECT FROM (BOOK AS "b")`.
- **Outer joins and NULL.** A field from the NULL-extended side of an outer join (the joined table of a `LEFT_JOIN`)
  that is `NOT NULL` in the schema must be selected lifted, `editor.NAME.nullable()`, which reads `String?`, and
  `editor.all().nullable()` lifts all columns of the table. Without the lift the query is rejected before SQL, naming
  the field. Filters such as `WHERE (BOOK.ID.IS_NULL())` need no lift, and neither does `count()`.
- **Automatic joins.** A field of a table the query does not join (`AUTHOR.NAME` in a query over `BOOK`) joins it
  automatically with a `LEFT JOIN` when exactly one relationship path leads there. With several (here: `author` and
  `editor`), the query is rejected before SQL, naming the paths; join the one you mean explicitly, as above. With no
  path, it is rejected because the field's table is not a source of the query; join it explicitly. Fields reached
  through a nullable relationship need `.nullable()` like any outer-joined field. Prefer an explicit `INNER_JOIN` when
  the related row always exists: it states the intent and lets the field stay non-null.
- **USING and NATURAL.** `SELECT FROM AUTHOR INNER_JOIN peer USING peer.ID` joins on columns of the same name
  (`USING arrayOf(a, b)` or `.USING(a, b)` for several). NATURAL joins take neither.
- Every typed field renders qualified, so selected join keys are unambiguous.

## Subqueries and derived sources

```kotlin
val prolificIds = SELECT
  .COLUMN(BOOK.AUTHOR)
  .FROM(BOOK)
  .GROUP_BY(BOOK.AUTHOR)
  .HAVING(COUNT() GREATER_THAN 10L)

val prolific = SELECT FROM AUTHOR WHERE (AUTHOR.ID IN prolificIds)

val withBooks = SELECT FROM AUTHOR WHERE EXISTS(SELECT COLUMN BOOK.ID FROM BOOK WHERE (BOOK.AUTHOR IS AUTHOR.ID))

val latestTitle = (SELECT COLUMN BOOK.TITLE FROM BOOK WHERE (BOOK.AUTHOR IS AUTHOR.ID) ORDER_BY BOOK.ID.DESC())
  .asScalar() AS "latest_title"
val authorsWithLatest = SELECT.COLUMNS(AUTHOR.NAME, latestTitle).FROM(AUTHOR)
```

- `x IN query` / `NOT_IN` take a one-column query whose type matches `x`.
- `EXISTS(query)`/`NOT_EXISTS(query)` are prefix calls returning a non-null predicate; the selection does not matter.
- `query.asScalar()` turns a one-column query into a value for projections, comparisons, `SET` values and function
  arguments. It is nullable (no rows give NULL; several rows take the first): give it a fallback with `IFNULL` where a
  non-null value is needed.
- Correlation follows SQL: a field of a table that the nested query does not use itself refers to the enclosing
  query's row (`AUTHOR.ID` above). To read another row of a table the outer query also uses, alias the inner one.
- A query used as a source is a derived table: `val totals = query AS "totals"` (the alias is optional for one use).
  Read its outputs with `totals[selectedExpression]`, passing the instance the inner query selected (a not nullable
  column of `table.all().nullable()` is passed with its `.nullable()`, as in `totals[BOOK.TITLE.nullable()]`):

  ```kotlin
  val pageTotal = TOTAL(BOOK.PAGES) AS "page_total"
  val totals = SELECT
    .COLUMNS(BOOK.AUTHOR, pageTotal)
    .FROM(BOOK)
    .GROUP_BY(BOOK.AUTHOR) AS "totals"
  val byPages = SELECT
    .COLUMNS(AUTHOR.NAME, totals[pageTotal])
    .FROM(AUTHOR)
    .INNER_JOIN(totals)
    .ON(AUTHOR.ID IS totals[BOOK.AUTHOR])
    .ORDER_BY(totals[pageTotal].DESC())
  ```

- A nested query never chooses a connection; call `usingConnection` on the outermost query only. Mapping of a nested
  query (`mapTo`) is ignored where SQL consumes it.

## Common table expressions

`WITH` declares up to three CTEs inline with typed handles; more go through `asCte()` handles and a no-argument
lambda. With `prolificIds` from [the subquery section](#subqueries-and-derived-sources):

```kotlin
val prolificCte = (prolificIds AS "prolific_ids").asCte()

val prolificAuthors = WITH(prolificCte) { ids ->
  SELECT
    .FROM(AUTHOR)
    .INNER_JOIN(ids)
    .ON(AUTHOR.ID IS ids[BOOK.AUTHOR])
}
```

- Name a CTE with `(query AS "name").asCte()`; unnamed ones get generated names. Read its outputs with
  `cte[selectedExpression]`, as with derived sources.
- `WITH` returns the finished main query: final configuration, nesting and terminals follow, clauses do not.
- Recursive CTEs are not supported; use raw SQL for them.
- Prefer a CTE when the same subquery is used more than once or a query reads better in steps; otherwise a subquery
  or join is equivalent.

## Compound queries

```kotlin
val names = ((SELECT COLUMN AUTHOR.NAME FROM AUTHOR) UNION (SELECT COLUMN AUTHOR.NICKNAME FROM AUTHOR))
  .ORDER_BY(AUTHOR.NAME.ASC())
```

- `UNION`, `UNION_ALL`, `INTERSECT`, `EXCEPT`, infix or with a dot. The left arm decides the result type and readers;
  the right arm must have the same type or a subtype. A one-column `UNION`/`UNION_ALL` with a nullable right arm reads
  nullable (`String?` here); with several columns, select the value `.nullable()` in the left arm.
- `ORDER_BY`, `LIMIT` and `OFFSET` after the last arm apply to the whole compound; its `ORDER_BY` terms name the first
  arm's fields.
- Map after compounding, `(a UNION b).mapTo(::Dto)`: an arm mapped with a function is rejected.
- Prefer `UNION_ALL` when duplicates are impossible or wanted; `UNION` sorts to remove them.

## Terminals

| Terminal | Does | Notes |
| --- | --- | --- |
| `execute()` | runs and reads every row into a `List` | blocking; compiles the query on every call |
| `takeFirst()` | a plan for the first row; `.execute()` returns `T?` | adds `LIMIT 1` unless the query has a LIMIT |
| `count()` | a plan for `SELECT count(*) FROM (query)` (flat `SELECT count(*) FROM … WHERE …` for a plain, unordered entity query); `.execute()` returns `Long` | counts the final rows, DISTINCT, GROUP BY and LIMIT included; skips joins only entity reads need |
| `useRows { rows -> }` | runs and hands a lazy `Sequence` to the block | rows valid only inside the block, iterable once, connection held until it returns |
| `compile()` | a reusable plan, `CompiledSelect<T>` | thread-safe; `execute()`, `useRows`, `bind`, `observe()` |
| adapter `observe()` | a stream re-run when read tables change | [reactive.md](reactive.md) |

- `takeFirst()` and `count()` return plans: call `.execute()` (or `observe()`) on them.
- Test existence with `takeFirst().execute() != null` on the ID or another NOT NULL column (a NULL value also reads
  as `null`), with `EXISTS`, or with `count().observe().isNotZero()` in a stream.
- Use `useRows` to stream large results without building a list, or to stop early; keep the block short and free of
  other database calls on the same thread's transaction.
- `usingConnection(connection)` picks another `DbConnection` for the query; the default connection is resolved when
  the terminal runs.

## Compiled plans and parameters

A query is compiled (resolved, rendered to SQL, readers bound) by every `execute()`. For a query that runs often, keep
the plan, and make the varying values parameters:

```kotlin
object BookQueries {
  private val authorId = BOOK.AUTHOR.asParameter("authorId")
  private val minPages = BOOK.PAGES.asParameter("minPages")

  private val byAuthorPlan = (
      SELECT
          FROM BOOK
          WHERE ((BOOK.AUTHOR IS authorId) AND (BOOK.PAGES GREATER_OR_EQUAL minPages))
          ORDER_BY BOOK.TITLE.ASC()
      )
    .compile()

  fun byAuthor(id: Long, pages: Int) = byAuthorPlan.bind(
    authorId TO id,
    minPages TO pages
  )
}

val books = BookQueries.byAuthor(id = 7L, pages = 100).execute()
```

- `field.asParameter("name")` creates a parameter typed and encoded like the field; use it wherever a value of that
  type goes. The name is for diagnostics; identity decides binding.
- `bind(…)` returns a new bound plan; every parameter must be bound exactly once. A query with parameters cannot be
  `execute()`d unbound.
- Plans are immutable and thread-safe: keep them in properties. `takeFirst()` and `count()` plans bind the same way.
- `observe()` works on bound plans, so a screen can observe `byAuthor(id, pages)`.
- Parameters are not allowed in view definitions; mutations bind on the statement: `statement.bind(…).execute()`.
