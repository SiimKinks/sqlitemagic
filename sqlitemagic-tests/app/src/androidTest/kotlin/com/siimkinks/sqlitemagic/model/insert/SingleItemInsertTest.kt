package com.siimkinks.sqlitemagic.model.insert

import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.model.*
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SingleItemInsertTest : DefaultConnectionTest {
  class SuccessfulOperation<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> T = TestModel<T>::newRandom,
      operation: DualOperation<T, T, Long> = InsertDualOperation()
  ) : DualOperationTestCase<T, T, Long>(
      "Simple insert succeeds",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = assertInsertSuccess())

  @Test
  fun successfulInsert() {
    assertThatDual {
      testCase { SuccessfulOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class OperationFailEmitsError<T>(
      forModel: TestModelWithUniqueColumn<T>,
      setUp: (TestModel<T>) -> T = newFailingByUniqueConstraint(),
      test: SingleOperation<T, T, TestObserver<Long>> = ObserveInsert()
  ) : SingleOperationTestCase<T, T, TestObserver<Long>>(
      "When insert fails observed stream emits error",
      model = forModel,
      setUp = setUp,
      test = test,
      assertResults = { _, _, ts ->
        ts.awaitTerminalEvent(1, TimeUnit.SECONDS)
        ts.assertNoValues()
        val errors = ts.errors()
        assertThat(errors.size).isEqualTo(1)
      })

  @Test
  fun failedInsertEmitsError() {
    assertThatSingle {
      testCase { OperationFailEmitsError(it as TestModelWithUniqueColumn) }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  class OperationFailOnComplexModelChildEmitsError<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      test: SingleOperation<T, T, TestObserver<Long>> = ObserveInsert()
  ) : SingleOperationTestCase<T, T, TestObserver<Long>>(
      "When complex model insert fails it emits error and rolls back all values",
      model = forModel,
      setUp = {
        val model = it as ComplexTestModelWithUniqueColumn
        val (v1) = insertNewRandom(model)
        val newRandom = model.newRandom()
        model.transferComplexUniqueVal(v1, newRandom)
      },
      test = test,
      assertResults = { model, testVal, ts ->
        assertSingleError(ts)
        assertTableDoesNotHaveValue(testVal, model)
      })

  @Test
  fun failedInsertOnComplexModelChildEmitsError() {
    assertThatSingle {
      testCase { OperationFailOnComplexModelChildEmitsError(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class StreamedOperation<T>(
      forModel: TestModel<T>,
      test: SingleOperation<T, T, TestObserver<Long>> = { model, testVal ->
        model.insertBuilder(testVal)
            .observe()
            .flatMap { id ->
              assertThat(id).isNotEqualTo(-1L)
              val newRandom = model.newRandom()
              model.setId(newRandom, -1)
              model.insertBuilder(newRandom).observe()
            }
            .subscribeOn(Schedulers.io())
            .test()
      }
  ) : SingleOperationTestCase<T, T, TestObserver<Long>>(
      "Two streamed insert operations work as expected",
      model = forModel,
      setUp = {
        val newRandom = it.newRandom()
        it.setId(newRandom, -1)
        newRandom
      },
      test = test,
      assertResults = { _, _, ts ->
        ts.awaitTerminalEvent(1, TimeUnit.SECONDS)
        ts.assertNoErrors()
        ts.assertComplete()
        val values = ts.values()
        assertThat(values).hasSize(1)
        assertThat(values[0]).isNotEqualTo(-1L)
      })

  @Test
  fun streamedInsert() {
    assertThatSingle {
      testCase { StreamedOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class InsertDualOperation<T> : DualOperation<T, T, Long> {
    override fun executeTest(model: TestModel<T>, testVal: T): Long = model
        .insertBuilder(testVal)
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: T): Long = model
        .insertBuilder(testVal)
        .observe()
        .blockingGet()
  }

  class ObserveInsert<T> : SingleOperation<T, T, TestObserver<Long>> {
    override fun invoke(model: TestModel<T>, testVal: T): TestObserver<Long> = model
        .insertBuilder(testVal)
        .observe()
        .test()
  }
}