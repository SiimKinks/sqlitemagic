package com.siimkinks.sqlitemagic.model.persist

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.insert.SingleItemInsertTest
import com.siimkinks.sqlitemagic.model.insert.SingleItemInsertWithConflictsTest
import com.siimkinks.sqlitemagic.model.persist.SingleItemPersistTest.StreamedPersistWithInsertOperation
import com.siimkinks.sqlitemagic.model.persist.SingleItemPersistTest.StreamedPersistWithUpdateOperation
import com.siimkinks.sqlitemagic.model.persist.SingleItemPersistWithConflictsTest.*
import com.siimkinks.sqlitemagic.model.update.SingleItemUpdateTest
import com.siimkinks.sqlitemagic.model.update.SingleItemUpdateWithConflictsTest
import com.siimkinks.sqlitemagic.model.update.assertUpdateSuccessForComplexNullableColumns
import com.siimkinks.sqlitemagic.model.update.assertUpdateSuccessForNullableColumns
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleItemPersistWithConflictsIgnoringNullTest : DefaultConnectionTest {
  @Test
  fun successfulPersistWithInsertAndConflictAlgorithmIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemInsertWithConflictsTest.SuccessfulOperationWithConflictAlgorithm(
            forModel = it,
            setUp = newRandomWithNulledColumns(),
            operation = PersistForInsertWithConflictAlgorithmDualOperation(
                algorithm = SQLiteDatabase.CONFLICT_FAIL,
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulPersistWithUpdateAndConflictAlgorithmIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemUpdateWithConflictsTest.SuccessfulOperationWithConflictAlgorithm(
            forModel = it,
            setUp = newUpdatableRandomWithNulledColumns(),
            operation = PersistForUpdateWithConflictAlgorithmDualOperation(
                algorithm = SQLiteDatabase.CONFLICT_FAIL,
                ignoreNullValues = true),
            assertResults = assertUpdateSuccessForNullableColumns())
      }
      isSuccessfulFor(*ALL_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun successfulPersistComplexWithInsertAndConflictAlgorithmIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemInsertWithConflictsTest.SuccessfulOperationWithConflictAlgorithm(
            forModel = it,
            setUp = newComplexRandomWithNulledColumns(),
            operation = PersistForInsertWithConflictAlgorithmDualOperation(
                algorithm = SQLiteDatabase.CONFLICT_FAIL,
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulPersistComplexWithUpdateAndConflictAlgorithmIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemUpdateWithConflictsTest.SuccessfulOperationWithConflictAlgorithm(
            forModel = it,
            setUp = newComplexUpdatableRandomWithNulledColumns(),
            operation = PersistForUpdateWithConflictAlgorithmDualOperation(
                algorithm = SQLiteDatabase.CONFLICT_FAIL,
                ignoreNullValues = true),
            assertResults = assertUpdateSuccessForComplexNullableColumns())
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithInsertFailWithIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemInsertWithConflictsTest.SimpleModelFailWithIgnoreConflict(
            forModel = it as TestModelWithUniqueColumn,
            operation = PersistForInsertWithConflictAlgorithmDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateFailWithIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemUpdateWithConflictsTest.SimpleModelFailWithIgnoreConflict(
            forModel = it as TestModelWithUniqueColumn,
            operation = PersistForFailingUpdateWithConflictAlgorithmDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithInsertComplexModelFailWithIgnoreConflictWhereParentFailsIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemInsertWithConflictsTest.ComplexModelFailWithIgnoreConflictWhereParentFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = PersistForInsertWithConflictAlgorithmDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateComplexModelFailWithIgnoreConflictWhereParentFailsIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemUpdateWithConflictsTest.ComplexModelFailWithIgnoreConflictWhereParentFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = PersistForFailingUpdateWithConflictAlgorithmDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithInsertComplexModelFailWithIgnoreConflictWhereChildFailsIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemInsertWithConflictsTest.ComplexModelFailWithIgnoreConflictWhereChildFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = PersistForInsertWithConflictAlgorithmDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateComplexModelFailWithIgnoreConflictWhereChildFailsIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemUpdateWithConflictsTest.ComplexModelFailWithIgnoreConflictWhereChildFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = PersistForFailingUpdateWithConflictAlgorithmDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun streamedPersistWithInsertAndIgnoreConflictIgnoringNull() {
    assertThatSingle {
      testCase {
        SingleItemInsertTest.StreamedOperation(
            forModel = it,
            test = StreamedPersistWithInsertOperation(
                ignoreConflict = true,
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedPersistWithUpdateAndIgnoreConflictIgnoringNull() {
    assertThatSingle {
      testCase {
        SingleItemUpdateTest.StreamedOperation(
            forModel = it,
            test = StreamedPersistWithUpdateOperation(
                ignoreConflict = true,
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByUniqueColumnAndIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemPersistIgnoringNullTest.OperationByUniqueColumnIgnoringNull(
            forModel = it,
            operation = PersistForUpdateDualOperation(ignoreNullValues = true) { model, testVal ->
              model.persistBuilder(testVal)
                  .conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByComplexUniqueColumnAndIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemPersistIgnoringNullTest.OperationByComplexUniqueColumnIgnoringNull(
            forModel = it,
            operation = PersistForUpdateDualOperation(ignoreNullValues = true) { model, testVal ->
              model.persistBuilder(testVal)
                  .conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByComplexColumnUniqueColumnAndIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        SingleItemPersistIgnoringNullTest.OperationByComplexColumnUniqueColumnIgnoringNull(
            forModel = it,
            operation = PersistForUpdateDualOperation(ignoreNullValues = true) { model, testVal ->
              model.persistBuilder(testVal)
                  .conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }
}