package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.migration.MigrationConfiguration
import com.siimkinks.sqlitemagic.migration.MigrationManifest
import com.siimkinks.sqlitemagic.migration.MigrationMetadataJson
import com.siimkinks.sqlitemagic.migration.MigrationResource
import com.siimkinks.sqlitemagic.migration.MigrationStep
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaResource
import com.siimkinks.sqlitemagic.migration.ReleaseSchemaSnapshot
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

internal class ReleaseEvidencePublicationTest {
  @TempDir
  lateinit var directory: File

  private val database get() = directory.resolve("db")
  private val releases get() = database.resolve("releases")
  private val assets get() = directory.resolve("src/paidRelease/assets")
  private val manifest get() = MigrationMetadataJson.readManifest(releases.resolve("manifest.json").readText())

  @Test
  fun `publishes actual compiled version with complete immutable evidence`() {
    writeCurrent(structure("books"))
    val schema = schema(version = 7)
    publish(schema)

    assertThat(releases.resolve("7.schema.json").exists()).isFalse()
    assertThat(DatabaseStructureJson.read(releases.resolve("7.struct").readText())).isEqualTo(structure("books"))
    assertThat(manifest).isEqualTo(
      MigrationManifest(
        formatVersion = 1,
        databaseId = ":app",
        releaseVariant = "paidRelease",
        currentVersion = 7,
        modules = listOf("Feature", ":app"),
        schemas = listOf(
          ReleaseSchemaResource(
            version = 7,
            name = "schemas/7.struct",
            configuration = schema.configuration,
            modules = schema.modules
          )
        ),
        steps = (1..7).map { version ->
          MigrationStep(
            toVersion = version,
            sqlResources = emptyList()
          )
        }
      )
    )
    assertThat(releases.resolve("1.struct").exists()).isFalse()
  }

  @Test
  fun `new version preserves old manual SQL and orders pending target SQL before generated SQL`() {
    writeCurrent(structure("books"))
    publish(schema(version = 7))
    writeAsset(
      name = "7.sql",
      text = "UPDATE books SET id = id + 1; -- manually reviewed\n"
    )
    val oldStruct = releases.resolve("7.struct").readBytes()
    val oldSql = assets.resolve("7.sql").readBytes()
    writeCurrent(structure("books", "authors"))
    writeAsset(
      name = "8.sql",
      text = "-- consumer data migration\nUPDATE books SET id = id + 2;\n"
    )
    val compiled = schema(version = 8).copy(
      tableSql = structure("books", "authors").tables.values.map(TableStructure::schema)
    )
    publish(compiled)

    assertThat(releases.resolve("7.struct").readBytes()).isEqualTo(oldStruct)
    assertThat(assets.resolve("7.sql").readBytes()).isEqualTo(oldSql)
    assertThat(assets.resolve("8.sql").readText()).isEqualTo(
      "-- consumer data migration\nUPDATE books SET id = id + 2;\n" +
          "CREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)\n"
    )
    assertThat(manifest.steps.last()).isEqualTo(
      MigrationStep(
        toVersion = 8,
        sqlResources = listOf(MigrationResource("8.sql"))
      )
    )
  }

  @Test
  fun `publication records intentionally empty resources and explicit gap steps`() {
    writeCurrent(structure("books"))
    publish(schema(version = 7))
    writeAsset(
      name = "10.sql",
      text = ""
    )
    writeAsset(
      name = "Feature10.views",
      text = ""
    )
    publish(schema(version = 10))

    assertThat(assets.resolve("10.sql").isFile).isTrue()
    assertThat(manifest.steps.drop(7)).isEqualTo(
      listOf(
        MigrationStep(
          toVersion = 8,
          sqlResources = emptyList()
        ),
        MigrationStep(
          toVersion = 9,
          sqlResources = emptyList()
        ),
        MigrationStep(
          toVersion = 10,
          sqlResources = listOf(
            MigrationResource(
              name = "10.sql",
              allowEmpty = true
            )
          )
        )
      )
    )
  }

