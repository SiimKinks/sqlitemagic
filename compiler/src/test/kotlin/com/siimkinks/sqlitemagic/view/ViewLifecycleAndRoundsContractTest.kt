package com.siimkinks.sqlitemagic.view

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Resolver
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG
import com.siimkinks.sqlitemagic.manager.DatabaseStructureJson
import com.siimkinks.sqlitemagic.manager.ViewStructure
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult
import com.siimkinks.sqlitemagic.processing.ProcessingStepResult.Continue
import com.siimkinks.sqlitemagic.utils.GeneratedSourceStep
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.mainDatabaseWithSubmodule
import com.siimkinks.sqlitemagic.utils.assertCompilationError
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertContainsUnwrapped
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.assertNotGeneratedSources
import com.siimkinks.sqlitemagic.utils.debugMigrationOptions
import com.siimkinks.sqlitemagic.utils.functionBody
import com.siimkinks.sqlitemagic.utils.insertAfter
import com.siimkinks.sqlitemagic.utils.insertBeforeLast
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.unwrapped
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class ViewLifecycleAndRoundsContractTest {
  private val compiler = SqliteMagicCompiler(::viewProcessingSteps)

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `generates a manager for a view-only main module without counting views as entities`() {
    compiler
      .compile(
        persistentViewSource(
          className = "ViewOnlyLifecycle",
          viewName = "view_only_lifecycle"
        ),
        databaseSource(
          className = "ViewOnlyDatabase",
          version = 7
        ),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_ViewOnlyLifecycle_Dao.kt",
        "ViewOnlyLifecycleTable.kt",
        "SqliteMagicDatabase.kt"
      )
      .withGeneratedSource("SqliteMagicDatabase.kt") {
        it.assertContainsUnwrapped(
          "views.add(SqliteMagic_ViewOnlyLifecycle_Dao.GENERATED_VIEW)",
          "getNrOfTables(moduleName: String?): Int = 0"
        )
      }
  }

  @Test
  fun `delegates a persistent view with its table and index to the shared schema creation`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "MixedLifecycleRow.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table

            @Table("mixed_lifecycle_rows")
            data class MixedLifecycleRow(
              @Id val id: Long,
              @Index("mixed_lifecycle_index") val value: String
            )
          """
        ),
        persistentViewSource(
          className = "MixedLifecycleView",
          viewName = "mixed_lifecycle_view"
        ),
        databaseSource(
          className = "MixedLifecycleDatabase",
          version = 15
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "db.execSQL(SqliteMagic_MixedLifecycleRow_Adapter.TABLE_SCHEMA)",
          "views.add(SqliteMagic_MixedLifecycleView_Dao.GENERATED_VIEW)",
          """db.execSQL("CREATE INDEX IF NOT EXISTS main.""",
          "getNrOfTables(moduleName: String?): Int = 1",
          "DELETE FROM mixed_lifecycle_rows"
        )
        generatedSource.assertDoesNotContain("DELETE FROM mixed_lifecycle_view")
      }
  }

  @Test
  fun `publishes a view-only submodule manager and aggregates it in the main manager`() {
    val structureOutputDirectory = temporaryDirectory.resolve("aggregated-staged")
    val submodule = compiler
      .compile(
        submoduleDatabaseSource(),
        persistentViewSource(
          className = "AggregatedFeatureView",
          viewName = "aggregated_feature_view"
        ),
        kspOptions = debugMigrationOptions(
          projectDirectory = temporaryDirectory,
          structureOutputDirectory = structureOutputDirectory
        )
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_AggregatedFeatureView_Dao.kt",
        "AggregatedFeatureViewTable.kt",
        "FeatureGeneratedClassesManager.kt"
      )
      .withGeneratedSource("FeatureGeneratedClassesManager.kt") {
        it.assertContains(
          "views.add(SqliteMagic_AggregatedFeatureView_Dao.GENERATED_VIEW)",
          "getNrOfTables(moduleName: String?): Int = 0"
        )
      }

    val structureFile = structureOutputDirectory.resolve("latest_feature.struct")
    assertThat(structureFile.exists()).isTrue()
    structureFile.readText().assertContains(
      """"views"""",
      "aggregated_feature_view"
    )

    compiler
      .compile(
        mainDatabaseWithSubmodule(),
        persistentViewSource(
          className = "AggregatedMainView",
          viewName = "aggregated_main_view"
        ),
        temporaryViewSource(),
        kspOptions = debugMigrationOptions(
          projectDirectory = temporaryDirectory.resolve("aggregated-main"),
          structureInputDirectories = listOf(structureOutputDirectory)
        ),
        classpaths = listOf(submodule.outputDirectory)
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContainsUnwrapped(
          "FeatureGeneratedClassesManager.createSchemaTables(db = db, temporary = temporary)",
          "FeatureGeneratedClassesManager.collectGeneratedViews(views = views)",
          "FeatureGeneratedClassesManager.createSchemaIndexes(db = db, temporary = temporary)",
          "FeatureGeneratedClassesManager.getNrOfTables(moduleName)"
        )
        generatedSource
          .functionBody("collectGeneratedViews")
          .unwrapped()
          .assertContainsInOrder(
            "FeatureGeneratedClassesManager.collectGeneratedViews(views = views)",
            "views.add(SqliteMagic_AggregatedMainView_Dao.GENERATED_VIEW)",
            "views.add(SqliteMagic_TemporaryLifecycleView_Dao.GENERATED_VIEW)"
          )
      }
  }

  @Test
  fun `accepts the approved temporary view annotation surface and emits temporary artifacts`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "TemporaryLifecycleRow.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

            @Table(
              value = "temporary_lifecycle_rows",
              options = [TEMPORARY]
            )
            data class TemporaryLifecycleRow(
              @Id val id: Long,
              @Index("temporary_lifecycle_index") val value: String
            )
          """
        ),
        temporaryViewSource(),
        databaseSource(
          className = "TemporaryLifecycleDatabase",
          version = 9
        ),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_TemporaryLifecycleView_Dao.kt",
        "TemporaryLifecycleViewTable.kt",
        "SqliteMagicDatabase.kt"
      )
      .withGeneratedSource("TemporaryLifecycleViewTable.kt") { generatedSource ->
        generatedSource.assertContains(": Table<TemporaryLifecycleView>(")
        generatedSource
          .unwrapped()
          .substringAfter(": Table<TemporaryLifecycleView>(")
          .substringBefore(") {")
          .assertDoesNotContain("temporary =")
      }
      .withGeneratedSource("SqliteMagicDatabase.kt") {
        it.assertContains(
          "db.execSQL(SqliteMagic_TemporaryLifecycleRow_Adapter.TABLE_SCHEMA)",
          "views.add(SqliteMagic_TemporaryLifecycleView_Dao.GENERATED_VIEW)",
          "CREATE INDEX IF NOT EXISTS temp."
        )
      }

    temporaryDirectory
      .resolve("db/latest.struct")
      .readText()
      .assertContains(
        """"temporaryViews"""",
        "temporary_lifecycle_view"
      )
    assertThat(temporaryDirectory.resolve("db/latest_debug.version").exists()).isFalse()
    assertThat(temporaryDirectory.resolve("src/debug/assets/1001.sql").exists()).isFalse()
  }

  @Test
  fun `generates a temporary-view-only submodule manager and publishes current ownership`() {
    val structureOutputDirectory = temporaryDirectory.resolve("staged-temporary")

    compiler
      .compile(
        submoduleDatabaseSource(),
        temporaryViewSource(),
        kspOptions = debugMigrationOptions(
          projectDirectory = temporaryDirectory,
          structureOutputDirectory = structureOutputDirectory
        )
      )
      .isOk()
      .assertGeneratedSources("FeatureGeneratedClassesManager.kt")
      .withGeneratedSource("FeatureGeneratedClassesManager.kt") { generatedSource ->
        generatedSource.assertDoesNotContain(
          "fun createSchema(",
          "fun createTemporarySchema(",
          "fun migrateViews("
        )
        generatedSource.assertContains(
          "views.add(SqliteMagic_TemporaryLifecycleView_Dao.GENERATED_VIEW)",
          "getNrOfTables(moduleName: String?): Int = 0"
        )
      }

    structureOutputDirectory
      .resolve("latest_feature.struct")
      .readText()
      .assertContains(
        """"temporaryViews"""",
        "temporary_lifecycle_view"
      )
    assertThat(structureOutputDirectory.resolve("feature.changed").exists()).isFalse()
  }

  @Test
  fun `renaming only a temporary view updates current ownership without a persistent migration`() {
    val options = debugMigrationOptions(projectDirectory = temporaryDirectory)
    val structureFile = temporaryDirectory.resolve("db/latest.struct")

    compiler
      .compile(
        temporaryViewSource(),
        databaseSource(
          className = "TemporaryLifecycleDatabase",
          version = 9
        ),
        kspOptions = options
      )
      .isOk()
    structureFile.readText().assertContains("temporary_lifecycle_view")

    compiler
      .compile(
        temporaryViewSource(
          className = "RenamedTemporaryLifecycleView",
          viewName = "renamed_temporary_lifecycle_view"
        ),
        databaseSource(
          className = "TemporaryLifecycleDatabase",
          version = 9
        ),
        kspOptions = options
      )
      .isOk()

    structureFile.readText().apply {
      assertContains(""""temporaryViews"""", "renamed_temporary_lifecycle_view")
      assertDoesNotContain(""""temporary_lifecycle_view"""")
    }
    assertThat(temporaryDirectory.resolve("db/latest_debug.version").exists()).isFalse()
    assertThat(temporaryDirectory.resolve("src/debug/assets/1001.sql").exists()).isFalse()
  }

  @Test
  fun `publishes current persistent view ownership over a legacy snapshot and recreates current views`() {
    val structureFile = temporaryDirectory.resolve("db/latest.struct")
    structureFile
      .createParentDirectories()
      .writeText(
        """
          {
            "tables": {},
            "indices": {},
            "temporaryTables": {},
            "temporaryIndices": {},
            "views": {
              "retired_lifecycle_view": {
                "name": "retired_lifecycle_view",
                "schema": "main",
                "module": null
              }
            }
          }
        """.trimIndent()
      )

    compiler
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
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") {
        it.assertContains("views.add(SqliteMagic_CurrentLifecycleView_Dao.GENERATED_VIEW)")
      }

    assertThat(structureFile.exists()).isTrue()
    structureFile.readText().apply {
      assertContains(
        """"views"""",
        "current_lifecycle_view"
      )
      assertDoesNotContain("retired_lifecycle_view")
    }
    assertThat(temporaryDirectory.resolve("src/debug/assets/1001.views").exists())
      .isFalse()
  }

  @Test
  fun `recreates views for an explicit query-only database version bump`() {
    compiler
      .compile(
        persistentViewSource(
          className = "QueryOnlyLifecycleView",
          viewName = "query_only_lifecycle_view"
        ),
        databaseSource(
          className = "QueryOnlyLifecycleDatabase",
          version = 12
        ),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory) + (OPTION_VARIANT_DEBUG to "false")
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") {
        it.assertContains(
          "override val dbVersion: Int = 12",
          "views.add(SqliteMagic_QueryOnlyLifecycleView_Dao.GENERATED_VIEW)"
        )
      }
    assertThat(temporaryDirectory.resolve("src/debug/assets/1001.sql").exists())
      .isFalse()
    assertThat(temporaryDirectory.resolve("src/debug/assets/1001.views").exists())
      .isFalse()
  }

  @Test
  fun `rejects a main view colliding with a staged submodule table before publishing its manager`() {
    val structureOutputDirectory = temporaryDirectory.resolve("staged")
    val submodule = compiler
      .compile(
        submoduleDatabaseSource(),
        tableSource(
          className = "FeatureCollisionRow",
          tableName = "SHARED_STAGED_IDENTITY"
        ),
        kspOptions = debugMigrationOptions(
          projectDirectory = temporaryDirectory,
          structureOutputDirectory = structureOutputDirectory
        )
      )
      .isOk()
    assertThat(structureOutputDirectory.resolve("latest_feature.struct").exists()).isTrue()

    compiler
      .compile(
        mainDatabaseWithSubmodule(),
        persistentViewSource(
          className = "MainCollisionView",
          viewName = "shared_staged_identity"
        ),
        kspOptions = debugMigrationOptions(
          projectDirectory = temporaryDirectory.resolve("main"),
          structureInputDirectories = listOf(structureOutputDirectory)
        ),
        classpaths = listOf(submodule.outputDirectory)
      )
      .assertCompilationError(
        "Duplicate SQLite schema identifier 'SHARED_STAGED_IDENTITY'",
        "table 'SHARED_STAGED_IDENTITY' from Feature conflicts with view 'shared_staged_identity' from main"
      )
      .assertNotGeneratedSources("SqliteMagicDatabase.kt")
  }

  @Test
  fun `does not publish a manager after view validation fails`() {
    compiler
      .compile(
        tableSource(
          className = "InvalidLifecycleTable",
          tableName = "invalid_lifecycle_rows"
        ),
        ViewSources.view(
          name = "InvalidLifecycleView",
          body = """
            @View("invalid_lifecycle_view")
            data class InvalidLifecycleView(
              @ViewColumn("value") val value: String
            )
          """,
          packageName = PACKAGE
        ),
        databaseSource(
          className = "InvalidLifecycleDatabase",
          version = 14
        ),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .assertCompilationError(
        "@View must declare exactly one @ViewQuery",
        "InvalidLifecycleView"
      )
      .assertNotGeneratedSources("SqliteMagicDatabase.kt")
    assertNoDebugPublication()
  }

  @Test
  fun `defers an inferred view query that refers to a table generated in a later round`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "InferredGeneratedTableView.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.LateLifecycleRowTable
            import com.siimkinks.sqlitemagic.SelectQuery
            import com.siimkinks.sqlitemagic.annotation.View
            import com.siimkinks.sqlitemagic.annotation.ViewColumn
            import com.siimkinks.sqlitemagic.annotation.ViewQuery

            private fun inferredLateQuery(): SelectQuery<String, *> {
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
        ),
        databaseSource(
          className = "InferredGeneratedTableDatabase",
          version = 16
        ),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).insertBeforeLast(
            GeneratedSourceStep(
              environment = environment,
              fileName = "LateLifecycleRow",
              contents = """
                package $PACKAGE

                import com.siimkinks.sqlitemagic.annotation.Table

                @Table("late_lifecycle_rows")
                data class LateLifecycleRow(
                  val value: String
                )
              """
            )
          )
        }
      )
      .isOk()
      .assertGeneratedSources(
        "LateLifecycleRowTable.kt",
        "SqliteMagic_InferredGeneratedTableView_Dao.kt",
        "InferredGeneratedTableViewTable.kt",
        "SqliteMagicDatabase.kt"
      )
      .withGeneratedSource("SqliteMagicDatabase.kt") {
        it.assertContains(
          "db.execSQL(SqliteMagic_LateLifecycleRow_Adapter.TABLE_SCHEMA)",
          "views.add(SqliteMagic_InferredGeneratedTableView_Dao.GENERATED_VIEW)"
        )
      }
      .apply {
        assertThat(environment.processingRounds).isAtLeast(2)
        assertThat(environment.viewElements.values.map(ViewElement::modelName))
          .containsExactly("InferredGeneratedTableView")
        assertThat(environment.viewRoundElementsForCurrentRound).isEmpty()
      }
    val structure = DatabaseStructureJson.read(
      temporaryDirectory
        .resolve("db/latest.struct")
        .readText()
    )
    assertThat(structure.tables.keys).containsExactly("late_lifecycle_rows")
    assertThat(structure.views).containsExactly(
      "inferred_generated_table_view",
      ViewStructure(name = "inferred_generated_table_view")
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
        databaseSource(
          className = "LaterViewDatabase",
          version = 17
        ),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).insertBeforeLast(
            GeneratedSourceStep(
              environment = environment,
              fileName = "LateLifecycleView",
              contents = ViewSources.viewContents(
                body = """
                  @View("late_lifecycle_view")
                  data class LateLifecycleView(
                    @ViewColumn("value") val value: String
                  ) {
                    companion object {
                      @ViewQuery
                      val QUERY: SelectQuery<String, *> =
                        error("compile-only later view query")
                    }
                  }
                """,
                packageName = PACKAGE
              )
            )
          )
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
        withGeneratedSource("SqliteMagicDatabase.kt") {
          it.assertContains("views.add(SqliteMagic_LateLifecycleView_Dao.GENERATED_VIEW)")
        }
      }
    val structure = DatabaseStructureJson.read(
      temporaryDirectory
        .resolve("db/latest.struct")
        .readText()
    )
    assertThat(structure.tables).isEmpty()
    assertThat(structure.views).containsExactly(
      "late_lifecycle_view",
      ViewStructure(name = "late_lifecycle_view")
    )
  }

  @Test
  fun `fails instead of silently accepting a changed view with the same type identity`() {
    SqliteMagicCompilation
      .compile(
        persistentViewSource(
          className = "ChangedIdentityLifecycleView",
          viewName = "changed_identity_lifecycle_view"
        ),
        databaseSource(
          className = "ChangedIdentityLifecycleDatabase",
          version = 18
        ),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).insertAfter<ViewCollectionStep>(ChangedViewElementStep(environment))
        }
      )
      .assertCompilationError(
        "FileAlreadyExistsException",
        "SqliteMagic_ChangedIdentityLifecycleView_Dao.kt"
      )
      .assertNotGeneratedSources("SqliteMagicDatabase.kt")
    assertNoDebugPublication()
  }

  @Test
  fun `does not publish a manager after a later-round view validation failure`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "LateInvalidViewSeed.kt",
          contents = "package $PACKAGE\nclass LateInvalidViewSeed"
        ),
        databaseSource(
          className = "LateInvalidLifecycleDatabase",
          version = 19
        ),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory),
        processingStepsFactory = { environment ->
          viewProcessingSteps(environment).insertBeforeLast(
            GeneratedSourceStep(
              environment = environment,
              fileName = "LateInvalidLifecycleView",
              contents = """
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
            )
          )
        }
      )
      .assertCompilationError(
        "@View must declare exactly one @ViewQuery",
        "LateInvalidLifecycleView"
      )
      .assertNotGeneratedSources("SqliteMagicDatabase.kt")
    assertNoDebugPublication()
  }

  private fun assertNoDebugPublication() = listOf(
    "db/latest.struct",
    "db/latest_debug.version",
    "src/debug/assets/1001.sql",
    "src/debug/assets/1001.views"
  ).forEach { assertThat(temporaryDirectory.resolve(it).exists()).isFalse() }

  private fun persistentViewSource(
    className: String,
    viewName: String
  ) = ViewSources.view(
    name = className,
    body = """
      @View("$viewName")
      data class $className(
        @ViewColumn("value") val value: String
      ) {
        companion object {
          @ViewQuery
          val QUERY: SelectQuery<String, *> = error("compile-only view query")
        }
      }
    """,
    packageName = PACKAGE
  )

  private fun temporaryViewSource(
    className: String = "TemporaryLifecycleView",
    viewName: String = "temporary_lifecycle_view"
  ) = SourceFile.kotlin(
    name = "$className.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.SelectQuery
      import com.siimkinks.sqlitemagic.annotation.View
      import com.siimkinks.sqlitemagic.annotation.ViewColumn
      import com.siimkinks.sqlitemagic.annotation.ViewOption
      import com.siimkinks.sqlitemagic.annotation.ViewQuery

      @View(
        value = "$viewName",
        options = [ViewOption.TEMPORARY]
      )
      data class $className(
        @ViewColumn("value") val value: String
      ) {
        companion object {
          @ViewQuery
          val QUERY: SelectQuery<String, *> =
            error("compile-only temporary view query")
        }
      }
    """
  )

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

  private class ChangedViewElementStep(
    private val environment: Environment
  ) : ProcessingStep {
    private var changed = false

    override fun process(resolver: Resolver): ProcessingStepResult {
      if (changed) return Continue
      val roundElement = environment.viewRoundElementsForCurrentRound.singleOrNull()
        ?: return Continue
      changed = true
      environment.addViewElement(
        roundElement.copy(
          view = roundElement.view.copy(isPublic = !roundElement.view.isPublic)
        )
      )
      return Continue
    }
  }
}
