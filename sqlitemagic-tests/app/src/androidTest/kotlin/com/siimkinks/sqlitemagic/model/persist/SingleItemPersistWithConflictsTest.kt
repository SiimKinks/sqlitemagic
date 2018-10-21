package com.siimkinks.sqlitemagic.model.persist

import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.test.runner.AndroidJUnit4
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.insert.SingleItemInsertTest
import com.siimkinks.sqlitemagic.model.insert.SingleItemInsertWithConflictsTest
import com.siimkinks.sqlitemagic.model.persist.SingleItemPersistTest.StreamedPersistWithInsertOperation
import com.siimkinks.sqlitemagic.model.persist.SingleItemPersistTest.StreamedPersistWithUpdateOperation
import com.siimkinks.sqlitemagic.model.update.SingleItemUpdateTest
import com.siimkinks.sqlitemagic.model.update.SingleItemUpdateWithConflictsTest
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleItemPersistWithConflictsTest : DefaultConnectionTest {
  @Test
  fun successfulPersistWithInsertAndConflictAlgorithm() {
    assertThatDual {
      testCase {
        SingleItemInsertWithConflictsTest.SuccessfulOperationWithConflictAlgorithm(
            forModel = it,
            operation = PersistForInsertWithConflictAlgorithmDualOperation(CONFLICT_FAIL))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulPersistWithUpdateAndConflictAlgorithm() {
    assertThatDual {
      testCase {
        SingleItemUpdateWithConflictsTest.SuccessfulOperationWithConflictAlgorithm(
            forModel = it,
            operation = PersistForUpdateWithConflictAlgorithmDualOperation(CONFLICT_FAIL))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun persistWithInsertFailWithIgnoreConflict() {
    assertThatDual {
      testCase {
        SingleItemInsertWithConflictsTest.SimpleModelFailWithIgnoreConflict(
            forModel = it as TestModelWithUniqueColumn,
            operation = PersistForInsertWithConflictAlgorithmDualOperation())
      }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateFailWithIgnoreConflict() {
    assertThatDual {
      testCase {
        SingleItemUpdateWithConflictsTest.SimpleModelFailWithIgnoreConflict(
            forModel = it as TestModelWithUniqueColumn,
            operation = PersistForFailingUpdateWithConflictAlgorithmDualOperation())
      }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithInsertComplexModelFailWithIgnoreConflictWhereParentFails() {
    assertThatDual {
      testCase {
        SingleItemInsertWithConflictsTest.ComplexModelFailWithIgnoreConflictWhereParentFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = PersistForInsertWithConflictAlgorithmDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateComplexModelFailWithIgnoreConflictWhereParentFails() {
    assertThatDual {
      testCase {
        SingleItemUpdateWithConflictsTest.ComplexModelFailWithIgnoreConflictWhereParentFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = PersistForFailingUpdateWithConflictAlgorithmDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithInsertComplexModelFailWithIgnoreConflictWhereChildFails() {
    assertThatDual {
      testCase {
        SingleItemInsertWithConflictsTest.ComplexModelFailWithIgnoreConflictWhereChildFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = PersistForInsertWithConflictAlgorithmDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateComplexModelFailWithIgnoreConflictWhereChildFails() {
    assertThatDual {
      testCase {
        SingleItemUpdateWithConflictsTest.ComplexModelFailWithIgnoreConflictWhereChildFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = PersistForFailingUpdateWithConflictAlgorithmDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun streamedPersistWithInsertAndIgnoreConflict() {
    assertThatSingle {
      testCase {
        SingleItemInsertTest.StreamedOperation(
            forModel = it,
            test = StreamedPersistWithInsertOperation(
                ignoreConflict = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedPersistWithUpdateAndIgnoreConflict() {
    assertThatSingle {
      testCase {
        SingleItemUpdateTest.StreamedOperation(
            forModel = it,
            test = StreamedPersistWithUpdateOperation(
                ignoreConflict = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByUniqueColumnAndIgnoreConflict() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByUniqueColumn(
            forModel = it,
            operation = PersistForUpdateDualOperation { model, testVal ->
              model.persistBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByComplexUniqueColumnAndIgnoreConflict() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByComplexUniqueColumn(
            forModel = it,
            operation = PersistForUpdateDualOperation { model, testVal ->
              model.persistBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByComplexColumnUniqueColumnAndIgnoreConflict() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByComplexColumnUniqueColumn(
            forModel = it,
            operation = PersistForUpdateDualOperation { model, testVal ->
              model.persistBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun persistWithUpdateByUniqueColumnWithNullIdAndConflictAlgorithm() {
    assertThatDual {
      testCase {
        SingleItemUpdateTest.OperationByUniqueColumnWithNullId(
            forModel = it,
            operation = PersistForUpdateDualOperation { model, testVal ->
              model.persistBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_NULLABLE_UNIQUE_AUTO_ID_MODELS)
    }
  }

  class PersistForInsertWithConflictAlgorithmDualOperation<T>(
      private val algorithm: Int = CONFLICT_IGNORE,
      private val ignoreNullValues: Boolean = false
  ) : DualOperation<T, T, Long> {
    override fun executeTest(model: TestModel<T>, testVal: T): Long = model
        .persistBuilder(testVal)
        .conflictAlgorithm(algorithm)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: T): Long = model
        .persistBuilder(testVal)
        .conflictAlgorithm(algorithm)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .blockingGet()
  }

  class PersistForUpdateWithConflictAlgorithmDualOperation<T>(
      private val algorithm: Int = CONFLICT_IGNORE,
      private val ignoreNullValues: Boolean = false
  ) : DualOperation<T, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: T): Boolean = model
        .persistBuilder(testVal)
        .conflictAlgorithm(algorithm)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .execute() != -1L

    override fun observeTest(model: TestModel<T>, testVal: T): Boolean = model
        .persistBuilder(testVal)
        .conflictAlgorithm(algorithm)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .blockingGet() != -1L
  }

  class PersistForFailingUpdateWithConflictAlgorithmDualOperation<T>(
      private val algorithm: Int = CONFLICT_IGNORE,
      private val ignoreNullValues: Boolean = false
  ) : DualOperation<T, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: T): Boolean = model
        .persistBuilder(testVal)
        .conflictAlgorithm(algorithm)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .execute() == -1L

    override fun observeTest(model: TestModel<T>, testVal: T): Boolean = model
        .persistBuilder(testVal)
        .conflictAlgorithm(algorithm)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .blockingGet() == -1L
  }
}