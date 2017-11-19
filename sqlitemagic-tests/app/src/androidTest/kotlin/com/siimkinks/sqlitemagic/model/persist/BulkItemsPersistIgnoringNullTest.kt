@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.persist

import android.support.test.runner.AndroidJUnit4
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.insert.BulkItemsInsertTest
import com.siimkinks.sqlitemagic.model.persist.BulkItemsPersistTest.*
import com.siimkinks.sqlitemagic.model.update.BulkItemsUpdateTest
import com.siimkinks.sqlitemagic.model.update.assertBulkUpdateSuccessForComplexNullableColumns
import com.siimkinks.sqlitemagic.model.update.assertBulkUpdateSuccessForNullableColumns
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BulkItemsPersistIgnoringNullTest : DefaultConnectionTest {
  @Test
  fun successfulBulkPersistWithInsertIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsInsertTest.SuccessfulBulkOperation(
            forModel = it,
            operation = BulkPersistDualOperation(
                ignoreNullValues = true,
                persistBuilderCallback = TestModel<Any>::bulkPersistBuilder),
            before = {
              val model = it as TestModelWithNullableColumns
              createVals {
                val random = model.newRandom()
                model.nullSomeColumns(random)
              }
            })
      }
      isSuccessfulFor(*ALL_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulBulkPersistWithUpdateIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.SuccessfulBulkOperation(
            forModel = it,
            before = {
              val model = it as TestModelWithNullableColumns
              createVals {
                val (v, id) = insertNewRandom(model)
                val updatedVal = model.updateAllVals(v, id)
                model.nullSomeColumns(updatedVal)
              }
            },
            operation = BulkPersistDualOperation(
                ignoreNullValues = true,
                persistBuilderCallback = TestModel<Any>::bulkPersistBuilder),
            assertResults = assertBulkUpdateSuccessForNullableColumns())
      }
      isSuccessfulFor(*ALL_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun bulkComplexPersistWithInsertIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsInsertTest.SuccessfulBulkOperation(
            forModel = it,
            operation = BulkPersistDualOperation(
                ignoreNullValues = true,
                persistBuilderCallback = TestModel<Any>::bulkPersistBuilder),
            before = {
              val model = it as ComplexTestModelWithNullableColumns
              createVals {
                val random = model.newRandom()
                model.nullSomeComplexColumns(random)
              }
            })
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun bulkComplexPersistWithUpdateIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.SuccessfulBulkOperation(
            forModel = it,
            before = {
              val model = it as ComplexTestModelWithNullableColumns
              createVals {
                val (v, id) = insertNewRandom(model)
                val updatedVal = model.updateAllVals(v, id)
                model.nullSomeComplexColumns(updatedVal)
              }
            },
            operation = BulkPersistDualOperation(
                ignoreNullValues = true,
                persistBuilderCallback = TestModel<Any>::bulkPersistBuilder),
            assertResults = assertBulkUpdateSuccessForComplexNullableColumns())
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun mutableModelBulkPersistWithInsertIgnoringNullSetsIds() {
    assertThatDual {
      testCase {
        BulkItemsInsertTest.MutableModelBulkOperationSetsIds(
            forModel = it,
            operation = BulkPersistDualOperation(
                ignoreNullValues = true,
                persistBuilderCallback = TestModel<Any>::bulkPersistBuilder))
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel
      )
    }
  }

  @Test
  fun earlyUnsubscribeFromPersistWithInsertRollbacksAllValuesIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsInsertTest.EarlyUnsubscribeRollbacksAllValues(
            forModel = it,
            test = EarlyUnsubscribe(ignoreNullValues = true))
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel
      )
    }
  }

  @Test
  fun earlyUnsubscribeFromPersistWithUpdateRollbacksAllValuesIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateTest.EarlyUnsubscribeRollbacksAllValues(
            forModel = it,
            test = EarlyUnsubscribe(ignoreNullValues = true))
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel
      )
    }
  }

  @Test
  fun failedBulkPersistWithInsertEmitsErrorIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsInsertTest.FailedBulkOperationEmitsError(
            forModel = it as TestModelWithUniqueColumn,
            test = ObserveBulkPersist(ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun failedBulkPersistWithUpdateEmitsErrorIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateTest.FailedBulkOperationEmitsError(
            forModel = it as TestModelWithUniqueColumn,
            test = ObserveBulkPersist(ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun streamedBulkPersistWithInsertIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsInsertTest.StreamedBulkOperation(
            forModel = it,
            test = StreamedBulkPersistWithInsertOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedBulkPersistWithUpdateIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateTest.StreamedBulkOperation(
            forModel = it,
            test = StreamedBulkPersistWithUpdateOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class BulkOperationByUniqueColumnIgnoringNull<T>(
      forModel: TestModel<T>,
      before: (TestModel<T>) -> List<T> = {
        val model = it as TestModelWithUniqueColumn
        createVals {
          val (v, id) = insertNewRandom(model)
          var updatedVal = it.updateAllVals(v, id)
          updatedVal = (it as NullableColumns<T>).nullSomeColumns(updatedVal)
          model.transferUniqueVal(v, updatedVal)
        }.sortedBy(model::getId)
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkPersistDualOperation(ignoreNullValues = true) { model, testVals ->
        model.bulkPersistBuilder(testVals)
            .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
      },
      assertResults: (TestModel<T>, List<T>, Boolean) -> Unit = assertBulkUpdateSuccessForNullableColumns()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk update by unique column ignoring null succeeds",
      model = forModel,
      setUp = before,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun bulkPersistWithUpdateByUniqueColumnIgnoringNull() {
    assertThatDual {
      testCase { BulkOperationByUniqueColumnIgnoringNull(forModel = it) }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  class BulkOperationByComplexUniqueColumnIgnoringNull<T>(
      forModel: TestModel<T>,
      before: (TestModel<T>) -> List<T> = {
        val model = it as ComplexTestModelWithUniqueColumn
        createVals {
          val (v, id) = insertNewRandom(model)
          var updatedVal = it.updateAllVals(v, id)
          updatedVal = (it as NullableColumns<T>).nullSomeColumns(updatedVal)
          model.transferComplexColumnUniqueVal(v, updatedVal)
        }.sortedBy(model::getId)
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkPersistDualOperation(ignoreNullValues = true) { model, testVals ->
        model.bulkPersistBuilder(testVals)
            .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
      },
      assertResults: (TestModel<T>, List<T>, Boolean) -> Unit = assertBulkUpdateSuccessForNullableColumns()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk update by complex unique column ignoring null succeeds",
      model = forModel,
      setUp = before,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun bulkPersistWithUpdateByComplexUniqueColumnIgnoringNull() {
    assertThatDual {
      testCase { BulkOperationByComplexUniqueColumnIgnoringNull(forModel = it) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class BulkOperationByComplexColumnUniqueColumnIgnoringNull<T>(
      forModel: TestModel<T>,
      before: (TestModel<T>) -> List<T> = {
        val model = it as ComplexTestModelWithUniqueColumn
        createVals {
          val (v, id) = insertNewRandom(model)
          var updatedVal = it.updateAllVals(v, id)
          updatedVal = (it as NullableColumns<T>).nullSomeColumns(updatedVal)
          model.transferAllComplexUniqueVals(v, updatedVal)
        }.sortedBy(model::getId)
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkPersistDualOperation(ignoreNullValues = true) { model, testVals ->
        model.bulkPersistBuilder(testVals)
            .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
      },
      assertResults: (TestModel<T>, List<T>, Boolean) -> Unit = assertBulkUpdateSuccessForNullableColumns()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk update complex column by its unique column ignoring null succeeds",
      model = forModel,
      setUp = before,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun bulkPersistWithUpdateByComplexColumnUniqueColumnIgnoringNull() {
    assertThatDual {
      testCase { BulkOperationByComplexColumnUniqueColumnIgnoringNull(forModel = it) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }
}