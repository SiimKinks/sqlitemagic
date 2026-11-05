# Schema changes and migrations

The schema comes from the models; the build generates the migrations. The developer's job is to review what a release
migration does to existing data and to test it.

## Contents

- [How it works](#how-it-works)
- [Changing models safely](#changing-models-safely)
- [Releasing a database version](#releasing-a-database-version)
- [Editing migration scripts](#editing-migration-scripts)
- [Testing migrations](#testing-migrations)
- [Version control](#version-control)

## How it works

- **Debug builds** (`migrateDebugDatabase = true`, the default) record the current schema in `db/` and generate a
  debug migration script into `src/<debug variant>/assets` (`src/debug/assets`, or `src/freeDebug/assets` with
  flavors) whenever the models change, so a debug install upgrades on the next
  launch. Nothing to do by hand.
- **Release builds** run only the scripts of published releases. Raising `@Database(version)` without publishing a
  release runs no script: the new schema reaches fresh installs only, and upgraded installs break.
- On open, the library runs, for every version between the installed and the current one, each submodule's
  `<submodule><version>.sql` and then `<version>.sql` from assets, inside the upgrade's transaction. A failing line
  rolls the whole upgrade back and names the script and line.
- Persistent views are dropped and recreated on every upgrade; temporary tables and views are created on every open.

## Changing models safely

| Change | What the generated migration does | Do instead, when data must survive |
| --- | --- | --- |
| Add a nullable property | `ALTER TABLE … ADD COLUMN` (a rebuild when not added last) | nothing |
| Add a non-null property | `ALTER TABLE … ADD COLUMN` (a rebuild when not added last); existing rows get the SQL default (`@Column(defaultValue)` or the storage type's implicit default, such as `''` or `0`), never the Kotlin default | set `defaultValue` to match the Kotlin default; a transformed type needs one its decoder accepts |
| Add a non-null relationship, `@Unique` or unique-index column | rebuilds the table empty (`DELETE FROM`) | add it nullable, or fill it in the script |
| Make a nullable property non-null | rebuilds the table, NULLs become the SQL default; rows that cannot take a default are deleted | give it `@Column(defaultValue)` (one its decoder accepts for a transformed type), or update the NULLs in the script first |
| Rename a property | drops the old column, adds the new one | keep the column with `@Column("old_name")` |
| Rename a class | renames the table when its columns are unchanged; otherwise drop plus create | keep the name with `@Table("old_name")` |
| Remove a property or class | drops the column (rebuild) or table | confirm the data is no longer needed |
| Change a view query | recreates the view | nothing |
| Add `onDeleteCascade` | adds the foreign key; deletes rows that reference missing rows | fix dangling rows in the script first |

Every step that discards data is printed as a build warning (`Migration deletes …`, dropped columns and tables), in
debug builds by KSP and for releases by the Gradle tasks. So is a step that fills existing rows of a transformed
column with its implicit default (`Migration fills existing rows …`), which its decoder may reject on every read.
Treat each warning as a decision to settle, not noise.

## Releasing a database version

1. Preview: `./gradlew preview<Variant>DbMigration` (for example `previewReleaseDbMigration`) prints the schema
   changes since the last release, the warnings and the SQL, and saves them in
   `build/sqlitemagic/<variant>/migration-preview`. It publishes nothing and needs no version change.
2. Resolve the warnings in the models where possible (nullable, defaults, `@Column("old_name")`), and preview again.
3. Raise `@Database(version)` by one and run `./gradlew migrate<Variant>Db`. It records the release schema in
   `db/releases` and writes `src/<build type>/assets/<version>.sql` (`src/release/assets`, for every flavor).
4. Review the script; add data fixes ([below](#editing-migration-scripts)).
5. Run the migration tests; commit `db/releases` and the script with the model change.

Keep the version as a single constant (for example a `buildConfigField`) when several places need it.

All product flavors share one release history in `db/releases` and one script per version in
`src/<build type>/assets`: keep `@Table` models identical across flavors (declare a flavor-only table in the shared
models and leave it unused elsewhere), and run `migrate<Variant>Db` for the selected migration release variant: the
first release variant, or the one set with `sqlitemagic { migrationTesting { releaseVariant = "paidRelease" } }`.
Other variants' release tasks fail while migration testing is on (the default in application modules).

## Editing migration scripts

- A script is one SQL statement per line; blank lines and `--` comments are allowed.
- The upgrade's transaction wraps every script: lines with `BEGIN`, `COMMIT`, `END` or `ROLLBACK` fail it, while
  `SAVEPOINT`, `RELEASE` and `ROLLBACK TO` work.
- Put data fixes where they apply: `UPDATE … SET column = … WHERE column IS NULL` before the rebuild statements that
  would delete or default those rows, backfills after the column exists.
- Models cannot declare triggers or virtual (full-text) tables, and release scripts run only on upgrades, never on a
  fresh install, where the schema comes from the models alone. The library has no supported place for them.
- Edit only release scripts that have not shipped. A shipped script is history; fix forward in the next version.
- Do not edit `db/` files or debug scripts; they are regenerated.

## Testing migrations

Application modules get JVM migration test support. Add a JVM driver and a test framework to unit tests
(`testImplementation("androidx.sqlite:sqlite-bundled-jvm:<version>")`), then:

```kotlin
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.siimkinks.sqlitemagic.MigrationTestResult
import com.siimkinks.sqlitemagic.SqliteMagicMigrationDatabase
import com.siimkinks.sqlitemagic.SqliteMagicMigrationTestHelper

class DatabaseMigrationTest {
  private val migrations = SqliteMagicMigrationTestHelper(
    descriptor = SqliteMagicMigrationDatabase,
    driver = BundledSQLiteDriver()
  )

  @Test
  fun `all releases migrate to the current schema`() {
    migrations.use(SqliteMagicMigrationTestHelper::validateAllMigrations)
  }

  @Test
  fun `books from version 4 get zero pages`() {
    migrations.use { helper ->
      helper
        .createDatabase(
          name = "library",
          version = 4
        )
        .use { database ->
          database.execute(
            sql = "INSERT INTO author(id, name) VALUES (?, ?)",
            bindArgs = listOf(7L, "Frank Herbert")
          )
          database.execute(
            sql = "INSERT INTO book(id, title, author) VALUES (?, ?, ?)",
            bindArgs = listOf(1L, "Dune", 7L)
          )
        }
      helper
        .runMigrationsAndValidate(
          name = "library",
          fromVersion = 4
        )
        .use { database ->
          assertEquals(
            MigrationTestResult(
              columns = listOf("id", "pages"),
              rows = listOf(listOf(1L, 0L))
            ),
            database.query("SELECT id, pages FROM book")
          )
        }
    }
  }
}
```

- `validateAllMigrations` checks that every recorded release migrates to exactly the current schema. Keep it in every
  app with a released database.
- Data tests seed an old version with raw SQL and assert rows after `runMigrationsAndValidate`; write one for every
  migration that transforms or may delete data.
- Also upgrade a real install on a device or emulator before shipping a migration that rebuilds tables.

## Version control

Commit `db/releases/` and `src/<release build type>/assets/*.sql`. Ignore the regenerated state, including the debug
scripts of flavored variants:

```gitignore
**/db/latest*.struct
**/db/latest_*.version
**/db/*.changed
**/src/debug/assets/*.sql
**/src/*Debug/assets/*.sql
```
