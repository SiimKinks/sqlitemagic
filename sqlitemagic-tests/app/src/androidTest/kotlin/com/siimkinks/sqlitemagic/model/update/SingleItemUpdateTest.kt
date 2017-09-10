package com.siimkinks.sqlitemagic.model.update

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
class SingleItemUpdateTest : DefaultConnectionTest {
  class SuccessfulOperation<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> T = newUpdatableRandom(),
      operation: DualOperation<T, T, Boolean> = UpdateDualOperation(),
      assertResults: (TestModel<T>, T, Boolean) -> Unit = assertUpdateSuccess()
  ) : DualOperationTestCase<T, T, Boolean>(
      "Simple update succeeds",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun successfulUpdate() {
    assertThatDual {
      testCase { SuccessfulOperation(forModel = it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class OperationFailEmitsError<T>(
      forModel: TestModelWithUniqueColumn<T>,
      test: SingleOperation<T, T, TestObserver<Long>> = ObserveUpdate()
  ) : SingleOperationTestCase<T, T, TestObserver<Long>>(
      "When update fails it emits error and rolls back values",
      model = forModel,
      setUp = {
        val (v1) = insertNewRandom(it)
        val (v2) = insertNewRandom(it)
        val model = it as TestModelWithUniqueColumn
        model.transferUniqueVal(v1, v2)
      },
      test = test,
      assertResults = { model, testVal, ts ->
        assertSingleError(ts)
        assertTableDoesNotHaveValue(testVal, model)
      })

  @Test
  fun failedUpdateEmitsError() {
    assertThatSingle {
      testCase { OperationFailEmitsError(forModel = it as TestModelWithUniqueColumn<Any>) }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  class OperationFailOnComplexModelChildEmitsError<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      test: SingleOperation<T, T, TestObserver<Long>> = ObserveUpdate()
  ) : SingleOperationTestCase<T, T, TestObserver<Long>>(
      "When complex model update fails it emits error and rolls back all values",
      model = forModel,
      setUp = {
        val (v1) = insertNewRandom(it)
        val (v2) = insertNewRandom(it)
        val model = it as ComplexTestModelWithUniqueColumn
        model.transferComplexUniqueVal(v1, v2)
      },
      test = test,
      assertResults = { model, testVal, ts ->
        assertSingleError(ts)
        assertTableDoesNotHaveValue(testVal, model)
      })

  @Test
  fun failedUpdateOnComplexModelChildEmitsError() {
    assertThatSingle {
      testCase { OperationFailOnComplexModelChildEmitsError(it as ComplexTestModelWithUniqueColumn<Any>) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class StreamedOperation<T>(
      forModel: TestModel<T>,
      test: SingleOperation<Pair<T, T>, T, TestObserver<Any>> = { model, (first, second) ->
        model.updateBuilder(first)
            .observe()
            .toSingleDefault(Any())
            .flatMap {
              model.updateBuilder(second)
                  .observe()
                  .toSingleDefault(Any())
            }
            .subscribeOn(Schedulers.io())
            .test()
      }
  ) : SingleOperationTestCase<Pair<T, T>, T, TestObserver<Any>>(
      "Two streamed update operations work as expected",
      model = forModel,
      setUp = { model ->
        val (v1, id1) = insertNewRandom(model)
        val first = model.updateAllVals(v1, id1)
        val (v2, id2) = insertNewRandom(model)
        val second = model.updateAllVals(v2, id2)
        first to second
      },
      test = test,
      assertResults = { _, _, ts ->
        ts.awaitTerminalEvent(1, TimeUnit.SECONDS)
        ts.assertNoErrors()
        ts.assertComplete()
        val values = ts.values()
        assertThat(values).hasSize(1)
      })

  @Test
  fun streamedUpdate() {
    assertThatSingle {
      testCase { StreamedOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class UpdateDualOperation<T> : DualOperation<T, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: T): Boolean = model
        .updateBuilder(testVal)
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: T): Boolean = model
        .updateBuilder(testVal)
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }

  class ObserveUpdate<T> : SingleOperation<T, T, TestObserver<Long>> {
    override fun invoke(model: TestModel<T>, testVal: T): TestObserver<Long> {
      val ts = TestObserver<Long>()
      model.updateBuilder(testVal)
          .observe()
          .subscribe(ts)
      return ts
    }
  }
}