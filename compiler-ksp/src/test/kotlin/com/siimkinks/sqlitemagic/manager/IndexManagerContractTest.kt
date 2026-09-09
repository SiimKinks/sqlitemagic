package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.index.indexProcessingSteps
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.siimkinks.sqlitemagic.utils.assertContains
import com.siimkinks.sqlitemagic.utils.assertContainsInOrder
import com.tschuchort.compiletesting.SourceFile
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

internal class IndexManagerContractTest : ProcessingStepsTest {
  override val processingSteps = ::indexProcessingSteps

  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `creates local persistent indexes after ordered tables and submodule delegation`() {
    val submodule = SqliteMagicCompilation
      .compile(indexedSubmodule())
      .isOk()

    submodule
      .compile(indexedMainDatabase())
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "FeatureGeneratedClassesManager.createSchema(db)",
          "SqliteMagic_MainParent_Adapter.TABLE_SCHEMA",
          "SqliteMagic_MainChild_Adapter.TABLE_SCHEMA",
          "CREATE UNIQUE INDEX IF NOT EXISTS",
          "main_parent_name_index"
        )
        generatedSource.assertContains(
          "main_parents",
          "name"
        )
      }
  }

  @Test
  fun `creates local temporary indexes after temporary submodule delegation`() {
    val submodule = SqliteMagicCompilation
      .compile(indexedSubmodule())
      .isOk()

    submodule
      .compile(indexedMainDatabase())
      .isOk()
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        val temporarySchema = generatedSource.substringAfter(
          "override fun createTemporarySchema(db: SupportSQLiteDatabase)"
        )
        temporarySchema.assertContainsInOrder(
          "FeatureGeneratedClassesManager.createTemporarySchema(db)",
          "SqliteMagic_MainSession_Adapter.TABLE_SCHEMA",
          "CREATE INDEX IF NOT EXISTS",
          "main_session_name_index"
        )
      }
  }

  @Test
  fun `creates indexes in the submodule persistent manager after its tables`() {
    SqliteMagicCompilation
      .compile(indexedSubmodule())
      .isOk()
      .assertGeneratedSources("FeatureGeneratedClassesManager.kt")
      .withGeneratedSource("FeatureGeneratedClassesManager.kt") { generatedSource ->
        generatedSource.assertContainsInOrder(
          "SqliteMagic_FeatureItem_Adapter.TABLE_SCHEMA",
          "CREATE INDEX IF NOT EXISTS",
          "feature_name_index"
        )
        generatedSource.assertContains(
          "feature_items",
          "name"
        )
      }
  }

  @Test
  fun `creates temporary indexes in the temporary schema method used on open`() {
    SqliteMagicCompilation
      .compile(indexedTemporaryTable())
      .isOk()
      .assertGeneratedSources("SqliteMagicDatabase.kt")
      .withGeneratedSource("SqliteMagicDatabase.kt") { generatedSource ->
        val temporarySchema = generatedSource.substringAfter(
          "override fun createTemporarySchema(db: SupportSQLiteDatabase)"
        )
        temporarySchema.assertContainsInOrder(
          "SqliteMagic_SessionItem_Adapter.TABLE_SCHEMA",
          "CREATE UNIQUE INDEX IF NOT EXISTS",
          "session_name_index"
        )
        temporarySchema.assertContains(
          "session_items",
          "name"
        )
      }
  }

  @Test
  fun `latest structure records persistent and temporary tables and indexes`() {
    SqliteMagicCompilation
      .compile(
        *indexedPersistentAndTemporaryTables().toTypedArray(),
        kspOptions = debugMigrationOptions()
      )
      .isOk()

    val latestStructure = Files.readString(
      temporaryDirectory.resolve("db/latest.struct")
    )
    latestStructure.assertContains(
      "\"tables\"",
      "persistent_items",
      "persistent_name_index",
      "\"temporaryTables\"",
      "temporary_items",
      "\"temporaryIndices\"",
      "temporary_name_index"
    )
  }

  @Test
  fun `temporary-only changes do not create migration SQL or a version marker`() {
    val options = debugMigrationOptions()
    SqliteMagicCompilation
      .compile(
        indexedTemporaryTable(tableName = "temporary_items_v1"),
        kspOptions = options
      )
      .isOk()
    SqliteMagicCompilation
      .compile(
        indexedTemporaryTable(tableName = "temporary_items_v2"),
        kspOptions = options
      )
      .isOk()

    assertThat(
      Files.exists(temporaryDirectory.resolve("db/latest_debug.version"))
    ).isFalse()
    assertThat(
      Files.exists(temporaryDirectory.resolve("src/debug/assets/1001.sql"))
    ).isFalse()
    Files.readString(temporaryDirectory.resolve("db/latest.struct")).assertContains(
      "\"temporaryTables\"",
      "temporary_items_v2",
      "\"temporaryIndices\"",
      "session_name_index"
    )
  }

  @Test
  fun `publishes the current submodule structure when automatic debug migration is disabled`() {
    val mainDirectory = temporaryDirectory.resolve("main")
    val submoduleDirectory = temporaryDirectory.resolve("feature")

    SqliteMagicCompilation
      .compile(
        indexedSubmodule(),
        kspOptions = mapOf(
          "sqlitemagic.migrate.debug" to "false",
          "sqlitemagic.project.dir" to submoduleDirectory.toString(),
          "sqlitemagic.main.module.path" to mainDirectory.toString(),
          "sqlitemagic.variant.name" to "debug",
          "sqlitemagic.variant.debug" to "true"
        )
      )
      .isOk()

    val publishedStructure = mainDirectory.resolve("db/latest_feature.struct")
    assertThat(Files.exists(publishedStructure)).isTrue()
    Files.readString(publishedStructure).assertContains(
      "feature_items",
      "feature_name_index"
    )
  }

  private fun debugMigrationOptions() = mapOf(
    "sqlitemagic.migrate.debug" to "true",
    "sqlitemagic.project.dir" to temporaryDirectory.toString(),
    "sqlitemagic.variant.name" to "debug",
    "sqlitemagic.variant.debug" to "true"
  )
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

private fun indexedMainDatabase() = SourceFile.kotlin(
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
)

private fun indexedTemporaryTable(
  tableName: String = "session_items",
  indexName: String = "session_name_index"
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
        value = "$indexName",
        unique = true
      )
      val name: String
    )
  """
)

private fun indexedPersistentAndTemporaryTables() = listOf(
  SourceFile.kotlin(
    name = "PersistentItem.kt",
    contents = """
      package $PACKAGE

      import com.siimkinks.sqlitemagic.annotation.Id
      import com.siimkinks.sqlitemagic.annotation.Index
      import com.siimkinks.sqlitemagic.annotation.Table

      @Table("persistent_items")
      data class PersistentItem(
        @Id val id: Long,
        @Index("persistent_name_index") val name: String
      )
    """
  ),
  indexedTemporaryTable(
    tableName = "temporary_items",
    indexName = "temporary_name_index"
  )
)