  @Test
  fun `adoption retains historical schema references without reading incomplete old evidence`() {
    writeCurrent(structure("books"))
    releases.mkdirs()
    releases.resolve("1.struct").writeText("historical incomplete and unreadable below chosen baseline")
    DatabaseStructureJson.write(
      file = releases.resolve("4.struct"),
      structure = structure("books")
    )
    writeAsset(
      name = "Feature2.sql",
      text = "CREATE TABLE legacy (id INTEGER)\n"
    )
    publish(schema(version = 5))

    assertThat(manifest.schemas).isEqualTo(
      listOf(
        ReleaseSchemaResource(
          version = 1,
          name = "schemas/1.struct"
        ),
        ReleaseSchemaResource(
          version = 4,
          name = "schemas/4.struct"
        ),
        ReleaseSchemaResource(
          version = 5,
          name = "schemas/5.struct",
          configuration = schema(version = 5).configuration,
          modules = schema(version = 5).modules
        )
      )
    )
    assertThat(manifest.steps[1]).isEqualTo(
      MigrationStep(
        toVersion = 2,
        sqlResources = listOf(MigrationResource("Feature2.sql"))
      )
    )
    assertThat(releases.resolve("1.struct").readText()).isEqualTo(
      "historical incomplete and unreadable below chosen baseline"
    )
  }

  @Test
  fun `reusing a published version or mixing selected release identities fails without changes`() {
    writeCurrent(structure("books"))
    val original = schema(version = 7)
    publish(original)
    val frozen = releases.listFiles().orEmpty().associate { it.name to it.readText() }
    val cases = mapOf(
      "same version" to original,
      "older version" to original.copy(version = 6),
      "other variant" to original.copy(
        version = 8,
        releaseVariant = "freeRelease"
      ),
      "other database" to original.copy(
        version = 8,
        databaseId = ":other",
        modules = listOf("Feature", ":other")
      )
    )
    cases.forEach { (_, candidate) -> assertThrows<IllegalStateException> { publish(candidate) } }
    assertThat(releases.listFiles().orEmpty().associate { it.name to it.readText() }).isEqualTo(frozen)
  }

  @Test
  fun `current module resources match production order and preserve previous steps`() {
    writeCurrent(structure("books"))
    publish(schema(version = 7).copy(modules = listOf("Removed", "Feature", ":app")))
    val previousSteps = manifest.steps
    listOf("Removed8.sql", "New8.sql", "Feature8.sql", "8.sql").forEach { name ->
      writeAsset(
        name = name,
        text = "SELECT 1\n"
      )
    }
    publish(schema(version = 8).copy(modules = listOf("New", "Feature", ":app")))

    assertThat(manifest.modules).containsExactly("Removed", "Feature", "New", ":app")
      .inOrder()
    assertThat(manifest.steps.take(7)).isEqualTo(previousSteps)
    assertThat(manifest.steps.last()).isEqualTo(
      MigrationStep(
        toVersion = 8,
        sqlResources = listOf(
          MigrationResource("New8.sql"),
          MigrationResource("Feature8.sql"),
          MigrationResource("8.sql")
        )
      )
    )
  }

  @Test
  fun `malformed pending SQL and unknown conventional modules fail preserving source inputs`() {
    writeCurrent(structure("books"))
    writeAsset(
      name = "7.sql",
      text = "UPDATE books SET id = 1; DELETE FROM books\n"
    )
    val original = assets.resolve("7.sql").readBytes()
    assertThrows<IllegalStateException> { publish(schema(version = 7)) }
    assertThat(assets.resolve("7.sql").readBytes()).isEqualTo(original)
    assertThat(releases.resolve("7.struct").exists()).isFalse()
    assets.resolve("7.sql").delete()
    writeAsset(
      name = "Unknown7.sql",
      text = "SELECT 1\n"
    )
    assertThat(assertThrows<IllegalStateException> { publish(schema(version = 7)) })
      .hasMessageThat()
      .contains("Unknown module prefix")
    assertThat(releases.resolve("7.struct").exists()).isFalse()
  }

