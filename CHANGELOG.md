Change Log
==========

Version 3.0.0 _(unreleased)_
---------------------------------

SqliteMagic 3 rewrites the query DSL and runs the database on an androidx `SQLiteDriver`, so applications choose
between the device's SQLite and a SQLite bundled with the app. The
[upgrade guide](.agents/skills/sqlitemagic-upgrade/SKILL.md) maps legacy setup and queries to 3.0, lists the changes
that compile but behave differently and walks through the first database release on 3.0.

* Breaking: Rewrite the query DSL as SQL-first Kotlin, where queries follow SQL clause order and type their columns,
  results and nullability
* Breaking: Without `queryDeep()`, a related object whose other constructor parameters are nullable is built from its
  ID, with `null` for those parameters, instead of being queried in full. Updating or persisting the parent also writes
  the related object, so writing back such a parent sets the related object's other columns to `NULL` unless it is
  persisted with `ignoreNullValues()`
* Breaking: Entity reads pass SQL NULL to a `@DbValueToObject` function that accepts NULL, as column selections do,
  instead of reading null for a nullable property
* Breaking: A view's columns are named by their `AS` alias, otherwise by the field's qualified name such as
  `book.title`, instead of the last segment of the name. Persistent views that 2.0 created keep the old names until a
  schema upgrade rebuilds them, so raise `@Database(version)` with the upgrade to 3.0. Raw SQL over a view quotes the
  dotted names, as in `SELECT "book.title" FROM book_card`
* Breaking: Remove the `Cursor` APIs: `toCursor()`, `CompiledCursorSelect` with its `getFromCurrentPosition`, and the
  raw queries that return or observe a `Cursor`. Read rows lazily with `useRows`
* Breaking: Remove `BooleanTransformer`. `Boolean` is a built-in column type, still stored as INTEGER 0 or 1
* Breaking: Columns of non-null properties are `NOT NULL`. The next generated migration rebuilds the affected tables,
  replacing stored NULLs with the column default or deleting their rows where the column has no default
* Breaking: `onDeleteCascade` applies to `handleRecursively = false` relationships too, which 2.0 silently ignored. The
  column references its parent with `ON DELETE CASCADE` and foreign keys are enforced, so a child must be written
  after its parent. The next generated migration rebuilds the table and deletes the rows that reference missing rows,
  with a warning
* Breaking: Migration scripts run on the database connection inside the upgrade's transaction, so a script line that
  begins or ends a transaction or savepoint, such as `BEGIN`, `COMMIT` or `SAVEPOINT`, fails the upgrade before it
  runs
* Breaking: With migration testing disabled, `migrate<Variant>Db` publishes the release under the `@Database` version,
  as it does with migration testing, instead of one above the latest release, and fails when that version is already
  released
* Breaking: Entity insert and persist throw SQLite's exceptions instead of `OperationFailedException`, and a conflicting
  bulk insert or persist throws instead of returning `false`
* Breaking: Bulk entity writes, entity deletes and the writes of entities with recursively handled relationships no
  longer make a caller's transaction roll back when they fail, are ignored or apply nothing. Bulk inserts, updates and
  persists and the writes of entities with recursively handled relationships run in a savepoint that undoes only their
  own changes, and an entity delete runs without one. A failure is still thrown, so an `inTransaction` block that does
  not catch it rolls back as before
* Breaking: After a statement makes SQLite roll back the whole transaction, such as a `CONFLICT_ROLLBACK` conflict or a
  trigger's `RAISE(ROLLBACK)`, later statements and nested transactions in it throw `IllegalStateException` instead
  of running outside the transaction, each committing on its own
* Breaking: The `inTransaction` block can no longer suspend, which left the transaction on another thread, nor
  `return`, `break` or `continue` out of the enclosing function, which skipped marking the transaction successful and
  rolled it back. `return@inTransaction` ends the block and commits
* Breaking: The database runs on an androidx `SQLiteDriver` set with `driver(...)`, which replaces
  `sqliteFactory(...)`. SupportSQLite is no longer used, so `DbDowngrader.onDowngrade` receives a `SQLiteConnection`
  instead of a `SupportSQLiteDatabase`. `FrameworkSQLiteDriver` from the new `sqlitemagic-framework-driver` artifact
  runs the device's SQLite, and `BundledSQLiteDriver` from `androidx.sqlite:sqlite-bundled` runs a SQLite shipped with
  the app
