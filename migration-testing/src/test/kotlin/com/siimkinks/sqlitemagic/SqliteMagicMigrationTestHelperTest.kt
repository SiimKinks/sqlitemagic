package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.migration.MigrationConfiguration
import com.siimkinks.sqlitemagic.migration.MigrationDatabaseDescriptor
import com.siimkinks.sqlitemagic.migration.MigrationManifest
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationResource
import com.siimkinks.sqlitemagic.migration.MigrationStep
import com.siimkinks.sqlitemagic.migration.MigrationViewSchema
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaResource
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

class SqliteMagicMigrationTestHelperTest {
  @Test
  fun `automatic coverage discovers history and records driver evidence`() {
    val descriptor = history()
    SqliteMagicMigrationTestHelper(
      descriptor = descriptor,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        val report = helper.validateAllMigrations()
        assertThat(report)
          .isEqualTo(
            MigrationValidationReport(
              databaseId = "example",
              releaseVariant = "release",
              baselineVersion = 1,
              currentVersion = 3,
              excludedVersions = emptyList(),
              paths = listOf(
                MigrationValidationPath(
                  fromVersion = 1,
                  toVersion = 3
                ),
                MigrationValidationPath(
                  fromVersion = 2,
                  toVersion = 3
                )
              ),
              mode = MigrationValidationMode.EVERY_VERSION_TO_CURRENT,
              driverImplementation = BundledSQLiteDriver::class.java.name,
              sqliteVersion = report.sqliteVersion
            )
          )
        assertThat(report.sqliteVersion).matches("\\d+\\.\\d+\\.\\d+")
        assertThat(report.pathCount).isEqualTo(2)
        assertThat(descriptor.opened).doesNotContain("2.views")
        assertThat(descriptor.opened).doesNotContain("3.views")
      }
  }

  @Test
  fun `explicit baselines exclude unavailable inputs and zero paths still verify target`() {
    val cases = listOf(2, 3)
    cases.forEach { baseline ->
      val descriptor = history(firstVersion = baseline)
      descriptor.resources["schemas/1.struct"] = "broken".toByteArray()
      descriptor.resources.remove("2.sql")
      if (baseline == 3) {
        descriptor.resources.remove("schemas/2.struct")
        descriptor.resources.remove("3.sql")
      }
      SqliteMagicMigrationTestHelper(
        descriptor = descriptor,
        driver = BundledSQLiteDriver()
      )
        .use { helper ->
          val report = helper.validateAllMigrations()
          assertWithMessage("baseline $baseline")
            .that(report.paths)
            .hasSize(3 - baseline)
          assertThat(report.excludedVersions).containsExactlyElementsIn(1 until baseline)
          assertThat(descriptor.opened).doesNotContain("schemas/1.struct")
          assertThat(descriptor.opened).doesNotContain("2.sql")
        }
    }
  }

  @Test
  fun `zero paths fail stale current snapshot consistency`() {
    val descriptor = history(firstVersion = 3)
    descriptor.resources["schemas/3.struct"] = descriptor.text("schemas/3.struct")
      .replace(
        oldValue = ", name_length INTEGER, slug TEXT",
        newValue = ""
      )
      .toByteArray()
    SqliteMagicMigrationTestHelper(
      descriptor = descriptor,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        val failure = assertThrows(IllegalStateException::class.java) { helper.validateAllMigrations() }
        assertThat(failure.message).contains("snapshot")
      }
  }

  @Test
  fun `current snapshot requires exact compiled release module order`() {
    val descriptor = history(firstVersion = 3)
    val manifest = MigrationMetadataJson.readManifest(descriptor.text("manifest.json"))
      .copy(modules = listOf("feature", "example"))
    val reference = manifest.schema(3).copy(modules = listOf("feature", "example"))
    val updatedManifest = manifest.copy(schemas = manifest.schemas.dropLast(1) + reference)
    descriptor.resources["manifest.json"] = MigrationMetadataJson.writeManifest(updatedManifest).toByteArray()
    SqliteMagicMigrationTestHelper(
      descriptor = descriptor,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        val failure = assertThrows(IllegalStateException::class.java) { helper.validateAllMigrations() }
        assertThat(failure.message).contains("module order")
      }
  }

