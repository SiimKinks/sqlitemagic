package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.GeneratedNames.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG
import com.siimkinks.sqlitemagic.dbconfig.DatabaseMetadata
import com.siimkinks.sqlitemagic.model.TableElement
import com.siimkinks.sqlitemagic.utils.ProcessingStepsTest
import com.siimkinks.sqlitemagic.utils.SqliteMagicCompilation
import com.squareup.kotlinpoet.ClassName
import org.junit.jupiter.api.Test

internal class GeneratedDatabaseElementTest : ProcessingStepsTest {
  override val processingSteps = ::genClassesManagerProcessingSteps

  @Test
  fun `creates complete durable submodule generation state`() {
    SqliteMagicCompilation
      .compile(
        submoduleDatabase(),
        kspOptions = mapOf(OPTION_VARIANT_DEBUG to "true")
      )
      .isOk()
      .apply {
        val tables = environment.tableElements.values.toList()
        val transformers = environment.transformerElements.values.toList()

        assertThat(GeneratedDatabaseElement.from(environment)).isEqualTo(
          GeneratedDatabaseElement(
            className = ClassName(PACKAGE_ROOT, "FeatureGeneratedClassesManager"),
            submoduleName = "Feature",
            databaseMetadata = DatabaseMetadata(
              dbName = null,
              dbVersion = null
            ),
            isDebug = true,
            tables = tables,
            transformers = transformers,
            submodules = emptyList()
          )
        )
      }
  }

  @Test
  fun `provides complete structures in dependency order`() {
    SqliteMagicCompilation
      .compile(schemaTables())
      .isOk()
      .apply {
        val database = GeneratedDatabaseElement.from(environment)
        val tablesByName = database.tables.associateBy(TableElement::tableName)
        val parent = tablesByName.getValue("parents")
        val child = tablesByName.getValue("children")
        val sessionCache = tablesByName.getValue("session_cache")
        val sessionOwner = tablesByName.getValue("session_owners")
        val orderedTables = CreationOrderedTables.from(database.tables)

        assertThat(DatabaseStructure.from(orderedTables)).isEqualTo(
          DatabaseStructure(
            tables = linkedMapOf(
              "parents" to TableStructure.from(parent),
              "children" to TableStructure.from(child)
            ),
            indices = linkedMapOf(),
            temporaryTables = linkedMapOf(
              "session_owners" to TableStructure.from(sessionOwner),
              "session_cache" to TableStructure.from(sessionCache)
            ),
            temporaryIndices = linkedMapOf()
          )
        )
      }
  }
}
