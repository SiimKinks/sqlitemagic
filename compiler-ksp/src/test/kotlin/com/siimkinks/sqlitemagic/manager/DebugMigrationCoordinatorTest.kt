package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.model.ModelCollectionStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.transformer.transformerCollectionProcessingSteps
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.ProcessorCompilationResult
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class DebugMigrationCoordinatorTest : ProcessingStepsTest {
  override val processingSteps = ::migrationCollectionSteps

  @TempDir
  lateinit var temporaryDirectory: Path

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
        orderedTables = orderedTables
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

  private fun debugMigrationOptions(
    projectDirectory: Path,
    mainModuleDirectory: Path? = null
  ) = buildMap {
    put(
      key = "sqlitemagic.migrate.debug",
      value = "true"
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
      orderedTables = orderedTables
    )
}

private fun migrationCollectionSteps(environment: Environment): List<ProcessingStep> =
  transformerCollectionProcessingSteps(environment) + ModelCollectionStep(environment)
