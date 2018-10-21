package com.siimkinks.sqlitemagic.model.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.TestUtil.assertTableCount
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BulkItemsInsertTest : DefaultConnectionTest {
  class SuccessfulBulkOperation<T>(
      forModel: TestModel<T>,
      operation: DualOperation<List<T>, T, Boolean> = BulkInsertDualOperation(),
      before: (TestModel<T>) -> List<T> = { createVals(createFun = it::newRandom) }
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Simple bulk insert succeeds",
      model = forModel,
      setUp = before,
      operation = operation,
      assertResults = { model, testValues, success ->
        assertThat(success).isTrue()
        val dbVals = Select.from(model.table).queryDeep().execute()
        assertThat(testValues.size).isEqualTo(dbVals.size)
        val dbIterator = dbVals.iterator()
        testValues.forEach {
          assertThat(model.valsAreEqual(it, dbIterator.next())).isTrue()
        }
      })

  @Test
  fun successfulBulkInsert() {
    assertThatDual {
      testCase { SuccessfulBulkOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class MutableModelBulkOperationSetsIds<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> List<T> = {
        createVals {
          val newRandom = it.newRandom()
          it.setId(newRandom, -1)
        }
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkInsertDualOperation()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk insert for mutable autoincremented id model sets ids",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = { model, testVals, success ->
        assertThat(success).isTrue()
        assertThat(Select
            .from(model.table)
            .count()
            .execute())
            .isEqualTo(testVals.size)

        for (v in testVals) {
          val id = model.getId(v)
          assertThat(id).isNotNull()
          assertThat(id).isNotEqualTo(-1L)
        }
      })

  @Test
  fun mutableModelBulkInsertSetsIds() {
    assertThatDual {
      testCase { MutableModelBulkOperationSetsIds(it) }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel
      )
    }
  }

  class EarlyUnsubscribeRollbacksAllValues<T>(
      forModel: TestModel<T>,
      test: SingleOperation<List<T>, T, AtomicInteger> = { model, testValues ->
        val eventsCount = AtomicInteger(0)
        val disposable = model.bulkInsertBuilder(testValues)
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
      setUp = { createVals(count = 500, createFun = it::newRandom) },
      test = test,
      assertResults = { model, _, eventsCount ->
        assertTableCount(0, model.table)
        assertThat(eventsCount.get()).isEqualTo(0)
      })

  @Test
  fun earlyUnsubscribeFromInsertRollbacksAllValues() {
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
      test: SingleOperation<List<T>, T, TestObserver<Long>> = ObserveBulkInsert()
  ) : SingleOperationTestCase<List<T>, T, TestObserver<Long>>(
      "When bulk insert fails it emits error and rolls back values",
      model = forModel,
      setUp = {
        val model = it as TestModelWithUniqueColumn
        val (v1) = insertNewRandom(model)
        val failingVals = createVals {
          val (v2) = insertNewRandom(model)
          model.transferUniqueVal(v1, v2)
        }
        val successValues = createVals { model.newRandom() }
        successValues + failingVals
      },
      test = test,
      assertResults = { model, testVals, ts ->
        assertSingleError(ts)
        assertTableDoesNotHaveValues(testVals, model)
      })

  @Test
  fun failedBulkInsertEmitsError() {
    assertThatSingle {
      testCase { FailedBulkOperationEmitsError(it as TestModelWithUniqueColumn) }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  class StreamedBulkOperation<T>(
      forModel: TestModel<T>,
      test: SingleOperation<List<T>, T, TestObserver<Boolean>> = StreamedBulkInsertOperation()
  ) : SingleOperationTestCase<List<T>, T, TestObserver<Boolean>>(
      "Two streamed bulk insert operations work as expected",
      model = forModel,
      setUp = {
        createVals {
          val newRandom = it.newRandom()
          it.setId(newRandom, -1)
          newRandom
        }
      },
      test = test,
      assertResults = { _, _, ts ->
        ts.awaitTerminalEvent(1, TimeUnit.SECONDS)
        ts.assertNoErrors()
        ts.assertComplete()
        ts.assertValue(true)
      })

  @Test
  fun streamedBulkInsert() {
    assertThatSingle {
      testCase { StreamedBulkOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class BulkInsertDualOperation<T> : DualOperation<List<T>, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: List<T>): Boolean = model
        .bulkInsertBuilder(testVal)
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: List<T>): Boolean = model
        .bulkInsertBuilder(testVal)
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }

  class ObserveBulkInsert<T> : SingleOperation<List<T>, T, TestObserver<Long>> {
    override fun invoke(model: TestModel<T>, testVal: List<T>): TestObserver<Long> {
      val ts = TestObserver<Long>()
      model.bulkInsertBuilder(testVal)
          .observe()
          .subscribe(ts)
      return ts
    }
  }

  class StreamedBulkInsertOperation<T>(private val ignoreConflict: Boolean = false) : SingleOperation<List<T>, T, TestObserver<Boolean>> {
    override fun invoke(model: TestModel<T>, testVals: List<T>): TestObserver<Boolean> = model
        .bulkInsertBuilder(testVals)
        .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
        .observe()
        .toSingleDefault(true)
        .flatMap { _ ->
          val newVals = createVals {
            val newRandom = model.newRandom()
            model.setId(newRandom, -1)
            newRandom
          }
          model.bulkInsertBuilder(newVals)
              .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
              .observe()
              .toSingleDefault(true)
        }
        .subscribeOn(Schedulers.io())
        .test()
  }
}