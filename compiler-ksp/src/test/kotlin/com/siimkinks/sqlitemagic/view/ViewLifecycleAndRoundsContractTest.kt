package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_MIGRATE_DEBUG
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_PROJECT_DIR
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_STRUCTURE_OUTPUT_DIR
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_NAME
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.mainDatabaseWithSubmodule
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.tschuchort.compiletesting.SourceFile
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class ViewLifecycleAndRoundsContractTest : ProcessingStepsTest {
  override val processingSteps = ::viewProcessingSteps

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `generates a manager for a view-only main module without counting views as entities`() {
    SqliteMagicCompilation
      .compile(
        persistentViewSource(
          className = "ViewOnlyLifecycle",
          viewName = "view_only_lifecycle"
        ),
        databaseSource(
          className = "ViewOnlyDatabase",
          version = 7
        ),
        kspOptions = debugOptions()
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_ViewOnlyLifecycle_Dao.kt",
        "ViewOnlyLifecycleTable.kt",
        "SqliteMagicDatabase.kt"
      )
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "CREATE VIEW view_only_lifecycle AS",
          "migrateViews",
          "getNrOfTables(moduleName: String?): Int = 0"
        )
        generatedSource.assertDoesNotContain("DELETE FROM view_only_lifecycle")
      }
  }

  @Test
  fun `does not require executing a defining query while compiling a manager`() {
    SqliteMagicCompilation
      .compile(
        persistentViewSource(
          className = "ThrowingQueryLifecycle",
          viewName = "throwing_query_lifecycle",
          queryInitializer = """error("the defining query must not execute during compilation")"""
        ),
        databaseSource(
          className = "ThrowingQueryDatabase",
          version = 8
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "throwing_query_lifecycle",
          "migrateViews"
        )
      }
  }

  @Test
  fun `generates a view-only submodule manager and publishes current ownership`() {
    val structureOutputDirectory = temporaryDirectory.resolve("staged")

    SqliteMagicCompilation
      .compile(
        submoduleDatabaseSource(),
        persistentViewSource(
          className = "FeatureLifecycleView",
          viewName = "feature_lifecycle_view"
        ),
        kspOptions = debugOptions(
          structureOutputDirectory = structureOutputDirectory
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_FeatureLifecycleView_Dao.kt",
        "FeatureLifecycleViewTable.kt",
        "FeatureGeneratedClassesManager.kt"
      )
      .withGeneratedSource("FeatureGeneratedClassesManager.kt") { generatedSource ->
        generatedSource.assertContains(
          "feature_lifecycle_view",
          "migrateViews",
          "getNrOfTables(moduleName: String?): Int = 0"
        )
      }

    val structureFile = structureOutputDirectory.resolve("latest_feature.struct")
    assertThat(Files.exists(structureFile)).isTrue()
    structureFile.toFile().readText().assertContains(
      """"views"""",
      "feature_lifecycle_view"
    )
  }

  @Test
  fun `aggregates a configured view-only submodule in the main manager`() {
    val submodule = SqliteMagicCompilation
      .compile(
        submoduleDatabaseSource(),
        persistentViewSource(
          className = "AggregatedFeatureView",
          viewName = "aggregated_feature_view"
        )
      )
      .isOk()

    submodule
      .compile(
        mainDatabaseWithSubmodule(),
        kspOptions = debugOptions()
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "FeatureGeneratedClassesManager.createSchema(db)",
          "FeatureGeneratedClassesManager.createTemporarySchema(db)",
          "FeatureGeneratedClassesManager.migrateViews(db)",
          "FeatureGeneratedClassesManager.getNrOfTables(moduleName)"
        )
      }
  }

  @Test
  fun `accepts the approved temporary view annotation surface and emits temporary artifacts`() {
    SqliteMagicCompilation
      .compile(
        temporaryViewSource(),
        databaseSource(
          className = "TemporaryLifecycleDatabase",
          version = 9
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_TemporaryLifecycleView_Dao.kt",
        "TemporaryLifecycleViewTable.kt",
        "SqliteMagicDatabase.kt"
      )
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "createTemporarySchema(db)",
          "CREATE TEMPORARY VIEW temporary_lifecycle_view AS"
        )
        generatedSource.assertDoesNotContain(
          "CREATE VIEW temporary_lifecycle_view AS"
        )
      }
  }

  @Test
  fun `publishes current persistent view ownership and removes stale names from a legacy snapshot`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct")
    structureFile.toFile().apply {
      parentFile.mkdirs()
      writeText(legacySnapshotWithView(viewName = "retired_lifecycle_view"))
    }

    SqliteMagicCompilation
      .compile(
        tableSource(
          className = "LifecycleMigrationRow",
          tableName = "lifecycle_migration_rows"
        ),
        persistentViewSource(
          className = "CurrentLifecycleView",
          viewName = "current_lifecycle_view"
        ),
        databaseSource(
          className = "LifecycleMigrationDatabase",
          version = 10
        ),
        kspOptions = debugOptions()
      )
      .isOk()

    assertThat(Files.exists(structureFile)).isTrue()
    structureFile.toFile().readText().apply {
      assertContains(
        """"views"""",
        "current_lifecycle_view"
      )
      assertDoesNotContain("retired_lifecycle_view")
    }
  }

  @Test
  fun `keeps lifecycle ordering for owned view removal and recreation on table migration`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct")
    structureFile.toFile().apply {
      parentFile.mkdirs()
      writeText(legacySnapshotWithView(viewName = "old_ordered_lifecycle_view"))
    }

    SqliteMagicCompilation
      .compile(
        tableSource(
          className = "OrderedLifecycleRow",
          tableName = "ordered_lifecycle_rows"
        ),
        persistentViewSource(
          className = "NewOrderedLifecycleView",
          viewName = "new_ordered_lifecycle_view"
        ),
        databaseSource(
          className = "OrderedLifecycleDatabase",
          version = 11
        ),
        kspOptions = debugOptions()
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "dropOwnedViews",
          "runMigrationScripts",
          "createViews"
        )
        generatedSource.assertContains(
          "old_ordered_lifecycle_view",
          "new_ordered_lifecycle_view"
        )
      }
  }

  @Test
  fun `recreates views for an explicit query-only database version bump`() {
    SqliteMagicCompilation
      .compile(
        persistentViewSource(
          className = "QueryOnlyLifecycleView",
          viewName = "query_only_lifecycle_view"
        ),
        databaseSource(
          className = "QueryOnlyLifecycleDatabase",
          version = 12
        ),
        kspOptions = debugOptions()
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "override fun getDbVersion(): Int = 12",
          "migrateViews",
          "query_only_lifecycle_view"
        )
      }
  }

  @Test
  fun `rejects a persistent view name colliding with a table identity`() {
    SqliteMagicCompilation
      .compile(
        tableSource(
          className = "LifecycleCollisionTable",
          tableName = "SHARED_LIFECYCLE_IDENTITY"
        ),
        persistentViewSource(
          className = "LifecycleCollisionView",
          viewName = "shared_lifecycle_identity"
        ),
        databaseSource(
          className = "LifecycleCollisionDatabase",
          version = 13
        )
      )
      .assertCompilationError("shared_lifecycle_identity")
  }

  @Test
  fun `does not publish a manager after view validation fails`() {
    SqliteMagicCompilation
      .compile(
        tableSource(
          className = "InvalidLifecycleTable",
          tableName = "invalid_lifecycle_rows"
        ),
        invalidViewSource(),
        databaseSource(
          className = "InvalidLifecycleDatabase",
          version = 14
        )
      )
      .assertCompilationError(
        "@View must declare exactly one @ViewQuery",
        "InvalidLifecycleView"
      )
      .assertNotGeneratedSources("SqliteMagicDatabase.kt")
  }

  @Test
  fun `defers an inferred view query that refers to a table generated in a later round`() {
    SqliteMagicCompilation
      .compile(
        inferredGeneratedTableViewSource(),
        processingStepsFactory = { environment ->
          val steps = viewProcessingSteps(environment)
          steps.dropLast(1) + GeneratedSourceStep(
            environment = environment,
            fileName = "LateLifecycleRow",
            contents = lateLifecycleRowSource()
          ) + steps.last()
        }
      )
      .isOk()
      .assertGeneratedSources(
        "LateLifecycleRowTable.kt",
        "SqliteMagic_InferredGeneratedTableView_Dao.kt",
        "InferredGeneratedTableViewTable.kt"
      )
  }

  @Test
  fun `retries a later generated view and emits one artifact per stable identity`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "LaterViewSeed.kt",
          contents = "package $PACKAGE\nclass LaterViewSeed"
        ),
        processingStepsFactory = { environment ->
          val steps = viewProcessingSteps(environment)
          steps.dropLast(1) + GeneratedSourceStep(
            environment = environment,
            fileName = "LateLifecycleView",
            contents = lateLifecycleViewSource()
          ) + steps.last()
        }
      )
      .isOk()
      .apply {
        val generatedSourceNames = generatedSourceNames()
        assertThat(
          generatedSourceNames.count(
            "SqliteMagic_LateLifecycleView_Dao.kt"::equals
          )
        ).isEqualTo(1)
        assertThat(
          generatedSourceNames.count(
            "LateLifecycleViewTable.kt"::equals
          )
        ).isEqualTo(1)
      }
  }

  @Test
  fun `does not publish a manager after a later-round view validation failure`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "LateInvalidViewSeed.kt",
          contents = "package $PACKAGE\nclass LateInvalidViewSeed"
        ),
        processingStepsFactory = { environment ->
          val steps = viewProcessingSteps(environment)
          steps.dropLast(1) + GeneratedSourceStep(
            environment = environment,
            fileName = "LateInvalidLifecycleView",
            contents = lateInvalidLifecycleViewSource()
          ) + steps.last()
        }
      )
      .assertCompilationError(
        "@View must declare exactly one @ViewQuery",
        "LateInvalidLifecycleView"
      )
      .assertNotGeneratedSources("SqliteMagicDatabase.kt")
  }

  private fun debugOptions(
    projectDirectory: Path = temporaryDirectory,
    structureOutputDirectory: Path? = null
  ) = buildMap {
    put(
      key = OPTION_MIGRATE_DEBUG,
      value = "true"
    )
    put(
      key = OPTION_PROJECT_DIR,
      value = projectDirectory.toString()
    )
    put(
      key = OPTION_VARIANT_NAME,
      value = "debug"
    )
    put(
      key = OPTION_VARIANT_DEBUG,
      value = "true"
    )
    structureOutputDirectory?.let { directory ->
      put(
        key = OPTION_STRUCTURE_OUTPUT_DIR,
        value = directory.toString()
      )
    }
  }

  private fun persistentViewSource(
    className: String,
    viewName: String,
    queryInitializer: String = """error("compile-only view query")"""
  ) = ViewSources.view(
    name = className,
    body = """
      @View("$viewName")
      data class $className(
        @ViewColumn("value") val value: String
      ) {
        companion object {
          @ViewQuery
          val QUERY: CompiledSelect<String, Select1> = $queryInitializer
        }
      }
    """,
    packageName = PACKAGE
  )

  private fun temporaryViewSource() = SourceFile.kotlin(
    name = "TemporaryLifecycleView.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.CompiledSelect
      import com.siimkinks.sqlitemagic.Select
      import com.siimkinks.sqlitemagic.annotation.View
      import com.siimkinks.sqlitemagic.annotation.ViewColumn
      import com.siimkinks.sqlitemagic.annotation.ViewOption
      import com.siimkinks.sqlitemagic.annotation.ViewQuery

      @View(
        value = "temporary_lifecycle_view",
        options = [ViewOption.TEMPORARY]
      )
      data class TemporaryLifecycleView(
        @ViewColumn("value") val value: String
      ) {
        companion object {
          @ViewQuery
          val QUERY: CompiledSelect<String, Select.Select1> =
            error("compile-only temporary view query")
        }
      }
    """
  )

  private fun invalidViewSource() = ViewSources.view(
    name = "InvalidLifecycleView",
    body = """
      @View("invalid_lifecycle_view")
      data class InvalidLifecycleView(
        @ViewColumn("value") val value: String
      )
    """,
    packageName = PACKAGE
  )

  private fun inferredGeneratedTableViewSource() = SourceFile.kotlin(
    name = "InferredGeneratedTableView.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.CompiledSelect
      import com.siimkinks.sqlitemagic.Select
      import com.siimkinks.sqlitemagic.annotation.View
      import com.siimkinks.sqlitemagic.annotation.ViewColumn
      import com.siimkinks.sqlitemagic.annotation.ViewQuery
      import com.siimkinks.sqlitemagic.LateLifecycleRowTable

      private fun inferredLateQuery(): CompiledSelect<String, Select.Select1> {
        LateLifecycleRowTable.LATE_LIFECYCLE_ROW
        return error("compile-only inferred query")
      }

      @View("inferred_generated_table_view")
      data class InferredGeneratedTableView(
        @ViewColumn("value") val value: String
      ) {
        companion object {
          @ViewQuery
          val QUERY = inferredLateQuery()
        }
      }
    """
  )

  @Language("kotlin")
  private fun lateLifecycleRowSource() = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Table

    @Table("late_lifecycle_rows")
    data class LateLifecycleRow(
      val value: String
    )
  """

  private fun lateLifecycleViewSource() = ViewSources.viewContents(
    body = """
      @View("late_lifecycle_view")
      data class LateLifecycleView(
        @ViewColumn("value") val value: String
      ) {
        companion object {
          @ViewQuery
          val QUERY: CompiledSelect<String, Select1> =
            error("compile-only later view query")
        }
      }
    """,
    packageName = PACKAGE
  )

  @Language("kotlin")
  private fun lateInvalidLifecycleViewSource() = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Id
    import com.siimkinks.sqlitemagic.annotation.Table
    import com.siimkinks.sqlitemagic.annotation.View
    import com.siimkinks.sqlitemagic.annotation.ViewColumn

    @Table("late_invalid_lifecycle_rows")
    data class LateInvalidLifecycleRow(
      @Id val id: Long
    )

    @View("late_invalid_lifecycle_view")
    data class LateInvalidLifecycleView(
      @ViewColumn("value") val value: String
    )
  """

  private fun tableSource(
    className: String,
    tableName: String
  ) = SourceFile.kotlin(
    name = "$className.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Id
      import com.siimkinks.sqlitemagic.annotation.Table

      @Table("$tableName")
      data class $className(
        @Id val id: Long,
        val value: String
      )
    """
  )

  private fun databaseSource(
    className: String,
    version: Int
  ) = SourceFile.kotlin(
    name = "$className.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Database

      @Database(name = "lifecycle.db", version = $version)
      class $className
    """
  )

  private fun submoduleDatabaseSource() = SourceFile.kotlin(
    name = "FeatureDatabase.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase

      @SubmoduleDatabase("feature")
      class FeatureDatabase
    """
  )

  @Language("json")
  private fun legacySnapshotWithView(viewName: String) = """
    {
      "tables": {},
      "indices": {},
      "temporaryTables": {},
      "temporaryIndices": {},
      "views": {
        "$viewName": {
          "name": "$viewName",
          "schema": "main",
          "module": null
        }
      }
    }
  """.trimIndent()

  private class GeneratedSourceStep(
    private val environment: Environment,
    private val fileName: String,
    private val contents: String
  ) : ProcessingStep {
    private var generated = false

    override fun process(resolver: Resolver): ProcessingStepResult {
      if (generated) return Continue
      generated = true
      environment.codeGenerator
        .createNewFile(
          dependencies = Dependencies(aggregating = false),
          packageName = PACKAGE,
          fileName = fileName
        )
        .bufferedWriter()
        .use { it.write(contents) }
      return Continue
    }
  }
}
