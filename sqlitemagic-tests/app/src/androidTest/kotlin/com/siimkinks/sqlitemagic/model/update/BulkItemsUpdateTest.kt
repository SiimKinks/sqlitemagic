@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BulkItemsUpdateTest : DefaultConnectionTest {
  class SuccessfulBulkOperation<T>(
      forModel: TestModel<T>,
      before: (TestModel<T>) -> List<T> = { model ->
        createVals {
          val (v, id) = insertNewRandom(model)
          model.updateAllVals(v, id)
        }
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkUpdateDualOperation(TestModel<T>::bulkUpdateBuilder),
      assertResults: (TestModel<T>, List<T>, Boolean) -> Unit = assertBulkUpdateSuccess()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Simple bulk update succeeds",
      model = forModel,
      setUp = before,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun successfulBulkUpdate() {
    assertThatDual {
      testCase { SuccessfulBulkOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class EarlyUnsubscribeRollbacksAllValues<T>(
      forModel: TestModel<T>,
      test: SingleOperation<List<T>, T, AtomicInteger> = { model, testValues ->
        val eventsCount = AtomicInteger(0)
        val disposable = model.bulkUpdateBuilder(testValues)
            .observe()
            .subscribeOn(Schedulers.io())
            .subscribe({ eventsCount.incrementAndGet() }, { eventsCount.incrementAndGet() })
        Thread.sleep(10)
        disposable.dispose()
        Thread.sleep(500)
        eventsCount
      }
  ) : SingleOperationTestCase<List<T>, T, AtomicInteger>(
      "Unsubscribing from in-flight operation before it completes rollbacks all values",
      model = forModel,
      setUp = { model ->
        createVals(500) {
          val (v, id) = insertNewRandom(model)
          model.updateAllVals(v, id)
        }
      },
      test = test,
      assertResults = { model, testValues, eventsCount ->
        assertThat(eventsCount.get()).isEqualTo(0)
        assertThat(Select
            .from(model.table)
            .queryDeep()
            .execute())
            .isNotEqualTo(testValues)
      })

  @Test
  fun earlyUnsubscribeFromUpdateRollbacksAllValues() {
    assertThatSingle {
      testCase { EarlyUnsubscribeRollbacksAllValues(it) }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel
      )
    }
  }

  class FailedBulkOperationEmitsError<T>(
      forModel: TestModelWithUniqueColumn<T>,
      test: SingleOperation<List<T>, T, TestObserver<Long>> = ObserveBulkUpdate()
  ) : SingleOperationTestCase<List<T>, T, TestObserver<Long>>(
      "When bulk update fails it emits error and rolls back values",
      model = forModel,
      setUp = {
        val model = it as TestModelWithUniqueColumn
        val (v1) = insertNewRandom(model)
        val failingVals = createVals {
          val (v2) = insertNewRandom(model)
          model.transferUniqueVal(v1, v2)
        }
        val successValues = createVals {
          val (v2, id2) = insertNewRandom(model)
          model.updateAllVals(v2, id2)
        }
        successValues + failingVals
      },
      test = test,
      assertResults = { model, testVals, ts ->
        assertSingleError(ts)
        assertTableDoesNotHaveValues(testVals, model)
      })

  @Test
  fun failedBulkUpdateEmitsError() {
    assertThatSingle {
      testCase { FailedBulkOperationEmitsError(it as TestModelWithUniqueColumn) }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  class StreamedBulkOperation<T>(
      forModel: TestModel<T>,
      test: SingleOperation<Pair<List<T>, List<T>>, T, TestObserver<Any>> = StreamedBulkUpdateOperation()
  ) : SingleOperationTestCase<Pair<List<T>, List<T>>, T, TestObserver<Any>>(
      "Two streamed bulk update operations work as expected",
      model = forModel,
      setUp = { model ->
        val first = createVals {
          val (v, id) = insertNewRandom(model)
          model.updateAllVals(v, id)
        }
        val second = createVals {
          val (v, id) = insertNewRandom(model)
          model.updateAllVals(v, id)
        }
        first to second
      },
      test = test,
      assertResults = { _, _, ts ->
        ts.awaitTerminalEvent()
        ts.assertNoErrors()
        ts.assertComplete()
        val values = ts.values()
        assertThat(values).hasSize(1)
      })

  @Test
  fun streamedBulkUpdate() {
    assertThatSingle {
      testCase { StreamedBulkOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class BulkOperationByUniqueColumn<T>(
      forModel: TestModel<T>,
      before: (TestModel<T>) -> List<T> = {
        val model = it as TestModelWithUniqueColumn
        createVals {
          val (v, id) = insertNewRandom(model)
          val updatedVal = model.updateAllVals(v, id)
          model.transferUniqueVal(v, updatedVal)
        }
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkUpdateDualOperation { model, testVal ->
        model.bulkUpdateBuilder(testVal)
            .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
      },
      assertResults: (TestModel<T>, List<T>, Boolean) -> Unit = assertBulkUpdateSuccess()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk update by unique column succeeds",
      model = forModel,
      setUp = before,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun bulkUpdateByUniqueColumn() {
    assertThatDual {
      testCase { BulkOperationByUniqueColumn(it) }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  class BulkOperationByComplexUniqueColumn<T>(
      forModel: TestModel<T>,
      before: (TestModel<T>) -> List<T> = {
        val model = it as ComplexTestModelWithUniqueColumn
        createVals {
          val (v, id) = insertNewRandom(model)
          val updatedVal = model.updateAllVals(v, id)
          model.transferComplexColumnUniqueVal(v, updatedVal)
        }
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkUpdateDualOperation { model, testVal ->
        model.bulkUpdateBuilder(testVal)
            .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
      },
      assertResults: (TestModel<T>, List<T>, Boolean) -> Unit = assertBulkUpdateSuccess()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk update by complex unique column succeeds",
      model = forModel,
      setUp = before,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun bulkUpdateByComplexUniqueColumn() {
    assertThatDual {
      testCase { BulkOperationByComplexUniqueColumn(it) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class BulkOperationByComplexColumnUniqueColumn<T>(
      forModel: TestModel<T>,
      before: (TestModel<T>) -> List<T> = {
        val model = it as ComplexTestModelWithUniqueColumn
        createVals {
          val (v, id) = insertNewRandom(model)
          val updatedVal = model.updateAllVals(v, id)
          model.transferAllComplexUniqueVals(v, updatedVal)
        }
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkUpdateDualOperation { model, testVal ->
        model.bulkUpdateBuilder(testVal)
            .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
      },
      assertResults: (TestModel<T>, List<T>, Boolean) -> Unit = assertBulkUpdateSuccess()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk update complex column by its unique column succeeds",
      model = forModel,
      setUp = before,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun bulkUpdateByComplexColumnUniqueColumn() {
    assertThatDual {
      testCase { BulkOperationByComplexColumnUniqueColumn(it) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class BulkOperationByUniqueColumnWithNullId<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> List<T> = {
        createVals {
          val (newRandom, id) = insertNewRandom(it)
          var updatedVal = it.updateAllVals(newRandom, id)
          updatedVal = it.setId(updatedVal, null)
          (it as TestModelWithUniqueColumn).transferUniqueVal(newRandom, updatedVal)
        }
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkUpdateDualOperation { model, testVal ->
        model.bulkUpdateBuilder(testVal)
            .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
      },
      assertResults: (TestModel<T>, List<T>, Boolean) -> Unit = { model, testVals, success ->
        assertThat(success).isTrue()

        val expected = testVals.map { model.setId(it, null) }
        val actual = Select
            .from(model.table)
            .queryDeep()
            .execute()
            .map { model.setId(it, null) }

        assertThat(actual).isEqualTo(expected)
      }
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk update entity with null id by unique column succeeds",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun bulkUpdateByUniqueColumnWithNullId() {
    assertThatDual {
      testCase { BulkOperationByUniqueColumnWithNullId(forModel = it) }
      isSuccessfulFor(*ALL_NULLABLE_UNIQUE_AUTO_ID_MODELS)
    }
  }

  class ObserveBulkUpdate<T> : SingleOperation<List<T>, T, TestObserver<Long>> {
    override fun invoke(model: TestModel<T>, testVal: List<T>): TestObserver<Long> = model
        .bulkUpdateBuilder(testVal)
        .observe()
        .test() as TestObserver<Long>
  }

  class StreamedBulkUpdateOperation<T>(private val ignoreConflict: Boolean = false) : SingleOperation<Pair<List<T>, List<T>>, T, TestObserver<Any>> {
    override fun invoke(model: TestModel<T>, testVals: Pair<List<T>, List<T>>): TestObserver<Any> = model
        .bulkUpdateBuilder(testVals.first)
        .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
        .observe()
        .toSingleDefault(Any())
        .flatMap {
          model.bulkUpdateBuilder(testVals.second)
              .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
              .observe()
              .toSingleDefault(Any())
        }
        .subscribeOn(Schedulers.io())
        .test()
  }
}