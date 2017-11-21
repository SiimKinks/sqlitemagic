@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.support.test.runner.AndroidJUnit4
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.insert.BulkItemsInsertTest
import com.siimkinks.sqlitemagic.model.update.BulkItemsUpdateTest
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BulkItemsPersistTest : DefaultConnectionTest {
  @Test
  fun successfulBulkPersistWithInsert() {
    assertThatDual {
      testCase {
        BulkItemsInsertTest.SuccessfulBulkOperation(
            forModel = it,
            operation = BulkPersistDualOperation(persistBuilderCallback = TestModel<Any>::bulkPersistBuilder))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulBulkPersistWithUpdate() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.SuccessfulBulkOperation(
            forModel = it,
            operation = BulkPersistDualOperation(persistBuilderCallback = TestModel<Any>::bulkPersistBuilder))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun mutableModelBulkPersistWithInsertSetsIds() {
    assertThatDual {
      testCase {
        BulkItemsInsertTest.MutableModelBulkOperationSetsIds(
            forModel = it,
            operation = BulkPersistDualOperation(persistBuilderCallback = TestModel<Any>::bulkPersistBuilder))
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  @Test
  fun earlyUnsubscribeFromPersistWithInsertRollbacksAllValues() {
    assertThatSingle {
      testCase {
        BulkItemsInsertTest.EarlyUnsubscribeRollbacksAllValues(
            forModel = it,
            test = EarlyUnsubscribe())
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  @Test
  fun earlyUnsubscribeFromPersistWithUpdateRollbacksAllValues() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateTest.EarlyUnsubscribeRollbacksAllValues(
            forModel = it,
            test = EarlyUnsubscribe())
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  @Test
  fun failedBulkPersistWithInsertEmitsError() {
    assertThatSingle {
      testCase {
        BulkItemsInsertTest.FailedBulkOperationEmitsError(
            forModel = it as TestModelWithUniqueColumn,
            test = ObserveBulkPersist())
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun failedBulkPersistWithUpdateEmitsError() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateTest.FailedBulkOperationEmitsError(
            forModel = it as TestModelWithUniqueColumn,
            test = ObserveBulkPersist())
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun streamedBulkPersistWithInsert() {
    assertThatSingle {
      testCase {
        BulkItemsInsertTest.StreamedBulkOperation(
            forModel = it,
            test = StreamedBulkPersistWithInsertOperation())
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedBulkPersistWithUpdate() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateTest.StreamedBulkOperation(
            forModel = it,
            test = StreamedBulkPersistWithUpdateOperation())
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByUniqueColumn() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByUniqueColumn(
            forModel = it,
            operation = BulkPersistDualOperation { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByComplexUniqueColumn() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByComplexUniqueColumn(
            forModel = it,
            operation = BulkPersistDualOperation { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByComplexColumnUniqueColumn() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByComplexColumnUniqueColumn(
            forModel = it,
            operation = BulkPersistDualOperation { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByUniqueColumnWithNullId() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByUniqueColumnWithNullId(
            forModel = it,
            operation = BulkPersistDualOperation { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_NULLABLE_UNIQUE_AUTO_ID_MODELS)
    }
  }

  class EarlyUnsubscribe<T>(private val ignoreNullValues: Boolean = false) : SingleOperation<List<T>, T, AtomicInteger> {
    override fun invoke(model: TestModel<T>, testVal: List<T>): AtomicInteger {
      val eventsCount = AtomicInteger(0)
      val disposable = model
          .bulkPersistBuilder(testVal)
          .also { if (ignoreNullValues) it.ignoreNullValues() }
          .observe()
          .subscribeOn(Schedulers.io())
          .subscribe({ eventsCount.incrementAndGet() }, { eventsCount.incrementAndGet() })
      Thread.sleep(10)
      disposable.dispose()
      Thread.sleep(500)
      return eventsCount
    }
  }

  class ObserveBulkPersist<T>(private val ignoreNullValues: Boolean = false) : SingleOperation<List<T>, T, TestObserver<Long>> {
    override fun invoke(model: TestModel<T>, testVal: List<T>): TestObserver<Long> = model
        .bulkPersistBuilder(testVal)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .test() as TestObserver<Long>
  }

  class StreamedBulkPersistWithInsertOperation<T>(
      private val ignoreNullValues: Boolean = false,
      private val ignoreConflict: Boolean = false
  ) : SingleOperation<List<T>, T, TestObserver<Boolean>> {
    override fun invoke(model: TestModel<T>, testVals: List<T>): TestObserver<Boolean> = model
        .bulkPersistBuilder(testVals)
        .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .toSingleDefault(true)
        .flatMap {
          val newVals = createVals {
            val newRandom = model.newRandom()
            model.setId(newRandom, -1)
            newRandom
          }
          model.bulkPersistBuilder(newVals)
              .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
              .also { if (ignoreNullValues) it.ignoreNullValues() }
              .observe()
              .toSingleDefault(true)
        }
        .subscribeOn(Schedulers.io())
        .test()
  }

  class StreamedBulkPersistWithUpdateOperation<T>(
      private val ignoreNullValues: Boolean = false,
      private val ignoreConflict: Boolean = false
  ) : SingleOperation<Pair<List<T>, List<T>>, T, TestObserver<Any>> {
    override fun invoke(model: TestModel<T>, testVal: Pair<List<T>, List<T>>): TestObserver<Any> = model
        .bulkPersistBuilder(testVal.first)
        .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .toSingleDefault(Any())
        .flatMap {
          model.bulkPersistBuilder(testVal.second)
              .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
              .also { if (ignoreNullValues) it.ignoreNullValues() }
              .observe()
              .toSingleDefault(Any())
        }
        .subscribeOn(Schedulers.io())
        .test()
  }
}