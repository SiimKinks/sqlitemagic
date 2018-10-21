@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.persist

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.test.runner.AndroidJUnit4
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.insert.BulkItemsInsertTest
import com.siimkinks.sqlitemagic.model.insert.BulkItemsInsertWithConflictsTest
import com.siimkinks.sqlitemagic.model.insert.assertEarlyUnsubscribeFromInsertRollbackedAllValues
import com.siimkinks.sqlitemagic.model.insert.assertEarlyUnsubscribeFromInsertStoppedAnyFurtherWork
import com.siimkinks.sqlitemagic.model.update.BulkItemsUpdateTest
import com.siimkinks.sqlitemagic.model.update.BulkItemsUpdateWithConflictsTest
import com.siimkinks.sqlitemagic.model.update.assertEarlyUnsubscribeFromUpdateRollbackedAllValues
import com.siimkinks.sqlitemagic.model.update.assertEarlyUnsubscribeFromUpdateStoppedAnyFurtherWork
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BulkItemsPersistWithConflictsTest : DefaultConnectionTest {
  @Test
  fun mutableModelBulkPersistWithInsertAndIgnoreConflictSetsIds() {
    assertThatDual {
      testCase {
        BulkItemsInsertTest.MutableModelBulkOperationSetsIds(
            forModel = it,
            operation = BulkPersistForInsertWithIgnoreConflictDualOperation())
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  @Test
  fun simpleModelBulkPersistWithInsertAndIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.SimpleModelBulkOperationWithIgnoreConflict(
            forModel = it as TestModelWithUniqueColumn,
            operation = BulkPersistForInsertWithIgnoreConflictDualOperation())
      }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun simpleModelBulkPersistWithUpdateAndIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.SimpleModelBulkOperationWithIgnoreConflict(
            forModel = it as TestModelWithUniqueColumn,
            operation = BulkPersistForUpdateWithIgnoreConflictDualOperation())
      }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexBulkPersistWithInsertAndIgnoreConflictWhereParentFails() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.ComplexModelBulkOperationWithIgnoreConflictWhereParentFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = BulkPersistForInsertWithIgnoreConflictDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexBulkPersistWithUpdateAndIgnoreConflictWhereParentFails() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.ComplexModelBulkOperationWithIgnoreConflictWhereParentFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = BulkPersistForUpdateWithIgnoreConflictDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexBulkPersistWithInsertAndIgnoreConflictWhereChildFails() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.ComplexModelBulkOperationWithIgnoreConflictWhereChildFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = BulkPersistForInsertWithIgnoreConflictDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexBulkPersistWithUpdateAndIgnoreConflictWhereChildFails() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.ComplexBulkOperationWithIgnoreConflictWhereChildFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = BulkPersistForUpdateWithIgnoreConflictDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexModelBulkPersistWithInsertAndIgnoreConflictWhereAllChildrenFail() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.ComplexBulkOperationWithIgnoreConflictWhereAllChildrenFail(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = BulkPersistForInsertWithIgnoreConflictWhereAllFailDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexModelBulkPersistWithUpdateAndIgnoreConflictWhereAllChildrenFail() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.ComplexBulkOperationWithIgnoreConflictWhereAllChildrenFail(
            forModel = it as ComplexTestModelWithUniqueColumn,
            operation = BulkPersistForUpdateWithIgnoreConflictWhereAllFailDualOperation())
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithInsertAndIgnoreConflictWhereAllFail() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.BulkOperationWithIgnoreConflictWhereAllFail(
            forModel = it,
            setUp = {
              val model = it as TestModelWithUniqueColumn
              createVals {
                val (v, _) = insertNewRandom(model)
                val newRandom = it.newRandom()
                model.transferUniqueVal(v, newRandom)
              }
            },
            operation = BulkPersistForInsertWithIgnoreConflictWhereAllFailDualOperation())
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateAndIgnoreConflictWhereAllFail() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.BulkOperationWithIgnoreConflictWhereAllFail(
            forModel = it as TestModelWithUniqueColumn,
            operation = BulkPersistForUpdateWithIgnoreConflictWhereAllFailDualOperation())
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun earlyUnsubscribeFromSimpleModelPersistWithInsertAndIgnoreConflict() {
    assertThatSingle {
      testCase {
        BulkItemsInsertWithConflictsTest.EarlyUnsubscribeWithIgnoreConflict(
            "Unsubscribing in-flight bulk persist with insert and ignore conflict algorithm " +
                "on simple models rollbacks all values",
            forModel = it,
            test = BulkPersistEarlyUnsubscribeWithIgnoreConflict(),
            assertResults = assertEarlyUnsubscribeFromInsertRollbackedAllValues())
      }
      isSuccessfulFor(*SIMPLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun earlyUnsubscribeFromSimpleModelPersistWithUpdateAndIgnoreConflict() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateWithConflictsTest.EarlyUnsubscribeWithIgnoreConflict(
            "Unsubscribing in-flight bulk persist with update and ignore conflict algorithm " +
                "on simple models rollbacks all values",
            forModel = it,
            test = BulkPersistEarlyUnsubscribeWithIgnoreConflict(),
            assertResults = assertEarlyUnsubscribeFromUpdateRollbackedAllValues())
      }
      isSuccessfulFor(*SIMPLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun earlyUnsubscribeFromComplexModelPersistWithInsertAndIgnoreConflict() {
    assertThatSingle {
      testCase {
        BulkItemsInsertWithConflictsTest.EarlyUnsubscribeWithIgnoreConflict(
            "Unsubscribing in-flight bulk persist with insert and ignore conflict algorithm " +
                "on complex models stops any further work",
            forModel = it,
            test = BulkPersistEarlyUnsubscribeWithIgnoreConflict(),
            assertResults = assertEarlyUnsubscribeFromInsertStoppedAnyFurtherWork())
      }
      isSuccessfulFor(*COMPLEX_AUTO_ID_MODELS)
    }
  }

  @Test
  fun earlyUnsubscribeFromComplexModelPersistWithUpdateAndIgnoreConflict() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateWithConflictsTest.EarlyUnsubscribeWithIgnoreConflict(
            "Unsubscribing in-flight bulk persist with update and ignore conflict algorithm " +
                "on complex models stops any further work",
            forModel = it,
            test = BulkPersistEarlyUnsubscribeWithIgnoreConflict(),
            assertResults = assertEarlyUnsubscribeFromUpdateStoppedAnyFurtherWork())
      }
      isSuccessfulFor(*COMPLEX_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedBulkPersistWithInsertAndIgnoreConflict() {
    assertThatSingle {
      testCase {
        BulkItemsInsertTest.StreamedBulkOperation(
            forModel = it,
            test = BulkItemsPersistTest.StreamedBulkPersistWithInsertOperation(
                ignoreConflict = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedBulkPersistWithUpdateAndIgnoreConflict() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateTest.StreamedBulkOperation(
            forModel = it,
            test = BulkItemsPersistTest.StreamedBulkPersistWithUpdateOperation(
                ignoreConflict = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByUniqueColumnAndIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByUniqueColumn(
            forModel = it,
            operation = BulkPersistDualOperation { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByComplexUniqueColumnAndIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByComplexUniqueColumn(
            forModel = it,
            operation = BulkPersistDualOperation { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByComplexColumnUniqueColumnAndIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByComplexColumnUniqueColumn(
            forModel = it,
            operation = BulkPersistDualOperation { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByUniqueColumnWithNullIdAndIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByUniqueColumnWithNullId(
            forModel = it,
            operation = BulkPersistDualOperation { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_NULLABLE_UNIQUE_AUTO_ID_MODELS)
    }
  }

  class BulkPersistForInsertWithIgnoreConflictDualOperation<T>(private val ignoreNullValues: Boolean = false) : DualOperation<List<T>, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: List<T>): Boolean = model
        .bulkPersistBuilder(testVal)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: List<T>): Boolean = model
        .bulkPersistBuilder(testVal)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }

  class BulkPersistForUpdateWithIgnoreConflictDualOperation<T>(private val ignoreNullValues: Boolean = false) : DualOperation<BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictTestVals<T>, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictTestVals<T>): Boolean = model
        .bulkPersistBuilder(testVal.allTestVals)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictTestVals<T>): Boolean = model
        .bulkPersistBuilder(testVal.allTestVals)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }

  class BulkPersistForInsertWithIgnoreConflictWhereAllFailDualOperation<T>(private val ignoreNullValues: Boolean = false) : DualOperation<List<T>, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: List<T>): Boolean =
        // we expect false result indicating all operations failure
        !model.bulkPersistBuilder(testVal)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .also { if (ignoreNullValues) it.ignoreNullValues() }
            .execute()

    override fun observeTest(model: TestModel<T>, testVal: List<T>): Boolean = model
        .bulkPersistBuilder(testVal)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }

  class BulkPersistForUpdateWithIgnoreConflictWhereAllFailDualOperation<T>(private val ignoreNullValues: Boolean = false) : DualOperation<BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>): Boolean =
        // we expect false result indicating all operations failure
        !model.bulkPersistBuilder(testVal.testVals)
            .conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
            .also { if (ignoreNullValues) it.ignoreNullValues() }
            .execute()

    override fun observeTest(model: TestModel<T>, testVal: BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>): Boolean = model
        .bulkPersistBuilder(testVal.testVals)
        .conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
        .also { if (ignoreNullValues) it.ignoreNullValues() }
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }

  class BulkPersistEarlyUnsubscribeWithIgnoreConflict<T>(private val ignoreNullValues: Boolean = false) : SingleOperation<List<T>, T, AtomicInteger> {
    override fun invoke(model: TestModel<T>, testVal: List<T>): AtomicInteger {
      val eventsCount = AtomicInteger(0)
      val disposable = model
          .bulkPersistBuilder(testVal)
          .conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
          .also { if (ignoreNullValues) it.ignoreNullValues() }
          .observe()
          .subscribeOn(Schedulers.io())
          .subscribe({ eventsCount.incrementAndGet() }, { eventsCount.incrementAndGet() })
      Thread.sleep(10)
      disposable.dispose()
      Thread.sleep(500)
      return eventsCount
    }
  }
}