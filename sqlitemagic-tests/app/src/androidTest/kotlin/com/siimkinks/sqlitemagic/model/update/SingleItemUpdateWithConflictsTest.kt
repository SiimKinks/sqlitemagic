package com.siimkinks.sqlitemagic.model.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.model.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class SingleItemUpdateWithConflictsTest : DefaultConnectionTest {
  class SuccessfulOperationWithConflictAlgorithm<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> T = newUpdatableRandom(),
      operation: DualOperation<T, T, Boolean> = UpdateDualOperation { model, testVal ->
        model.updateBuilder(testVal)
            .conflictAlgorithm(CONFLICT_FAIL)
      },
      assertResults: (TestModel<T>, T, Boolean) -> Unit = assertUpdateSuccess()
  ) : DualOperationTestCase<T, T, Boolean>(
      "Update with arbitrary conflict algorithm succeeds",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun successfulUpdateWithConflictAlgorithm() {
    assertThatDual {
      testCase { SuccessfulOperationWithConflictAlgorithm(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class SimpleModelFailWithIgnoreConflict<T>(
      forModel: TestModelWithUniqueColumn<T>,
      operation: DualOperation<T, T, Boolean> = FailingUpdateWithConflictIgnoreDualOperation()
  ) : DualOperationTestCase<T, T, Boolean>(
      "Failed simple model update with CONFLICT_IGNORE does not throw, but emits false",
      model = forModel,
      setUp = { model ->
        val (v1) = insertNewRandom(model)
        val (v2) = insertNewRandom(model)
        (model as TestModelWithUniqueColumn).transferUniqueVal(v1, v2)
      },
      operation = operation,
      assertResults = { model, testVal, success ->
        assertThat(success).isTrue()
        assertTableDoesNotHaveValue(testVal, model)
      })

  @Test
  fun simpleModelUpdateFailWithIgnoreConflict() {
    assertThatDual {
      testCase { SimpleModelFailWithIgnoreConflict(it as TestModelWithUniqueColumn) }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  class ComplexModelFailWithIgnoreConflictWhereParentFails<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      operation: DualOperation<T, T, Boolean> = FailingUpdateWithConflictIgnoreDualOperation()
  ) : DualOperationTestCase<T, T, Boolean>(
      "Failed complex model update with CONFLICT_IGNORE where conflict happens in parent " +
          "does not throw, emits false and has no affect on child tables",
      model = forModel,
      setUp = {
        val model = it as ComplexTestModelWithUniqueColumn
        val (v1, id1) = insertNewRandom(model)
        val (v2) = insertNewRandom(model)
        val result = model.updateAllVals(v1, id1)
        model.transferUniqueVal(v2, result)
      },
      operation = operation,
      assertResults = { model, testVal, success ->
        assertThat(success).isTrue()
        assertTableDoesNotHaveValue(testVal, model)
      })

  @Test
  fun complexModelUpdateFailWithIgnoreConflictWhereParentFails() {
    assertThatDual {
      testCase { ComplexModelFailWithIgnoreConflictWhereParentFails(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class ComplexModelFailWithIgnoreConflictWhereChildFails<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      operation: DualOperation<T, T, Boolean> = FailingUpdateWithConflictIgnoreDualOperation()
  ) : DualOperationTestCase<T, T, Boolean>(
      "Failed complex model update with CONFLICT_IGNORE where conflict happens in child " +
          "does not throw, emits false and has no affect on parent and child tables",
      model = forModel,
      setUp = {
        val model = it as ComplexTestModelWithUniqueColumn
        val (v1, id1) = insertNewRandom(model)
        val (v2) = insertNewRandom(model)
        val result = model.updateAllVals(v1, id1)
        model.transferComplexColumnUniqueVal(v2, result)
      },
      operation = operation,
      assertResults = { model, testVal, success ->
        assertThat(success).isTrue()
        assertTableDoesNotHaveValue(testVal, model)
      })

  @Test
  fun complexModelUpdateFailWithIgnoreConflictWhereChildFails() {
    assertThatDual {
      testCase { ComplexModelFailWithIgnoreConflictWhereChildFails(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun updateByUniqueColumnWithConflictAlgorithm() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByUniqueColumn(
            forModel = it,
            operation = UpdateDualOperation { model, testVal ->
              model.updateBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun updateByComplexUniqueColumnWithConflictAlgorithm() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByComplexUniqueColumn(
            forModel = it,
            operation = UpdateDualOperation { model, testVal ->
              model.updateBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun updateByComplexColumnUniqueColumnWithConflictAlgorithm() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByComplexColumnUniqueColumn(
            forModel = it,
            operation = UpdateDualOperation { model, testVal ->
              model.updateBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun updateByUniqueColumnWithNullIdAndConflictAlgorithm() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByUniqueColumnWithNullId(
            forModel = it,
            operation = UpdateDualOperation { model, testVal ->
              model.updateBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_NULLABLE_UNIQUE_AUTO_ID_MODELS)
    }
  }

  class FailingUpdateWithConflictIgnoreDualOperation<T>(
      private val algorithm: Int = CONFLICT_IGNORE
  ) : DualOperation<T, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: T): Boolean =
        // we expect false result indicating operation failure
        !model.updateBuilder(testVal)
            .conflictAlgorithm(algorithm)
            .execute()

    override fun observeTest(model: TestModel<T>, testVal: T): Boolean = model
        .updateBuilder(testVal)
        .conflictAlgorithm(algorithm)
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }
}