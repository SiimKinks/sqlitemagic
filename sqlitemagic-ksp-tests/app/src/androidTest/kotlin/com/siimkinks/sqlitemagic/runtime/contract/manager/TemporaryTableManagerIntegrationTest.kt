package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.database.Cursor
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.SubmoduleGeneratedClassesManager
import com.siimkinks.sqlitemagic.TestApp
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.ManagerIntegrationModelCase
import com.siimkinks.sqlitemagic.runtime.model.catalog.ManagerIntegrationModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import org.junit.Test

private const val SUBMODULE_NAME = "Submodule"

private data class SeededManagerModelCase<T>(
  val modelCase: ManagerIntegrationModelCase<T>,
  val expected: T
)

class TemporaryTableManagerIntegrationTest : RuntimeDatabaseTest() {
  @Test
  fun initialOpenCreatesTemporaryTablesOutsidePersistentSchema() {
    assertThat(tableNames(master = "sqlite_temp_master"))
      .containsExactlyElementsIn(ManagerIntegrationModelCatalog.temporaryTableNames)
    assertThat(tableNames(master = "sqlite_master"))
      .isEmpty()
  }

  @Test
  fun managerRoutesLocalAndSubmoduleTablePositions() {
    val database = SqliteMagicDatabase()
    val routedSubmoduleCount = database.getNrOfTables(SUBMODULE_NAME)

    assertThat(database.getSubmoduleNames())
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

    assertThat(tableNames(master = "sqlite_temp_master"))
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
    assertInserted(result = result)
    return SeededManagerModelCase(
      modelCase = modelCase,
      expected = value
    )
  }

  private fun assertSingleRow(seededCase: SeededManagerModelCase<*>) =
    assertSingleRowTyped(seededCase = seededCase)

  private fun <T> assertSingleRowTyped(seededCase: SeededManagerModelCase<T>) =
    assertThat(captureRows(table = seededCase.modelCase.table))
      .containsExactly(seededCase.expected)

  private fun assertEmpty(modelCase: ManagerIntegrationModelCase<*>) =
    assertEmptyTyped(modelCase = modelCase)

  private fun <T> assertEmptyTyped(modelCase: ManagerIntegrationModelCase<T>) =
    assertThat(captureRows(table = modelCase.table))
      .isEmpty()

  private fun assertReopenState(seededCase: SeededManagerModelCase<*>) =
    assertReopenStateTyped(seededCase = seededCase)

  private fun <T> assertReopenStateTyped(seededCase: SeededManagerModelCase<T>) = when {
    seededCase.modelCase.isTemporary -> assertEmptyTyped(modelCase = seededCase.modelCase)
    else -> assertSingleRowTyped(seededCase = seededCase)
  }

  private fun assertInserted(result: EntityInsertResult) = when (result) {
    is EntityInsertResult.Inserted -> Unit
    EntityInsertResult.Ignored -> error("Deterministic seed insert was ignored")
  }

  private fun tableNames(master: String): Set<String> {
    val tableNames = ManagerIntegrationModelCatalog.temporaryTableNames
    val placeholders = tableNames.joinToString { "?" }
    return Select
      .raw("SELECT name FROM $master WHERE type = 'table' AND name IN ($placeholders)")
      .from(SIMPLE_MUTABLE_ENTITY)
      .withArgs(*tableNames.toTypedArray())
      .execute()
      .use(Cursor::readNames)
  }

  private fun reopenDefaultConnection() {
    val application = InstrumentationRegistry
      .getInstrumentation()
      .targetContext
      .applicationContext as TestApp
    application.initDb(app = application)
  }
}

private fun Cursor.readNames() = buildSet {
  while (moveToNext()) {
    add(getString(0))
  }
}
