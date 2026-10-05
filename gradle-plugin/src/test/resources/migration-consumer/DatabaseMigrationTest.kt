package com.siimkinks.sqlitemagic

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.migration.MigrationDatabaseDescriptor
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream

/** The integration harness copies this unchanged test into successive genuine release fixtures. */
internal class DatabaseMigrationTest {
  @Test
  fun `retained releases migrate to the selected compiled release`() {
    SqliteMagicMigrationTestHelper(
      descriptor = SqliteMagicMigrationDatabase,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        val report = helper.validateAllMigrations()
        val expected = (1 until SqliteMagicMigrationDatabase.currentVersion).map { version ->
          MigrationValidationPath(
            fromVersion = version,
            toVersion = SqliteMagicMigrationDatabase.currentVersion
          )
        }
        assertThat(report.paths)
          .containsExactlyElementsIn(expected)
        assertThat(report.releaseVariant)
          .isEqualTo("release")
        assertThat(report.currentVersion)
          .isNotEqualTo(BuildConfig.DB_VERSION)
        assertThat(SqliteMagicDatabase().dbVersion)
          .isGreaterThan(report.currentVersion)
        println("Migration coverage: ${report.paths}; SQLite ${report.sqliteVersion}")
      }
  }

  @Test
  fun `version two derives name lengths and preserves bound data through reopen`() {
    SqliteMagicMigrationTestHelper(
      descriptor = SqliteMagicMigrationDatabase,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        helper
          .createDatabase(
            name = "names",
            version = 1
          )
          .use { database ->
            database.execute(
              sql = "INSERT INTO migration_person(id,name) VALUES (?,?)",
              bindArgs = listOf(7, "O'Brien")
            )
          }
        helper
          .runMigrationsAndValidate(
            name = "names",
            fromVersion = 1,
            toVersion = 2
          )
          .use { database ->
            assertThat(database.query("SELECT id,name,name_length FROM migration_person"))
              .isEqualTo(
                MigrationTestResult(
                  columns = listOf("id", "name", "name_length"),
                  rows = listOf(listOf(7L, "O'BRIEN", 7L))
                )
              )
          }
        helper
          .runMigrationsAndValidate(
            name = "names",
            fromVersion = 2,
            toVersion = 2
          )
          .use { database ->
            assertThat(database.query("PRAGMA foreign_keys"))
              .isEqualTo(
                MigrationTestResult(
                  columns = listOf("foreign_keys"),
                  rows = listOf(listOf(1L))
                )
              )
            assertThat(database.query("SELECT id,name,name_length FROM migration_person"))
              .isEqualTo(
                MigrationTestResult(
                  columns = listOf("id", "name", "name_length"),
                  rows = listOf(listOf(7L, "O'BRIEN", 7L))
                )
              )
          }
        if (SqliteMagicMigrationDatabase.currentVersion >= 3) {
          helper
            .runMigrationsAndValidate(
              name = "names",
              fromVersion = 2
            )
            .use { database ->
              assertThat(database.query("SELECT id,name,name_length,slug FROM migration_person"))
                .isEqualTo(
                  MigrationTestResult(
                    columns = listOf("id", "name", "name_length", "slug"),
                    rows = listOf(listOf(7L, "O'BRIEN", 7L, "o'brien"))
                  )
                )
              assertThat(database.query("PRAGMA user_version"))
                .isEqualTo(
                  MigrationTestResult(
                    columns = listOf("user_version"),
                    rows = listOf(listOf(3L))
                  )
                )
            }
        }
      }
  }

  @Test
  fun `later malformed SQL rolls back the entire upgrade range`() {
    if (SqliteMagicMigrationDatabase.currentVersion < 3) return
    val resourceName = thirdReleaseSqlResource()
    val descriptor = object : MigrationDatabaseDescriptor by SqliteMagicMigrationDatabase {
      override fun openResource(name: String): InputStream {
        val original = SqliteMagicMigrationDatabase.openResource(name)
        return when (name) {
          resourceName -> original.use { stream ->
            ByteArrayInputStream(stream.readBytes() + "\nBROKEN SQL\n".toByteArray())
          }
          else -> original
        }
      }
    }
    SqliteMagicMigrationTestHelper(
      descriptor = descriptor,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        helper
          .createDatabase(
            name = "rollback",
            version = 1
          )
          .use { database ->
            database.execute(
              sql = "INSERT INTO migration_person(id,name) VALUES (?,?)",
              bindArgs = listOf(1, "Original")
            )
          }
        val failure = assertThrows<IllegalStateException> {
          helper.runMigrationsAndValidate(
            name = "rollback",
            fromVersion = 1
          )
        }
        assertThat(failure.message)
          .contains(resourceName)
        assertThat(failure.message)
          .contains("line")
        assertThat(failure.message)
          .contains("statement")
        helper
          .runMigrationsAndValidate(
            name = "rollback",
            fromVersion = 1,
            toVersion = 1
          )
          .use { database ->
            assertThat(database.query("SELECT id,name FROM migration_person"))
              .isEqualTo(
                MigrationTestResult(
                  columns = listOf("id", "name"),
                  rows = listOf(listOf(1L, "Original"))
                )
              )
            assertThat(database.query("PRAGMA user_version"))
              .isEqualTo(
                MigrationTestResult(
                  columns = listOf("user_version"),
                  rows = listOf(listOf(1L))
                )
              )
          }
      }
  }

  @Test
  fun `missing manifest listed resource reports its release and resource context`() {
    if (SqliteMagicMigrationDatabase.currentVersion < 3) return
    val resourceName = thirdReleaseSqlResource()
    val descriptor = object : MigrationDatabaseDescriptor by SqliteMagicMigrationDatabase {
      override fun openResource(name: String): InputStream = when (name) {
        resourceName -> throw FileNotFoundException("Fixture resource $name unavailable")
        else -> SqliteMagicMigrationDatabase.openResource(name)
      }
    }
    val failure = assertThrows<IllegalStateException> {
      SqliteMagicMigrationTestHelper(
        descriptor = descriptor,
        driver = BundledSQLiteDriver()
      )
    }
    assertThat(failure.message)
      .contains(resourceName)
    assertThat(failure.message)
      .contains("step 3")
  }

  private fun thirdReleaseSqlResource() = SqliteMagicMigrationDatabase
    .openResource(SqliteMagicMigrationDatabase.manifestResource)
    .bufferedReader()
    .use { reader ->
      MigrationMetadataJson.readManifest(reader.readText())
        .steps
        .single { it.toVersion == 3 }
        .sqlResources
        .last()
        .name
    }
}
