package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.Dependencies
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_VERSION
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG
import com.siimkinks.sqlitemagic.index.IndexElement
import com.siimkinks.sqlitemagic.model.TableElement
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
import com.siimkinks.sqlitemagic.utils.insertBeforeLast
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.unwrapped
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal class GenClassesManagerContractTest {
  private val compiler = SqliteMagicCompiler(::genClassesManagerProcessingSteps)

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `generates the main database from optional and non-Long ID tables`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "MainDatabaseTables.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Database
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Table

            @Database(name = "library.db", version = 7)
            class LibraryDatabase

            @Table("notes")
            data class Note(
              val text: String
            )

            @Table("books")
            data class Book(
              @Id(autoIncrement = false) val isbn: String,
              val title: String
            )
          """
        ),
        kspOptions = mapOf(
          OPTION_DB_VERSION to "7",
          OPTION_VARIANT_DEBUG to "true"
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "public class SqliteMagicDatabase : GeneratedDatabase",
          """override val dbName: String? = "library.db"""",
          "override val dbVersion: Int = 7",
          "override val isDebug: Boolean = true",
          "SqliteMagic_Note_Adapter.TABLE_SCHEMA",
          "SqliteMagic_Book_Adapter.TABLE_SCHEMA",
          "override fun getNrOfTables(moduleName: String?): Int = 2"
        )
        generatedSource.assertDoesNotContain("createTemporarySchema")
      }
  }

  @Test
  fun `separates persistent and temporary schemas and preserves relationship order`() {
    compiler
      .compile(schemaTables())
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "override fun configureDatabase(db: SupportSQLiteDatabase)",
          "db.setForeignKeyConstraintsEnabled(true)",
          "db.execSQL(SqliteMagic_Parent_Adapter.TABLE_SCHEMA)",
          "db.execSQL(SqliteMagic_Child_Adapter.TABLE_SCHEMA)",
          "db.execSQL(SqliteMagic_SessionOwner_Adapter.TABLE_SCHEMA)",
          "db.execSQL(SqliteMagic_SessionCache_Adapter.TABLE_SCHEMA)"
        )
        generatedSource.assertContainsUnwrapped(
          "override fun createSchema(db: SupportSQLiteDatabase): Unit = " +
              "SqlUtil.createSchema(db = db, schema = this, temporary = false)",
          CREATE_TEMPORARY_SCHEMA_DELEGATION,
          "override fun migrateViews(db: SupportSQLiteDatabase): Unit = SqlUtil.migrateViews(db = db, schema = this)"
        )
        generatedSource
          .functionBody("createSchemaTables")
          .unwrapped()
          .assertContainsInOrder(
            "if (temporary)",
            "SqliteMagic_SessionOwner_Adapter.TABLE_SCHEMA",
            "SqliteMagic_SessionCache_Adapter.TABLE_SCHEMA",
            "else",
            "SqliteMagic_Parent_Adapter.TABLE_SCHEMA",
            "SqliteMagic_Child_Adapter.TABLE_SCHEMA"
          )
      }
      .apply {
        val database = environment.generatedDatabaseElement()
        val tablesByName = database.tables.associateBy(TableElement::tableName)

        assertThat(DatabaseStructure.from(CreationOrderedTables.from(database.tables))).isEqualTo(
          DatabaseStructure(
            tables = mapOf(
              "parents" to TableStructure.from(tablesByName.getValue("parents")),
              "children" to TableStructure.from(tablesByName.getValue("children"))
            ),
            temporaryTables = mapOf(
              "session_owners" to TableStructure.from(tablesByName.getValue("session_owners")),
              "session_cache" to TableStructure.from(tablesByName.getValue("session_cache"))
            )
          )
        )
      }
  }

  @Test
  fun `generates clear counts and the empty-module gate`() {
    compiler
      .compile(
        SourceFile.kotlin(
          name = "Empty.kt",
          contents = """
            package $PACKAGE

            class Empty
          """
        )
      )
      .isOk()
      .assertNotGeneratedSources("SqliteMagicDatabase.kt")

    compiler
      .compile(
        SourceFile.kotlin(
          name = "TransformerTable.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
            import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

            data class Token(val value: String)

            data class NumericToken(val value: Long)

            object TokenTransformers {
              @ObjectToDbValue
              fun toDatabaseValue(value: Token): String = value.value

              @DbValueToObject
              fun fromDatabaseValue(value: String): Token = Token(value)
            }

            object NumericTokenTransformers {
              @ObjectToDbValue
              fun toDatabaseValue(value: NumericToken): Long = value.value

              @DbValueToObject
              fun fromDatabaseValue(value: Long): NumericToken = NumericToken(value)
            }

            @Table("transformed_values")
            data class TransformedValue(
              val token: Token,
              val numericToken: NumericToken
            )
          """
        )
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContains(
          "override val dbName: String? = null",
          "override val submoduleNames: Array<String>?",
          "get() = null",
          """db.execSQL("DELETE FROM transformed_values")""",
          """changedTables.add("transformed_values")"""
        )
        generatedSource.assertContainsUnwrapped(
          "override fun clearData(db: SupportSQLiteDatabase): MutableScatterSet<String> = " +
              "SqlUtil.clearData(db = db, schema = this, tableCount = getNrOfTables(null))"
        )
      }
  }

  @Test
  fun `generates public submodule aggregation and module-aware adapters`() {
    val submodule = compiler
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
        )
      )
      .isOk()
      .assertGeneratedSources(
        "FeatureGeneratedClassesManager.kt",
        "SqliteMagic_FeatureItem_Adapter.kt"
      )
      .withGeneratedSource("FeatureGeneratedClassesManager.kt") {
        it.assertContains(
          "public object FeatureGeneratedClassesManager",
          "public fun getNrOfTables(moduleName: String?): Int = 1"
        )
      }
      .withGeneratedSource("SqliteMagic_FeatureItem_Adapter.kt") {
        it.assertContains("""override val moduleName: String? = "Feature"""")
      }

    compiler
      .compile(
        mainDatabaseWithSubmodule(),
        classpaths = listOf(submodule.outputDirectory)
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") {
        it.assertContainsUnwrapped(
          "FeatureGeneratedClassesManager.configureDatabase(db)",
          "FeatureGeneratedClassesManager.createSchemaTables(db = db, temporary = temporary)",
          "FeatureGeneratedClassesManager.collectGeneratedViews(views = views)",
          "FeatureGeneratedClassesManager.createSchemaIndexes(db = db, temporary = temporary)",
          "FeatureGeneratedClassesManager.clearTables(db = db, changedTables = changedTables)",
          CREATE_TEMPORARY_SCHEMA_DELEGATION,
          "override val submoduleNames: Array<String>?",
          """get() = arrayOf("Feature")""",
          """"Feature" -> FeatureGeneratedClassesManager.getNrOfTables(moduleName)"""
        )
      }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("modulesWithoutManagerContents")
  fun `rejects SqliteMagic annotations in a module without manager contents`(
    fileName: String,
    contents: String
  ) {
    compiler
      .compile(
        SourceFile.kotlin(
          name = fileName,
          contents = contents
        )
      )
      .assertCompilationError(
        "SqliteMagic annotations require at least one @Table or @View in the same module",
        fileName
      )
      .assertNotGeneratedSources("FeatureGeneratedClassesManager.kt", "SqliteMagicDatabase.kt")
  }

  @Test
  fun `persists complete debug migration state`() {
    compiler
      .compile(
        schemaTables(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory)
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")

    temporaryDirectory
      .resolve("db/latest.struct")
      .readText()
      .assertContains(
        """"parents"""",
        """"children"""",
        """"indices":{}""",
        """"temporaryTables"""",
        "session_owners",
        "session_cache"
      )
  }

  @Test
  fun `restores debug migration state when manager generation fails`() {
    val structureFile = temporaryDirectory
      .resolve("db/latest.struct")
      .toFile()
    DatabaseStructureJson.write(
      file = structureFile,
      structure = DatabaseStructure()
    )
    val versionFile = temporaryDirectory.resolve("db/latest_debug.version")
    versionFile.writeText("1007")
    val marker = temporaryDirectory.resolve("db/feature.changed")
    marker.writeText("old_feature_view\n")

    SqliteMagicCompilation
      .compile(
        schemaTables(),
        kspOptions = debugMigrationOptions(projectDirectory = temporaryDirectory),
        processingStepsFactory = { environment ->
          genClassesManagerProcessingSteps(environment)
            .insertBeforeLast(ReserveManagerFileStep(environment))
        }
      )
      .assertCompilationError("SqliteMagicDatabase")

    assertThat(DatabaseStructureJson.read(structureFile))
      .isEqualTo(DatabaseStructure())
    assertThat(versionFile.readText())
      .isEqualTo("1007")
    assertThat(marker.readText())
      .isEqualTo("old_feature_view\n")
    assertThat(temporaryDirectory.resolve("src/debug/assets/1008.sql").exists())
      .isFalse()
  }

  @Test
  fun `includes tables and indexes generated in later rounds before finalizing the manager`() {
    SqliteMagicCompilation
      .compile(
        SourceFile.kotlin(
          name = "Initial.kt",
          contents = """
            package $PACKAGE

            class Initial
          """
        ),
        processingStepsFactory = { environment ->
          genClassesManagerProcessingSteps(environment)
            .insertBeforeLast(
              GeneratedSourceStep(
                environment = environment,
                fileName = "LateTable",
                contents = """
                  package $PACKAGE

                  import com.siimkinks.sqlitemagic.annotation.Index
                  import com.siimkinks.sqlitemagic.annotation.Table

                  @Table("late_tables")
                  data class LateTable(@Index("late_index") val value: String)
                """
              )
            )
        }
      )
      .isOk()
      .assertGeneratedSources(
        "SqliteMagic_LateTable_Adapter.kt",
        "SqliteMagicDatabase.kt"
      )
      .withGeneratedSource("SqliteMagicDatabase.kt") {
        it.assertContains(
          "db.execSQL(SqliteMagic_LateTable_Adapter.TABLE_SCHEMA)",
          "override fun getNrOfTables(moduleName: String?): Int = 1",
          "late_index"
        )
      }
      .apply {
        assertThat(environment.indexElements.values.map(IndexElement::name))
          .containsExactly("late_index")
      }
  }

  companion object {
    private const val CREATE_TEMPORARY_SCHEMA_DELEGATION =
      "override fun createTemporarySchema(db: SupportSQLiteDatabase): Unit = " +
          "SqlUtil.createSchema(db = db, schema = this, temporary = true)"

    @JvmStatic
    fun modulesWithoutManagerContents() = listOf(
      Arguments.of(
        "EmptyFeatureDatabase.kt",
        """
          package $PACKAGE

          import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase

          @SubmoduleDatabase("feature")
          class FeatureDatabase
        """
      ),
      Arguments.of(
        "TransformersOnly.kt",
        """
          package $PACKAGE

          import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
          import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

          data class Token(val value: String)

          object TokenTransformers {
            @ObjectToDbValue
            fun toDatabaseValue(value: Token): String = value.value

            @DbValueToObject
            fun fromDatabaseValue(value: String): Token = Token(value)
          }
        """
      )
    )
  }
}

private class ReserveManagerFileStep(
  private val environment: Environment
) : ProcessingStep {
  override fun finish(): ProcessingStepResult {
    val className = environment.getGenClassesManagerClassName()
    environment.codeGenerator
      .createNewFile(
        dependencies = Dependencies(aggregating = true),
        packageName = className.packageName,
        fileName = className.simpleName
      )
      .bufferedWriter()
      .use { it.write("package ${className.packageName}\nclass ${className.simpleName}\n") }
    return Continue
  }
}
