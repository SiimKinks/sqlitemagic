package com.siimkinks.sqlitemagic.runtime.contract.insert

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.rxjava2.observe
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class SuccessfulSingleInsertTest(
  private val modelCase: InsertModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeInsertsAndReadsBack() = captureInsert(
    modelCase = modelCase,
    operation = EntityInsertBuilder::execute
  )

  @Test
  fun observeInsertsAndReadsBack() = captureInsert(
    modelCase = modelCase,
    operation = { it.observe().blockingGet() }
  )

  private fun <T> captureInsert(
    modelCase: InsertModelCase<T>,
    operation: (EntityInsertBuilder) -> EntityInsertResult
  ) {
    val value = modelCase.newValue(sequence = 1)
    val result = operation(modelCase.insert(value = value))
    val inserted = insertedResult(
      result = result,
      modelName = modelCase.name
    )
    modelCase.rowIdExpectation.assertRowId(inserted.rowId)
    modelCase.verifyAfterInsert(
      value = value,
      result = inserted
    )
    val actual = captureRows(table = modelCase.table)
    val expected = modelCase.expectedAfterInsert(
      value = value,
      result = inserted
    )
    assertThat(actual).containsExactly(expected)
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.insertCases
  }
}
