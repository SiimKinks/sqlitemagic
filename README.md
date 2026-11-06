<h1 align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset=".github/assets/logo-dark.svg">
    <img alt="SqliteMagic" src=".github/assets/logo-light.svg" width="426">
  </picture>
</h1>

Simple yet powerful SQLite database layer for Android that makes database handling feel like magic.

#### Overview:
* Simple, intuitive & **typesafe API**
* SQL-first [Kotlin](https://kotlinlang.org/) DSL – queries read like SQL while the compiler checks clause order,
  column types and nullability
* Minimal setup needed
* Your choice of SQLite engine: the device's SQLite, or a recent SQLite bundled with the app for the same SQL on every
  Android version
* Kotlin coroutines and RxJava support for observed queries and one-shot operations, through the
  `sqlitemagic-coroutines`, `sqlitemagic-rxjava3` and `sqlitemagic-rxjava2` adapters
* Built for Kotlin – data classes, generated extension functions and typed result rows
* Full support for complex columns – related objects and embedded values
* Support for SQLite views defined with the same DSL
* Persist any third party object with fully customizable object transformers
* Automatic schema migrations with generated migration test support
* **No reflection**
* Compile time code generation with [KSP](https://kotlinlang.org/docs/ksp-overview.html)
* Probably the fastest library for Android SQLite database operations *(without memory caching)*

Getting Started
-----------------

SqliteMagic 3.x is built for Kotlin Android projects that use KSP. It supports Android API 24 and newer.

#### Publish SqliteMagic:

SqliteMagic 3.x is not published to a public repository. Build a release from source and install it into your local
Maven repository. Check out the release's tag, because the branch already carries the next `-SNAPSHOT` version:

```shell
git checkout <latest version>
./gradlew publishToMavenLocal
```

This publishes the Gradle plugin, runtime, annotations, KSP processor, migration testing, framework driver, coroutines,
RxJava 3 and RxJava 2 artifacts as `com.siimkinks.sqlitemagic:*:<latest version>`. To share builds through a
self-hosted Maven repository such as Artifactory or Nexus, add it to `publishing.repositories` in
[buildsystem/maven-push.gradle](buildsystem/maven-push.gradle) and run `./gradlew publish`.

#### Add SqliteMagic to Project:

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
  // the SQLite driver; see Choose a SQLite Driver. The framework driver uses the same version as the plugin
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-framework-driver:<latest version>")
  // optional: the RxJava 3 observe() APIs; use the same version as the plugin
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-rxjava3:<latest version>")
  // optional: the RxJava 2 observe() APIs; use the same version as the plugin
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-rxjava2:<latest version>")
  // optional: the coroutine Flow and suspend APIs; use the same version as the plugin
  implementation("com.siimkinks.sqlitemagic:sqlitemagic-coroutines:<latest version>")
}
```

The plugin adds the matching runtime, annotations and KSP processor dependencies. Optional configuration, with the
defaults:

```kotlin
sqlitemagic {
  // add the runtime, annotations and KSP processor dependencies
  configureAutomatically = true
  // generate public instead of internal operation extensions for public models
  publicKotlinExtensionFunctions = false
  // migrate debug databases automatically when the schema changes
  migrateDebugDatabase = true
  // in a library module with models: directory of the application module that owns the database,
  // relative to the root project, such as "app"
  mainModulePath = null
  migrationTesting {
    // generate JVM migration test support in application modules
    enabled = true
    // release variant whose schema history the tests validate; null selects the first release variant
    releaseVariant = null
  }
}
```

#### Initialize Library:

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
connection. The database is opened on first access, which creates the schema or runs migration scripts as needed - no
other hidden runtime performance costs.

#### Choose a SQLite Driver:

The driver decides which SQLite runs the database. There is no default; pick one:

<table style="width:100%; border-collapse: collapse;" >
<tr>
<th>Device SQLite</th>
<th>Bundled SQLite</th>
</tr>
<tr style="background: none">
<td style="padding:0; margin:0; border:none; width:50%;">

```kotlin
// build.gradle.kts; same version as the plugin
implementation(
  "com.siimkinks.sqlitemagic:sqlitemagic-framework-driver:<latest version>"
)

// Application.kt
import com.siimkinks.sqlitemagic.framework.FrameworkSQLiteDriver

// Application.onCreate
SqliteMagic.builder(this)
  .database(SqliteMagicDatabase())
  .driver(FrameworkSQLiteDriver())
  .openDefaultConnection()
```

</td>
<td style="padding:0; margin:0; border:none; width:50%;">

```kotlin
// build.gradle.kts
implementation(
  "androidx.sqlite:sqlite-bundled:<sqlite version>"
)

// Application.kt
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

// Application.onCreate
SqliteMagic.builder(this)
  .database(SqliteMagicDatabase())
  .driver(BundledSQLiteDriver())
  .openDefaultConnection()
```

</td>
</tr>
</table>

`FrameworkSQLiteDriver` runs the SQLite of the device, whose version follows the Android version (SQLite 3.9 on
API 24). It keeps Android's typed `SQLiteException` subclasses, the `LOCALIZED` and `UNICODE` collations, ICU case
folding in `lower`, `upper` and `LIKE`, and the Database Inspector, and it adds nothing to the app size. Three limits
of Android's database APIs remain:

* a row larger than a cursor window, about 2 MB, cannot be read and fails with `SQLiteException`;
* a statement with a `RETURNING` clause (API 34 and later) runs again for each further cursor window, about 2 MB, of
  rows it returns, so keep its result small or use the bundled driver;
* each connection takes the app's default locale when it opens. After the locale changes while the app runs, a
  connection that opens later sorts `LOCALIZED` text by the new locale and the earlier ones by the old locale until
  the app restarts.

`BundledSQLiteDriver` ships a recent SQLite inside the app, so newer SQL features work on every supported Android
version. It costs:

* about 1.3 MB per ABI in the installed app;
* no Database Inspector, and no ICU case folding or Android collations;
* errors are plain `SQLException`s with SQLite's result code in the message, instead of typed subclasses;
* once the schema uses SQL the device's SQLite does not understand, only a SQLite that does can open the database.

Never open the database file through Android's own database APIs while the app runs it on a bundled driver: two SQLite
libraries in one process do not see each other's locks and can corrupt the file. A corrupt database fails with an
exception and its files are kept.

File databases use one writer and up to four reader connections that open when needed, in WAL journal mode. Reads run
alongside writes and see the last committed data; the thread that runs a transaction reads its own changes. Reads of
temporary tables and views and raw SELECTs that declare no tables run on the writer, so raw SQL such as `RETURNING` or
`last_insert_rowid()` sees the connection that changes the data. A statement waits at most 30 seconds for a connection
that other threads hold, then fails with `SQLException`. A connection waits up to 3 seconds for a file lock held by
another connection or process (`PRAGMA busy_timeout = 3000`). In WAL mode, connections run with
`PRAGMA synchronous = NORMAL`, so a transaction committed just before a power loss or system crash can be rolled back,
while the database stays intact. The builder tunes this, and `configureConnection` runs after these defaults:

```kotlin
import androidx.sqlite.execSQL

SqliteMagic.builder(this)
  .database(SqliteMagicDatabase())
  .driver(BundledSQLiteDriver())
  .readerConnections(2) // 0 runs every statement on one connection
  .connectionPoolTimeout(10.seconds) // Duration.INFINITE waits without a limit
  .configureConnection { connection -> // runs on every connection that opens
    connection.execSQL("PRAGMA synchronous = FULL")
  }
  .openDefaultConnection()
```

#### Observe With RxJava:

The reactive APIs come from the `sqlitemagic-rxjava3` adapter for RxJava 3 or the `sqlitemagic-rxjava2` adapter for
RxJava 2, whose version must be the same as the plugin version. Both adapters have the same API. Import `observe` (and
`run` for one-shot queries) from `com.siimkinks.sqlitemagic.rxjava3` or `com.siimkinks.sqlitemagic.rxjava2`; the
examples use RxJava 3. A file that uses both adapters imports one of them with an alias. Observed queries emit on
`Schedulers.io()` unless the connection is configured with another scheduler:

```kotlin
import com.siimkinks.sqlitemagic.rxjava3.scheduleRxQueriesOn

SqliteMagic.builder(this)
  .database(SqliteMagicDatabase())
  .driver(FrameworkSQLiteDriver())
  .scheduleRxQueriesOn(Schedulers.computation())
  .openDefaultConnection()
```

Changes made while an observed query's notification is being processed are combined into one further notification.

#### Observe With Coroutines:

The coroutine APIs come from the `sqlitemagic-coroutines` adapter, whose version must be the same as the plugin version,
in `com.siimkinks.sqlitemagic.coroutines`. `observe()` returns a cold `Flow` of the query that emits again whenever a
queried table changes; changes made while an emission is being processed are combined into one further emission. Its
`runQuery` operators run the query. One-shot work uses suspend functions: `runQueryOnce()` on an observed query,
`Query.run()`, and `await()` on entity operations, UPDATE and DELETE statements and raw SELECTs that do not declare the
tables they read.

```kotlin
import com.siimkinks.sqlitemagic.coroutines.await
import com.siimkinks.sqlitemagic.coroutines.observe
import com.siimkinks.sqlitemagic.coroutines.runQuery

val authors: Flow<List<Author>> = (SELECT FROM AUTHOR)
  .observe()
  .runQuery()

suspend fun save(author: Author): EntityInsertResult = author
  .insert()
  .await()
```

The database work runs on `Dispatchers.IO` unless the connection is configured with another dispatcher:

```kotlin
import com.siimkinks.sqlitemagic.coroutines.dispatchCoroutineQueriesOn

SqliteMagic.builder(this)
  .database(SqliteMagicDatabase())
  .driver(FrameworkSQLiteDriver())
  .dispatchCoroutineQueriesOn(Dispatchers.Default)
  .openDefaultConnection()
```

Applied directly to `observe()`, the `runQuery` operators also wait for changes on that dispatcher, so a change resumes
the collector only to deliver the result. The first change made while the collector processes a result starts the next
query at once, and later changes are combined into one further query, so the earlier of the two results may miss them.

Run suspending work in one transaction with `withTransaction`. It commits when the block and the coroutines it launched
complete, and rolls back when they fail or are cancelled. A cancellation that arrives while the transaction commits
still throws `CancellationException`, although the transaction committed. Every call of the connection inside it takes
part in it, on whatever thread it runs, and one-shot queries read the transaction's own changes:

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

Observers of the changed tables are notified after the commit. Collecting an observed query inside the transaction fails
with `IllegalStateException`. Do not call `runBlocking` inside the block: its coroutine runs outside the transaction,
so its calls wait for the transaction to end and fail after the pool timeout, and given the block's context it can wait
forever for a dispatcher thread that it blocks. Code in the block that cannot suspend uses the blocking calls, such as
`execute()`, which take part in the transaction. A `withTransaction` inside another transaction,
including one that the calling thread started with `newTransaction()`, runs as its nested transaction; that thread
waits for it with `runBlocking`. A long transaction can let waiting writers run with `yieldIfContended()`, which
commits the work so far and continues; it does nothing in a nested transaction. Coroutine calls that wait for the writer
connection suspend instead of blocking a dispatcher thread.

A transaction started with `newTransaction()` or `inTransaction {}` belongs to the thread that started it, so a suspend
call that the dispatcher runs on another thread does not take part in it, and the `inTransaction {}` block cannot
suspend. Reading a query on a thread with such a transaction, whether by collecting it, `runQueryOnce()`, `Query.run()`
or a raw SELECT's `await()`, fails with `IllegalStateException`.

#### Define Database:

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

**Database operation builder methods are generated during compile time by KSP as Kotlin
[extension functions](https://kotlinlang.org/docs/extensions.html) on objects with `@Table` annotation, with bulk
operations in a generated object named after the model (`Authors`). Typed tables and columns for the DSL (`AUTHOR`,
`AUTHOR.FIRST_NAME`) are generated alongside them. The schema follows the Kotlin declarations: columns of non-null
properties are `NOT NULL`, so selected values keep their property types. Generated code is plain Kotlin, so one can
still see human readable code during debugging - just press "step into" when a generated method is encountered.**

#### Do Operations With Objects:

<table style="width:100%; border-collapse: collapse;" >
<tr>
<th>Synchronous</th>
<th>RxJava</th>
</tr>
<tr style="background: none">
<td style="padding:0; margin:0; border:none; width:50%;">

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

// insert -- NOTE: author object also gets
// inserted and the whole operation
// is wrapped in transaction
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

</td>
<td style="padding:0; margin:0; border:none; width:50%;">

```kotlin
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.rxjava3.observe
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

// insert -- NOTE: author object also gets
// inserted and the whole operation is
// wrapped in transaction when result
// object gets subscribed
val insert: Single<EntityInsertResult> = book
  .insert()
  .observe()

// update
val update: Completable = author
  .update()
  .observe()

// update or insert
val persist: Single<EntityPersistResult> = author
  .persist()
  .observe()

// update or insert but ignore null values
val persistNonNull = author
  .persist()
  .ignoreNullValues()
  .observe()

// delete
val delete: Single<Int> = author
  .delete()
  .observe()

// Bulk operations are also supported
val bulkPersist: Completable = Authors
  .persist(someAuthors)
  .ignoreNullValues()
  .observe()
```

</td>
</tr>
</table>

(All database operations notify the active observed queries, RxJava or coroutine `Flow`, that read the modified tables)

#### Use Typesafe Operation Builders:

<table style="width:100%; border-collapse: collapse;" >
<tr>
<th>Synchronous</th>
<th>RxJava</th>
</tr>
<tr style="background: none">
<td style="padding:0; margin:0; border:none; width:50%;">

```kotlin
import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
...

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

</td>
<td style="padding:0; margin:0; border:none; width:50%;">

```kotlin
import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
import com.siimkinks.sqlitemagic.rxjava3.observe
...

val update: Single<Int> = (
    UPDATE
        TABLE BOOK
        SET (BOOK.TITLE to "Foo")
        WHERE (BOOK.ID IS 77L)
    )
  .observe()

val delete: Single<Int> = (
    DELETE
        FROM BOOK
        WHERE ((BOOK.ID IS_NOT 77L)
            AND (BOOK.TITLE IS "Foo"))
    )
  .observe()
```

</td>
</tr>
</table>

#### Query Data:
SqliteMagic ships with its own SQL-first DSL (or Domain Specific Language) that emulates SQL in Kotlin (inspired by
[jOOQ](https://www.jooq.org/)). Clauses follow SQL order, each stage offers only what SQL allows next and every query is
an immutable value that can be reused. Clauses are written infix, as below, or as dot calls:
`SELECT.FROM(AUTHOR).WHERE(AUTHOR.LAST_NAME IS "Bar")`. The whole DSL is imported from `com.siimkinks.sqlitemagic`.

<table style="width:100%; border-collapse: collapse;" >
<tr>
<th>Synchronous</th>
<th>RxJava</th>
</tr>
<tr style="background: none">
<td style="padding:0; margin:0; border:none; width:50%;">

```kotlin
import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
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



```

</td>
<td style="padding:0; margin:0; border:none; width:50%;">

```kotlin
import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
import com.siimkinks.sqlitemagic.rxjava3.ListQueryObservable
import com.siimkinks.sqlitemagic.rxjava3.observe
...

// ListQueryObservable is an Observable of Query
// which offers query-specific convenience
// operators, such as runQuery() that emits
// List<Author> on every change.
val observable: ListQueryObservable<Author> = (
    SELECT
        FROM AUTHOR
        WHERE ((AUTHOR.FIRST_NAME LIKE "Foo%")
            AND (AUTHOR.LAST_NAME IS_NOT "Bar"))
        ORDER_BY AUTHOR.LAST_NAME.DESC()
        LIMIT 10
    )
  .observe()
```

</td>
</tr>
</table>

#### Query Complex Data:

<table style="width:100%; border-collapse: collapse;" >
<tr>
<th>Synchronous</th>
<th>RxJava</th>
</tr>
<tr style="background: none">
<td style="padding:0; margin:0; border:none; width:50%;">

```kotlin
import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
...

// the resulting Book objects also contain
// Author objects
val books: List<Book> = (
    SELECT
        FROM BOOK
        WHERE ((BOOK.TITLE IS "Bar")
            AND (BOOK.AUTHOR IS someAuthor.id))
        ORDER_BY AUTHOR.LAST_NAME.ASC()
        LIMIT 10
    )
  // this tells to query all complex data
  // which is queried in a single
  // SELECT statement
  .queryDeep()
  .execute()
```

</td>
<td style="padding:0; margin:0; border:none; width:50%;">

```kotlin
import com.siimkinks.sqlitemagic.AuthorTable.Companion.AUTHOR
import com.siimkinks.sqlitemagic.BookTable.Companion.BOOK
import com.siimkinks.sqlitemagic.rxjava3.ListQueryObservable
import com.siimkinks.sqlitemagic.rxjava3.observe
...

// the resulting Book objects also contain
// Author objects
val observable: ListQueryObservable<Book> = (
    SELECT
        FROM BOOK
        WHERE ((BOOK.TITLE IS "Bar")
            AND (BOOK.AUTHOR IS someAuthor.id))
        ORDER_BY AUTHOR.LAST_NAME.ASC()
        LIMIT 10
    )
  // this tells to query all complex data
  // which is queried in a single
  // SELECT statement
  .queryDeep()
  .observe()
```

</td>
</tr>
</table>

Without `queryDeep()`, each `Author` holds only its ID, since its other properties are nullable. A related object is
built from its ID alone when all its other properties are nullable or have default values; otherwise it is always
queried in full. Updating or persisting a `Book` also writes its `Author`, so writing back a shallow-queried `Book`
sets the author's names to `NULL` unless it is persisted with `ignoreNullValues()`.

#### Query Columns:

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

// single columns, the first result and counting
val titles: List<String> = (SELECT COLUMN BOOK.TITLE FROM BOOK).execute()
val first: Author? = (SELECT FROM AUTHOR).takeFirst().execute()
val count: Long = (SELECT FROM AUTHOR).count().execute()

// rows read one at a time while the block runs
val firstTitles = (SELECT COLUMN BOOK.TITLE FROM BOOK ORDER_BY BOOK.TITLE.ASC())
  .useRows { titles -> titles.take(10).toList() }

// raw SQL whose columns are known only when it runs
val columnNames = SELECT.RAW("PRAGMA table_info(book)").useRows { rows ->
  buildList {
    while (rows.step()) {
      add(rows.read(1, SqlType.STRING))
    }
  }
}
```

`useRows` holds a database connection until its block returns, and the rows can be read only inside the block.

There is so much more to querying data like joins, subqueries, compound queries, common table expressions (`WITH`),
SQL functions, window functions, views, compiled queries with named parameters, raw SQL, RxJava convenience operators,
etc. **Every part of the DSL is documented in KDoc, and the [sample app](sqlitemagic-sample) shows the pieces working
together.**

#### Migrate Database:

Debug databases migrate automatically when the schema changes. When releasing a new database version, raise
`@Database(version)` and run `./gradlew migrateReleaseDb` (`migrateFreeReleaseDb` with flavors). In application
modules only the selected migration release variant can run it: the first release variant, or the one set with
`sqlitemagic { migrationTesting { releaseVariant = "paidRelease" } }`. It records the release schema in
`db/releases` and generates its migration script into `src/release/assets`; commit both.

To see what the next release would contain, run `./gradlew previewReleaseDbMigration` (or
`preview<Variant>DbMigration`) at any time, without raising the version first. It prints the schema changes since the
latest release, the migration warnings and the migration SQL, and saves them in
`build/sqlitemagic/release/migration-preview` without publishing anything.

Builds also keep the current schema in `db/` and write debug migration scripts into `src/debug/assets` (or the debug
variant's directory, such as `src/freeDebug/assets`). These are local build state, so keep them out of version control:

```gitignore
**/db/latest*.struct
**/db/latest_*.version
**/db/*.changed
**/src/debug/assets/*.sql
**/src/*Debug/assets/*.sql
```

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

Documentation
-----------------

- [Sample app](sqlitemagic-sample) – a todo app with tables, a view, reactive queries and migration tests
- [Using SqliteMagic](.agents/skills/sqlitemagic/SKILL.md) – a guide to every feature of 3.x and how to use it
  idiomatically and efficiently: models, entity operations, the query DSL, raw SQL, views, coroutines and RxJava,
  transactions, migrations and performance. It is also an agent skill: copy the `sqlitemagic` directory into your
  project's skills directory, such as `.claude/skills/`, so a coding agent writes SqliteMagic code the way it is meant
  to be written
- [Upgrading from 2.x or 0.x](.agents/skills/sqlitemagic-upgrade/SKILL.md) – 3.x rewrites the query DSL and runs
  on an androidx `SQLiteDriver`; this guide maps legacy setup and queries to 3.x, lists behavior changes and covers the
  first database release on 3.x. It is also an agent skill: copy the `sqlitemagic-upgrade` directory into your
  project's skills directory, such as `.claude/skills/`, to let a coding agent do the upgrade
- [Wiki](https://github.com/SiimKinks/sqlitemagic/wiki) and [Javadoc](https://siimkinks.github.io/sqlitemagic/javadoc/)
  for 0.x

Updates
------------

All updates can be found in the [CHANGELOG](CHANGELOG.md).

Bugs and Feedback
-----------------

**For bugs, questions and discussions please use the [Github Issues](https://github.com/SiimKinks/sqlitemagic/issues).**

License
--------

    Copyright 2026 Siim Kinks

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

