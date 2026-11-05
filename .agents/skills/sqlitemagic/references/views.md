# Views

A `@View` model names a SELECT as an SQLite view and reads its rows as objects. The query is written in the DSL;
the library renders it into `CREATE VIEW`.

## Contents

- [Declare a view](#declare-a-view)
- [Columns and names](#columns-and-names)
- [Definition rules](#definition-rules)
- [Reading views](#reading-views)
- [When to use a view](#when-to-use-a-view)

## Declare a view

```kotlin
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View
data class AuthorSummary(
  @ViewColumn("author")
  val author: Author,
  @ViewColumn("book_count")
  val bookCount: Long,
  @ViewColumn("longest")
  val longest: Int?
) {
  companion object {
    @ViewQuery
    val QUERY = SELECT
      .COLUMNS(
        AUTHOR.all() AS "author",
        (SELECT COLUMN COUNT() FROM BOOK WHERE (BOOK.AUTHOR IS AUTHOR.ID)).asScalar() AS "book_count",
        (SELECT COLUMN MAX(BOOK.PAGES) FROM BOOK WHERE (BOOK.AUTHOR IS AUTHOR.ID)).asScalar() AS "longest"
      )
      .FROM(AUTHOR)
  }
}
```

- Exactly one `@ViewQuery`, a non-private companion-object property holding the query itself (a `SelectQuery` of any
  shape). Do not call `compile()`, `usingConnection()` or a terminal on it.
- The view name is the class name in snake case (`author_summary`); `@View("name")` overrides it.
  `@View(options = [ViewOption.TEMPORARY])` creates it per connection.
- The model follows table-model rules: constructor-backed or mutable; non-null properties are required, and a NULL in
  one fails the read with `SQLException("Column … was NULL")`, so type nullable whatever the query can make NULL
  (`MAX` above).
- Generated: a table object named in upper snake case (`AUTHOR_SUMMARY`) with fields for the view's scalar columns
  (`AUTHOR_SUMMARY.BOOK_COUNT`).

## Columns and names

- A view column is named by its `AS` alias, otherwise by the field's qualified name: an unaliased `AUTHOR.NAME`
  is the column `author.name`. Computed values always take an `AS` alias.
- A root property maps the column named after it in snake case (`bookCount` → alias `book_count`); `@ViewColumn("…")`
  names another, and is required for qualified names (`"author.name"`).
- A property whose type is a `@Table` (or `@View`) model reads a whole block: select `AUTHOR.all() AS "author"` and
  annotate `@ViewColumn("author") val author: Author`. Its relationships are built from the columns the definition
  joins for them, or from their IDs where the model allows. A nullable block reads `null` when all its columns are NULL.
- Properties of `@Embedded` values map by their snake-case names with the embedded prefix.
- Raw SQL over a view quotes dotted names: `SELECT "author.name", book_count FROM author_summary`.

## Definition rules

- Values in the definition render as escaped SQL literals: `WHERE (BOOK.STATUS IS BookStatus.PUBLISHED)` becomes part
  of the view text. Named parameters are rejected when the view is created.
- Raw fragments in a definition must write constants into their text; SQLite rejects `?` in views.
- The definition is rendered once per process, when views are first created, and checked then: a required
  relationship block the definition cannot fill, or a parameter, fails before any DDL.
- Changing a view's query changes the schema: debug builds migrate automatically; a release needs a new database
  version ([migrations.md](migrations.md)). Persistent views are dropped and recreated on every upgrade.
- Functions and syntax in the definition must exist in every SQLite that opens the database (the framework driver's
  minimum is SQLite 3.9 on API 24).

## Reading views

```kotlin
val summaries = (
    SELECT
        FROM AUTHOR_SUMMARY
        WHERE (AUTHOR_SUMMARY.BOOK_COUNT GREATER_THAN 0L)
        ORDER_BY AUTHOR_SUMMARY.BOOK_COUNT.DESC()
    )
  .execute()

val counts = SELECT.COLUMNS(AUTHOR_SUMMARY.BOOK_COUNT, AUTHOR_SUMMARY.LONGEST).FROM(AUTHOR_SUMMARY)
```

- `SELECT FROM VIEW` reads whole models; column selections, filters, joins and aggregates work as on tables.
- `mapTo(VIEW)` partial reads cannot fill a required table- or view-typed property; read such views whole.
- Observed view queries re-run when any table in the definition changes.
- Views are read-only: write the underlying tables.

## When to use a view

A view is a stored query, not a cache: SQLite runs the definition on every read, with the outer query's filters
merged in. Use one when a joined or aggregated shape is read in several places as a model, or must be readable from
raw SQL and tooling. For a shape used in one place, a Kotlin query value (`SELECT.COLUMNS(…).mapTo(::Dto)`) is simpler
and needs no migration when it changes.
