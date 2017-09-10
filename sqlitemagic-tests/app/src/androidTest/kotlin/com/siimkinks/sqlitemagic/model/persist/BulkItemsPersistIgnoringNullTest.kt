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
            operation = BulkPersistDualOperation(ignoreNullValues = true),
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
            operation = BulkPersistDualOperation(ignoreNullValues = true),
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
            operation = BulkPersistDualOperation(ignoreNullValues = true),
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
            operation = BulkPersistDualOperation(ignoreNullValues = true),
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
            operation = BulkPersistDualOperation(ignoreNullValues = true))
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
}