* Breaking: Errors are the driver's exceptions. The framework driver keeps Android's typed `SQLiteException`
  subclasses; the bundled driver throws `SQLException` with SQLite's result code in the message
* Breaking: File databases use WAL with up to four reader connections, so a read during another thread's
  transaction returns the last committed data instead of waiting for the transaction to end. They run with
  `PRAGMA synchronous = NORMAL`, so a transaction committed just before a power loss or system crash can be rolled
  back, while the database stays intact. Run `PRAGMA synchronous = FULL` in `configureConnection` to keep every
  committed transaction
* Breaking: A statement that waits longer than `connectionPoolTimeout` (30 seconds by default) for a connection held by
  other threads fails with `SQLException` instead of waiting
* Breaking: A corrupt database fails with an exception and keeps its files, instead of being deleted and created
  again
* Breaking: RxJava support moves out of the runtime into the `sqlitemagic-rxjava2` artifact. Import `observe`, `run`,
  `scheduleRxQueriesOn`, `ListQueryObservable`, `SingleItemQueryObservable` and `CountQueryObservable` from
  `com.siimkinks.sqlitemagic.rxjava2`, where `run()` emits the non-null result type
* Breaking: Observed queries combine the changes made while a notification is being processed into one further
  notification, so a burst of writes no longer queues one query run per write. A write made during an emission emits
  after that emission returns
* Breaking: Observing a query on a closed connection completes without emitting instead of running the query, so its
  `Single` results, such as `runQueryOnce()` and `observe()` of a raw SELECT without declared tables, fail with
  `NoSuchElementException`
* Breaking: Observed count queries no longer offer `runQueryOrDefault` and `runQueryOnceOrDefault`, whose default a
  count never used; use `runQuery()` and `runQueryOnce()`
* Breaking: Remove `logWarning`, the one-argument `logError` and `logQueryTime` from `Logger`; query times are no
  longer logged
* New: Common table expressions, window functions, typed `Row2`–`Row9` results and `mapTo` for mapping rows to your
  own classes
* New: `useRows` reads the rows of a query lazily inside a block, as a `Sequence` of typed rows or, for raw SQL whose
  columns are known only when it runs, as `RawRows` read with the types of `readAs` and `columnType` for the storage
  class of each value
* New: A model whose other constructor parameters are nullable can be the target of a `handleRecursively = false`
  relationship, which 2.0 rejected at compile time
* New: Generated migrations warn when they drop tables, columns or rows, and when they fill existing rows of a
  transformed column with the storage type's implicit default (such as `''` or `0`), which the transformer may not
  decode
* New: `preview<Variant>DbMigration` prints the next release's schema changes, migration warnings and migration SQL
  without publishing the release or raising the database version
* New: Transformers can store a value as `Boolean`
* New: `readerConnections`, `connectionPoolTimeout` and `configureConnection` configure the database connections
* New: The `sqlitemagic-rxjava3` artifact provides the RxJava support for RxJava 3, with the same API as
  `sqlitemagic-rxjava2` in `com.siimkinks.sqlitemagic.rxjava3`
* New: The `sqlitemagic-coroutines` artifact provides Kotlin coroutine support in
  `com.siimkinks.sqlitemagic.coroutines`: `observe()` returns a `Flow` with the RxJava adapters' query operators,
  one-shot work uses suspend `runQueryOnce()`, `Query.run()` and `await()`, and `dispatchCoroutineQueriesOn` sets the
  dispatcher
* New: `withTransaction` in `sqlitemagic-coroutines` runs suspending work in one transaction that follows its coroutines
  across threads, with `yieldIfContended()` to let waiting writers run. Coroutine calls suspend while they wait for
  the writer connection
* Fix: Tables and columns named after SQL keywords, such as a property `order` or `group`, are created, written and
  migrated instead of failing with a syntax error
* Fix: Table rebuilds in generated migrations keep the table's AUTOINCREMENT counter, so the IDs of deleted rows are
  not used again
* Fix: A migration that adds `onDeleteCascade` to an existing relationship deletes the rows that reference missing
  rows, as the cascade would have, and warns about it, instead of failing the upgrade with a foreign key error
* Fix: A migration that creates a table referencing a rebuilt table, which another rebuilt table references, keeps its
  foreign key to the rebuilt table instead of to the dropped temporary copy
* Fix: A relationship that references a deleted row reads as an object holding only the stored ID in a deep read, as
  `null` when the property is nullable, and otherwise fails with `SQLException` naming the relationship, instead of
  being read from the missing row's NULL columns