  @Test
  fun `noncanonical main and module SQL filenames fail before changing history`() {
    writeCurrent(structure("books"))
    listOf("07.sql", "Feature07.sql", "+7.sql", "Feature+7.sql").forEach { name ->
      writeAsset(
        name = name,
        text = "SELECT 1\n"
      )
      val frozen = assets.listFiles().orEmpty().associateWith(File::readText)

      val failure = assertThrows<IllegalStateException> { publish(schema(version = 7)) }

      assertWithMessage(name).that(failure.message).contains("Noncanonical conventional migration resource")
      assertWithMessage(name).that(frozen.keys.associateWith(File::readText)).isEqualTo(frozen)
      assertWithMessage(name).that(releases.exists()).isFalse()
      check(assets.resolve(name).delete())
    }
  }

  @Test
  fun `duplicate numeric SQL aliases retain duplicate diagnostics before publication`() {
    writeCurrent(structure("books"))
    listOf("7.sql", "07.sql").forEach { name ->
      writeAsset(
        name = name,
        text = "SELECT 1\n"
      )
    }
    val before = assets.listFiles().orEmpty().associateWith(File::readText)

    val failure = assertThrows<IllegalStateException> { publish(schema(version = 7)) }

    assertThat(failure.message).contains("Duplicate numeric sql version 7")
    assertThat(before.keys.associateWith(File::readText)).isEqualTo(before)
    assertThat(releases.exists()).isFalse()
  }

  @Test
  fun `invalid UTF8 is rejected before generation`() {
    writeCurrent(structure("books"))
    assets.mkdirs()
    val sql = assets.resolve("7.sql")
    val original = byteArrayOf(0xc3.toByte(), 0x28)
    sql.writeBytes(original)
    assertThrows<Exception> { publish(schema(version = 7)) }
    assertThat(sql.readBytes()).isEqualTo(original)
    assertThat(releases.resolve("7.struct").exists()).isFalse()
  }

  @Test
  fun `missing published compiler baseline fails without modifying retained files`() {
    writeCurrent(structure("books"))
    publish(schema(version = 7))
    releases.resolve("7.struct").delete()
    val frozen = releases.listFiles().orEmpty().associate { it.name to it.readText() }
    writeCurrent(structure("books", "authors"))

    assertThat(assertThrows<IllegalStateException> { publish(schema(version = 8)) })
      .hasMessageThat()
      .contains("Restore the published compiler baseline")
    assertThat(releases.listFiles().orEmpty().associate { it.name to it.readText() }).isEqualTo(frozen)
    assertThat(assets.resolve("8.sql").exists()).isFalse()
  }

  @Test
  fun `initial manifest retains production upgrades predating the testing baseline`() {
    writeCurrent(structure("books"))
    writeAsset(
      name = "10.sql",
      text = "SELECT 1\n"
    )
    publish(schema(version = 10))

    assertThat(manifest.resolve(fromVersion = 5, toVersion = 10).steps).isEqualTo(
      (6..10).map { version ->
        MigrationStep(
          toVersion = version,
          sqlResources = when (version) {
            10 -> listOf(MigrationResource("10.sql"))
            else -> emptyList()
          }
        )
      }
    )
    assertThat(manifest.schemas).containsExactly(
      ReleaseSchemaResource(
        version = 10,
        name = "schemas/10.struct",
        configuration = schema(version = 10).configuration,
        modules = schema(version = 10).modules
      )
    )
  }

  @Test
  fun `explicit adoption enriches current structure without rewriting history and rejects mismatch`() {
    writeCurrent(structure("books"))
    releases.mkdirs()
    val retained = releases.resolve("7.struct")
    DatabaseStructureJson.write(
      file = retained,
      structure = structure("books")
    )
    writeAsset(
      name = "7.sql",
      text = "UPDATE books SET id = id + 1\n"
    )
    val before = listOf(retained, assets.resolve("7.sql")).associateWith(File::readText)
    assertThrows<IllegalStateException> {
      publish(schema(version = 7).copy(tableSql = listOf("CREATE TABLE other (id INTEGER)")))
    }
    assertThat(releases.resolve("manifest.json").exists()).isFalse()
    publish(schema(version = 7))
    assertThat(before.keys.associateWith(File::readText)).isEqualTo(before)
    assertThat(manifest.schema(7)).isEqualTo(
      ReleaseSchemaResource(
        version = 7,
        name = "schemas/7.struct",
        configuration = schema(version = 7).configuration,
        modules = schema(version = 7).modules
      )
    )
    assertThrows<IllegalStateException> { publish(schema(version = 7)) }
  }

