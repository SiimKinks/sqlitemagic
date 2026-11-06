---
name: sqlitemagic
description: >-
  Write, extend and review Android code that uses SqliteMagic 3, the KSP-generated SQLite layer with a SQL-first
  Kotlin DSL: setup and drivers, @Table models, relationships, transformers and views, entity operations (insert,
  update, persist, delete), queries (SELECT FROM, SELECT.COLUMNS, joins, subqueries, aggregates, compiled queries),
  UPDATE and DELETE statements, raw SQL, observing queries with coroutines or RxJava, transactions and migrations.
  Use whenever code imports com.siimkinks.sqlitemagic or a task adds a table, column, query, repository, database
  screen or migration in a SqliteMagic project, even if the request only says "store this", "query the db" or "make
  it reactive". Not for upgrading from SqliteMagic 2.x or 0.x (use sqlitemagic-upgrade), nor for changing
  SqliteMagic itself.
---

# Using SqliteMagic

SqliteMagic generates, at compile time with KSP, a typed SQLite layer from annotated Kotlin classes: the schema,
entity operations as extension functions, typed table and column objects for a SQL-first query DSL, and migrations.
There is no reflection and no runtime schema discovery. Code written well against it reads like SQL, lets the Kotlin
compiler check clause order, column types and nullability, and does only the database work the screen needs.

## Know the sources

Read the reference a task needs, not all of them up front:

| Task | Reference |
| --- | --- |
| Gradle setup, choosing a driver, initialization, connection tuning, multi-module projects, logging | [setup.md](references/setup.md) |
| Declaring tables, IDs, columns, nullability, defaults, indexes, relationships, embedded values, transformers; what is generated | [models.md](references/models.md) |
| Saving and deleting objects: insert, update, persist, delete, bulk operations, results, conflicts | [entity-operations.md](references/entity-operations.md) |
| Reading data: SELECT shapes, result types, clauses, joins, aliases, mapping, subqueries, CTEs, compounds, terminals, parameters | [queries.md](references/queries.md) |
| Building values and predicates: comparisons, NULL, arithmetic, aggregates, windows, text helpers, CASE/COALESCE, CAST, raw fragments, functions | [expressions.md](references/expressions.md) |
| UPDATE and DELETE statements and whole raw SQL | [statements.md](references/statements.md) |
| SQLite views from a DSL query | [views.md](references/views.md) |
| Observing queries and one-shot async work with coroutines or RxJava | [reactive.md](references/reactive.md) |
| Transactions, threads, connections and errors | [transactions.md](references/transactions.md) |
| Schema changes, release migrations and migration tests | [migrations.md](references/migrations.md) |
| Making database code fast | [performance.md](references/performance.md) |
| Reviewing SqliteMagic code, or checking your own before finishing | [review-checklist.md](references/review-checklist.md) |

