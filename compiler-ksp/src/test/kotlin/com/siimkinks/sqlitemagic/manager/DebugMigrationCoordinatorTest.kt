package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_STRUCTURE_OUTPUT_DIR
import com.siimkinks.sqlitemagic.index.mockSqliteIdentifier
import com.siimkinks.sqlitemagic.index.mockSqliteSchemaIdentity
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.transformer.transformerCollectionProcessingSteps
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.ProcessorCompilationResult
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.view.mockViewElement
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal class DebugMigrationCoordinatorTest : ProcessingStepsTest {
  override val processingSteps = ::migrationCollectionSteps

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `publishes a complete staged submodule snapshot without debug migration`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val structureOutputDirectory = temporaryDirectory.resolve("staged")
    val staleStructure = DatabaseStructure(
      tables = linkedMapOf(
        "stale_items" to TableStructure(name = "stale_items")
      )
    )
    DatabaseStructureJson.write(
      file = structureOutputDirectory.resolve("latest_feature.struct").toFile(),
      structure = staleStructure
    )
    DatabaseStructureJson.write(
      file = structureOutputDirectory.resolve("latest_old.struct").toFile(),
      structure = staleStructure
    )
    structureOutputDirectory.resolve("feature.changed").toFile().writeText("")
    structureOutputDirectory.resolve("old.changed").toFile().writeText("")

    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "FeatureDatabase.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
            import com.siimkinks.sqlitemagic.annotation.Table

            @SubmoduleDatabase("feature")
            class FeatureDatabase

            @Table("feature_items")
            data class FeatureItem(
              @Id val id: Long,
              val name: String
            )
          """
        ),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          mainModuleDirectory = mainDirectory,
          migrateDebug = false,
          structureOutputDirectory = structureOutputDirectory
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val orderedTables = CreationOrderedTables.from(database.tables)
    val expectedStructure = DatabaseStructure.from(
      orderedTables = orderedTables,
      indexes = database.indices
    )

    assertThat(
      DebugMigrationCoordinator(
        configuration = DebugMigrationConfiguration.from(compilation.environment.options),
        logger = compilation.environment.logger
      ).handle(
        database = database,
        currentStructure = expectedStructure
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = null))
    assertThat(
      DatabaseStructureJson.read(
        structureOutputDirectory.resolve("latest_feature.struct").toFile()
      )
    ).isEqualTo(expectedStructure)
    assertThat(Files.exists(structureOutputDirectory.resolve("latest_old.struct")))
      .isFalse()
    assertThat(Files.exists(structureOutputDirectory.resolve("feature.changed")))
      .isFalse()
    assertThat(Files.exists(structureOutputDirectory.resolve("old.changed")))
      .isFalse()
    assertThat(Files.exists(mainDirectory.resolve("db/latest_feature.struct")))
      .isFalse()
  }

  @Test
  fun `retains an identical staged submodule structure while replacing stale state`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val stagingDirectory = temporaryDirectory.resolve("staged")
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          mainModuleDirectory = mainDirectory,
          migrateDebug = false,
          structureOutputDirectory = stagingDirectory
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement
      .from(compilation.environment)
      .copy(submoduleName = "feature")
    val structure = DatabaseStructure.from(
      orderedTables = CreationOrderedTables.from(database.tables),
      indexes = database.indices,
      views = database.views
    )
    val currentFile = stagingDirectory.resolve("latest_feature.struct").toFile()
    DatabaseStructureJson.write(
      file = currentFile,
      structure = structure
    )
    val originalBytes = currentFile.readBytes()
    val originalTimestamp = 946684800000L
    check(currentFile.setLastModified(originalTimestamp))
    val staleStructure = stagingDirectory.resolve("latest_old.struct").toFile()
    staleStructure.writeText("stale")
    val currentMarker = stagingDirectory.resolve("feature.changed").toFile()
    currentMarker.writeText("stale marker")
    val staleMarker = stagingDirectory.resolve("old.changed").toFile()
    staleMarker.writeText("stale marker")

    assertThat(
      DebugMigrationCoordinator(
        configuration = DebugMigrationConfiguration.from(compilation.environment.options),
        logger = compilation.environment.logger
      ).handle(
        database = database,
        currentStructure = structure
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(currentFile.readBytes()).isEqualTo(originalBytes)
    assertThat(currentFile.lastModified()).isEqualTo(originalTimestamp)
    assertThat(staleStructure.exists()).isFalse()
    assertThat(currentMarker.exists()).isFalse()
    assertThat(staleMarker.exists()).isFalse()
  }

  @Test
  fun `publishes a complete staged main snapshot without debug migration`() {
    val structureOutputDirectory = temporaryDirectory.resolve("staged")
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(
          projectDirectory = temporaryDirectory,
          migrateDebug = false,
          structureOutputDirectory = structureOutputDirectory
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement
      .from(compilation.environment)
      .copy(views = listOf(mockView(name = "main_view")))
    val orderedTables = CreationOrderedTables.from(database.tables)

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(
      DatabaseStructureJson.read(
        structureOutputDirectory
          .resolve("latest.struct")
          .toFile()
      )
    ).isEqualTo(
      DatabaseStructure.from(
        orderedTables = orderedTables,
        views = database.views
      )
    )
  }

  @Test
  fun `does not publish a submodule snapshot when migration generation fails`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val compilation = SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "FeatureWithoutRowIdDatabase.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID

            @SubmoduleDatabase("feature")
            class FeatureDatabase

            @Table(value = "feature_items", options = [WITHOUT_ROWID])
            data class FeatureItem(
              @Id val id: String,
              val name: String
            )
          """
        ),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          mainModuleDirectory = mainDirectory
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val orderedTables = CreationOrderedTables.from(database.tables)
    val currentStructure = DatabaseStructure.from(orderedTables)
    val currentTable = currentStructure.tables.getValue("feature_items")
    val previousTable = currentTable.copy(
      schema = "CREATE TABLE IF NOT EXISTS feature_items (name TEXT DEFAULT '')",
      columns = arrayListOf(currentTable.columns.single { column -> column.name == "name" })
    )
    val previousStructure = DatabaseStructure(
      tables = linkedMapOf("feature_items" to previousTable)
    )
    DatabaseStructureJson.write(
      file = submoduleDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = previousStructure
    )

    val outcome = DebugMigrationCoordinator(
      configuration = DebugMigrationConfiguration.from(compilation.environment.options),
      logger = compilation.environment.logger
    )
      .handle(
        database = database,
        currentStructure = currentStructure
      )

    assertThat(outcome)
      .isEqualTo(
        DebugMigrationOutcome(databaseVersionOverride = null)
      )
    assertThat(
      DatabaseStructureJson.read(
        submoduleDirectory
          .resolve("db/latest.struct")
          .toFile()
      )
    )
      .isEqualTo(previousStructure)
    assertThat(Files.exists(mainDirectory.resolve("db/latest_feature.struct")))
      .isFalse()
    assertThat(Files.exists(mainDirectory.resolve("db/feature.changed")))
      .isFalse()
    assertThat(Files.exists(submoduleDirectory.resolve("src/debug/assets/Feature1001.sql")))
      .isFalse()
  }

  @Test
  fun `uses the latest persisted debug version for an unchanged main database`() {
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = temporaryDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = DatabaseStructure.from(orderedTables)
    )
    temporaryDirectory
      .resolve("db/latest_debug.version")
      .toFile()
      .apply {
        parentFile.mkdirs()
        writeText("1007")
      }

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1007))
  }

  @Test
  fun `uses the default debug version for an initial unchanged main database`() {
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = temporaryDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = DatabaseStructure.from(orderedTables)
    )

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1000))
  }

  @Test
  fun `reads a configured main version and restores only the local write target`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val projectDirectory = temporaryDirectory.resolve("project")
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(
          projectDirectory = projectDirectory,
          mainModuleDirectory = mainDirectory
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val structure = DatabaseStructure.from(CreationOrderedTables.from(database.tables))
    DatabaseStructureJson.write(
      file = projectDirectory.resolve("db/latest.struct").toFile(),
      structure = structure
    )
    val mainVersion = mainDirectory.resolve("db/latest_debug.version").toFile()
    check(mainVersion.parentFile.mkdirs())
    mainVersion.writeText("1007")
    val localVersion = projectDirectory.resolve("db/latest_debug.version").toFile()
    localVersion.writeText("1003")
    val marker = projectDirectory.resolve("db/feature.changed").toFile()
    marker.writeText("")
    val failure = IOException("manager generation failed")

    val actual = assertThrows<IOException> {
      DebugMigrationCoordinator(
        configuration = DebugMigrationConfiguration.from(compilation.environment.options),
        logger = compilation.environment.logger
      ).handle(
        database = database,
        currentStructure = structure,
        completion = { outcome ->
          assertThat(outcome).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1008))
          assertThat(localVersion.readText()).isEqualTo("1008")
          throw failure
        }
      )
    }

    assertThat(actual).isSameInstanceAs(failure)
    assertThat(mainVersion.readText()).isEqualTo("1007")
    assertThat(localVersion.readText()).isEqualTo("1003")
    assertThat(marker.isFile).isTrue()
  }

  @Test
  fun `increments and persists the debug version after a main migration`() {
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = temporaryDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = DatabaseStructure()
    )
    temporaryDirectory
      .resolve("db/latest_debug.version")
      .toFile()
      .apply {
        parentFile.mkdirs()
        writeText("1007")
      }

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1008))
    assertThat(
      temporaryDirectory
        .resolve("db/latest_debug.version")
        .toFile()
        .readText()
    ).isEqualTo("1008")
    assertThat(
      Files.exists(
        temporaryDirectory.resolve("src/debug/assets/1008.sql")
      )
    ).isTrue()
  }

  @Test
  fun `consumes a submodule change marker after advancing an unchanged main database`() {
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = temporaryDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = DatabaseStructure.from(orderedTables)
    )
    val versionFile = temporaryDirectory
      .resolve("db/latest_debug.version")
      .toFile()
    versionFile.writeText("1007")
    val changeMarker = temporaryDirectory
      .resolve("db/feature.changed")
      .toFile()
    changeMarker.writeText("")

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1008))
    assertThat(versionFile.readText())
      .isEqualTo("1008")
    assertThat(changeMarker.exists())
      .isFalse()
    assertThat(Files.exists(temporaryDirectory.resolve("src/debug/assets/1008.sql")))
      .isFalse()
    assertThat(Files.exists(temporaryDirectory.resolve("src/debug/assets/1008.views")))
      .isFalse()
  }

  @Test
  fun `ignores directories whose names end in changed`() {
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = temporaryDirectory.resolve("db/latest.struct").toFile(),
      structure = DatabaseStructure.from(orderedTables)
    )
    temporaryDirectory.resolve("db/latest_debug.version").toFile().writeText("1007")
    val directory = temporaryDirectory.resolve("db/ignored.changed")
    Files.createDirectory(directory)

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1007))
    assertThat(Files.isDirectory(directory)).isTrue()
  }

  @Test
  fun `adds removed submodule view names from markers to the main removal artifact`() {
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
    val database = GeneratedDatabaseElement.from(compilation.environment)
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = temporaryDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = DatabaseStructure.from(orderedTables)
    )
    temporaryDirectory
      .resolve("db/latest_debug.version")
      .toFile()
      .writeText("1007")
    val marker = temporaryDirectory
      .resolve("db/feature.changed")
      .toFile()
    marker.writeText("removed_feature_view\nrenamed_feature_view\n")

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1008))
    assertThat(
      temporaryDirectory
        .resolve("src/debug/assets/1008.views")
        .toFile()
        .readText()
    ).isEqualTo("removed_feature_view\nrenamed_feature_view\n")
    assertThat(marker.exists())
      .isFalse()
  }

  @Test
  fun `submodule retries retain pending same-version assets and accumulate another view change`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          mainModuleDirectory = mainDirectory
        )
      )
      .isOk()
    val baseDatabase = GeneratedDatabaseElement
      .from(compilation.environment)
      .copy(submoduleName = "feature")
    val orderedTables = CreationOrderedTables.from(baseDatabase.tables)
    val previous = DatabaseStructure.from(orderedTables)
      .copy(
        views = linkedMapOf("old_view" to ViewStructure(name = "old_view"))
      )
    DatabaseStructureJson.write(
      file = submoduleDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = previous
    )
    val mainMarker = mainDirectory
      .resolve("db/feature.changed")
      .toFile()
    mainMarker.parentFile.mkdirs()
    mainMarker.writeText("previous_removed_view\n")
    val assetsDirectory = submoduleDirectory.resolve("src/debug/assets")
    val sqlFile = assetsDirectory
      .resolve("feature1001.sql")
      .toFile()
    val viewsFile = assetsDirectory
      .resolve("feature1001.views")
      .toFile()
    sqlFile.parentFile.mkdirs()
    sqlFile.writeText("SELECT 1;\n")
    viewsFile.writeText("previous_removed_view\n")

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = baseDatabase.copy(views = listOf(mockView(name = "old_view"))),
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(sqlFile.readText())
      .isEqualTo("SELECT 1;\n")
    assertThat(viewsFile.readText())
      .isEqualTo("previous_removed_view\n")

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = baseDatabase.copy(views = listOf(mockView(name = "new_view"))),
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(sqlFile.readText())
      .isEqualTo("SELECT 1;\n")
    assertThat(viewsFile.readText())
      .isEqualTo("previous_removed_view\nold_view\n")
    assertThat(mainMarker.readText())
      .isEqualTo("previous_removed_view\nold_view\n")
  }

  @Test
  fun `staged unchanged submodule retry retains same-version assets without a main marker`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val stagingDirectory = temporaryDirectory.resolve("staged")
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          mainModuleDirectory = mainDirectory,
          structureOutputDirectory = stagingDirectory
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement
      .from(compilation.environment)
      .copy(
        submoduleName = "feature",
        views = listOf(mockView(name = "new_view"))
      )
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = submoduleDirectory.resolve("db/latest.struct").toFile(),
      structure = DatabaseStructure(
        views = linkedMapOf("old_view" to ViewStructure(name = "old_view"))
      )
    )
    val assetsDirectory = submoduleDirectory.resolve("src/debug/assets")
    val sqlFile = assetsDirectory.resolve("feature1001.sql").toFile()
    val viewsFile = assetsDirectory.resolve("feature1001.views").toFile()
    val mainMarker = mainDirectory.resolve("db/feature.changed").toFile()

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(sqlFile.isFile)
      .isTrue()
    assertThat(viewsFile.readText())
      .isEqualTo("old_view\n")
    assertThat(Files.exists(stagingDirectory.resolve("latest_feature.struct")))
      .isTrue()
    assertThat(mainMarker.exists())
      .isFalse()
    val originalSql = sqlFile.readBytes()
    val originalViews = viewsFile.readBytes()
    assertThat(originalSql.size)
      .isGreaterThan(0)

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(sqlFile.readBytes())
      .isEqualTo(originalSql)
    assertThat(viewsFile.readBytes())
      .isEqualTo(originalViews)
    assertThat(mainMarker.exists())
      .isFalse()
  }

  @Test
  fun `completion failure restores migration artifacts version and submodule markers`() {
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
    val database = GeneratedDatabaseElement
      .from(compilation.environment)
      .copy(views = listOf(mockView(name = "new_view")))
    val orderedTables = CreationOrderedTables.from(database.tables)
    val previous = DatabaseStructure.from(orderedTables)
      .copy(
        views = linkedMapOf("old_view" to ViewStructure(name = "old_view"))
      )
    val structureFile = temporaryDirectory
      .resolve("db/latest.struct")
      .toFile()
    DatabaseStructureJson.write(
      file = structureFile,
      structure = previous
    )
    val versionFile = temporaryDirectory
      .resolve("db/latest_debug.version")
      .toFile()
    versionFile.writeText("1007")
    val marker = temporaryDirectory
      .resolve("db/feature.changed")
      .toFile()
    marker.writeText("removed_feature_view\n")
    val assetsDirectory = temporaryDirectory
      .resolve("src/debug/assets")
      .toFile()
    check(assetsDirectory.mkdirs())
    val sqlFile = assetsDirectory.resolve("1008.sql")
    sqlFile.writeText("previous sql\n")
    val viewsFile = assetsDirectory.resolve("1008.views")
    viewsFile.writeText("previous view\n")
    val failure = IOException("manager generation failed")

    val actual = assertThrows<IOException> {
      DebugMigrationCoordinator(
        configuration = DebugMigrationConfiguration.from(compilation.environment.options),
        logger = compilation.environment.logger
      ).handle(
        database = database,
        currentStructure = DatabaseStructure.from(
          orderedTables = orderedTables,
          indexes = database.indices,
          views = database.views
        ),
        completion = { outcome ->
          assertThat(outcome)
            .isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1008))
          throw failure
        }
      )
    }

    assertThat(actual)
      .isSameInstanceAs(failure)
    assertThat(DatabaseStructureJson.read(structureFile))
      .isEqualTo(previous)
    assertThat(versionFile.readText())
      .isEqualTo("1007")
    assertThat(sqlFile.readText())
      .isEqualTo("previous sql\n")
    assertThat(viewsFile.readText())
      .isEqualTo("previous view\n")
    assertThat(marker.readText())
      .isEqualTo("removed_feature_view\n")
  }

  @Test
  fun `view-only debug rename advances version and publishes previous owned names`() {
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
    val database = GeneratedDatabaseElement
      .from(compilation.environment)
      .copy(views = listOf(mockView(name = "new_view")))
    val orderedTables = CreationOrderedTables.from(database.tables)
    val previous = DatabaseStructure.from(orderedTables)
      .copy(
        views = linkedMapOf("old_view" to ViewStructure(name = "old_view"))
      )
    DatabaseStructureJson.write(
      file = temporaryDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = previous
    )
    temporaryDirectory
      .resolve("db/latest_debug.version")
      .toFile()
      .writeText("1007")

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1008))
    assertThat(
      temporaryDirectory
        .resolve("db/latest_debug.version")
        .toFile()
        .readText()
    ).isEqualTo("1008")
    assertThat(
      temporaryDirectory
        .resolve("src/debug/assets/1008.views")
        .toFile()
        .readText()
    ).isEqualTo("old_view\n")
    assertThat(Files.exists(temporaryDirectory.resolve("src/debug/assets/1008.sql")))
      .isFalse()
  }

  @Test
  fun `submodule view-only change marks main version for advancement`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          mainModuleDirectory = mainDirectory
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement
      .from(compilation.environment)
      .copy(
        submoduleName = "feature",
        views = listOf(mockView(name = "feature_view"))
      )
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = submoduleDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = DatabaseStructure.from(orderedTables)
    )

    assertThat(
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = orderedTables
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(Files.exists(mainDirectory.resolve("db/feature.changed")))
      .isTrue()
    assertThat(
      DatabaseStructureJson.read(
        mainDirectory
          .resolve("db/latest_feature.struct")
          .toFile()
      )?.views?.keys
    ).containsExactly("feature_view")
    assertThat(Files.exists(submoduleDirectory.resolve("src/debug/assets/feature1001.views")))
      .isFalse()
  }

  @Test
  fun `restores debug artifacts when staged submodule publication fails`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val blockedStaging = temporaryDirectory
      .resolve("blocked-staging")
      .toFile()
    blockedStaging.writeText("not a directory")
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          mainModuleDirectory = mainDirectory,
          structureOutputDirectory = blockedStaging.toPath()
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement
      .from(compilation.environment)
      .copy(
        submoduleName = "feature",
        views = listOf(mockView(name = "new_view"))
      )
    val orderedTables = CreationOrderedTables.from(database.tables)
    val previous = DatabaseStructure.from(orderedTables)
      .copy(
        views = linkedMapOf("old_view" to ViewStructure(name = "old_view"))
      )
    val structureFile = submoduleDirectory
      .resolve("db/latest.struct")
      .toFile()
    DatabaseStructureJson.write(
      file = structureFile,
      structure = previous
    )

    runDebugMigration(
      compilation = compilation,
      database = database,
      orderedTables = orderedTables
    )

    assertThat(DatabaseStructureJson.read(structureFile))
      .isEqualTo(previous)
    assertThat(Files.exists(submoduleDirectory.resolve("src/debug/assets/feature1001.views")))
      .isFalse()
    assertThat(Files.exists(mainDirectory.resolve("db/feature.changed")))
      .isFalse()
  }

  @Test
  fun `restores stale staged structures and markers when replacement fails`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val stagingDirectory = temporaryDirectory.resolve("staged")
    val blockedTarget = stagingDirectory.resolve("latest_feature.struct")
    Files.createDirectories(blockedTarget)
    val staleStructure = stagingDirectory
      .resolve("latest_old.struct")
      .toFile()
    staleStructure.writeText("stale structure")
    val staleMarker = stagingDirectory
      .resolve("old.changed")
      .toFile()
    staleMarker.writeText("stale marker")
    val compilation = SqliteMagicCompilation
      .compile(
        debugMainDatabase(),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          mainModuleDirectory = mainDirectory,
          migrateDebug = false,
          structureOutputDirectory = stagingDirectory
        )
      )
      .isOk()
    val database = GeneratedDatabaseElement
      .from(compilation.environment)
      .copy(submoduleName = "feature")

    assertThrows<Exception> {
      runDebugMigration(
        compilation = compilation,
        database = database,
        orderedTables = CreationOrderedTables.from(database.tables)
      )
    }

    assertThat(staleStructure.readText())
      .isEqualTo("stale structure")
    assertThat(staleMarker.readText())
      .isEqualTo("stale marker")
    assertThat(Files.isDirectory(blockedTarget))
      .isTrue()
  }

  private fun mockView(name: String) = mockViewElement(
    identity = mockSqliteSchemaIdentity(identifier = mockSqliteIdentifier(rawName = name))
  )

  private fun debugMigrationOptions(
    projectDirectory: Path,
    mainModuleDirectory: Path? = null,
    migrateDebug: Boolean = true,
    structureOutputDirectory: Path? = null
  ) = buildMap {
    put(
      key = "sqlitemagic.migrate.debug",
      value = migrateDebug.toString()
    )
    put(
      key = "sqlitemagic.project.dir",
      value = projectDirectory.toString()
    )
    put(
      key = "sqlitemagic.variant.name",
      value = "debug"
    )
    put(
      key = "sqlitemagic.variant.debug",
      value = "true"
    )
    mainModuleDirectory?.let { directory ->
      put(
        key = "sqlitemagic.main.module.path",
        value = directory.toString()
      )
    }
    structureOutputDirectory?.let { directory ->
      put(
        key = OPTION_STRUCTURE_OUTPUT_DIR,
        value = directory.toString()
      )
    }
  }

  private fun debugMainDatabase() = SourceFile.kotlin(
    name = "DebugMainDatabase.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Id
      import com.siimkinks.sqlitemagic.annotation.Table

      @Table("debug_items")
      data class DebugItem(
        @Id val id: Long,
        val name: String
      )
    """
  )

  private fun runDebugMigration(
    compilation: ProcessorCompilationResult,
    database: GeneratedDatabaseElement,
    orderedTables: CreationOrderedTables
  ) = DebugMigrationCoordinator(
    configuration = DebugMigrationConfiguration.from(compilation.environment.options),
    logger = compilation.environment.logger
  )
    .handle(
      database = database,
      currentStructure = DatabaseStructure.from(
        orderedTables = orderedTables,
        indexes = database.indices,
        views = database.views
      )
    )
}

private fun migrationCollectionSteps(environment: Environment): List<ProcessingStep> =
  transformerCollectionProcessingSteps(environment) + ModelCollectionStep(environment)
