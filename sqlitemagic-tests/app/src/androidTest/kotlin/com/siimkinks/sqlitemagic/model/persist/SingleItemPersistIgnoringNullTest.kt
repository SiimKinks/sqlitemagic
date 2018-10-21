@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.persist

import androidx.test.runner.AndroidJUnit4
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.insert.SingleItemInsertTest
import com.siimkinks.sqlitemagic.model.persist.SingleItemPersistTest.*
import com.siimkinks.sqlitemagic.model.update.SingleItemUpdateTest
import com.siimkinks.sqlitemagic.model.update.assertUpdateSuccessForComplexNullableColumns
import com.siimkinks.sqlitemagic.model.update.assertUpdateSuccessForNullableColumns
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleItemPersistIgnoringNullTest : DefaultConnectionTest {
  @Test
  fun successfulPersistWithInsertIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemInsertTest.SuccessfulOperation(
            forModel = it,
            setUp = newRandomWithNulledColumns(),
            operation = PersistForInsertDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulPersistWithUpdateIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.SuccessfulOperation(
            forModel = it,
            setUp = newUpdatableRandomWithNulledColumns(),
            operation = PersistForUpdateDualOperation(
                ignoreNullValues = true,
                persistBuilderCallback = TestModel<Any>::persistBuilder),
            assertResults = assertUpdateSuccessForNullableColumns())
      }
      isSuccessfulFor(*ALL_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun successfulPersistComplexWithInsertIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemInsertTest.SuccessfulOperation(
            forModel = it,
            setUp = newComplexRandomWithNulledColumns(),
            operation = PersistForInsertDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulPersistComplexWithUpdateIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.SuccessfulOperation(
            forModel = it,
            setUp = newComplexUpdatableRandomWithNulledColumns(),
            operation = PersistForUpdateDualOperation(
                ignoreNullValues = true,
                persistBuilderCallback = TestModel<Any>::persistBuilder),
            assertResults = assertUpdateSuccessForComplexNullableColumns())
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun failedPersistWithInsertEmitsErrorIgnoringNull() {
    assertThatSingle {
      testCase {
        SingleItemInsertTest.OperationFailEmitsError(
            forModel = it as TestModelWithUniqueColumn,
            test = ObservePersist(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun failedPersistWithUpdateEmitsErrorIgnoringNull() {
    assertThatSingle {
      testCase {
        SingleItemUpdateTest.OperationFailEmitsError(
            forModel = it as TestModelWithUniqueColumn,
            test = ObservePersist(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun failedPersistWithInsertOnComplexModelChildEmitsErrorIgnoringNull() {
    assertThatSingle {
      testCase {
        SingleItemInsertTest.OperationFailOnComplexModelChildEmitsError(
            forModel = it as ComplexTestModelWithUniqueColumn,
            test = ObservePersist(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun failedPersistWithUpdateOnComplexModelChildEmitsErrorIgnoringNull() {
    assertThatSingle {
      testCase {
        SingleItemUpdateTest.OperationFailOnComplexModelChildEmitsError(
            forModel = it as ComplexTestModelWithUniqueColumn,
            test = ObservePersist(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun streamedPersistWithInsertIgnoringNull() {
    assertThatSingle {
      testCase {
        SingleItemInsertTest.StreamedOperation(
            forModel = it,
            test = StreamedPersistWithInsertOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedPersistWithUpdateIgnoringNull() {
    assertThatSingle {
      testCase {
        SingleItemUpdateTest.StreamedOperation(
            forModel = it,
            test = StreamedPersistWithUpdateOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class OperationByUniqueColumnIgnoringNull<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> T = {
        val (newRandom, id) = insertNewRandom(it)
        var updatedVal = it.updateAllVals(newRandom, id)
        updatedVal = (it as NullableColumns<T>).nullSomeColumns(updatedVal)
        (it as TestModelWithUniqueColumn).transferUniqueVal(newRandom, updatedVal)
      },
      operation: DualOperation<T, T, Boolean> = PersistForUpdateDualOperation(ignoreNullValues = true) { model, testVal ->
        model.persistBuilder(testVal)
            .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
      },
      assertResults: (TestModel<T>, T, Boolean) -> Unit = assertUpdateSuccessForNullableColumns()
  ) : DualOperationTestCase<T, T, Boolean>(
      "Update by unique column ignoring null succeeds",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun persistWithUpdateByUniqueColumnIgnoringNull() {
    assertThatDual {
      testCase { OperationByUniqueColumnIgnoringNull(forModel = it) }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  class OperationByComplexUniqueColumnIgnoringNull<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> T = {
        val (newRandom, id) = insertNewRandom(it)
        var updatedVal = it.updateAllVals(newRandom, id)
        updatedVal = (it as NullableColumns<T>).nullSomeColumns(updatedVal)
        (it as ComplexTestModelWithUniqueColumn).transferComplexColumnUniqueVal(newRandom, updatedVal)
      },
      operation: DualOperation<T, T, Boolean> = PersistForUpdateDualOperation(ignoreNullValues = true) { model, testVal ->
        model.persistBuilder(testVal)
            .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
      },
      assertResults: (TestModel<T>, T, Boolean) -> Unit = assertUpdateSuccessForNullableColumns()
  ) : DualOperationTestCase<T, T, Boolean>(
      "Update by complex unique column ignoring null succeeds",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun persistWithUpdateByComplexUniqueColumnIgnoringNull() {
    assertThatDual {
      testCase { OperationByComplexUniqueColumnIgnoringNull(forModel = it) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class OperationByComplexColumnUniqueColumnIgnoringNull<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> T = {
        val (newRandom, id) = insertNewRandom(it)
        var updatedVal = it.updateAllVals(newRandom, id)
        updatedVal = (it as NullableColumns<T>).nullSomeColumns(updatedVal)
        (it as ComplexTestModelWithUniqueColumn).transferAllComplexUniqueVals(newRandom, updatedVal)
      },
      operation: DualOperation<T, T, Boolean> = PersistForUpdateDualOperation(ignoreNullValues = true) { model, testVal ->
        model.persistBuilder(testVal)
            .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
      },
      assertResults: (TestModel<T>, T, Boolean) -> Unit = assertUpdateSuccessForNullableColumns()
  ) : DualOperationTestCase<T, T, Boolean>(
      "Update complex column by its unique column ignoring null succeeds",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = assertResults)

  @Test
  fun persistWithUpdateByComplexColumnUniqueColumnIgnoringNull() {
    assertThatDual {
      testCase { OperationByComplexColumnUniqueColumnIgnoringNull(forModel = it) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }
}