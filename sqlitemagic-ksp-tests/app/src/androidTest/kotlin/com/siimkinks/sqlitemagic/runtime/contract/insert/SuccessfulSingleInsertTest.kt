package com.siimkinks.sqlitemagic.runtime.contract.insert

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.ModelCatalog
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SuccessfulSingleInsertTest(
  private val modelCase: InsertModelCase<*>
) : RuntimeDatabaseTest() {
  @Test
  fun executeInsertsAndReadsBack() {
    captureInsert(
      modelCase = modelCase,
      operation = EntityInsertBuilder::execute
    )
  }

  @Test
  fun observeInsertsAndReadsBack() {
    captureInsert(
      modelCase = modelCase,
      operation = { it.observe().blockingGet() }
    )
  }

  private fun <T> captureInsert(
    modelCase: InsertModelCase<T>,
    operation: (EntityInsertBuilder) -> EntityInsertResult
  ) {
    val value = modelCase.newValue(sequence = 1)
    val result = operation(modelCase.insert(value = value))
    val inserted = when (result) {
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
    val actual = Select
      .from(modelCase.table)
      .queryDeep()
      .execute()
    val expected = modelCase.expectedAfterInsert(
      value = value,
      result = inserted
    )
    assertThat(actual).containsExactly(expected)
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun modelCases() = ModelCatalog.insertCases
  }
}