* Fix: A query result larger than one cursor window (about 2 MB) is read from one snapshot, so a write committed during
  the read no longer makes it skip or repeat rows or fail to read a row
* Fix: On the device's SQLite before API 28, a row larger than the cursor window fails with `SQLiteException` instead
  of reading as NULL values
* Fix: Persisting an existing entity whose new relationship gets a generated ID stores that ID instead of failing with
  `OperationFailedException` or storing the relationship's placeholder ID
* Fix: When an `inTransaction` block throws and ending the transaction also fails, the block's exception is thrown with
  the failure to end the transaction suppressed, instead of replacing it
* Fix: An entity write on one thread no longer deadlocks with a transaction on another thread that writes the same
  table
* Fix: On the device's SQLite before API 28, `ROLLBACK TO` a savepoint undoes only the savepoint instead of ending
  the whole transaction
* Fix: When `yieldIfContendedSafely` committed part of a transaction and its final commit fails, observers of the
  tables written before the yield are notified
* Fix: Writes from several threads at once no longer leave observed queries without further updates or make disposing
  them hang, and a write made right after subscribing is no longer missed

Version 2.0.0 _(2026-10-28)_
---------------------------------

SqliteMagic 2 moves code generation from the Java annotation processor and bytecode transform to KSP and supports
Kotlin natively: models can be Kotlin data classes, and the generated code and query DSL are Kotlin. It also adds
embedded values, table and view options, transformed IDs and JVM migration tests.

* Breaking: Generate code with a KSP processor instead of the Java annotation processor and bytecode transform. Apply
  the `com.google.devtools.ksp` plugin next to the SqliteMagic plugin, which adds the processor
* Breaking: Generated code and the query DSL are Kotlin. Java models are still supported when they, their persisted
  members and their external transformer functions are public
* Breaking: Remove AutoValue support, the IntelliJ plugin and the `sqlitemagic-kotlin` artifact, whose extensions are
  part of the main artifact
* Breaking: Remove the `generateMagicMethods`, `useKotlin`, `debugBytecodeProcessor`, `generateLogging` and
  `autoValueAnnotation` plugin options and the `useAccessMethods` property of `@Table` and `@Column`
* Breaking: Raise the minimum Android API from 16 to 24
* Breaking: Artifacts are built with Kotlin 2.4, so consuming modules need a Kotlin compiler that reads its metadata
* Breaking: Pass the generated database to the setup builder with `.database(SqliteMagicDatabase())`
* Breaking: Generated files, including DAOs, are in the `com.siimkinks.sqlitemagic` package
* Breaking: Single-entity `insert()` returns `EntityInsertResult` and `persist()` returns `EntityPersistResult` instead
  of the row ID
* Breaking: Disallow BLOB IDs
* New: IDs can use any other supported type, including transformed types
* New: Embedded values with `@Embedded`
* New: Temporary and `WITHOUT ROWID` tables and temporary views with `TableOption` and `ViewOption`
* New: JVM migration tests for application modules with `SqliteMagicMigrationTestHelper`
* New: `generateSqliteMagicMigrationManifest` task adopts release history recorded by 0.x for migration tests, and
  `cleanSqliteMagicMetadata` task removes regenerable metadata and KSP caches while keeping release history and
  migration scripts
* Add: Support for Android Gradle plugin 9
* Fix: A failing migration script statement fails and rolls back the upgrade, naming the script and line, instead of
  being ignored
* Fix: Empty bulk operations no longer roll back an enclosing transaction
* Fix: Rx query operators stop after delivering an error
* Fix: Query results read correct rows after the cursor window refills

Version 0.25.1 _(2020-04-12)_
---------------------------------

* Fix: Avoid unnecessary recompilation of debug builds

Version 0.25.0 _(2020-04-11)_
---------------------------------

* Add: Support for Android Gradle plugin version >= 4
* Add: Support for Gradle incremental processing
* Breaking: Remove support for defining DB name and version in build config file

Version 0.24.2 _(2019-05-13)_
---------------------------------

* Fix: View validation false positive in Kotlin and AutoValue mixed project

Version 0.24.1 _(2019-01-15)_
---------------------------------

* Fix: Support for older Android Gradle plugin versions

Version 0.24.0 _(2019-01-06)_
---------------------------------

* Breaking: Migrate to AndroidX
* Breaking: Refactor release automatic schema migrations -- now all schema files must be in single dir
* Add: DB migration task only per build type
* Add: Support for compound operators

