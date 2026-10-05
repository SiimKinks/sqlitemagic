package com.siimkinks.sqlitemagic

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class MergedAssetsMigrationTest {
  @Test
  fun `selected release SQL updates seeded data through every effective asset`() {
    assertThat(SqliteMagicMigrationDatabase.releaseVariant).isEqualTo("paidRelease")
    assertThat(SqliteMagicMigrationDatabase.currentVersion).isEqualTo(9)
    assertThat(BuildConfig.DB_VERSION).isEqualTo(1038)
    SqliteMagicMigrationTestHelper(
      descriptor = SqliteMagicMigrationDatabase,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        helper
          .createDatabase(
            name = "merged-assets",
            version = 1
          )
          .use { database ->
            database.execute(
              sql = "INSERT INTO asset_record(id,value) VALUES (?,?)",
              bindArgs = listOf(1, "seed")
            )
          }
        helper
          .runMigrationsAndValidate(
            name = "merged-assets",
            fromVersion = 1
          )
          .use { database ->
            assertThat(database.query("SELECT id,value FROM asset_record"))
              .isEqualTo(
                MigrationTestResult(
                  columns = listOf("id", "value"),
                  rows = listOf(listOf(1L, "EXPECTED_VALUE"))
                )
              )
          }
      }
  }
}
