package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TransitiveRelationshipModelCase
import com.siimkinks.sqlitemagic.runtime.model.TransitiveRelationshipTableRows
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class TransitiveRelationshipOperationTest(
  private val modelCase: TransitiveRelationshipModelCase<*>
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun executeSingleInsertPersistsAllTransitiveRows() = assertSingleInsert(
    modelCase = modelCase,
    operation = EntityInsertBuilder::execute
  )

  @Test
  fun observeSingleInsertPersistsAllTransitiveRows() = assertSingleInsert(
    modelCase = modelCase,
    operation = { it.observe().blockingGet() }
  )

  @Test
  fun executeBulkInsertPersistsAllTransitiveRows() = assertBulkInsert(
    modelCase = modelCase,
    operation = EntityBulkInsertBuilder::execute
  )

  @Test
  fun observeBulkInsertPersistsAllTransitiveRows() = assertBulkInsert(
    modelCase = modelCase,
    operation = { builder ->
      builder
        .observe()
        .blockingAwait()
      true
    }
  )

  private fun <T> assertSingleInsert(
    modelCase: TransitiveRelationshipModelCase<T>,
    operation: (EntityInsertBuilder) -> EntityInsertResult
  ) {
    val value = modelCase.newValue(sequence = 1)
    val inserted = insertedResult(
      result = operation(modelCase.insert(value = value)),
      modelName = modelCase.name
    )
    modelCase.rowIdExpectation.assertRowId(inserted.rowId)
    modelCase.verifyAfterInsert(
      value = value,
      result = inserted
    )

    val expected = modelCase.expectedAfterInsert(
      value = value,
      result = inserted
    )
    assertThat(captureRows(table = modelCase.table))
      .containsExactly(expected)
    assertTransitiveRows(
      modelCase = modelCase,
      values = listOf(expected)
    )
  }

  private fun <T> assertBulkInsert(
    modelCase: TransitiveRelationshipModelCase<T>,
    operation: (EntityBulkInsertBuilder) -> Boolean
  ) {
    val values = List(
      size = 3,
      init = modelCase::newValue
    )
    assertThat(
      operation(
        modelCase.bulkInsert(values = values)
      )
    ).isTrue()

    val actual = captureRows(table = modelCase.table)
    val expected = modelCase.expectedAfterBulkInsert(
      values = values,
      actual = actual
    )
    assertThat(actual)
      .containsExactlyElementsIn(expected)
    assertTransitiveRows(
      modelCase = modelCase,
      values = expected
    )
  }

  private fun <T> assertTransitiveRows(
    modelCase: TransitiveRelationshipModelCase<T>,
    values: List<T>
  ) {
    val expectedRowsByTable = values
      .flatMap(modelCase::transitiveRelatedTableRows)
      .groupBy(TransitiveRelationshipTableRows::table)

    assertThat(expectedRowsByTable).hasSize(3)
    expectedRowsByTable.forEach { (table, descriptors) ->
      assertThat(captureRows(table = table))
        .containsExactlyElementsIn(descriptors.flatMap(TransitiveRelationshipTableRows::rows))
    }
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.transitiveRelationshipCases
  }
}