Version 0.23.1 _(2018-08-18)_
---------------------------------

* Fix: Multithreading bug for compiled select

Version 0.23.0 _(2018-07-31)_
---------------------------------

* New: Add support for release migrations
* New: Add ability to redefine columns in any table
* New: Always rebuild views on migrations
* Fix: Add support for latest AutoValue version

Version 0.22.0 _(2018-05-20)_
---------------------------------

* Breaking: Rename `Select#asColumn` to `Select#toColumn`
* Add: Support for table as subquery

Version 0.21.2 _(2018-04-15)_
---------------------------------

* Add: Support for cross-module object transformers
* Add: Support for views migration

Version 0.21.1 _(2018-04-08)_
---------------------------------

* Fix: Transformation for incremental submodules compilation
* Fix: Automatic dependency resolution

Version 0.21.0 _(2018-03-30)_
---------------------------------

* New: Change defaults to meet the most used setups
* New: Add better support for views in kotlin
* Fix: Multimodule builds when main module has no tables and there are more than one submodule

Version 0.20.0 _(2018-03-11)_
---------------------------------

* Breaking: Change `Query#run` return type from `Observable` to `Maybe`
* New: Add automatic schema migration for tables and indices on submodules

Version 0.19.1 _(2018-03-08)_
---------------------------------

* New: Add support for database downgrading

Version 0.19.0 _(2018-03-03)_
---------------------------------

* New: Add support for indices
* New: Add automatic schema migration for tables and indices on main modules
* Fix: Compilation bug with persist operation by column on complex models

Version 0.18.0 _(2018-01-21)_
---------------------------------

* Breaking: Build on top of the Android architecture components Sqlite support library. This allows swapping out the underlying Sqlite implementation to that of your choosing
* Breaking: Remove `java.util.Date` transformer
* New: Add support for multi module builds where database models are located across modules

Version 0.17.0 _(2018-01-06)_
---------------------------------

* Breaking: Rename `Select#val` to `Select#asColumn` as it was conflicting with kotlin reserved keywords
* New: Add support for raw UPDATE and DELETE statements
* Fix: Generic types parsing for transformers

Version 0.16.1 _(2017-11-28)_
---------------------------------

* Fix: Bugs related to support for object(s) update/persist operations with custom WHERE clause by unique column

Version 0.16.0 _(2017-11-19)_
---------------------------------

* New: Add nullability metadata to column types
    - Now every column has metadata about their nullability which makes the whole API even more type safe
* New: Add support for object(s) update/persist operations with custom WHERE clause by unique column
* New: Add `unaryMinus` method to numeric columns which changes positive values to negative and vice versa
* New: Add `not` method to expressions which negates the expression.

Version 0.15.2 _(2017-10-21)_
---------------------------------

* Fix: Transformation bug in Windows

Version 0.15.0 _(2017-09-30)_
---------------------------------

* New: Add support for Android Gradle plugin 3.0.0

Version 0.14.0 _(2017-09-11)_
---------------------------------

* New: All operations now support constraint conflict resolution
* New: Add support for multiple object transformers in one class
* New: More options to configure gradle plugin
* Fix: Nullable single column queries

Version 0.13.0 _(2017-06-25)_
---------------------------------

* New: Kotlin extensions module - adds useful extension functions and LINQ style SQL DSL
* New: Add ability to create raw queries without providing observed table(s)
* New: Add `DbConnection#clearData` method which clears all data in tables
* Fix: Generate correct code for complex data classes
* Fix: Do not fail bytecode transformation when IOE happens during class file loading

Version 0.12.0 _(2017-06-14)_
---------------------------------

* Better kotlin support (support data classes, generate extension functions instead of magic functions).

#### Breaking changes:

* Port to RxJava 2 (RxJava 1 support coming back in future release).

Version 0.11.0 _(2016-12-18)_
---------------------------------

* Option to create expression from raw string via `Expr.raw()`.
* Option to update complex column by its ID in `UPDATE` statement builder.
* Inner selection and function columns now append less SQL when possible.

#### Breaking changes:

* Return `Completable` instead of `Single<Boolean>` in entity bulk operations and `entity.update().observe()`

Version 0.10.0 _(2016-11-24)_
---------------------------------
_First public release_

* Better API for the [`ORDER BY` clause](https://github.com/SiimKinks/sqlitemagic/wiki/The-ORDER-BY-Clause).
* Unified naming in SQL. _Breaking change:_ in table names camelCase is replaced with underscores "_".
* Better wording in javadoc.
