# Upgrade installed databases

SqliteMagic 3 expects another schema than the one 2.x created, so the first 3.x release of an app ships a database
version with a migration. Skip this file only when no installed database outlives the upgrade (in-memory or
temporary databases, or an app that has never shipped).

## Contents

- [What changes in the schema](#what-changes-in-the-schema)
- [Release the upgrade](#release-the-upgrade)
- [Files on disk](#files-on-disk)
- [Test the upgrade](#test-the-upgrade)

## What changes in the schema

- Columns of non-null properties become `NOT NULL`. The generated migration rebuilds each affected table once, fills
  stored NULLs with the column's default and deletes the rows it cannot fill
  ([behavior](behavior.md#the-schema-follows-kotlin-nullability)).
- View columns are named by `AS` alias or qualified name (`book.title`). Every upgrade drops and recreates the
  persistent views, so they get the new names only with a new database version
  ([behavior](behavior.md#view-columns-have-qualified-names)).
- Tables and columns named after SQL keywords, AUTOINCREMENT counters and added `onDeleteCascade` foreign keys migrate
  correctly; nothing to do.

Raising `@Database(version)` alone is not enough: it recreates the views, but release builds then run no migration
script, so installed tables stay without `NOT NULL` while fresh installs get it. The table rebuild exists only in the
script that `migrate<Variant>Db` generates.

## Release the upgrade

Do this once the code compiles and its tests pass on 3.x. With `migrateDebugDatabase` on (the default), debug
databases keep migrating automatically; a project that turned it off and runs the release scripts in debug builds
needs the published release for debug builds too.

1. Preview without publishing: `./gradlew previewReleaseDbMigration` (`preview<Variant>DbMigration` for other release
   variants). It prints the schema changes since the latest release, the migration warnings and the migration SQL,
   and saves them in `build/sqlitemagic/release/migration-preview`.
2. Review every warning, especially `Migration deletes …`: each names rows or tables the rebuild discards. Resolve
   them in the models first (declare the property nullable where NULL is meant, give the column a
   `@Column(defaultValue)`), then preview again.
3. Raise `@Database(version)` and run `./gradlew migrateReleaseDb` (`migrate<Variant>Db`). It records the release in
   `db/releases` and writes the migration script to `src/release/assets`; commit both. With migration testing
   disabled it publishes under the `@Database` version and fails when that version is already released, so raise the
   version first.
4. Where stored NULLs need another value than the column default, or rows the script deletes must survive, edit the
   generated script: put `UPDATE … SET column = … WHERE column IS NULL` statements before the rebuild statements, or
   replace the `DELETE FROM` statements. Settle each data-dropping statement with the user; the build only warns.
5. Hand-written migration scripts run inside the upgrade's transaction: remove `BEGIN`, `COMMIT`, `END`, `ROLLBACK`,
   `SAVEPOINT` and `RELEASE` lines, which now fail the upgrade.

The `migration-testing` helper's API is unchanged. Data-migration tests that pass `toVersion` move it to the new
version, or omit it to migrate to the current one. JVM migration tests
(`SqliteMagicMigrationTestHelper.validateAllMigrations`) fail with "Current release snapshot tables
or indices differ from independent compiled release export. Advance the database version…" from the moment the code
is on 3.x until step 3 publishes the new release. That failure is expected; do not change the tests to pass it.

## Files on disk

- File databases use WAL, so committed data can sit in a `<name>-wal` file next to the database file, with a
  `<name>-shm` index, until SQLite checkpoints it. Code that copies, exports, backs up or deletes only the database
  file, and backup rules (`full_backup_content`, `data_extraction_rules`) that name only that file, must handle the
  `-wal` and `-shm` files with it. To keep the legacy journal mode instead, set `readerConnections(0)` and choose the
  mode in `configureConnection`; that gives up concurrent reads, so settle it with the user.
- In WAL mode, connections run with `PRAGMA synchronous = NORMAL`: a transaction committed just before a power loss or
  system crash can be rolled back, while the database stays intact. Run `PRAGMA synchronous = FULL` in
  `configureConnection` where every committed transaction must survive.
- A corrupt database throws and keeps its files; legacy deleted the database and created it again.

## Test the upgrade

- Run the JVM migration tests after step 3; they validate every recorded release against the current schema.
- Install the last 2.x build on a device or emulator, create data that exercises NULL-prone columns, views and
  relationships, upgrade to the new build without uninstalling, then read every view and the affected tables. This is
  the path every existing user takes, and nothing else exercises the real installed schema.
- On the bundled driver, also check raw SQL that relies on ICU case folding or Android collations
  ([setup](setup.md#choose-a-driver)).
