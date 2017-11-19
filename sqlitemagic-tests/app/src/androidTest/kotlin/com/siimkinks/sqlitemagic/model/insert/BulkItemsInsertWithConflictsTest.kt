package com.siimkinks.sqlitemagic.model.insert

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableWithUniqueTable
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.insert.BulkItemsInsertTest.*
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BulkItemsInsertWithConflictsTest : DefaultConnectionTest {
  @Test
  fun mutableModelBulkInsertWithIgnoreConflictSetsIds() {
    assertThatDual {
      testCase {
        MutableModelBulkOperationSetsIds(
            forModel = it,
            operation = BulkInsertWithIgnoreConflictDualOperation())
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel
      )
    }
  }

  class SimpleModelBulkOperationWithIgnoreConflict<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> List<T> = {
        val testVals = createVals { insertNewRandom(it).first }
        for (i in 0..10) {
          testVals.add(it.newRandom())
        }
        Collections.shuffle(testVals)
        testVals
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkInsertWithIgnoreConflictDualOperation()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk insert for simple model succeeds although half the collection is already in DB" +
          "and half is not -- result should be that all values are in DB",
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
      })

  @Test
  fun simpleModelBulkInsertWithIgnoreConflict() {
    assertThatDual {
      testCase { SimpleModelBulkOperationWithIgnoreConflict(it as TestModelWithUniqueColumn) }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  class ComplexModelBulkOperationWithIgnoreConflictWhereParentFails<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      setUp: (TestModel<T>) -> List<T> = {
        val model = it as ComplexTestModelWithUniqueColumn
        val testVals = createVals {
          val (v1, _) = insertNewRandom(model)
          val newRandom = model.newRandom()
          model.transferUniqueVal(v1, newRandom)
        }
        for (i in 0..10) {
          testVals.add(model.newRandom())
        }
        Collections.shuffle(testVals)
        testVals
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkInsertWithIgnoreConflictDualOperation()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk insert with CONFLICT_IGNORE where parent fails must rollback all failed parent children, " +
          "but not fail the whole operation",
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
        (model as ComplexTestModelWithUniqueColumn).assertChildrenCountPerParentCount(testVals)
      })

  @Test
  fun complexModelBulkInsertWithIgnoreConflictWhereParentFails() {
    assertThatDual {
      testCase { ComplexModelBulkOperationWithIgnoreConflictWhereParentFails(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class ComplexModelBulkOperationWithIgnoreConflictWhereChildFails<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      setUp: (TestModel<T>) -> List<T> = {
        val model = it as ComplexTestModelWithUniqueColumn
        val testVals = createVals {
          val (newRandom, _) = insertNewRandom(model)
          val newVal = model.newRandom()
          model.transferComplexColumnUniqueVal(newRandom, newVal)
        }
        for (i in 0..10) {
          testVals.add(it.newRandom())
        }
        Collections.shuffle(testVals)
        testVals
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkInsertWithIgnoreConflictDualOperation()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk insert with CONFLICT_IGNORE where child fails must rollback parent and other " +
          "successful children, but not fail the whole operation",
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
        (model as ComplexTestModelWithUniqueColumn).assertChildrenCountPerParentCount(testVals)
      })

  @Test
  fun complexModelBulkInsertWithIgnoreConflictWhereChildFails() {
    assertThatDual {
      testCase { ComplexModelBulkOperationWithIgnoreConflictWhereChildFails(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class ComplexBulkOperationWithIgnoreConflictWhereAllChildrenFail<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      setUp: (TestModel<T>) -> List<T> = {
        val model = it as ComplexTestModelWithUniqueColumn
        createVals {
          val (newRandom) = insertNewRandom(it)
          val newVal = it.newRandom()
          model.transferComplexColumnUniqueVal(newRandom, newVal)
        }
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkInsertWithIgnoreConflictWhereAllFailDualOperation()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk insert observe with ignore conflict where all complex model children fail must " +
          "complete and rollback all executed changes",
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
        assertThat(Select
            .from(SimpleMutableWithUniqueTable.SIMPLE_MUTABLE_WITH_UNIQUE)
            .count()
            .execute())
            .isEqualTo(testVals.size * 2)
      })

  @Test
  fun complexModelBulkInsertWithIgnoreConflictWhereAllChildrenFail() {
    assertThatDual {
      testCase { ComplexBulkOperationWithIgnoreConflictWhereAllChildrenFail(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class BulkOperationWithIgnoreConflictWhereAllFail<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> List<T> = {
        createVals {
          val (newRandom, id) = insertNewRandom(it)
          it.setId(newRandom, id)
        }
      },
      operation: DualOperation<List<T>, T, Boolean> = BulkInsertWithIgnoreConflictWhereAllFailDualOperation()
  ) : DualOperationTestCase<List<T>, T, Boolean>(
      "Bulk insert with ignore conflict where all fail must complete and leave table untouched",
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
      })

  @Test
  fun bulkInsertWithIgnoreConflictWhereAllFail() {
    assertThatDual {
      testCase { BulkOperationWithIgnoreConflictWhereAllFail(it) }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  class EarlyUnsubscribeWithIgnoreConflict<T>(
      description: String = "Early unsubscribe from bulk insert with ignore conflict algorithm",
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> List<T> = {
        createVals(count = 500, createFun = it::newRandom)
      },
      test: (TestModel<T>, List<T>) -> AtomicInteger = { model, testVals ->
        val eventsCount = AtomicInteger(0)
        val disposable = model.bulkInsertBuilder(testVals)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .observe()
            .subscribeOn(Schedulers.io())
            .subscribe({ eventsCount.incrementAndGet() }, { eventsCount.incrementAndGet() })
        Thread.sleep(10)
        disposable.dispose()
        Thread.sleep(500)
        eventsCount
      },
      assertResults: (TestModel<T>, List<T>, AtomicInteger) -> Unit
  ) : SingleOperationTestCase<List<T>, T, AtomicInteger>(
      description,
      model = forModel,
      setUp = setUp,
      test = test,
      assertResults = assertResults)

  @Test
  fun earlyUnsubscribeFromSimpleWithIgnoreConflict() = EarlyUnsubscribeWithIgnoreConflict(
      "Unsubscribing in-flight bulk insert with ignore conflict algorithm " +
          "on simple models rollbacks all values",
      forModel = simpleMutableAutoIdTestModel,
      assertResults = assertEarlyUnsubscribeFromInsertRollbackedAllValues())
      .test()

  @Test
  fun earlyUnsubscribeFromComplexWithIgnoreConflict() = EarlyUnsubscribeWithIgnoreConflict(
      "Unsubscribing in-flight bulk insert with ignore conflict algorithm " +
          "on complex models stops any further work",
      forModel = complexMutableAutoIdTestModel,
      assertResults = assertEarlyUnsubscribeFromInsertStoppedAnyFurtherWork())
      .test()

  @Test
  fun streamedBulkInsertWithIgnoreConflict() {
    assertThatSingle {
      testCase {
        StreamedBulkOperation(
            forModel = it,
            test = StreamedBulkInsertOperation(ignoreConflict = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class BulkInsertWithIgnoreConflictDualOperation<T> : DualOperation<List<T>, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: List<T>): Boolean = model
        .bulkInsertBuilder(testVal)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: List<T>): Boolean = model
        .bulkInsertBuilder(testVal)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }

  class BulkInsertWithIgnoreConflictWhereAllFailDualOperation<T> : DualOperation<List<T>, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: List<T>): Boolean =
        // we expect false result indicating all operations failure
        !model.bulkInsertBuilder(testVal)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()

    override fun observeTest(model: TestModel<T>, testVal: List<T>): Boolean = model
        .bulkInsertBuilder(testVal)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }
}