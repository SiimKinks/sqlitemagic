package com.siimkinks.sqlitemagic

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

internal class DatabaseMigrationTest {
  @Test
  fun `all retained releases migrate to the compiled selected release`() {
    migrationHelper().use { helper ->
      val report = helper.validateAllMigrations()
      val descriptor = SqliteMagicMigrationDatabase
      val baseline = descriptor.firstVersion ?: 1
      val expected = MigrationValidationReport(
        databaseId = descriptor.databaseId,
        releaseVariant = descriptor.releaseVariant,
        baselineVersion = baseline,
        currentVersion = descriptor.currentVersion,
        excludedVersions = emptyList(),
        paths = (baseline until descriptor.currentVersion).map { version ->
          MigrationValidationPath(
            fromVersion = version,
            toVersion = descriptor.currentVersion
          )
        },
        mode = MigrationValidationMode.EVERY_VERSION_TO_CURRENT,
        driverImplementation = BundledSQLiteDriver::class.java.name,
        sqliteVersion = report.sqliteVersion
      )
      assertThat(report)
        .isEqualTo(expected)
      println("Selected-release migration coverage: $report")
    }
  }

  @Test
  fun `version two uppercases names derives lengths and preserves identities through reopen`() {
    listOf(
      Triple(
        first = "apostrophe",
        second = "O'Brien",
        third = "O'BRIEN"
      ),
      Triple(
        first = "ordinary",
        second = "Alice",
        third = "ALICE"
      ),
      Triple(
        first = "spaces",
        second = "Mary Jane",
        third = "MARY JANE"
      )
    ).forEach { (label, original, uppercase) ->
      migrationHelper().use { helper ->
        helper
          .createDatabase(
            name = label,
            version = 1
          )
          .use { database ->
            database.execute(
              sql = "INSERT INTO migration_person(id,name) VALUES (?,?)",
              bindArgs = listOf(7L, original)
            )
          }
        val expected = MigrationTestResult(
          columns = listOf("id", "name", "name_length"),
          rows = listOf(listOf(7L, uppercase, original.length.toLong()))
        )
        helper
          .runMigrationsAndValidate(
            name = label,
            fromVersion = 1,
            toVersion = 2
          )
          .use { database ->
            assertWithMessage("$label after 1 -> 2")
              .that(database.query("SELECT id,name,name_length FROM migration_person"))
              .isEqualTo(expected)
          }
        helper
          .runMigrationsAndValidate(
            name = label,
            fromVersion = 2,
            toVersion = 2
          )
          .use { database ->
            assertWithMessage("$label after reopening release 2")
              .that(database.query("SELECT id,name,name_length FROM migration_person"))
              .isEqualTo(expected)
          }
      }
    }
  }

  @Test
  fun `version three derives lowercase slugs while preserving existing names lengths and identities`() {
    listOf(
      Triple(
        first = "apostrophe",
        second = "O'BRIEN",
        third = "o'brien"
      ),
      Triple(
        first = "ordinary",
        second = "ALICE",
        third = "alice"
      ),
      Triple(
        first = "spaces",
        second = "MARY JANE",
        third = "mary jane"
      )
    ).forEach { (label, original, slug) ->
      migrationHelper().use { helper ->
        helper
          .createDatabase(
            name = label,
            version = 2
          )
          .use { database ->
            database.execute(
              sql = "INSERT INTO migration_person(id,name,name_length) VALUES (?,?,?)",
              bindArgs = listOf(7L, original, original.length.toLong())
            )
          }
        helper
          .runMigrationsAndValidate(
            name = label,
            fromVersion = 2,
            toVersion = 3
          )
          .use { database ->
            val expected = MigrationTestResult(
              columns = listOf("id", "name", "name_length", "slug"),
              rows = listOf(listOf(7L, original, original.length.toLong(), slug))
            )
            assertWithMessage("$label after 2 -> 3")
              .that(database.query("SELECT id,name,name_length,slug FROM migration_person"))
              .isEqualTo(expected)
          }
      }
    }
  }

