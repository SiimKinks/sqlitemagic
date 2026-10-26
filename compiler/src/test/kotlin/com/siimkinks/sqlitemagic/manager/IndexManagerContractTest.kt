package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.index.indexProcessingSteps
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompiler
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.siimkinks.sqlitemagic.utils.assertDoesNotContain
import com.siimkinks.sqlitemagic.utils.assertGeneratedSources
import com.siimkinks.sqlitemagic.utils.debugMigrationOptions
import com.siimkinks.sqlitemagic.utils.functionBody
import com.siimkinks.sqlitemagic.utils.isOk
import com.siimkinks.sqlitemagic.utils.unwrapped
import com.siimkinks.sqlitemagic.utils.withGeneratedSource
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

internal class IndexManagerContractTest {
  private val compiler = SqliteMagicCompiler(::indexProcessingSteps)

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `creates indexes after tables in submodule and main managers with submodule delegation`() {
    val submodule = compiler
      .compile(indexedSubmodule())
      .isOk()
      .assertGeneratedSources("FeatureGeneratedClassesManager.kt")
      .withGeneratedSource("FeatureGeneratedClassesManager.kt") { generatedSource ->
        generatedSource
          .functionBody("createSchemaTables")
          .unwrapped()
          .assertContainsInOrder(
            "if (!temporary)",
            "SqliteMagic_FeatureItem_Adapter.TABLE_SCHEMA"
          )
        generatedSource
          .functionBody("createSchemaIndexes")
          .unwrapped()
          .assertContainsInOrder(
            "if (!temporary)",
            """CREATE INDEX IF NOT EXISTS main.\"feature_name_index\" ON \"feature_items\" (\"name\")"""
          )
      }

    compiler
      .compile(
        SourceFile.kotlin(
          name = "MainDatabase.kt",
          contents = """
            package $PACKAGE

            import com.siimkinks.sqlitemagic.annotation.Column
            import com.siimkinks.sqlitemagic.annotation.Database
            import com.siimkinks.sqlitemagic.annotation.Id
            import com.siimkinks.sqlitemagic.annotation.Index
            import com.siimkinks.sqlitemagic.annotation.Table
            import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

            @Database(submodules = [FeatureDatabase::class])
            class MainDatabase

            @Table("main_children")
            data class MainChild(
              @Id val id: Long,
              @Column(handleRecursively = true) val parent: MainParent
            )

            @Table("main_parents")
            data class MainParent(
              @Id val id: Long,
              @Index(
                value = "main_parent_name_index",
                unique = true
              )
              val name: String
            )

            @Table(
              value = "main_sessions",
              options = [TEMPORARY]
            )
            data class MainSession(
              @Id val id: Long,
              @Index("main_session_name_index") val name: String
            )
          """
        ),
        classpaths = listOf(submodule.outputDirectory)
      )
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource
          .functionBody("createSchemaTables")
          .unwrapped()
          .run {
            assertContainsInOrder(
              "FeatureGeneratedClassesManager.createSchemaTables(db = db, temporary = temporary)",
              "SqliteMagic_MainParent_Adapter.TABLE_SCHEMA",
              "SqliteMagic_MainChild_Adapter.TABLE_SCHEMA"
            )
            assertContainsInOrder(
              "FeatureGeneratedClassesManager.createSchemaTables(db = db, temporary = temporary)",
              "if (temporary)",
              "SqliteMagic_MainSession_Adapter.TABLE_SCHEMA",
              "else"
            )
            substringAfter("else").assertDoesNotContain("SqliteMagic_MainSession_Adapter.TABLE_SCHEMA")
          }
        generatedSource
          .functionBody("createSchemaIndexes")
          .unwrapped()
          .run {
            assertContainsInOrder(
              "FeatureGeneratedClassesManager.createSchemaIndexes(db = db, temporary = temporary)",
              """CREATE UNIQUE INDEX IF NOT EXISTS main.\"main_parent_name_index\" ON \"main_parents\" (\"name\")"""
            )
            substringAfter("else").assertContains("main_parent_name_index")
            substringBefore("else").assertDoesNotContain("main_parent_name_index")
            assertContainsInOrder(
              "FeatureGeneratedClassesManager.createSchemaIndexes(db = db, temporary = temporary)",
              "if (temporary)",
              "CREATE INDEX IF NOT EXISTS",
              "main_session_name_index",
              "else"
            )
            substringAfter("else").assertDoesNotContain("main_session_name_index")
          }
      }
  }

  @Test
  fun `creates temporary indexes in the temporary schema method used on open`() {
    compiler
      .compile(indexedTemporaryTable())
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource
          .functionBody("createSchemaTables")
          .unwrapped()
          .run {
            assertContainsInOrder(
              "if (temporary)",
              "SqliteMagic_SessionItem_Adapter.TABLE_SCHEMA"
            )
            assertDoesNotContain("else")
          }
        generatedSource
          .functionBody("createSchemaIndexes")
          .unwrapped()
          .run {
            assertContainsInOrder(
              "if (temporary)",
              """CREATE UNIQUE INDEX IF NOT EXISTS temp.\"session_name_index\" ON \"session_items\" (\"name\")"""
            )
            assertDoesNotContain("else")
          }
      }
  }

  @Test
  fun `temporary-only changes do not create migration SQL or a version marker`() {
    val options = debugMigrationOptions(projectDirectory = temporaryDirectory)
    compiler
      .compile(
        indexedTemporaryTable(tableName = "temporary_items_v1"),
        kspOptions = options
      )
      .isOk()
    compiler
      .compile(
        indexedTemporaryTable(tableName = "temporary_items_v2"),
        kspOptions = options
      )
      .isOk()

    assertThat(temporaryDirectory.resolve("db/latest_debug.version").exists()).isFalse()
    assertThat(temporaryDirectory.resolve("src/debug/assets/1001.sql").exists()).isFalse()
    temporaryDirectory
      .resolve("db/latest.struct")
      .readText()
      .assertContains(
        """"temporaryTables"""",
        "temporary_items_v2",
        """"temporaryIndices"""",
        "session_name_index"
      )
  }

  @Test
  fun `publishes the current submodule structure when automatic debug migration is disabled`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")

    compiler
      .compile(
        indexedSubmodule(),
        kspOptions = debugMigrationOptions(
          projectDirectory = submoduleDirectory,
          migrateDebug = false,
          mainModuleDirectory = mainDirectory
        )
      )
      .isOk()

    val publishedStructure = mainDirectory.resolve("db/latest_feature.struct")
    assertThat(publishedStructure.exists()).isTrue()
    publishedStructure
      .readText()
      .assertContains(
        "feature_items",
        "feature_name_index"
      )
  }
}

private fun indexedSubmodule() = SourceFile.kotlin(
  name = "FeatureDatabase.kt",
  contents = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Id
    import com.siimkinks.sqlitemagic.annotation.Index
    import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase
    import com.siimkinks.sqlitemagic.annotation.Table

    @SubmoduleDatabase("feature")
    class FeatureDatabase

    @Table("feature_items")
    data class FeatureItem(
      @Id val id: Long,
      @Index("feature_name_index") val name: String
    )
  """
)

private fun indexedTemporaryTable(
  tableName: String = "session_items"
) = SourceFile.kotlin(
  name = "SessionItem.kt",
  contents = """
    package $PACKAGE

    import com.siimkinks.sqlitemagic.annotation.Id
    import com.siimkinks.sqlitemagic.annotation.Index
    import com.siimkinks.sqlitemagic.annotation.Table
    import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

    @Table(
      value = "$tableName",
      options = [TEMPORARY]
    )
    data class SessionItem(
      @Id val id: Long,
      @Index(
        value = "session_name_index",
        unique = true
      )
      val name: String
    )
  """
)
