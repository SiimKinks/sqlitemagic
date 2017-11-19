@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.insert.SingleItemInsertTest
import com.siimkinks.sqlitemagic.model.update.SingleItemUpdateTest
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleItemPersistTest : DefaultConnectionTest {
  @Test
  fun successfulPersistWithInsert() {
    assertThatDual {
      testCase {
        SingleItemInsertTest.SuccessfulOperation(
            forModel = it,
            operation = PersistForInsertDualOperation())
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulPersistWithUpdate() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.SuccessfulOperation(
            forModel = it,
            operation = PersistForUpdateDualOperation(persistBuilderCallback = TestModel<Any>::persistBuilder))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun failedPersistWithInsertEmitsError() {
    assertThatSingle {
      testCase {
        SingleItemInsertTest.OperationFailEmitsError(
            forModel = it as TestModelWithUniqueColumn,
            test = ObservePersist())
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun failedPersistWithUpdateEmitsError() {
    assertThatSingle {
      testCase {
        SingleItemUpdateTest.OperationFailEmitsError(
            forModel = it as TestModelWithUniqueColumn,
            test = ObservePersist())
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun failedPersistWithInsertOnComplexModelChildEmitsError() {
    assertThatSingle {
      testCase {
        SingleItemInsertTest.OperationFailOnComplexModelChildEmitsError(
            forModel = it as ComplexTestModelWithUniqueColumn,
            test = ObservePersist())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun failedPersistWithUpdateOnComplexModelChildEmitsError() {
    assertThatSingle {
      testCase {
        SingleItemUpdateTest.OperationFailOnComplexModelChildEmitsError(
            forModel = it as ComplexTestModelWithUniqueColumn,
            test = ObservePersist())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun streamedPersistWithInsert() {
    assertThatSingle {
      testCase {
        SingleItemInsertTest.StreamedOperation(
            forModel = it,
            test = StreamedPersistWithInsertOperation())
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedPersistWithUpdate() {
    assertThatSingle {
      testCase {
        SingleItemUpdateTest.StreamedOperation(
            forModel = it,
            test = StreamedPersistWithUpdateOperation())
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByUniqueColumn() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByUniqueColumn(
            forModel = it,
            operation = PersistForUpdateDualOperation { model, testVal ->
              model.persistBuilder(testVal)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByComplexUniqueColumn() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByComplexUniqueColumn(
            forModel = it,
            operation = PersistForUpdateDualOperation { model, testVal ->
              model.persistBuilder(testVal)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByComplexColumnUniqueColumn() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByComplexColumnUniqueColumn(
            forModel = it,
            operation = PersistForUpdateDualOperation { model, testVal ->
              model.persistBuilder(testVal)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class PersistForInsertDualOperation<T>(private val ignoreNullValues: Boolean = false) : DualOperation<T, T, Long> {
    override fun executeTest(model: TestModel<T>, testVal: T): Long = model
        .persistBuilder(testVal)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: T): Long = model
        .persistBuilder(testVal)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .blockingGet()
  }

  class ObservePersist<T>(private val ignoreNullValues: Boolean = false) : SingleOperation<T, T, TestObserver<Long>> {
    override fun invoke(model: TestModel<T>, testVal: T): TestObserver<Long> = model
        .persistBuilder(testVal)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .test()
  }

  class StreamedPersistWithInsertOperation<T>(
      private val ignoreNullValues: Boolean = false,
      private val ignoreConflict: Boolean = false
  ) : SingleOperation<T, T, TestObserver<Long>> {
    override fun invoke(model: TestModel<T>, testVal: T): TestObserver<Long> = model
        .persistBuilder(testVal)
        .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .flatMap { id ->
          assertThat(id).isNotEqualTo(-1L)
          val newRandom = model.newRandom()
          model.setId(newRandom, -1)
          model.persistBuilder(newRandom)
              .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
              .also { if (ignoreNullValues) it.ignoreNullValues() }
              .observe()
        }
        .subscribeOn(Schedulers.io())
        .test()
  }

  class StreamedPersistWithUpdateOperation<T>(
      private val ignoreNullValues: Boolean = false,
      private val ignoreConflict: Boolean = false
  ) : SingleOperation<Pair<T, T>, T, TestObserver<Any>> {
    override fun invoke(model: TestModel<T>, testVal: Pair<T, T>): TestObserver<Any> = model
        .persistBuilder(testVal.first)
        .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .flatMap {
          model.persistBuilder(testVal.second)
              .also { if (ignoreConflict) it.conflictAlgorithm(CONFLICT_IGNORE) }
              .also { if (ignoreNullValues) it.ignoreNullValues() }
              .observe()
        }
        .subscribeOn(Schedulers.io())
        .test() as TestObserver<Any>
  }
}
