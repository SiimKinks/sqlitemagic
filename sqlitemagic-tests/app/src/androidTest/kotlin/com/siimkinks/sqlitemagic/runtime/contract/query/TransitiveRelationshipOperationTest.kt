package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.model.TransitiveRelationshipModelCase
import com.siimkinks.sqlitemagic.runtime.model.TransitiveRelationshipTableRows
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class TransitiveRelationshipOperationTest(
  private val modelCase: TransitiveRelationshipModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeSingleInsertPersistsAllTransitiveRows() {
    assertSingleInsert(
      operation = EntityInsertBuilder::execute
    )
  }

  @Test
  fun observeSingleInsertPersistsAllTransitiveRows() {
    assertSingleInsert { builder ->
      builder
        .observe()
        .blockingGet()
    }
  }

  @Test
  fun executeBulkInsertPersistsAllTransitiveRows() {
    assertBulkInsert(
      operation = EntityBulkInsertBuilder::execute
    )
  }

  @Test
  fun observeBulkInsertPersistsAllTransitiveRows() {
    assertBulkInsert { builder ->
      builder
        .observe()
        .blockingAwait()
      true
    }
  }

  private fun assertSingleInsert(
    operation: (EntityInsertBuilder) -> EntityInsertResult
  ) = assertSingleInsert(
    modelCase = modelCase,
    operation = operation
  )

  private fun <T> assertSingleInsert(
    modelCase: TransitiveRelationshipModelCase<T>,
    operation: (EntityInsertBuilder) -> EntityInsertResult
  ) {
    val value = modelCase.newValue(sequence = 1)
    val inserted = when (val result = operation(modelCase.insert(value = value))) {
      is EntityInsertResult.Inserted -> result
      EntityInsertResult.Ignored -> throw AssertionError("Insert was ignored for ${modelCase.name}")
    }
    when (modelCase.rowIdExpectation) {
      InsertRowIdExpectation.PRESENT -> assertThat(inserted.rowId).isNotNull()
      InsertRowIdExpectation.ABSENT -> assertThat(inserted.rowId).isNull()
    }
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

  private fun assertBulkInsert(
    operation: (EntityBulkInsertBuilder) -> Boolean
  ) = assertBulkInsert(
    modelCase = modelCase,
    operation = operation
  )

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
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.transitiveRelationshipCases
  }
}