Every DSL declaration has KDoc; when the reference and the IDE disagree about a signature, the compiled API wins. The
[README](https://github.com/SiimKinks/sqlitemagic/blob/3.0.0/README.md) and the
[sample app](https://github.com/SiimKinks/sqlitemagic/tree/3.0.0/sqlitemagic-sample) show the pieces together.

## The model to keep in mind

- **The Kotlin class is the schema.** A non-null property is a `NOT NULL` column and selects as a non-null value; a
  nullable property is a nullable column. Changing a class changes the schema, which debug builds migrate
  automatically and release builds migrate through a generated, reviewed script.
- **Queries are immutable values.** Every clause returns a new query, and nothing touches the database until
  `execute()`, `useRows { }` or a reactive adapter's operator runs it; `takeFirst()`, `count()` and `compile()` only
  make reusable plans. Define a query once and reuse it: pass it around, nest it, observe it or execute it.
- **SQL order, SQL words, one spelling.** `SELECT … FROM … JOIN … ON … WHERE … GROUP_BY … HAVING … ORDER_BY …
  LIMIT … OFFSET`, each stage offering only what SQL allows next. Clauses are uppercase infix calls that also work
  with a dot: `SELECT FROM BOOK WHERE (BOOK.PAGES GREATER_THAN 300)` is
  `SELECT.FROM(BOOK).WHERE(BOOK.PAGES.GREATER_THAN(300))`. Final configuration (`queryDeep()`, `readAs(…)`, `mapTo(…)`,
  `usingConnection(…)`) follows the last clause.
- **Types follow SQL facts.** Each expression knows its decoded Kotlin type and whether SQL can make it NULL. Outer
  joins, aggregates over empty input, scalar subqueries, `/` and `%` can, and the types say so. When the compiler or
  the pre-SQL check rejects something, it is usually pointing at a real NULL or ambiguity, not at a library quirk.
- **SQLite owns SQL semantics.** The library renders what you wrote, binds values natively (INTEGER, REAL, TEXT,
  BLOB) and surfaces SQLite's own errors. It does not emulate missing features: on the device's SQLite, a feature newer
  than the device's SQLite version fails when the statement is prepared.
- **Change notification is built in.** Every write through the library notifies the observed queries that read the
  changed tables, including the tables an `ON DELETE CASCADE` deletes from, after the transaction commits. Raw SQL
  declares what it reads (`observes`) and changes (`notifies`).

## Example schema

References use this schema; names are illustrative. Generated objects are `AUTHOR` and `BOOK`
(`import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK`), with fields such as `BOOK.TITLE`, `BOOK.AUTHOR` (the
foreign key, a `Long`) and `BOOK.STATUS`.

```kotlin
@Table
data class Author(
  @Id val id: Long? = null,
  val name: String,
  val nickname: String? = null
)

@Table
data class Book(
  @Id val id: Long? = null,
  val title: String,
  val pages: Int,
  @Column(onDeleteCascade = true)
  val author: Author,
  val editor: Author? = null,
  @Column(defaultValue = "'DRAFT'")
  val status: BookStatus = BookStatus.DRAFT
)

enum class BookStatus { DRAFT, PUBLISHED }

@ObjectToDbValue
fun bookStatusToDb(value: BookStatus): String = value.name

@DbValueToObject
fun bookStatusFromDb(value: String): BookStatus = BookStatus.valueOf(value)
```

`Book` reaches `Author` along two relationships, `author` and `editor`, so a query over `BOOK` that uses `AUTHOR.NAME`
must join the path it means; [queries.md](references/queries.md#joins-and-aliases) shows how.

## Write it the way the library's author would

1. **Model with Kotlin types.** Data classes with `val`s; `@Id val id: Long? = null` for generated row IDs; non-null
   wherever the value is required; Kotlin default values for new objects; a related `@Table` object instead of a raw
   foreign key; `@Embedded` for value objects; one transformer pair per custom type (enums stored by name, not
   ordinal). See [models.md](references/models.md).
2. **Keep queries next to their model.** Put reusable queries in the model's companion object or a repository as
   functions or properties that *return the query*, not its result, so each caller picks `execute()`, `observe()`,
   `takeFirst()` or `count()`:

   ```kotlin
   // inside Book
   companion object {
     fun byAuthor(authorId: Long) = (
         SELECT
             FROM BOOK
             WHERE (BOOK.AUTHOR IS authorId)
             ORDER_BY BOOK.TITLE.ASC()
         )

     val COUNT = (SELECT FROM BOOK).count()
   }
   ```

   `Book.byAuthor(id).execute()`, `Book.byAuthor(id).observe().runQuery()` and `Book.COUNT.execute()` then all reuse
   one definition.

3. **Select what the code uses.** A screen that shows titles selects `BOOK.TITLE`, or a few columns mapped to a small
   class with `mapTo(::BookRow)`, not whole entities. Read whole entities when the code needs the object, for example
   to write it back. Count with `count()`, test existence with `takeFirst()` or `EXISTS(…)`, never with
   `execute().size`.
4. **Let SQL do set work.** Filter, sort, group, aggregate and paginate in the query, not in Kotlin over a full list.
   Change columns with an `UPDATE` statement (`SET (BOOK.PAGES to BOOK.PAGES + 1)`) instead of read-modify-write of an
   entity. Write many objects with the bulk operations (`Books.insert(list)`) or inside one transaction.
5. **Pass host values directly.** Comparisons, `IN`, `BETWEEN`, `LIKE` and `SET` take plain Kotlin values, bound as
   `?` through the column's encoder: `BOOK.STATUS IS BookStatus.PUBLISHED`. `LIMIT` and `OFFSET` take `Int`/`Long`
   and are written as literals. Use `asValue()` only where SQL needs a standalone value expression
   (`IFNULL(AUTHOR.NICKNAME, "Anonymous".asValue())`, `THEN`/`ELSE` values, a number on the left of `+`). Never build
   SQL text from values.
6. **Parenthesize infix operands.** Kotlin infix calls share one precedence, unlike SQL operators:
   `WHERE ((BOOK.PAGES GREATER_THAN 300) AND (BOOK.TITLE LIKE "K%"))`. Keep a call site in one style: an infix
   statement stays infix, a fluent chain stays fluent. Write new statements in the style the file already uses;
   in a file with none yet, use infix.
7. **Name occurrences once.** Create each alias, selected computed value, CTE and derived source once in a `val` and
   reuse that value; the library identifies them by instance, not by name.
8. **Make NULL explicit at its source.** Lift a field read through an outer join with `.nullable()`, give a nullable
   result a fallback with `IFNULL`/`COALESCE` in SQL or `?:` in Kotlin, compare with NULL through `IS_NULL()`. No `!!`
   on database results; use `checkNotNull(…)` with a message when a value must exist.
9. **Stay in the DSL; drop to raw SQL deliberately.** Raw fragments (`"…".expr(SqlType.BOOLEAN, args)`), raw
   statements (`SELECT.RAW`, `UPDATE.RAW`, `DELETE.RAW`) and declared functions (`scalarFunction`) cover what the DSL
   lacks. Bind arguments, interpolate generated fields (`"${BOOK.TITLE}"` renders the quoted column) and declare
   `observes`/`notifies`.
10. **Do database work off the main thread.** Use the coroutine adapter's `observe()`/`await()`/`withTransaction`, or
    the RxJava adapter's `observe()` with `subscribeOn(Schedulers.io())` for writes; blocking `execute()` belongs in
    background code, transactions and tests.
11. **Handle the exceptions SQLite throws.** Writes throw `android.database.SQLException` subclasses on constraint
    violations; catch `SQLException` (not `SQLiteConstraintException`, which only the framework driver throws) where a
    conflict is an expected outcome, or choose a conflict algorithm instead.
12. **Let the build own the schema.** Never edit generated code, `db/` state or debug migration scripts by hand. For a
    release, raise `@Database(version)`, run `migrate<Variant>Db`, review the generated script and its warnings, and
    keep a migration test. See [migrations.md](references/migrations.md).

## Imports

Everything a user writes comes from `com.siimkinks.sqlitemagic`: statement starters (`SELECT`, `UPDATE`, `DELETE`,
`WITH`), top-level functions and operators (`AND`, `OR`, `COUNT`, `IFNULL`, `CASE`, `EXISTS`, …), extensions
(`queryDeep`, `mapTo`, `readAs`, `asScalar`, `asValue`, `asLiteral`, `expr`), the generated entity operations
(`com.siimkinks.sqlitemagic.insert`, `…persist`) and the generated table objects (`…AuthorTable.Companion.AUTHOR`).
Methods of a query, field or expression (`WHERE`, `ORDER_BY`, `IS`, `ASC()`, `nullable()`) are members and need no
import, except the arithmetic operators (`+`, `-`, `*`, `/`, `%`) and the `SET` that assigns a numeric expression
(`SET(BOOK.PAGES, BOOK.PAGES + 1)`): both are top-level (`com.siimkinks.sqlitemagic.plus`, `…SET`), and without them the
member `SET` reports a type mismatch rather than an unresolved name. Reactive `observe()`, `await()` and their operators
come from the adapter's package (`com.siimkinks.sqlitemagic.coroutines`, `.rxjava3` or `.rxjava2`). Conflict algorithm
constants are Android's `SQLiteDatabase.CONFLICT_*`. Let the IDE add imports; when a DSL word is "unresolved", it is
usually a missing import of a top-level function, not a missing feature.

## Workflow for a database task

1. Find the project's setup: the `@Database` class, the driver, the reactive adapter in use (coroutines, RxJava 3 or 2)
   and existing models and repositories. Follow their conventions; this skill fills in what the project does not show.
2. Change models first, build once so KSP regenerates tables and columns, then write queries against the generated
   objects. Generated code lives in `build/generated/ksp/<variant>/kotlin/com/siimkinks/sqlitemagic/`; read it to see a
   field's exact type rather than guessing.
3. Write the query or operation with the references above, choosing the smallest selection and the reactive form that
   fits the caller.
4. Build, then prepare and run the code. A JVM unit test that calls `compile()` (or `takeFirst()`/`count()`) on each
   query surfaces the checks made when a query is prepared (outer-join NULLs, ambiguous joins, partial entities)
   without a database. Behavior against data, and view definitions, need a real database: an instrumented test, or the
   migration test helper with `BundledSQLiteDriver`.
5. When a model change alters the schema of a released app, follow [migrations.md](references/migrations.md) before
   calling the work done.
6. Walk [review-checklist.md](references/review-checklist.md) over the changed code.