  @Test
  fun `historical targets discover optional SQL from modules removed by the current release`() {
    val descriptor = history()
    val manifest = MigrationMetadataJson.readManifest(descriptor.text("manifest.json"))
    val updated = manifest.copy(
      modules = listOf("Feature", "example"),
      schemas = manifest.schemas.map { reference ->
        reference.copy(
          modules = when (reference.version) {
            3 -> listOf("example")
            else -> listOf("Feature", "example")
          }
        )
      },
      steps = manifest.steps.map { step ->
        when (step.toVersion) {
          2 -> step.copy(sqlResources = emptyList())
          else -> step
        }
      }
    )
    descriptor.resources["manifest.json"] = MigrationMetadataJson.writeManifest(updated).toByteArray()
    descriptor.resources["Feature2.sql"] = "UPDATE person SET name='historical feature'\n".toByteArray()
    SqliteMagicMigrationTestHelper(
      descriptor = descriptor,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        listOf("historical", "current").forEach { name ->
          helper.createDatabase(
            name = name,
            version = 1
          )
            .use { database -> database.execute("INSERT INTO person VALUES(1,'original')") }
        }
        helper.runMigrationsAndValidate(
          name = "historical",
          fromVersion = 1,
          toVersion = 2
        )
          .use { database ->
            assertThat(database.query("SELECT name FROM person")).isEqualTo(
              MigrationTestResult(
                columns = listOf("name"),
                rows = listOf(listOf("HISTORICAL FEATURE"))
              )
            )
          }
        helper.runMigrationsAndValidate(
          name = "current",
          fromVersion = 1
        )
          .use { database ->
            assertThat(database.query("SELECT name FROM person")).isEqualTo(
              MigrationTestResult(
                columns = listOf("name"),
                rows = listOf(listOf("ORIGINAL"))
              )
            )
          }
      }
  }

  @Test
  fun `blob result values compare by content after statement closure`() {
    SqliteMagicMigrationTestHelper(
      descriptor = history(),
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        helper.createDatabase(
          name = "blob",
          version = 1
        )
          .use { database ->
            database.execute(
              sql = "INSERT INTO person(id,name) VALUES (?,?)",
              bindArgs = listOf(1, byteArrayOf(1, 2, 3))
            )
            val result = database.query("SELECT id,name FROM person")
            val expected = MigrationTestResult(
              columns = listOf("id", "name"),
              rows = listOf(listOf(1L, byteArrayOf(1, 2, 3)))
            )
            assertThat(result).isEqualTo(expected)
            assertThat(result.hashCode()).isEqualTo(expected.hashCode())
          }
      }
  }

  @Test
  fun `alternate modes retain full validation for each reported path`() {
    val cases = mapOf(
      MigrationValidationMode.ADJACENT to listOf(
        MigrationValidationPath(
          fromVersion = 1,
          toVersion = 2
        ),
        MigrationValidationPath(
          fromVersion = 2,
          toVersion = 3
        )
      ),
      MigrationValidationMode.OLDEST_TO_CURRENT to listOf(
        MigrationValidationPath(
          fromVersion = 1,
          toVersion = 3
        )
      )
    )
    cases.forEach { (mode, expected) ->
      SqliteMagicMigrationTestHelper(
        descriptor = history(),
        driver = BundledSQLiteDriver()
      )
        .use { helper ->
          assertWithMessage(mode.name).that(helper.validateAllMigrations(mode).paths).isEqualTo(expected)
        }
    }
  }

