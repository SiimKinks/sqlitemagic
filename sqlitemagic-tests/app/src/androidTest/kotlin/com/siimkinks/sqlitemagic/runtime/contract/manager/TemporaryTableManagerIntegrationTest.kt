package com.siimkinks.sqlitemagic.runtime.contract.manager

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.SubmoduleGeneratedClassesManager
import com.siimkinks.sqlitemagic.runtime.model.ManagerIntegrationModelCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ManagerIntegrationModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_MASTER
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_TEMP_MASTER
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.reopenDefaultConnection
import com.siimkinks.sqlitemagic.runtime.support.schemaObjectNames
import org.junit.Rule
import org.junit.Test

private const val SUBMODULE_NAME = "Submodule"

private data class SeededManagerModelCase<T>(
  val modelCase: ManagerIntegrationModelCase<T>,
  val expected: T
)

internal class TemporaryTableManagerIntegrationTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun initialOpenCreatesTemporaryTablesOutsidePersistentSchema() {
    assertThat(tableNames(master = SQLITE_TEMP_MASTER))
      .containsExactlyElementsIn(ManagerIntegrationModelCatalog.temporaryTableNames)
    assertThat(tableNames(master = SQLITE_MASTER))
      .isEmpty()
  }

  @Test
  fun managerRoutesLocalAndSubmoduleTablePositions() {
    val database = SqliteMagicDatabase()
    val routedSubmoduleCount = database.getNrOfTables(SUBMODULE_NAME)

    assertThat(database.submoduleNames)
      .isEqualTo(arrayOf(SUBMODULE_NAME))
    assertThat(database.getNrOfTables(null))
      .isEqualTo(database.getNrOfTables("") + routedSubmoduleCount)
    assertThat(routedSubmoduleCount)
      .isEqualTo(SubmoduleGeneratedClassesManager.getNrOfTables(null))
    assertThat(SubmoduleGeneratedClassesManager.getNrOfTables(null))
      .isEqualTo(ManagerIntegrationModelCatalog.submoduleCases.size)

    assertSeededRowsReadBack()
  }

  @Test
  fun clearDataEmptiesPersistentAndTemporaryTables() {
    seedAllTables()

    SqliteMagic.getDefaultConnection()
      .clearData()

    ManagerIntegrationModelCatalog.cases.forEach(::assertEmpty)
  }

  @Test
  fun reopeningConnectionRecreatesEmptyTemporaryTablesAndRetainsPersistentRows() {
    val seededCases = seedAllTables()

    reopenDefaultConnection()

    assertThat(tableNames(master = SQLITE_TEMP_MASTER))
      .containsExactlyElementsIn(ManagerIntegrationModelCatalog.temporaryTableNames)
    seededCases.forEach(::assertReopenState)
  }

  private fun assertSeededRowsReadBack() = seedAllTables()
    .forEach(::assertSingleRow)

  private fun seedAllTables() = ManagerIntegrationModelCatalog.cases
    .map(::seed)

  private fun seed(modelCase: ManagerIntegrationModelCase<*>) = seedTyped(modelCase = modelCase)

  private fun <T> seedTyped(modelCase: ManagerIntegrationModelCase<T>): SeededManagerModelCase<T> {
    val value = modelCase.newValue()
    val result = modelCase
      .insert(value)
      .execute()
    insertedResult(
      result = result,
      modelName = modelCase.name
    )
    return SeededManagerModelCase(
      modelCase = modelCase,
      expected = value
    )
  }

  private fun assertSingleRow(seededCase: SeededManagerModelCase<*>) = assertRowsIgnoringOrder(
    table = seededCase.modelCase.table,
    expected = listOf(seededCase.expected)
  )

  private fun assertEmpty(modelCase: ManagerIntegrationModelCase<*>) = assertRowsIgnoringOrder(
    table = modelCase.table,
    expected = emptyList()
  )

  private fun assertReopenState(seededCase: SeededManagerModelCase<*>) = when {
    seededCase.modelCase.isTemporary -> assertEmpty(modelCase = seededCase.modelCase)
    else -> assertSingleRow(seededCase = seededCase)
  }

  private fun tableNames(master: String) = ManagerIntegrationModelCatalog.temporaryTableNames
    .intersect(
      schemaObjectNames(
        master = master,
        type = "table"
      )
    )
}
