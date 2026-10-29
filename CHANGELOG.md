Change Log
==========

Version 2.1.0 _(unreleased)_
---------------------------------

SqliteMagic 2.1 rewrites the query DSL. The [migration guide](.agents/skills/sqlitemagic-dsl-migration/SKILL.md) maps
legacy queries to the new DSL and lists the changes that compile but behave differently.

* Breaking: Rewrite the query DSL as SQL-first Kotlin, where queries follow SQL clause order and type their columns,
  results and nullability
* Breaking: Columns of non-null properties are `NOT NULL`. The next generated migration rebuilds the affected tables,
  replacing stored NULLs with the column default or deleting their rows where the column has no default
* Breaking: Entity insert and persist throw SQLite's exceptions instead of `OperationFailedException`, and a conflicting
  bulk insert or persist throws instead of returning `false`
* Breaking: Without `queryDeep()`, a related object whose other constructor parameters are nullable is built from its
  ID, with `null` for those parameters, instead of being queried in full. Such objects can also be referenced with
  `handleRecursively = false`
* Breaking: With migration testing disabled, `migrate<Variant>Db` publishes the release under the `@Database` version,
  as it does with migration testing, instead of one above the latest release, and fails when that version is already
  released
* New: Common table expressions, window functions, typed `Row2`–`Row9` results and `mapTo` for mapping rows to own
  classes
* New: Generated migrations warn when they drop tables, columns or rows
* New: `preview<Variant>DbMigration` prints the next release's schema changes, migration warnings and migration SQL
  without publishing the release or raising the database version

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
