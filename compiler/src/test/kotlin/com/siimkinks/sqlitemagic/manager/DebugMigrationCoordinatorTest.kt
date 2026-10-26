package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.KSPLogger
import com.siimkinks.sqlitemagic.CompilerOptions
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.index.mockSqliteIdentifier
import com.siimkinks.sqlitemagic.index.mockSqliteSchemaIdentity
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.transformer.transformerCollectionProcessingSteps
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.debugMigrationOptions
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.view.mockViewElement
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class DebugMigrationCoordinatorTest {
  private val compiler = SqliteMagicCompiler(::migrationCollectionSteps)

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `publishes a complete staged submodule snapshot without debug migration`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val structureOutputDirectory = temporaryDirectory.resolve("staged")
    val staleStructure = DatabaseStructure(
      tables = mapOf(
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
    structureOutputDirectory
      .resolve("feature.changed")
      .writeText("")
    structureOutputDirectory
      .resolve("old.changed")
      .writeText("")

    val configuration = debugMigrationConfiguration(
      projectDirectory = submoduleDirectory,
      mainModuleDirectory = mainDirectory,
      migrateDebug = false,
      structureOutputDirectory = structureOutputDirectory
    )
    val database = debugMainDatabase
      .copy(submoduleName = "Feature")
    val orderedTables = CreationOrderedTables.from(database.tables)
    val expectedStructure = DatabaseStructure.from(
      orderedTables = orderedTables,
      indexes = database.indices
    )

    assertThat(
      debugMigrationCoordinator(configuration)
        .handle(
          database = database,
          currentStructure = expectedStructure
        )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(
      DatabaseStructureJson.read(
        structureOutputDirectory.resolve("latest_feature.struct").toFile()
      )
    ).isEqualTo(expectedStructure)
    assertThat(
      structureOutputDirectory
        .resolve("latest_old.struct")
        .exists()
    ).isFalse()
    assertThat(
      structureOutputDirectory
        .resolve("feature.changed")
        .exists()
    ).isFalse()
    assertThat(
      structureOutputDirectory
        .resolve("old.changed")
        .exists()
    ).isFalse()
    assertThat(
      mainDirectory
        .resolve("db/latest_feature.struct")
        .exists()
    ).isFalse()
  }

  @Test
  fun `retains an identical staged submodule structure while replacing stale state`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val stagingDirectory = temporaryDirectory.resolve("staged")
    val configuration = debugMigrationConfiguration(
      projectDirectory = submoduleDirectory,
      mainModuleDirectory = mainDirectory,
      migrateDebug = false,
      structureOutputDirectory = stagingDirectory
    )
    val database = debugMainDatabase
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
      debugMigrationCoordinator(configuration)
        .handle(
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
    val configuration = debugMigrationConfiguration(
      projectDirectory = temporaryDirectory,
      migrateDebug = false,
      structureOutputDirectory = structureOutputDirectory
    )
    val database = debugMainDatabase
      .copy(views = listOf(mockView(name = "main_view")))
    val orderedTables = CreationOrderedTables.from(database.tables)

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
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
    val compilation = compiler
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
    val database = compilation.environment.generatedDatabaseElement()
    val orderedTables = CreationOrderedTables.from(database.tables)
    val currentStructure = DatabaseStructure.from(orderedTables)
    val currentTable = currentStructure.tables.getValue("feature_items")
    val previousTable = currentTable.copy(
      schema = "CREATE TABLE IF NOT EXISTS feature_items (name TEXT DEFAULT '')",
      columns = listOf(currentTable.columns.single { it.name == "name" })
    )
    val previousStructure = DatabaseStructure(
      tables = mapOf("feature_items" to previousTable)
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
      .isEqualTo(DebugMigrationOutcome())
    assertThat(
      DatabaseStructureJson.read(
        submoduleDirectory
          .resolve("db/latest.struct")
          .toFile()
      )
    )
      .isEqualTo(previousStructure)
    assertThat(
      mainDirectory
        .resolve("db/latest_feature.struct")
        .exists()
    ).isFalse()
    assertThat(
      mainDirectory
        .resolve("db/feature.changed")
        .exists()
    ).isFalse()
    assertThat(
      submoduleDirectory
        .resolve("src/debug/assets/Feature1001.sql")
        .exists()
    ).isFalse()
  }

  @Test
  fun `uses the default debug version for an initial unchanged main database`() {
    val configuration = debugMigrationConfiguration(projectDirectory = temporaryDirectory)
    val database = debugMainDatabase
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = temporaryDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = DatabaseStructure.from(orderedTables)
    )

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1000))
  }

  @ParameterizedTest(name = "{0} debug migration history, pending migration: {1}")
  @CsvSource("main,false", "feature,false", "feature,true")
  fun `keeps only the latest ten debug version steps for each module`(module: String, pendingMigration: Boolean) {
    val mainDirectory = temporaryDirectory.resolve("main")
    val projectDirectory = temporaryDirectory.resolve(module)
    val configuration = debugMigrationConfiguration(
      projectDirectory = projectDirectory,
      mainModuleDirectory = mainDirectory
    )
    val submoduleName = module.takeUnless { it == "main" }
    val database = debugMainDatabase
      .copy(submoduleName = submoduleName)
    val orderedTables = CreationOrderedTables.from(database.tables)
    val latestVersion = when {
      pendingMigration -> 1021
      else -> 1020
    }
    val prefix = submoduleName.orEmpty()
    DatabaseStructureJson.write(
      file = projectDirectory.resolve("db/latest.struct").toFile(),
      structure = DatabaseStructure.from(orderedTables)
    )
    val versionFile = mainDirectory.resolve("db/latest_debug.version").toFile()
    versionFile.parentFile.mkdirs()
    versionFile.writeText("1020")
    val assetsDirectory = projectDirectory.resolve("src/debug/assets").toFile()
    check(assetsDirectory.mkdirs())
    (1001..latestVersion)
      .filter { it != 1002 }
      .forEach { version ->
        assetsDirectory.resolve("$prefix$version.sql").writeText("-- migration $version\n")
      }
    val preservedFiles = listOf(
      "${prefix}1.sql",
      "${prefix}1000.sql",
      "${prefix}1022.sql",
      "${prefix}1001.txt",
      "${prefix}01001.sql",
      "other1001.sql",
      "seed.sql"
    )
    preservedFiles.forEach { assetsDirectory.resolve(it).writeText("preserved") }
    val preservedDirectory = assetsDirectory.resolve("${prefix}1002.sql/nested.txt")
    check(preservedDirectory.parentFile.mkdirs())
    preservedDirectory.writeText("preserved directory")
    val releaseFile = projectDirectory.resolve("src/release/assets/${prefix}1001.sql").toFile()
    check(releaseFile.parentFile.mkdirs())
    releaseFile.writeText("release migration")

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    ).isEqualTo(
      DebugMigrationOutcome(databaseVersionOverride = latestVersion.takeUnless { database.isSubmodule })
    )

    val retainedFiles = (latestVersion - 9..latestVersion).map { "$prefix$it.sql" }
    assertThat(
      assetsDirectory
        .listFiles()
        .orEmpty()
        .map(File::getName)
    ).containsExactlyElementsIn(retainedFiles + preservedFiles + "${prefix}1002.sql")
    retainedFiles.forEach { name ->
      val version = name.removePrefix(prefix)
        .removeSuffix(".sql")
      assertThat(assetsDirectory.resolve(name).readText()).isEqualTo("-- migration $version\n")
    }
    assertThat(preservedDirectory.readText()).isEqualTo("preserved directory")
    assertThat(releaseFile.readText()).isEqualTo("release migration")
  }

  @Test
  fun `reads a configured main version and restores only the local write target`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val projectDirectory = temporaryDirectory.resolve("project")
    val configuration = debugMigrationConfiguration(
      projectDirectory = projectDirectory,
      mainModuleDirectory = mainDirectory
    )
    val database = debugMainDatabase
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
      debugMigrationCoordinator(configuration)
        .handle(
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
    val configuration = debugMigrationConfiguration(projectDirectory = temporaryDirectory)
    val database = debugMainDatabase
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
        writeText("1020")
      }
    val assetsDirectory = temporaryDirectory.resolve("src/debug/assets").toFile()
    check(assetsDirectory.mkdirs())
    (1001..1020).forEach { version ->
      assetsDirectory.resolve("$version.sql").writeText("-- migration $version\n")
    }

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1021))
    assertThat(
      temporaryDirectory
        .resolve("db/latest_debug.version")
        .readText()
    ).isEqualTo("1021")
    assertThat(
      temporaryDirectory
        .resolve("src/debug/assets/1021.sql")
        .exists()
    ).isTrue()
    assertThat(
      assetsDirectory
        .listFiles()
        .orEmpty()
        .map(File::getName)
    ).containsExactlyElementsIn((1012..1021).map { "$it.sql" })
  }

  @Test
  fun `table-only submodule change advances version without publishing view resources`() {
    val configuration = debugMigrationConfiguration(projectDirectory = temporaryDirectory)
    val database = debugMainDatabase
      .copy(views = listOf(mockView(name = "main_view")))
    val orderedTables = CreationOrderedTables.from(database.tables)
    val previous = DatabaseStructure.from(
      orderedTables = orderedTables,
      indexes = database.indices,
      views = database.views
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
    marker.writeText("")

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1008))
    assertThat(
      temporaryDirectory
        .resolve("src/debug/assets/1008.sql")
        .exists()
    ).isFalse()
    assertThat(DatabaseStructureJson.read(structureFile))
      .isEqualTo(previous)
    assertThat(versionFile.readText())
      .isEqualTo("1008")
    assertThat(marker.exists())
      .isFalse()
  }

  @Test
  fun `ignores directories whose names end in changed`() {
    val configuration = debugMigrationConfiguration(projectDirectory = temporaryDirectory)
    val database = debugMainDatabase
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = temporaryDirectory.resolve("db/latest.struct").toFile(),
      structure = DatabaseStructure.from(orderedTables)
    )
    temporaryDirectory
      .resolve("db/latest_debug.version")
      .writeText("1007")
    val directory = temporaryDirectory.resolve("db/ignored.changed")
    directory.createDirectory()

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1007))
    assertThat(directory.isDirectory()).isTrue()
  }

  @Test
  fun `consumes submodule markers without reading their obsolete view names`() {
    val configuration = debugMigrationConfiguration(projectDirectory = temporaryDirectory)
    val database = debugMainDatabase
      .copy(views = listOf(mockView(name = "main_view")))
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = temporaryDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = DatabaseStructure.from(
        orderedTables = orderedTables,
        indexes = database.indices,
        views = database.views
      )
    )
    temporaryDirectory
      .resolve("db/latest_debug.version")
      .writeText("1007")
    val marker = temporaryDirectory
      .resolve("db/feature.changed")
      .toFile()
    marker.writeText("removed_feature_view\nrenamed_feature_view\n")

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1008))
    assertThat(marker.exists())
      .isFalse()
  }

  @Test
  fun `submodule retries retain pending SQL and mark another view change`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val configuration = debugMigrationConfiguration(
      projectDirectory = submoduleDirectory,
      mainModuleDirectory = mainDirectory
    )
    val baseDatabase = debugMainDatabase
      .copy(submoduleName = "feature")
    val orderedTables = CreationOrderedTables.from(baseDatabase.tables)
    val previous = DatabaseStructure.from(orderedTables)
      .copy(
        views = mapOf("old_view" to ViewStructure(name = "old_view"))
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
    sqlFile.parentFile.mkdirs()
    sqlFile.writeText("SELECT 1;\n")

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = baseDatabase.copy(views = listOf(mockView(name = "old_view")))
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(sqlFile.readText())
      .isEqualTo("SELECT 1;\n")

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = baseDatabase.copy(views = listOf(mockView(name = "new_view")))
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(sqlFile.readText())
      .isEqualTo("SELECT 1;\n")
    assertThat(mainMarker.readText())
      .isEmpty()
  }

  @Test
  fun `staged unchanged submodule retry retains same-version assets without a main marker`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val stagingDirectory = temporaryDirectory.resolve("staged")
    val configuration = debugMigrationConfiguration(
      projectDirectory = submoduleDirectory,
      mainModuleDirectory = mainDirectory,
      structureOutputDirectory = stagingDirectory
    )
    val database = debugMainDatabase
      .copy(
        submoduleName = "feature",
        views = listOf(mockView(name = "new_view"))
      )
    val orderedTables = CreationOrderedTables.from(database.tables)
    DatabaseStructureJson.write(
      file = submoduleDirectory.resolve("db/latest.struct").toFile(),
      structure = DatabaseStructure(
        views = mapOf("old_view" to ViewStructure(name = "old_view"))
      )
    )
    val assetsDirectory = submoduleDirectory.resolve("src/debug/assets")
    val sqlFile = assetsDirectory.resolve("feature1001.sql").toFile()
    val mainMarker = mainDirectory.resolve("db/feature.changed").toFile()

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(sqlFile.isFile)
      .isTrue()
    assertThat(DatabaseStructureJson.read(stagingDirectory.resolve("latest_feature.struct").toFile()))
      .isEqualTo(
        DatabaseStructure.from(
          orderedTables = orderedTables,
          indexes = database.indices,
          views = database.views
        )
      )
    assertThat(mainMarker.exists())
      .isFalse()
    val originalSql = sqlFile.readBytes()
    assertThat(originalSql)
      .isNotEmpty()

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(sqlFile.readBytes())
      .isEqualTo(originalSql)
    assertThat(mainMarker.exists())
      .isFalse()
  }

  @Test
  fun `completion failure restores migration artifacts version and submodule markers`() {
    val configuration = debugMigrationConfiguration(projectDirectory = temporaryDirectory)
    val database = debugMainDatabase
      .copy(views = listOf(mockView(name = "new_view")))
    val orderedTables = CreationOrderedTables.from(database.tables)
    val previous = DatabaseStructure.from(orderedTables)
      .copy(
        views = mapOf("old_view" to ViewStructure(name = "old_view"))
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
    versionFile.writeText("1020")
    val marker = temporaryDirectory
      .resolve("db/feature.changed")
      .toFile()
    marker.writeText("removed_feature_view\n")
    val assetsDirectory = temporaryDirectory
      .resolve("src/debug/assets")
      .toFile()
    check(assetsDirectory.mkdirs())
    val expiredSqlFile = assetsDirectory.resolve("1001.sql")
    expiredSqlFile.writeText("old debug migration\n")
    val sqlFile = assetsDirectory.resolve("1021.sql")
    sqlFile.writeText("previous sql\n")
    val failure = IOException("manager generation failed")

    val actual = assertThrows<IOException> {
      debugMigrationCoordinator(configuration)
        .handle(
          database = database,
          currentStructure = DatabaseStructure.from(
            orderedTables = orderedTables,
            indexes = database.indices,
            views = database.views
          ),
          completion = { outcome ->
            assertThat(outcome)
              .isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1021))
            throw failure
          }
        )
    }

    assertThat(actual)
      .isSameInstanceAs(failure)
    assertThat(DatabaseStructureJson.read(structureFile))
      .isEqualTo(previous)
    assertThat(versionFile.readText())
      .isEqualTo("1020")
    assertThat(expiredSqlFile.readText())
      .isEqualTo("old debug migration\n")
    assertThat(sqlFile.readText())
      .isEqualTo("previous sql\n")
    assertThat(marker.readText())
      .isEqualTo("removed_feature_view\n")
  }

  @Test
  fun `view-only debug rename advances version without publishing view resources`() {
    val configuration = debugMigrationConfiguration(projectDirectory = temporaryDirectory)
    val database = debugMainDatabase
      .copy(views = listOf(mockView(name = "new_view")))
    val orderedTables = CreationOrderedTables.from(database.tables)
    val previous = DatabaseStructure.from(orderedTables)
      .copy(
        views = mapOf("old_view" to ViewStructure(name = "old_view"))
      )
    DatabaseStructureJson.write(
      file = temporaryDirectory
        .resolve("db/latest.struct")
        .toFile(),
      structure = previous
    )
    temporaryDirectory
      .resolve("db/latest_debug.version")
      .writeText("1007")

    assertThat(
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    ).isEqualTo(DebugMigrationOutcome(databaseVersionOverride = 1008))
    assertThat(
      temporaryDirectory
        .resolve("db/latest_debug.version")
        .readText()
    ).isEqualTo("1008")
    assertThat(
      temporaryDirectory
        .resolve("src/debug/assets/1008.sql")
        .exists()
    ).isFalse()
  }

  @Test
  fun `submodule view-only change marks main version for advancement`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val configuration = debugMigrationConfiguration(
      projectDirectory = submoduleDirectory,
      mainModuleDirectory = mainDirectory
    )
    val database = debugMainDatabase
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
        configuration = configuration,
        database = database
      )
    ).isEqualTo(DebugMigrationOutcome())
    assertThat(
      mainDirectory
        .resolve("db/feature.changed")
        .exists()
    ).isTrue()
    assertThat(
      DatabaseStructureJson.read(
        mainDirectory
          .resolve("db/latest_feature.struct")
          .toFile()
      )?.views?.keys
    ).containsExactly("feature_view")
  }

  @Test
  fun `restores debug artifacts when staged submodule publication fails`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val blockedStaging = temporaryDirectory
      .resolve("blocked-staging")
      .toFile()
    blockedStaging.writeText("not a directory")
    val configuration = debugMigrationConfiguration(
      projectDirectory = submoduleDirectory,
      mainModuleDirectory = mainDirectory,
      structureOutputDirectory = blockedStaging.toPath()
    )
    val database = debugMainDatabase
      .copy(
        submoduleName = "feature",
        views = listOf(mockView(name = "new_view"))
      )
    val orderedTables = CreationOrderedTables.from(database.tables)
    val previous = DatabaseStructure.from(orderedTables)
      .copy(
        views = mapOf("old_view" to ViewStructure(name = "old_view"))
      )
    val structureFile = submoduleDirectory
      .resolve("db/latest.struct")
      .toFile()
    DatabaseStructureJson.write(
      file = structureFile,
      structure = previous
    )

    runDebugMigration(
      configuration = configuration,
      database = database
    )

    assertThat(DatabaseStructureJson.read(structureFile))
      .isEqualTo(previous)
    assertThat(
      mainDirectory
        .resolve("db/feature.changed")
        .exists()
    ).isFalse()
  }

  @Test
  fun `restores stale staged structures and markers when replacement fails`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")
    val stagingDirectory = temporaryDirectory.resolve("staged")
    val blockedTarget = stagingDirectory.resolve("latest_feature.struct")
    blockedTarget.createDirectories()
    val staleStructure = stagingDirectory
      .resolve("latest_old.struct")
      .toFile()
    staleStructure.writeText("stale structure")
    val staleMarker = stagingDirectory
      .resolve("old.changed")
      .toFile()
    staleMarker.writeText("stale marker")
    val configuration = debugMigrationConfiguration(
      projectDirectory = submoduleDirectory,
      mainModuleDirectory = mainDirectory,
      migrateDebug = false,
      structureOutputDirectory = stagingDirectory
    )
    val database = debugMainDatabase
      .copy(submoduleName = "feature")

    assertThrows<Exception> {
      runDebugMigration(
        configuration = configuration,
        database = database
      )
    }

    assertThat(staleStructure.readText())
      .isEqualTo("stale structure")
    assertThat(staleMarker.readText())
      .isEqualTo("stale marker")
    assertThat(blockedTarget.isDirectory())
      .isTrue()
  }

  private fun mockView(name: String) = mockViewElement(
    identity = mockSqliteSchemaIdentity(identifier = mockSqliteIdentifier(rawName = name))
  )

  private fun debugMigrationConfiguration(
    projectDirectory: Path,
    mainModuleDirectory: Path? = null,
    migrateDebug: Boolean = true,
    structureOutputDirectory: Path? = null
  ) = DebugMigrationConfiguration.from(
    CompilerOptions.from(
      debugMigrationOptions(
        projectDirectory = projectDirectory,
        mainModuleDirectory = mainModuleDirectory,
        migrateDebug = migrateDebug,
        structureOutputDirectory = structureOutputDirectory
      )
    )
  )

  private fun debugMigrationCoordinator(configuration: DebugMigrationConfiguration) = DebugMigrationCoordinator(
    configuration = configuration,
    logger = DEBUG_MAIN_DATABASE.logger
  )

  private val debugMainDatabase get() = DEBUG_MAIN_DATABASE.database

  private fun runDebugMigration(
    configuration: DebugMigrationConfiguration,
    database: GeneratedDatabaseElement
  ) = debugMigrationCoordinator(configuration)
    .handle(
      database = database,
      currentStructure = DatabaseStructure.from(
        orderedTables = CreationOrderedTables.from(database.tables),
        indexes = database.indices,
        views = database.views
      )
    )

  private companion object {
    val DEBUG_MAIN_DATABASE by lazy {
      SqliteMagicCompilation
        .compile(
          SourceFile.kotlin(
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
          ),
          processingStepsFactory = ::migrationCollectionSteps
        )
        .isOk()
        .environment
        .let { environment ->
          DebugMainDatabase(
            logger = environment.logger,
            database = environment.generatedDatabaseElement()
          )
        }
    }
  }

  private class DebugMainDatabase(
    val logger: KSPLogger,
    val database: GeneratedDatabaseElement
  )
}

private fun migrationCollectionSteps(environment: Environment) =
  transformerCollectionProcessingSteps(environment) + ModelCollectionStep(environment)