  @Test
  fun `focused bound data survives close reopen and current target creates compiled views`() {
    SqliteMagicMigrationTestHelper(
      descriptor = history(),
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        helper.createDatabase(
          name = "names",
          version = 1
        )
          .use { database ->
            database.execute(
              sql = "INSERT INTO person(id, name) VALUES (?, ?)",
              bindArgs = listOf(7, "O'Brien")
            )
          }
        helper.runMigrationsAndValidate(
          name = "names",
          fromVersion = 1,
          toVersion = 2
        )
          .use { database ->
            assertThat(database.query("SELECT id, name, name_length FROM person")).isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "name", "name_length"),
                rows = listOf(listOf(7L, "O'BRIEN", 7L))
              )
            )
            assertThrows(IllegalStateException::class.java) {
              helper.runMigrationsAndValidate(
                name = "names",
                fromVersion = 2
              )
            }
          }
        helper.runMigrationsAndValidate(
          name = "names",
          fromVersion = 2
        )
          .use { database ->
            assertThat(database.query("SELECT id, name, name_length, slug FROM person_view")).isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "name", "name_length", "slug"),
                rows = listOf(listOf(7L, "O'BRIEN", 7L, "o'brien"))
              )
            )
          }
      }
  }

  @Test
  fun `later SQL failure rolls back schema data and user version`() {
    val descriptor = history()
    descriptor.resources["3.sql"] = "\n-- skipped\nALTER TABLE person ADD COLUMN slug TEXT\nBROKEN SQL\n".toByteArray()
    SqliteMagicMigrationTestHelper(
      descriptor = descriptor,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        helper.createDatabase(
          name = "rollback",
          version = 1
        )
          .use { database ->
            database.execute(
              sql = "INSERT INTO person(id,name) VALUES (?,?)",
              bindArgs = listOf(1, "Original")
            )
          }
        val failure = assertThrows(IllegalStateException::class.java) {
          helper.runMigrationsAndValidate(
            name = "rollback",
            fromVersion = 1
          )
        }
        assertThat(failure.message).contains("3.sql")
        assertThat(failure.message).contains("line 4")
        helper.runMigrationsAndValidate(
          name = "rollback",
          fromVersion = 1,
          toVersion = 1
        )
          .use { database ->
            assertThat(database.query("SELECT id,name FROM person")).isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "name"),
                rows = listOf(listOf(1L, "Original"))
              )
            )
            assertThat(database.query("PRAGMA user_version")).isEqualTo(
              MigrationTestResult(
                columns = listOf("user_version"),
                rows = listOf(listOf(1L))
              )
            )
          }
      }
  }

  @Test
  fun `missing corrupt empty and incomplete covered inputs fail instead of reducing coverage`() {
    val cases = listOf(
      "missing manifest" to "manifest.json",
      "missing SQL" to "2.sql",
      "corrupt schema" to "schemas/1.struct",
      "empty SQL" to "3.sql",
      "invalid UTF8" to "schemas/2.struct",
      "missing schema SQL" to "schemas/1.struct"
    )
    cases.forEach { (label, resource) ->
      val descriptor = history()
      when (label) {
        "missing manifest", "missing SQL" -> descriptor.resources.remove(resource)
        "corrupt schema" -> descriptor.resources[resource] = "{\"tables\":{\"broken\":{}}}".toByteArray()
        "empty SQL" -> descriptor.resources[resource] = "\n-- no statements\n".toByteArray()
        "invalid UTF8" -> descriptor.resources[resource] = byteArrayOf(0xc3.toByte(), 0x28)
        "missing schema SQL" -> descriptor.resources[resource] = descriptor.text(resource)
          .replace(
            oldValue = "\"schema\":",
            newValue = "\"unknown\":"
          )
          .toByteArray()
      }
      val failure = assertThrows(IllegalStateException::class.java) {
        SqliteMagicMigrationTestHelper(
          descriptor = descriptor,
          driver = BundledSQLiteDriver()
        )
      }
      assertWithMessage(label)
        .that(failure.message)
        .contains("example")
      assertWithMessage(label)
        .that(failure.message)
        .contains(resource)
    }
  }

  @Test
  fun `missing covered manifest step fails before any driver opens`() {
    val descriptor = history()
    val manifest = MigrationMetadataJson.readManifest(descriptor.text("manifest.json"))
      .let { it.copy(steps = it.steps.drop(1)) }
    descriptor.resources["manifest.json"] = MigrationMetadataJson.writeManifest(manifest).toByteArray()
    val driver = RecordingDriver()
    val failure = assertThrows(IllegalStateException::class.java) {
      SqliteMagicMigrationTestHelper(
        descriptor = descriptor,
        driver = driver
      )
    }
    assertThat(failure.message).contains("cover every version")
    assertThat(driver.paths).isEmpty()
  }

  @Test
  fun `backward migration requests fail before opening another connection`() {
    val driver = RecordingDriver()
    SqliteMagicMigrationTestHelper(
      descriptor = history(),
      driver = driver
    )
      .use { helper ->
        helper.createDatabase(
          name = "backward",
          version = 2
        )
          .close()
        val opens = driver.paths.size
        val failure = assertThrows(IllegalStateException::class.java) {
          helper.runMigrationsAndValidate(
            name = "backward",
            fromVersion = 2,
            toVersion = 1
          )
        }
        assertThat(failure.message).contains("Invalid migration range 2 -> 1")
        assertThat(driver.paths).hasSize(opens)
      }
  }

  @Test
  fun `optional resource read and close failures are not treated as absent scripts`() {
    listOf("read", "close").forEach { phase ->
      val history = history()
      val manifest = MigrationMetadataJson.readManifest(history.text("manifest.json"))
        .let { it.copy(steps = it.steps.map { step -> step.copy(sqlResources = emptyList()) }) }
      history.resources["manifest.json"] = MigrationMetadataJson.writeManifest(manifest).toByteArray()
      val descriptor = object : MigrationDatabaseDescriptor by history {
        override fun openResource(name: String): InputStream = when (name) {
          "2.sql" -> object : ByteArrayInputStream("UPDATE person SET name='changed'\n".toByteArray()) {
            override fun read(
              buffer: ByteArray,
              offset: Int,
              length: Int
            ): Int {
              if (phase == "read") throw FileNotFoundException("optional stream read failed")
              return super.read(buffer, offset, length)
            }

            override fun close() {
              if (phase == "close") throw FileNotFoundException("optional stream close failed")
              super.close()
            }
          }
          else -> history.openResource(name)
        }
      }
      val driver = RecordingDriver()
      val failure = assertThrows(IllegalStateException::class.java) {
        SqliteMagicMigrationTestHelper(
          descriptor = descriptor,
          driver = driver
        )
      }
      assertThat(failure.message).contains("optional stream $phase failed")
      assertThat(driver.paths).isEmpty()
    }
  }

  @Test
  fun `missing manifest SQL reports its step before any driver opens`() {
    val descriptor = history()
    descriptor.resources.remove("3.sql")
    val driver = RecordingDriver()
    val failure = assertThrows(IllegalStateException::class.java) {
      SqliteMagicMigrationTestHelper(
        descriptor = descriptor,
        driver = driver
      )
    }
    assertThat(failure.message).contains("3.sql")
    assertThat(failure.message).contains("step 3")
    assertThat(driver.paths).isEmpty()
  }

  @Test
  fun `helper owns isolated files and closes active connections during cleanup`() {
    val driver = RecordingDriver()
    val first = SqliteMagicMigrationTestHelper(
      descriptor = history(),
      driver = driver
    )
    val second = SqliteMagicMigrationTestHelper(
      descriptor = history(),
      driver = driver
    )
    val firstDatabase = first.createDatabase(
      name = "same",
      version = 1
    )
    val secondDatabase = second.createDatabase(
      name = "same",
      version = 1
    )
    assertThrows(IllegalStateException::class.java) {
      first.createDatabase(
        name = "same",
        version = 1
      )
    }
    assertThat(driver.paths.map(Path::getParent).distinct()).hasSize(2)
    first.close()
    assertThrows(IllegalStateException::class.java) { firstDatabase.query("SELECT 1") }
    assertThat(secondDatabase.query("SELECT 1")).isEqualTo(
      MigrationTestResult(
        columns = listOf("1"),
        rows = listOf(listOf(1L))
      )
    )
    second.close()
    assertThat(driver.paths.any { Files.exists(it) }).isFalse()
    assertThat(driver.paths.map(Path::getParent).any { Files.exists(it) }).isFalse()
  }

  @Test
  fun `transaction controls fail constructor preflight before any native database opens`() {
    val descriptor = history()
    descriptor.resources["2.sql"] = "\nUPDATE person SET name=upper(name)\nCOMMIT;\n".toByteArray()
    descriptor.resources["3.sql"] = "BROKEN_SQL\n".toByteArray()
    val driver = RecordingDriver()
    val failure = assertThrows(IllegalStateException::class.java) {
      SqliteMagicMigrationTestHelper(
        descriptor = descriptor,
        driver = driver
      )
    }
    assertThat(driver.paths).isEmpty()
    assertThat(descriptor.opened).doesNotContain("3.sql")
    val context = generateSequence(
      seed = failure as Throwable,
      nextFunction = Throwable::cause
    )
      .mapNotNull(Throwable::message)
      .joinToString("\n")
    assertThat(context).contains("2.sql at physical line 3")
    assertThat(context).contains("Unsupported migration transaction control")
  }

  @Test
  fun `recorded foreign key policy changes before migration and every reopened connection`() {
    val descriptor = relationshipHistory()
    SqliteMagicMigrationTestHelper(
      descriptor = descriptor,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        helper.createDatabase(
          name = "relationship",
          version = 1
        )
          .use { database ->
            database.execute("INSERT INTO parent VALUES (1)")
            database.execute("INSERT INTO child VALUES (1,1)")
            assertThat(database.query("PRAGMA foreign_keys")).isEqualTo(
              MigrationTestResult(
                columns = listOf("foreign_keys"),
                rows = listOf(listOf(1L))
              )
            )
          }
        helper.runMigrationsAndValidate(
          name = "relationship",
          fromVersion = 1,
          toVersion = 2
        )
          .use { database ->
            assertThat(database.query("PRAGMA foreign_keys")).isEqualTo(
              MigrationTestResult(
                columns = listOf("foreign_keys"),
                rows = listOf(listOf(0L))
              )
            )
            assertThat(database.query("SELECT * FROM child")).isEqualTo(
              MigrationTestResult(
                columns = listOf("id", "parent_id"),
                rows = listOf(listOf(1L, 1L))
              )
            )
          }
        helper.runMigrationsAndValidate(
          name = "relationship",
          fromVersion = 2,
          toVersion = 3
        )
          .use { database ->
            assertThat(database.query("PRAGMA foreign_keys")).isEqualTo(
              MigrationTestResult(
                columns = listOf("foreign_keys"),
                rows = listOf(listOf(1L))
              )
            )
            assertThrows(Exception::class.java) { database.execute("INSERT INTO child VALUES(2,999)") }
          }
      }
  }

  @Test
  fun `foreign key violations fail validation even when target enforcement is disabled`() {
    val descriptor = relationshipHistory()
    descriptor.resources["2.sql"] = "INSERT INTO child VALUES (99,999)\n".toByteArray()
    SqliteMagicMigrationTestHelper(
      descriptor = descriptor,
      driver = BundledSQLiteDriver()
    )
      .use { helper ->
        helper.createDatabase(
          name = "orphan",
          version = 1
        )
          .close()
        val failure = assertThrows(IllegalStateException::class.java) {
          helper.runMigrationsAndValidate(
            name = "orphan",
            fromVersion = 1,
            toVersion = 2
          )
        }
        assertThat(failure.message).contains("foreign_key_check")
        assertThat(failure.message).contains("child")
      }
  }

  private fun history(firstVersion: Int? = null): MemoryDescriptor {
    val schemas = (1..3).map { version ->
      val extras = when (version) {
        1 -> ""
        2 -> ", name_length INTEGER"
        else -> ", name_length INTEGER, slug TEXT"
      }
      val columns = when (version) {
        1 -> "id,name"
        2 -> "id,name,name_length"
        else -> "id,name,name_length,slug"
      }
      ReleaseSchemaSnapshot(
        formatVersion = 1,
        databaseId = "example",
        releaseVariant = "release",
        version = version,
        modules = listOf("example"),
        configuration = MigrationConfiguration(foreignKeysEnabled = true),
        tableSql = listOf("CREATE TABLE person(id INTEGER PRIMARY KEY, name TEXT$extras)"),
        views = listOf(
          MigrationViewSchema(
            name = "person_view",
            createSql = "CREATE VIEW person_view AS SELECT $columns FROM person"
          )
        ),
        indexSql = when (version) {
          3 -> listOf("CREATE INDEX person_slug ON person(slug)")
          else -> emptyList()
        }
      )
    }
    val descriptor = descriptor(
      schemas = schemas,
      firstVersion = firstVersion
    )
    descriptor.resources["2.sql"] = (
        "ALTER TABLE person ADD COLUMN name_length INTEGER\n" +
            "UPDATE person SET name=upper(name), name_length=length(name)\n"
        ).toByteArray()
    descriptor.resources["3.sql"] = (
        "ALTER TABLE person ADD COLUMN slug TEXT\n" +
            "UPDATE person SET slug=lower(name)\n" +
            "CREATE INDEX person_slug ON person(slug)\n"
        ).toByteArray()
    // Obsolete view-removal assets must never participate in the supported upgrade path.
    descriptor.resources["2.views"] = "broken obsolete input\n".toByteArray()
    descriptor.resources["3.views"] = "person_view\n".toByteArray()
    return descriptor
  }

  private fun relationshipHistory(): MemoryDescriptor {
    val schemas = (1..3).map { version ->
      ReleaseSchemaSnapshot(
        formatVersion = 1,
        databaseId = "example",
        releaseVariant = "release",
        version = version,
        modules = listOf("example"),
        configuration = MigrationConfiguration(foreignKeysEnabled = version != 2),
        tableSql = listOf(
          "CREATE TABLE parent(id INTEGER PRIMARY KEY)",
          "CREATE TABLE child(id INTEGER PRIMARY KEY, parent_id INTEGER REFERENCES parent(id))"
        ),
        views = emptyList(),
        indexSql = emptyList()
      )
    }
    val descriptor = descriptor(schemas = schemas)
    descriptor.resources["2.sql"] = "INSERT INTO child VALUES (99,999)\nDELETE FROM child WHERE id=99\n".toByteArray()
    descriptor.resources["3.sql"] = "UPDATE child SET parent_id=1 WHERE id=1\n".toByteArray()
    return descriptor
  }

  private fun descriptor(
    schemas: List<ReleaseSchemaSnapshot>,
    firstVersion: Int? = null
  ): MemoryDescriptor {
    val manifest = MigrationManifest(
      formatVersion = 1,
      databaseId = "example",
      releaseVariant = "release",
      currentVersion = schemas.last().version,
      modules = listOf("example"),
      schemas = schemas.map {
        ReleaseSchemaResource(
          version = it.version,
          name = "schemas/${it.version}.struct",
          configuration = it.configuration,
          modules = it.modules
        )
      },
      steps = schemas.drop(1)
        .map { snapshot ->
          MigrationStep(
            toVersion = snapshot.version,
            sqlResources = listOf(MigrationResource(name = "${snapshot.version}.sql"))
          )
        }
    )
    val resources = schemas.associate { snapshot ->
      "schemas/${snapshot.version}.struct" to structure(snapshot).toByteArray()
    }
      .toMutableMap()
    resources["manifest.json"] = MigrationMetadataJson.writeManifest(manifest).toByteArray()
    resources["compiled.json"] = MigrationMetadataJson.writeSchema(schemas.last()).toByteArray()
    return MemoryDescriptor(
      resources = resources,
      currentVersion = schemas.last().version,
      firstVersion = firstVersion
    )
  }

  private fun structure(snapshot: ReleaseSchemaSnapshot): String {
    fun quoted(value: String) = "\"" + value.replace(
      oldValue = "\"",
      newValue = "\\\""
    ) + "\""

    val tables = snapshot.tableSql.mapIndexed { index, sql ->
      "\"table$index\":{\"schema\":${quoted(sql)},\"columns\":[]}"
    }.joinToString(",")
    val indices = snapshot.indexSql.mapIndexed { index, sql ->
      "\"index$index\":{\"indexSql\":${quoted(sql)}}"
    }.joinToString(",")
    val views = snapshot.views.joinToString(",") { view ->
      "${quoted(view.name)}:{\"name\":${quoted(view.name)}}"
    }
    return "{\"tables\":{$tables},\"indices\":{$indices},\"views\":{$views}}"
  }

  private class MemoryDescriptor(
    val resources: MutableMap<String, ByteArray>,
    override val currentVersion: Int,
    override val firstVersion: Int?
  ) : MigrationDatabaseDescriptor {
    override val databaseId = "example"
    override val releaseVariant = "release"
    override val resourceRoot = "META-INF/sqlitemagic/test"
    override val manifestResource = "manifest.json"
    override val currentSchemaResource = "compiled.json"
    val opened: List<String> field = mutableListOf()

    override fun openResource(name: String): InputStream {
      opened.add(name)
      return ByteArrayInputStream(resources[name] ?: throw FileNotFoundException(name))
    }

    fun text(name: String) = resources.getValue(name).toString(Charsets.UTF_8)
  }

  private class RecordingDriver : SQLiteDriver {
    val paths: List<Path> field = mutableListOf()
    private val delegate = BundledSQLiteDriver()

    override fun open(fileName: String) = delegate.open(fileName)
      .also { paths.add(Path.of(fileName)) }
  }
}