  @Test
  fun `merged pending data SQL precedes generated SQL and generated current asset enters manifest`() {
    writeCurrent(structure("books"))
    publish(schema(version = 7))
    val merged = directory.resolve("build/merged-assets")
    check(merged.mkdirs())
    val pending = merged.resolve("8.sql")
    pending.writeText("UPDATE books SET id = id + 2;\n")
    merged.resolve("Feature8.sql").writeText("SELECT 8\n")
    writeCurrent(structure("books", "authors"))

    ReleaseMigrationCoordinator.migrate(
      projectDir = directory,
      databaseDirectory = database,
      variantName = "paidRelease",
      compiledRelease = schema(version = 8).copy(
        tableSql = structure("books", "authors").tables.values.map(TableStructure::schema)
      ),
      evidenceAssetsDirectory = merged
    )

    assertThat(pending.readText()).isEqualTo("UPDATE books SET id = id + 2;\n")
    assertThat(assets.resolve("8.sql").readText()).isEqualTo(
      "UPDATE books SET id = id + 2;\nCREATE TABLE IF NOT EXISTS authors (id INTEGER PRIMARY KEY)\n"
    )
    assertThat(manifest.steps.last()).isEqualTo(
      MigrationStep(
        toVersion = 8,
        sqlResources = listOf(MigrationResource("Feature8.sql"), MigrationResource("8.sql"))
      )
    )
  }

  @Test
  fun `merged data-only main and library resources are recorded without copying into generated destination`() {
    writeCurrent(structure("books"))
    val merged = directory.resolve("build/merged-assets")
    check(merged.mkdirs())
    merged.resolve("Feature2.sql").writeText("SELECT 2\n")
    merged.resolve("7.sql").writeText("UPDATE books SET id = id + 2;\n")
    val frozen = merged.listFiles().orEmpty().associateWith(File::readBytes)

    ReleaseMigrationCoordinator.migrate(
      projectDir = directory,
      databaseDirectory = database,
      variantName = "paidRelease",
      compiledRelease = schema(version = 7),
      evidenceAssetsDirectory = merged
    )

    assertThat(assets.exists()).isFalse()
    frozen.forEach { (file, bytes) -> assertThat(file.readBytes()).isEqualTo(bytes) }
    assertThat(manifest.steps[1]).isEqualTo(
      MigrationStep(
        toVersion = 2,
        sqlResources = listOf(MigrationResource("Feature2.sql"))
      )
    )
    assertThat(manifest.steps.last()).isEqualTo(
      MigrationStep(
        toVersion = 7,
        sqlResources = listOf(MigrationResource("7.sql"))
      )
    )
  }

  @Test
  fun `adoption and later unchanged schema publication retain merged data SQL outside source destination`() {
    writeCurrent(structure("books"))
    DatabaseStructureJson.write(
      file = releases.resolve("7.struct"),
      structure = structure("books")
    )
    val merged = directory.resolve("build/merged-assets")
    check(merged.mkdirs())
    merged.resolve("7.sql").writeText("SELECT 7\n")
    merged.resolve("8.sql").writeText("SELECT 8\n")

    listOf(7, 8).forEach { version ->
      ReleaseMigrationCoordinator.migrate(
        projectDir = directory,
        databaseDirectory = database,
        variantName = "paidRelease",
        compiledRelease = schema(version),
        evidenceAssetsDirectory = merged
      )
      assertThat(assets.exists()).isFalse()
      assertThat(manifest.steps.last()).isEqualTo(
        MigrationStep(
          toVersion = version,
          sqlResources = listOf(MigrationResource("$version.sql"))
        )
      )
      assertThat(merged.resolve("$version.sql").readText()).isEqualTo("SELECT $version\n")
    }
  }

