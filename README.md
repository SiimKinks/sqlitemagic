<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/assets/logo-dark.svg">
    <img alt="SqliteMagic" src=".github/assets/logo-light.svg" width="426">
  </picture>
</h1>

Simple yet powerful SQLite database layer for Android that makes database handling feel like magic.

* Simple, intuitive & **typesafe API**
* SQL-first [Kotlin](https://kotlinlang.org/) DSL – queries read like SQL while the compiler checks clause order,
  column types and nullability
* Minimal setup needed
* Built for Kotlin – data classes, generated extension functions and typed result rows
* Compile time code generation with [KSP](https://kotlinlang.org/docs/ksp-overview.html) – **no reflection**
* Your choice of SQLite engine: the device's SQLite, or a recent SQLite bundled with the app for the same SQL on every
  Android version
* Kotlin coroutines and RxJava support for observed queries and one-shot operations
* Full support for complex columns – related objects and embedded values
* Support for SQLite views defined with the same DSL
* Full-text search with SQLite FTS5 and FTS4 – indexes declared on tables, kept in sync and migrated, searched, ranked
  and highlighted with the DSL
* Persist any third party object with fully customizable object transformers
* Automatic schema migrations with generated migration test support
* Probably the fastest library for Android SQLite database operations *(without memory caching)*

## Getting Started

SqliteMagic 3.x is built for Kotlin Android projects that use KSP. It supports Android API 24 and newer. Its artifacts
are built with Kotlin 2.4, so consuming modules need a Kotlin compiler that reads its metadata: Kotlin 2.3 or newer.

### Build From Source

SqliteMagic 3.x is not published to a public repository. Build a release from source and install it into your local
Maven repository. Check out the release's tag, because the branch already carries the next `-SNAPSHOT` version:

```shell
git checkout <latest version>
./gradlew publishToMavenLocal
```

To share builds through a self-hosted Maven repository such as Artifactory or Nexus, add it to
`publishing.repositories` in [buildsystem/maven-push.gradle](buildsystem/maven-push.gradle) and run
`./gradlew publish`.

### Add SqliteMagic to Project

In `settings.gradle.kts`:

```kotlin
pluginManagement {
  repositories {
    mavenLocal() // or your self-hosted repository
    google()
    mavenCentral()
    gradlePluginPortal()
  }
}

dependencyResolutionManagement {
  repositories {
    mavenLocal() // or your self-hosted repository
    google()
    mavenCentral()
  }
}
```

In the module's `build.gradle.kts`:

```kotlin
plugins {
  id("com.android.application")
  id("com.google.devtools.ksp") version "<ksp version>"
  id("com.siimkinks.sqlitemagic") version "<latest version>"
}

dependencies {
  // the SQLite driver; see Choose a SQLite Driver
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-framework-driver:<latest version>")
  // optional: the RxJava 3 or RxJava 2 APIs
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-rxjava3:<latest version>")
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-rxjava2:<latest version>")
  // optional: the coroutine Flow and suspend APIs
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-coroutines:<latest version>")
}
```

The plugin adds the matching runtime, annotations and KSP processor dependencies. Every `com.siimkinks.sqlitemagic`
artifact uses the plugin's version. The `sqlitemagic { }` extension is optional; see its
[options](.agents/skills/sqlitemagic/references/setup.md#plugin-options).

### Initialize Library

```kotlin
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.annotation.Database
import com.siimkinks.sqlitemagic.framework.FrameworkSQLiteDriver

@Database(
  name = "library.db",
  version = 1
)
class App : Application() {
  override fun onCreate() {
    super.onCreate()
    SqliteMagic.builder(this)
      .database(SqliteMagicDatabase())
      .driver(FrameworkSQLiteDriver())
      .openDefaultConnection()
  }
}
```

**Note**: `SqliteMagicDatabase` is generated. Any place with a reference to Application context is ok to use for
initialization, but it must happen before a database is accessed. Initialization only configures the default
connection. The database is opened on first access, which creates the schema or runs migration scripts as needed.

### Choose a SQLite Driver

The driver decides which SQLite runs the database. There is no default; pass one to `driver(...)`:

| | Device SQLite | Bundled SQLite |
| --- | --- | --- |
| Dependency | `com.siimkinks.sqlitemagic:sqlitemagic-framework-driver` | `androidx.sqlite:sqlite-bundled` |
| Class | `com.siimkinks.sqlitemagic.framework.FrameworkSQLiteDriver` | `androidx.sqlite.driver.bundled.BundledSQLiteDriver` |
| SQLite version | the device's: 3.9 on API 24, newer with Android | recent, the same on every device |
| App size | nothing added | about 1.3 MB per ABI |
| Errors | Android's typed `SQLiteException` subclasses | `SQLException` with SQLite's result code in the message |
| Text | ICU case folding, `LOCALIZED` and `UNICODE` collations | ASCII case folding, no Android collations |
| Database Inspector | yes | no |
| Full-text search | FTS4 | FTS5 and FTS4 |

The library does not check SQLite versions: SQL that the device's SQLite does not understand fails there with a syntax
error. With the bundled driver, never open the database file through Android's own database APIs while the app runs:
two SQLite libraries in one process do not see each other's locks and can corrupt the file.

The other limits of each driver are listed under
[Choose a driver](.agents/skills/sqlitemagic/references/setup.md#choose-a-driver), and the connection pool, its
defaults and its tuning under
[Connections and their defaults](.agents/skills/sqlitemagic/references/setup.md#connections-and-their-defaults).

## Usage

### Define Database

_Note that there is no need to extend or implement any base classes or interfaces_

```kotlin
@Table
data class Author(
  @Id(autoIncrement = false)
  val id: Long,
  val firstName: String?,
  val lastName: String?
)

@Table
data class Book(
  @Id(autoIncrement = false)
  val id: Long,
  val title: String,
  val author: Author
)
```

KSP generates the database operations as Kotlin [extension functions](https://kotlinlang.org/docs/extensions.html) on
`@Table` classes, bulk operations in an object named after the model (`Authors`), and the typed tables and columns of
the DSL (`AUTHOR`, `AUTHOR.FIRST_NAME`). The schema follows the Kotlin declarations: columns of non-null properties are
`NOT NULL`, so selected values keep their property types. Generated code is plain Kotlin, so one can still see human
readable code during debugging – just press "step into" when a generated method is encountered.

### Do Operations With Objects

```kotlin
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
...

val author = Author(
  id = 73,
  firstName = "Foo",
  lastName = "Bar"
)
val book = Book(
  id = 77,
  title = "Bar",
  author = author
)

// insert -- NOTE: author object also gets inserted and the whole operation is wrapped in transaction
val inserted: EntityInsertResult = book
  .insert()
  .execute()

// update
val success: Boolean = author
  .update()
  .execute()

// update or insert
val persisted: EntityPersistResult = author
  .persist()
  .execute()

// update or insert but ignore null values
author
  .persist()
  .ignoreNullValues()
  .execute()

// delete
val nrOfDeletedRows: Int = author
  .delete()
  .execute()

// Bulk operations are also supported
val bulkSuccess: Boolean = Authors
  .persist(someAuthors)
  .ignoreNullValues()
  .execute()
```

### Use Typesafe Operation Builders

```kotlin
import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
...

// columns left out take their defaults
val nrOfInsertedRows: Int = (INSERT INTO AUTHOR)
  .COLUMNS(AUTHOR.FIRST_NAME, AUTHOR.LAST_NAME)
  .VALUES("Foo", "Bar")
  .VALUES("Baz", null)
  .execute()

val nrOfUpdatedRows: Int = (
    UPDATE
        TABLE BOOK
        SET (BOOK.TITLE to "Foo")
        WHERE (BOOK.ID IS 77L)
    )
  .execute()

val nrOfDeletedRows: Int = (
    DELETE
        FROM BOOK
        WHERE ((BOOK.ID IS_NOT 77L)
            AND (BOOK.TITLE IS "Foo"))
    )
  .execute()
```

INSERT also takes a list of rows, the rows of a query and, with `ON_CONFLICT`, SQLite's UPSERT (SQLite 3.24, which
the framework driver has from API 30):

```kotlin
// insert the book, or update the existing book with this ID when its title differs;
// EXCLUDED is the value that the INSERT proposed
val nrOfWrittenRows: Int = (INSERT INTO BOOK)
  .COLUMNS(BOOK.ID, BOOK.TITLE, BOOK.AUTHOR)
  .VALUES(77L, "Foo", someAuthor.id)
  .ON_CONFLICT(BOOK.ID)
  .DO_UPDATE
  .SET(BOOK.TITLE, BOOK.TITLE.EXCLUDED)
  .WHERE(BOOK.TITLE.EXCLUDED IS_NOT BOOK.TITLE)
  .execute()
```

### Query Data

SqliteMagic ships with its own SQL-first DSL (or Domain Specific Language) that emulates SQL in Kotlin (inspired by
[jOOQ](https://www.jooq.org/)). Clauses follow SQL order, each stage offers only what SQL allows next and every query is
an immutable value that can be reused. Clauses are written infix, as below, or as dot calls:
`SELECT.FROM(AUTHOR).WHERE(AUTHOR.LAST_NAME IS "Bar")`. The whole DSL is imported from `com.siimkinks.sqlitemagic`.

```kotlin
import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
...

val authors: List<Author> = (
    SELECT
        FROM AUTHOR
        WHERE ((AUTHOR.FIRST_NAME LIKE "Foo%")
            AND (AUTHOR.LAST_NAME IS_NOT "Bar"))
        ORDER_BY AUTHOR.LAST_NAME.DESC()
        LIMIT 10
    )
  .execute()

// the resulting Book objects also contain Author objects
val books: List<Book> = (
    SELECT
        FROM BOOK
        WHERE ((BOOK.TITLE IS "Bar")
            AND (BOOK.AUTHOR IS someAuthor.id))
        ORDER_BY AUTHOR.LAST_NAME.ASC()
        LIMIT 10
    )
  // this tells to query all complex data which is queried in a single SELECT statement
  .queryDeep()
  .execute()
```

Without `queryDeep()`, each `Author` holds only its ID, since its other properties are nullable. Updating or persisting
a `Book` also writes its `Author`, so writing back a shallow-queried `Book` sets the author's names to `NULL` unless it
is persisted with `ignoreNullValues()`.

### Query Columns

```kotlin
data class AuthorName(val firstName: String?, val lastName: String?)

// typed rows -- List<Row2<String?, String?>>
val names = (
    SELECT.COLUMNS(AUTHOR.FIRST_NAME, AUTHOR.LAST_NAME)
        FROM AUTHOR
        ORDER_BY AUTHOR.LAST_NAME.ASC()
    )
  .execute()

// or your own classes -- List<AuthorName>
val authorNames = SELECT
  .COLUMNS(AUTHOR.FIRST_NAME, AUTHOR.LAST_NAME)
  .FROM(AUTHOR)
  .mapTo(::AuthorName)
  .execute()

// whole entities beside other values -- List<Row2<Author, Long>>
val booksPerAuthor = (
    SELECT.COLUMNS(AUTHOR, COUNT(BOOK.ID))
        FROM AUTHOR
        LEFT_JOIN BOOK ON (BOOK.AUTHOR IS AUTHOR.ID)
        GROUP_BY AUTHOR.ID
    )
  .execute()

// single columns, the first result and counting
val titles: List<String> = (SELECT COLUMN BOOK.TITLE FROM BOOK).execute()
val first: Author? = (SELECT FROM AUTHOR).takeFirst().execute()
val count: Long = (SELECT FROM AUTHOR).count().execute()

// rows read one at a time while the block runs
val firstTitles = (SELECT COLUMN BOOK.TITLE FROM BOOK ORDER_BY BOOK.TITLE.ASC())
  .useRows { titles -> titles.take(10).toList() }
```

There is so much more to querying data like joins, subqueries, compound queries, common table expressions (`WITH`),
SQL functions, window functions, views, compiled queries with named parameters, raw SQL, etc. **Every part of the DSL
is documented in KDoc, and the [sample app](sqlitemagic-sample) shows the pieces working together.**

### Observe Queries and Run Asynchronously

Every operation above ends in `execute()`, which blocks the calling thread. The adapters replace it: `observe()` on a
query emits again whenever a queried table changes, and one-shot work becomes a suspend function or an RxJava type.
The RxJava 3 and RxJava 2 adapters have the same API, in `com.siimkinks.sqlitemagic.rxjava3` and
`com.siimkinks.sqlitemagic.rxjava2`.

<table>
<tr>
<th>Coroutines</th>
<th>RxJava</th>
</tr>
<tr>
<td width="50%">

```kotlin
import com.siimkinks.sqlitemagic.coroutines.await
import com.siimkinks.sqlitemagic.coroutines.observe
import com.siimkinks.sqlitemagic.coroutines.runQuery
...

val authors: Flow<List<Author>> = (SELECT FROM AUTHOR)
  .observe()
  .runQuery()

suspend fun save(author: Author): EntityInsertResult = author
  .insert()
  .await()

suspend fun rename(id: Long, title: String): Int = (
    UPDATE
        TABLE BOOK
        SET (BOOK.TITLE to title)
        WHERE (BOOK.ID IS id)
    )
  .await()
```

</td>
<td width="50%">

```kotlin
import com.siimkinks.sqlitemagic.rxjava3.observe
...



val authors: Observable<List<Author>> = (SELECT FROM AUTHOR)
  .observe()
  .runQuery()

fun save(author: Author): Single<EntityInsertResult> = author
  .insert()
  .observe()

fun rename(id: Long, title: String): Single<Int> = (
    UPDATE
        TABLE BOOK
        SET (BOOK.TITLE to title)
        WHERE (BOOK.ID IS id)
    )
  .observe()
```

</td>
</tr>
</table>

All database operations notify the active observed queries that read the modified tables. Coroutine work runs on
`Dispatchers.IO` and RxJava query streams on `Schedulers.io()`, unless the builder sets another with
`dispatchCoroutineQueriesOn` or `scheduleRxQueriesOn`. See
[Coroutines and RxJava](.agents/skills/sqlitemagic/references/reactive.md) for every operator and result type.

### Run Transactions

Run suspending work in one transaction with `withTransaction`. It commits when the block and the coroutines it launched
complete, and rolls back when they fail or are cancelled:

```kotlin
import com.siimkinks.sqlitemagic.coroutines.await
import com.siimkinks.sqlitemagic.coroutines.observe
import com.siimkinks.sqlitemagic.coroutines.runQueryOnce
import com.siimkinks.sqlitemagic.coroutines.withTransaction

suspend fun renameAuthor(id: Long, lastName: String) = withTransaction {
  val author = (SELECT FROM AUTHOR WHERE (AUTHOR.ID IS id))
    .takeFirst()
    .observe()
    .runQueryOnce()
  author
    ?.copy(lastName = lastName)
    ?.update()
    ?.await()
}
```

Blocking code uses `inTransaction { }` or `newTransaction()`, whose transaction belongs to the thread that started it.
Observers of the changed tables are notified after the commit. See
[Transactions](.agents/skills/sqlitemagic/references/transactions.md) for the rules inside both kinds.

### Full-Text Search

`@FullTextIndex` declares an SQLite full-text index on a table and `@FullText` the columns it indexes:

```kotlin
@Table
@FullTextIndex(tokenize = "porter unicode61")
data class Article(
  @Id val id: Long? = null,
  @FullText val title: String,
  @FullText val body: String,
  val author: String
)
```

The schema creates the index as an FTS table, `article_fts`, that generated triggers keep in sync, so the app writes
only `Article`. Migrations create, drop and refill the index as the model changes. Queries search it through the
generated index object `ARTICLE_FTS`:

```kotlin
import com.siimkinks.sqlitemagic.ArticleFtsTable.Companion.ARTICLE_FTS
import com.siimkinks.sqlitemagic.ArticleTable.Companion.ARTICLE
...

// best matches first
val articles: List<Article> = (
    SELECT
        FROM ARTICLE
        WHERE (ARTICLE_FTS MATCH "sqlite OR room")
        ORDER_BY ARTICLE_FTS.RANK.ASC()
    )
  .execute()

// user input as a query that cannot fail, the last term matching the words it begins;
// matches in titles rank higher, with the title highlighted and a snippet of the body
val hits: List<Row3<Article, String, String>> = (
    SELECT.COLUMNS(
        ARTICLE,
        HIGHLIGHT(ARTICLE_FTS.TITLE, "<b>", "</b>"),
        SNIPPET(ARTICLE_FTS.BODY, "<b>", "</b>", "…", 10)
    )
        FROM ARTICLE
        WHERE (ARTICLE_FTS MATCH ARTICLE_FTS.terms(text = input, prefix = true))
        ORDER_BY BM25(ARTICLE_FTS.TITLE to 10.0).ASC()
        LIMIT 20
    )
  .execute()
```

The table needs a stable row ID: an `@Id` stored as `INTEGER`, or a
[`@RowId`](.agents/skills/sqlitemagic/references/models.md#ids). FTS5 needs the bundled driver;
`@FullTextIndex(version = FullTextVersion.FTS4)` declares an FTS4 index, which both drivers support, without `RANK`,
`BM25` and `HIGHLIGHT`. See [Full-text search](.agents/skills/sqlitemagic/references/full-text-search.md) for index
options, joins and index maintenance.

### Migrate Database

Debug databases migrate automatically when the schema changes. When releasing a new database version, raise
`@Database(version)` and run `./gradlew migrateReleaseDb` (`migrateFreeReleaseDb` with flavors). It records the release
schema in `db/releases` and generates its migration script into `src/release/assets`; review the script and commit
both. The rest of `db/` and the debug migration scripts are local build state to keep
[out of version control](.agents/skills/sqlitemagic/references/migrations.md#version-control).

To see what the next release would contain, run `./gradlew previewReleaseDbMigration` at any time, without raising the
version first. It prints the schema changes since the latest release, the migration warnings and the migration SQL.

Application modules also get generated JVM migration test support. Add a JVM SQLite driver, such as
`androidx.sqlite:sqlite-bundled-jvm`, and a test framework to the unit tests, then validate every recorded release
against the current schema:

```kotlin
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.siimkinks.sqlitemagic.SqliteMagicMigrationDatabase
import com.siimkinks.sqlitemagic.SqliteMagicMigrationTestHelper
...

@Test
fun `all releases migrate to the current schema`() {
  SqliteMagicMigrationTestHelper(
    descriptor = SqliteMagicMigrationDatabase,
    driver = BundledSQLiteDriver()
  ).use(SqliteMagicMigrationTestHelper::validateAllMigrations)
}
```

See [Schema changes and migrations](.agents/skills/sqlitemagic/references/migrations.md) for what each model change
does to existing data, product flavors and editing migration scripts.

## Documentation

- [Sample app](sqlitemagic-sample) – a todo app with tables, a view, full-text search, reactive queries and migration
  tests
- [Using SqliteMagic](.agents/skills/sqlitemagic/SKILL.md) – a guide to every feature of 3.x and how to use it
  idiomatically and efficiently: models, entity operations, the query DSL, raw SQL, views, full-text search,
  coroutines and RxJava, transactions, migrations and performance
- [Upgrading from 2.x or 0.x](.agents/skills/sqlitemagic-upgrade/SKILL.md) – 3.x rewrites the query DSL and runs
  on an androidx `SQLiteDriver`; this guide maps legacy setup and queries to 3.x, lists behavior changes and covers the
  first database release on 3.x
- [CHANGELOG](CHANGELOG.md) – all updates
- [Wiki](https://github.com/SiimKinks/sqlitemagic/wiki) and [Javadoc](https://siimkinks.github.io/sqlitemagic/javadoc/)
  for 0.x

Both guides are also agent skills: copy the `sqlitemagic` or `sqlitemagic-upgrade` directory into your project's skills
directory, such as `.claude/skills/`, so a coding agent writes SqliteMagic code the way it is meant to be written or
does the upgrade.

## Bugs and Feedback

**For bugs, questions and discussions please use the [Github Issues](https://github.com/SiimKinks/sqlitemagic/issues).**

## License

Copyright 2026 Siim Kinks

Licensed under the [Apache License, Version 2.0](LICENSE).
