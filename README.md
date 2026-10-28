SqliteMagic
=======

Simple yet powerful SQLite database layer for Android that makes database handling feel like magic.

#### Overview:
* Simple, intuitive & **typesafe API**
* SQL-first [Kotlin](https://kotlinlang.org/) DSL – queries read like SQL while the compiler checks clause order,
  column types and nullability
* Minimal setup needed
* Built in RxJava support with reactive stream semantics on queries and operations
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

SqliteMagic 2.x is built for Kotlin Android projects that use KSP. It supports Android API 24 and newer.

#### Publish SqliteMagic:

SqliteMagic 2.x is not published to a public repository. Build it from source and install it into your local Maven
repository:

```shell
./gradlew publishToMavenLocal
```

This publishes the Gradle plugin, runtime, annotations, KSP processor and migration testing artifacts as
`com.siimkinks.sqlitemagic:*:<latest version>`. To share builds through a self-hosted Maven repository such as
Artifactory or Nexus, add it to `publishing.repositories` in
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
  implementation("androidx.sqlite:sqlite-framework:<sqlite version>")
  // needed for the reactive observe() APIs
  implementation("io.reactivex.rxjava2:rxjava:<rxjava version>")
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
@Database(
  name = "library.db",
  version = 1
)
class App : Application() {
  override fun onCreate() {
    super.onCreate()
    SqliteMagic.builder(this)
      .database(SqliteMagicDatabase())
      .sqliteFactory(FrameworkSQLiteOpenHelperFactory())
      .openDefaultConnection()
  }
}
```
**Note**: `SqliteMagicDatabase` is generated. Any place with a reference to Application context is ok to use for
initialization, but it must happen before a database is accessed. Initialization only configures the default
connection. The database is opened on first access, which creates the schema or runs migration scripts as needed - no
other hidden runtime performance costs.

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

(All database operations trigger RxJava notifications on active queries that listen to table that is being modified)

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
```

There is so much more to querying data like joins, subqueries, compound queries, common table expressions (`WITH`),
SQL functions, window functions, views, compiled queries with named parameters, raw SQL, RxJava convenience operators,
etc. **Every part of the DSL is documented in KDoc, and the [sample app](sqlitemagic-sample) shows the pieces working
together.**

#### Migrate Database:

Debug databases migrate automatically when the schema changes. When releasing a new database version, run
`./gradlew migrateReleaseDb` (or `migrate<Variant>Db` for other release variants). It records the release schema in
`db/releases` and generates its migration script into `src/release/assets`; commit both. Builds also keep the current
schema in `db/` and write debug migration scripts into `src/debug/assets`. These are local build state, so keep them
out of version control:

```gitignore
**/db/latest*.struct
**/db/latest_*.version
**/db/*.changed
**/src/debug/assets/*.sql
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
- [Migrating from 0.x](.agents/skills/sqlitemagic-dsl-migration/SKILL.md) – 2.x moves code generation to KSP and
  rewrites the DSL; this guide maps legacy queries to the new DSL and lists behavior changes
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