  @Test
  fun `account entries retain their values and declared summaries before an owner deletion cascades`() {
    migrationHelper().use { helper ->
      helper
        .createDatabase(
          name = "accounts",
          version = 4
        )
        .use { database ->
          database.execute(
            sql = "INSERT INTO migration_person(id,name,name_length,slug) VALUES (?,?,?,?)",
            bindArgs = listOf(7L, "O'BRIEN", 7L, "o'brien")
          )
          database.execute(
            sql = "INSERT INTO migration_person(id,name,name_length,slug) VALUES (?,?,?,?)",
            bindArgs = listOf(8L, "UNPUBLISHED", 11L, null)
          )
          listOf("alpha" to "Personal", "bravo" to "Business").forEach { (id, label) ->
            database.execute(
              sql = "INSERT INTO migration_account(id,label) VALUES (?,?)",
              bindArgs = listOf(id, label)
            )
          }
          listOf(
            listOf("alpha", "invoice", 7L, 12L, "obsolete personal memo"),
            listOf("bravo", "invoice", 7L, 25L, null)
          ).forEach { values ->
            database.execute(
              sql = "INSERT INTO migration_entry(account,reference,person,amount,memo) VALUES (?,?,?,?,?)",
              bindArgs = values
            )
          }
        }
      helper
        .runMigrationsAndValidate(
          name = "accounts",
          fromVersion = 4,
          toVersion = 6
        )
        .use { database ->
          val expectedEntries = MigrationTestResult(
            columns = listOf("id", "account", "reference", "person", "amount", "status"),
            rows = listOf(
              listOf(1L, "alpha", "invoice", 7L, 12.0, "posted"),
              listOf(2L, "bravo", "invoice", 7L, 25.0, "posted")
            )
          )
          assertThat(
            database.query("SELECT id,account,reference,person,amount,status FROM migration_entry ORDER BY id")
          )
            .isEqualTo(expectedEntries)
          assertThat(database.query("SELECT id,name,name_length,slug FROM migration_person_summary ORDER BY id"))
            .isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "name", "name_length", "slug"),
                rows = listOf(listOf(7L, "O'BRIEN", 7L, "o'brien"))
              )
            )
          assertThat(
            database.query("SELECT id,amount,label,status,reference FROM migration_entry_summary ORDER BY id")
          )
            .isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "amount", "label", "status", "reference"),
                rows = listOf(
                  listOf(1L, 12.0, "Personal", "posted", "invoice"),
                  listOf(2L, 25.0, "Business", "posted", "invoice")
                )
              )
            )
          database.execute(
            sql = "INSERT INTO migration_entry(id,account,reference,person) VALUES (?,?,?,?)",
            bindArgs = listOf(3L, "bravo", "new", null)
          )
          database.execute(
            sql = "DELETE FROM migration_account WHERE id = ?",
            bindArgs = listOf("alpha")
          )
          assertThat(
            database.query("SELECT id,account,reference,person,amount,status FROM migration_entry ORDER BY id")
          )
            .isEqualTo(
              MigrationTestResult(
                columns = expectedEntries.columns,
                rows = listOf(
                  listOf(2L, "bravo", "invoice", 7L, 25.0, "posted"),
                  listOf(3L, "bravo", "new", null, 0.0, "posted")
                )
              )
            )
          assertThat(database.query("SELECT id,label FROM migration_account ORDER BY id"))
            .isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "label"),
                rows = listOf(listOf("bravo", "Business"))
              )
            )
        }
    }
  }

  @Test
  fun `unique labels slugs and account references retain their business keys after migration`() {
    migrationHelper().use { helper ->
      helper
        .createDatabase(
          name = "business-keys",
          version = 4
        )
        .use { database ->
          database.execute(
            sql = "INSERT INTO migration_account(id,label) VALUES (?,?)",
            bindArgs = listOf("alpha", "Personal")
          )
          database.execute(
            sql = "INSERT INTO migration_person(id,name,name_length,slug) VALUES (?,?,?,?)",
            bindArgs = listOf(7L, "ALICE", 5L, "alice")
          )
          database.execute(
            sql = "INSERT INTO migration_entry(id,account,reference,person,amount) VALUES (?,?,?,?,?)",
            bindArgs = listOf(11L, "alpha", "invoice", 7L, 10L)
          )
        }
      helper
        .runMigrationsAndValidate(
          name = "business-keys",
          fromVersion = 4,
          toVersion = 6
        )
        .use { database ->
          listOf("ignored" to "Personal", "bravo" to "Business").forEach { (id, label) ->
            database.execute(
              sql = "INSERT OR IGNORE INTO migration_account(id,label) VALUES (?,?)",
              bindArgs = listOf(id, label)
            )
          }
          listOf(
            listOf(12L, "alpha", "invoice", 99.0),
            listOf(13L, "bravo", "invoice", 20.0)
          ).forEach { values ->
            database.execute(
              sql = "INSERT OR IGNORE INTO migration_entry(id,account,reference,amount) VALUES (?,?,?,?)",
              bindArgs = values
            )
          }
          listOf(
            listOf(8L, "DUPLICATE", "alice"),
            listOf(9L, "DRAFT ONE", null),
            listOf(10L, "DRAFT TWO", null)
          ).forEach { values ->
            database.execute(
              sql = "INSERT OR IGNORE INTO migration_person(id,name,slug) VALUES (?,?,?)",
              bindArgs = values
            )
          }
          assertThat(database.query("SELECT id,label FROM migration_account ORDER BY id"))
            .isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "label"),
                rows = listOf(listOf("alpha", "Personal"), listOf("bravo", "Business"))
              )
            )
          assertThat(database.query("SELECT id,account,reference,amount,status FROM migration_entry ORDER BY id"))
            .isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "account", "reference", "amount", "status"),
                rows = listOf(
                  listOf(11L, "alpha", "invoice", 10.0, "posted"),
                  listOf(13L, "bravo", "invoice", 20.0, "posted")
                )
              )
            )
          assertThat(database.query("SELECT id,name,name_length,slug FROM migration_person ORDER BY id"))
            .isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "name", "name_length", "slug"),
                rows = listOf(
                  listOf(7L, "ALICE", 5L, "alice"),
                  listOf(9L, "DRAFT ONE", null, null),
                  listOf(10L, "DRAFT TWO", null, null)
                )
              )
            )
        }
    }
  }

  @Test
  fun `profile storage preserves transformed text real values blobs and nullable embedded contacts`() {
    listOf(
      Triple(
        first = "contact present",
        second = "Tallinn",
        third = "+372 555"
      ),
      Triple(
        first = "contact absent",
        second = null,
        third = null
      )
    ).forEach { (label, city, phone) ->
      migrationHelper().use { helper ->
        val avatar = byteArrayOf(0, 1, 127, -1)
        helper
          .createDatabase(
            name = label,
            version = 4
          )
          .use { database ->
            database.execute(
              sql = "INSERT INTO migration_profile(id,contact_city,contact_phone,email,rating,avatar) " +
                  "VALUES (?,?,?,?,?,?)",
              bindArgs = listOf(7L, city, phone, "o'brien@example.com", 2.5, avatar)
            )
          }
        helper
          .runMigrationsAndValidate(
            name = label,
            fromVersion = 4,
            toVersion = 6
          )
          .use { database ->
            val expected = MigrationTestResult(
              columns = listOf(
                "id", "contact_city", "contact_phone", "contact_postal_code", "email", "rating", "avatar"
              ),
              rows = listOf(listOf(7L, city, phone, null, "o'brien@example.com", 2.5, avatar))
            )
            assertWithMessage(label)
              .that(
                database.query(
                  "SELECT id,contact_city,contact_phone,contact_postal_code,email,rating,avatar " +
                      "FROM migration_profile"
                )
              )
              .isEqualTo(expected)
          }
      }
    }
  }

  @Test
  fun `renamed preferences and submodule regions retain values and receive their declared defaults`() {
    migrationHelper().use { helper ->
      helper
        .createDatabase(
          name = "settings-and-regions",
          version = 4
        )
        .use { database ->
          database.execute(
            sql = "INSERT INTO migration_preference(key,value) VALUES (?,?)",
            bindArgs = listOf("theme", "dark")
          )
          database.execute(
            sql = "INSERT INTO migration_region(id,label) VALUES (?,?)",
            bindArgs = listOf("eu", "Europe")
          )
        }
      helper
        .runMigrationsAndValidate(
          name = "settings-and-regions",
          fromVersion = 4,
          toVersion = 6
        )
        .use { database ->
          assertThat(database.query("SELECT key,value FROM migration_setting ORDER BY key"))
            .isEqualTo(
              MigrationTestResult(
                columns = listOf("key", "value"),
                rows = listOf(listOf("theme", "dark"))
              )
            )
          assertThat(database.query("SELECT id,label,country FROM migration_region ORDER BY id"))
            .isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "label", "country"),
                rows = listOf(listOf("eu", "Europe", "global"))
              )
            )
        }
    }
  }

  private fun migrationHelper() = SqliteMagicMigrationTestHelper(
    descriptor = SqliteMagicMigrationDatabase,
    driver = BundledSQLiteDriver()
  )
}
