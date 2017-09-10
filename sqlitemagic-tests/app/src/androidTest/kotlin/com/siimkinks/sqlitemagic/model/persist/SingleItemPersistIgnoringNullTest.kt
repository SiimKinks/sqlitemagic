package com.siimkinks.sqlitemagic.model.persist

import android.support.test.runner.AndroidJUnit4
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
                ignoreNullValues = true),
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
                ignoreNullValues = true),
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
}