  @Test
  fun `generated SQL is listed when merged assets snapshot predates generation`() {
    writeCurrent(structure("books"))
    publish(schema(version = 7))
    writeCurrent(structure("books", "authors"))
    val merged = directory.resolve("build/merged-assets")
    check(merged.mkdirs())

    ReleaseMigrationCoordinator.migrate(
      projectDir = directory,
      databaseDirectory = database,
      variantName = "paidRelease",
      compiledRelease = schema(version = 8).copy(
        tableSql = structure("books", "authors").tables.values.map(TableStructure::schema)
      ),
      evidenceAssetsDirectory = merged
    )

    assertThat(merged.resolve("8.sql").exists()).isFalse()
    assertThat(assets.resolve("8.sql").isFile).isTrue()
    assertThat(manifest.steps.last()).isEqualTo(
      MigrationStep(
        toVersion = 8,
        sqlResources = listOf(MigrationResource("8.sql"))
      )
    )
  }

  @Test
  fun `merged resource publication failure rolls back generated SQL and release snapshot`() {
    writeCurrent(structure("books"))
    publish(schema(version = 7))
    writeCurrent(structure("books", "authors"))
    writeAsset(
      name = "8.sql",
      text = "UPDATE books SET id = id + 1;\n"
    )
    val merged = directory.resolve("build/merged-assets")
    check(merged.mkdirs())
    merged.resolve("8.sql").writeText("UPDATE books SET id = id + 2;\n")
    merged.resolve("Feature8.sql").writeText("SELECT 1; SELECT 2;\n")
    val retainedManifest = releases.resolve("manifest.json").readBytes()
    val pending = assets.resolve("8.sql").readBytes()

    assertThrows<IllegalStateException> {
      ReleaseMigrationCoordinator.migrate(
        projectDir = directory,
        databaseDirectory = database,
        variantName = "paidRelease",
        compiledRelease = schema(version = 8).copy(
          tableSql = structure("books", "authors").tables.values.map(TableStructure::schema)
        ),
        evidenceAssetsDirectory = merged
      )
    }

    assertThat(releases.resolve("manifest.json").readBytes()).isEqualTo(retainedManifest)
    assertThat(releases.resolve("8.struct").exists()).isFalse()
    assertThat(assets.resolve("8.sql").readBytes()).isEqualTo(pending)
    assertThat(merged.resolve("8.sql").readText()).isEqualTo("UPDATE books SET id = id + 2;\n")
  }

  private fun publish(compiled: ReleaseSchemaSnapshot) = ReleaseMigrationCoordinator.migrate(
    projectDir = directory,
    databaseDirectory = database,
    variantName = "paidRelease",
    compiledRelease = compiled
  )

  private fun writeCurrent(current: DatabaseStructure) = DatabaseStructureJson.write(
    file = database.resolve("latest.struct"),
    structure = current
  )

  private fun writeAsset(
    name: String,
    text: String
  ) {
    assets.mkdirs()
    assets.resolve(name).writeText(text)
  }

  private fun schema(version: Int) = ReleaseSchemaSnapshot(
    formatVersion = 1,
    databaseId = ":app",
    releaseVariant = "paidRelease",
    version = version,
    modules = listOf("Feature", ":app"),
    configuration = MigrationConfiguration(foreignKeysEnabled = true),
    tableSql = structure("books").tables.values.map(TableStructure::schema),
    views = emptyList(),
    indexSql = emptyList()
  )

  private fun structure(vararg names: String) = DatabaseStructure(
    tables = names.associateWith(::tableStructure)
  )

  private fun tableStructure(name: String) = TableStructure(
    name = name,
    schema = "CREATE TABLE IF NOT EXISTS $name (id INTEGER PRIMARY KEY)",
    columns = listOf(
      ColumnStructure(
        id = true,
        name = "id",
        sqlType = "INTEGER",
        schema = "id INTEGER PRIMARY KEY"
      )
    )
  )
}
