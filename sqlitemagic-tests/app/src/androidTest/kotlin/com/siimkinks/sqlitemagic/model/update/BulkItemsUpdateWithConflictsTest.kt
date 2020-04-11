@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.update

import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.update.BulkItemsUpdateTest.StreamedBulkOperation
import com.siimkinks.sqlitemagic.model.update.BulkItemsUpdateTest.StreamedBulkUpdateOperation
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class BulkItemsUpdateWithConflictsTest : DefaultConnectionTest {
  class BulkUpdateWithIgnoreConflictTestVals<out T>(
      private val model: TestModelWithUniqueColumn<T>,
      private val nullSomeColumns: Boolean = false,
      private val transferUniqueVal: (TestModelWithUniqueColumn<T>, T, T) -> T = TestModelWithUniqueColumn<T>::transferUniqueVal
  ) {
    val allTestVals: List<T>
    val updated: List<T>
    val updatedIds: List<Long>
    val failing: List<T>
    val failingIds: List<Long>
    val notUpdated: List<T>

    init {
      val (newRandom) = insertNewRandom(model)
      val failingIds = ArrayList<Long>(10)
      val failing = createVals {
        val (v, id) = insertNewRandom(model)
        failingIds.add(id)
        transferUniqueVal(model, newRandom, v)
            .let { if (nullSomeColumns) (model as NullableColumns<T>).nullSomeColumns(it) else it }
      }
      val notUpdated = Select
          .from(model.table)
          .where(model.idColumn.`in`(failingIds))
          .queryDeep()
          .execute()
      val updatedIds = ArrayList<Long>(10)
      val updated = createVals {
        val (v, id) = insertNewRandom(model)
        updatedIds.add(id)
        model.updateAllVals(v, id)
            .let { if (nullSomeColumns) (model as NullableColumns<T>).nullSomeColumns(it) else it }
      }
      val testVals = failing + updated
      Collections.shuffle(testVals)
      this.allTestVals = testVals
      this.updated = updated
      this.failing = failing
      this.notUpdated = notUpdated
      this.updatedIds = updatedIds
      this.failingIds = failingIds
    }

    fun assertCorrectUpdateResult() {
      val expectedlyNotUpdated = Select
          .from(model.table)
          .where(model.idColumn.`in`(failingIds))
          .queryDeep()
          .execute()

      assertThat(failing)
          .containsNoneIn(expectedlyNotUpdated)
      assertThat(notUpdated)
          .containsExactlyElementsIn(expectedlyNotUpdated)

      val updatedDbVals = Select
          .from(model.table)
          .where(model.idColumn.`in`(updatedIds))
          .queryDeep()
          .execute()
      if (!nullSomeColumns) {
        assertThat(updated)
            .containsExactlyElementsIn(updatedDbVals)
      } else {
        assertThat(updatedDbVals.size).isEqualTo(updated.size)
        with(model as NullableColumns<T>) {
          updated.sortedBy { model.getId(it) }
              .zip(updatedDbVals)
              .forEach { (testVal, dbVal) ->
                assertAllExceptNulledColumnsAreUpdated(dbVal, testVal)
              }
        }
      }
    }
  }

  class SimpleModelBulkOperationWithIgnoreConflict<T>(
      forModel: TestModelWithUniqueColumn<T>,
      setUp: (TestModel<T>) -> BulkUpdateWithIgnoreConflictTestVals<T> = {
        val model = it as TestModelWithUniqueColumn
        BulkUpdateWithIgnoreConflictTestVals(model)
      },
      operation: DualOperation<BulkUpdateWithIgnoreConflictTestVals<T>, T, Boolean> = BulkUpdateWithConflictIgnoreDualOperation()
  ) : DualOperationTestCase<BulkUpdateWithIgnoreConflictTestVals<T>, T, Boolean>(
      "Bulk update with ignore conflict algorithm does not throw on conflict and updates as much as possible",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = { _, testVals, success ->
        assertThat(success).isTrue()
        testVals.assertCorrectUpdateResult()
      })

  @Test
  fun simpleModelBulkUpdateWithIgnoreConflict() {
    assertThatDual {
      testCase { SimpleModelBulkOperationWithIgnoreConflict(it as TestModelWithUniqueColumn) }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  class ComplexModelBulkOperationWithIgnoreConflictWhereParentFails<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      setUp: (TestModel<T>) -> BulkUpdateWithIgnoreConflictTestVals<T> = {
        BulkUpdateWithIgnoreConflictTestVals(model = it as ComplexTestModelWithUniqueColumn)
      },
      operation: DualOperation<BulkUpdateWithIgnoreConflictTestVals<T>, T, Boolean> = BulkUpdateWithConflictIgnoreDualOperation()
  ) : DualOperationTestCase<BulkUpdateWithIgnoreConflictTestVals<T>, T, Boolean>(
      "Bulk update with ignore conflict algorithm where parent fails does not throw " +
          "on conflict and updates as much as possible -- not updated value children must" +
          "not be updated as well",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = { _, testVals, success ->
        assertThat(success).isTrue()
        testVals.assertCorrectUpdateResult()
      })

  @Test
  fun complexBulkUpdateWithIgnoreConflictWhereParentFails() {
    assertThatDual {
      testCase { ComplexModelBulkOperationWithIgnoreConflictWhereParentFails(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class ComplexBulkOperationWithIgnoreConflictWhereChildFails<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      setUp: (TestModel<T>) -> BulkUpdateWithIgnoreConflictTestVals<T> = {
        BulkUpdateWithIgnoreConflictTestVals(it as ComplexTestModelWithUniqueColumn) { model, src, target ->
          (model as ComplexTestModelWithUniqueColumn).transferComplexColumnUniqueVal(src, target)
        }
      },
      operation: DualOperation<BulkUpdateWithIgnoreConflictTestVals<T>, T, Boolean> = BulkUpdateWithConflictIgnoreDualOperation()
  ) : DualOperationTestCase<BulkUpdateWithIgnoreConflictTestVals<T>, T, Boolean>(
      "Bulk update with ignore conflict algorithm where child fails does not throw " +
          "on conflict and updates as much as possible -- failed parents must be rolled back " +
          "along with failed children",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = { _, testVals, success ->
        assertThat(success).isTrue()
        testVals.assertCorrectUpdateResult()
      })

  @Test
  fun complexBulkUpdateWithIgnoreConflictWhereChildFails() {
    assertThatDual {
      testCase { ComplexBulkOperationWithIgnoreConflictWhereChildFails(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class BulkUpdateWithIgnoreConflictWhereAllFailTestVals<out T>(
      private val model: TestModelWithUniqueColumn<T>,
      private val nullSomeColumns: Boolean = false,
      private val transferUniqueVal: (TestModelWithUniqueColumn<T>, T, T) -> T = TestModelWithUniqueColumn<T>::transferUniqueVal
  ) {
    val testVals: List<T>
    val ids: List<Long>
    private val dbValsBeforeTest: List<T>

    init {
      val (newRandom, _) = insertNewRandom(model)
      val ids = ArrayList<Long>(10)
      val testVals = createVals {
        val (v, id) = insertNewRandom(model)
        ids.add(id)
        transferUniqueVal(model, newRandom, v)
            .let { if (nullSomeColumns) (model as NullableColumns<T>).nullSomeColumns(it) else it }
      }
      val dbValsBeforeTest = Select
          .from(model.table)
          .where(model.idColumn.`in`(ids))
          .queryDeep()
          .execute()

      this.testVals = testVals
      this.ids = ids
      this.dbValsBeforeTest = dbValsBeforeTest
    }

    fun assertCorrectUpdateResult() {
      val dbValsAfterTest = Select
          .from(model.table)
          .where(model.idColumn.`in`(ids))
          .queryDeep()
          .execute()

      assertThat(testVals)
          .containsNoneIn(dbValsAfterTest)
      assertThat(dbValsBeforeTest)
          .containsExactlyElementsIn(dbValsAfterTest)
    }
  }

  class ComplexBulkOperationWithIgnoreConflictWhereAllChildrenFail<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      setUp: (TestModel<T>) -> BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T> = {
        BulkUpdateWithIgnoreConflictWhereAllFailTestVals(it as ComplexTestModelWithUniqueColumn) { model, src, target ->
          (model as ComplexTestModelWithUniqueColumn).transferComplexColumnUniqueVal(src, target)
        }
      },
      operation: DualOperation<BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>, T, Boolean> = BulkUpdateWithIgnoreConflictWhereAllFailDualOperation()
  ) : DualOperationTestCase<BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>, T, Boolean>(
      "Bulk update with ignore conflict where all children fail must not throw, complete and " +
          "leave table untouched",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = { _, testVals, result ->
        assertThat(result).isTrue()
        testVals.assertCorrectUpdateResult()
      })

  @Test
  fun complexModelBulkUpdateWithIgnoreConflictWhereAllChildrenFail() {
    assertThatDual {
      testCase { ComplexBulkOperationWithIgnoreConflictWhereAllChildrenFail(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class BulkOperationWithIgnoreConflictWhereAllFail<T>(
      forModel: TestModelWithUniqueColumn<T>,
      setUp: (TestModel<T>) -> BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T> = {
        val model = it as TestModelWithUniqueColumn<T>
        BulkUpdateWithIgnoreConflictWhereAllFailTestVals(model)
      },
      operation: DualOperation<BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>, T, Boolean> = BulkUpdateWithIgnoreConflictWhereAllFailDualOperation()
  ) : DualOperationTestCase<BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>, T, Boolean>(
      "Bulk update with ignore conflict where all fail must not throw, complete and " +
          "leave table untouched",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = { _, testVals, result ->
        assertThat(result).isTrue()
        testVals.assertCorrectUpdateResult()
      })

  @Test
  fun bulkUpdateWithIgnoreConflictWhereAllFail() {
    assertThatDual {
      testCase { BulkOperationWithIgnoreConflictWhereAllFail(it as TestModelWithUniqueColumn) }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  class EarlyUnsubscribeWithIgnoreConflict<T>(
      description: String = "Unsubscribing from in-flight operation before it completes rollbacks all values",
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> List<T> = { model ->
        createVals(500) {
          val (v, id) = insertNewRandom(model)
          model.updateAllVals(v, id)
        }
      },
      test: (TestModel<T>, List<T>) -> AtomicInteger = { model, testValues ->
        val eventsCount = AtomicInteger(0)
        val disposable = model.bulkUpdateBuilder(testValues)
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
      "Unsubscribing in-flight bulk update with ignore conflict algorithm " +
          "on simple models rollbacks all values",
      forModel = simpleMutableAutoIdTestModel,
      assertResults = assertEarlyUnsubscribeFromUpdateRollbackedAllValues())
      .test()

  @Test
  fun earlyUnsubscribeFromComplexWithIgnoreConflict() = EarlyUnsubscribeWithIgnoreConflict(
      "Unsubscribing in-flight bulk update with ignore conflict algorithm " +
          "on complex models stops any further work",
      forModel = complexMutableAutoIdTestModel,
      assertResults = assertEarlyUnsubscribeFromUpdateStoppedAnyFurtherWork())
      .test()

  @Test
  fun streamedBulkUpdateWithIgnoreConflict() {
    assertThatSingle {
      testCase {
        StreamedBulkOperation(
            forModel = it,
            test = StreamedBulkUpdateOperation(ignoreConflict = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun bulkUpdateByUniqueColumnWithIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByUniqueColumn(
            forModel = it,
            operation = BulkUpdateDualOperation { model, testVal ->
              model.bulkUpdateBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkUpdateByComplexUniqueColumnWithIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByComplexUniqueColumn(
            forModel = it,
            operation = BulkUpdateDualOperation { model, testVal ->
              model.bulkUpdateBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkUpdateByComplexColumnUniqueColumnWithIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByComplexColumnUniqueColumn(
            forModel = it,
            operation = BulkUpdateDualOperation { model, testVal ->
              model.bulkUpdateBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkUpdateByUniqueColumnWithNullIdAndIgnoreConflict() {
    assertThatDual {
      testCase {
        BulkItemsUpdateTest.BulkOperationByUniqueColumnWithNullId(
            forModel = it,
            operation = BulkUpdateDualOperation { model, testVal ->
              model.bulkUpdateBuilder(testVal)
                  .conflictAlgorithm(CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_NULLABLE_UNIQUE_AUTO_ID_MODELS)
    }
  }

  class BulkUpdateWithConflictIgnoreDualOperation<T> : DualOperation<BulkUpdateWithIgnoreConflictTestVals<T>, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictTestVals<T>): Boolean = model
        .bulkUpdateBuilder(testVal.allTestVals)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: BulkUpdateWithIgnoreConflictTestVals<T>): Boolean = model
        .bulkUpdateBuilder(testVal.allTestVals)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }

  class BulkUpdateWithIgnoreConflictWhereAllFailDualOperation<T> : DualOperation<BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>, T, Boolean> {
    override fun executeTest(model: TestModel<T>, testVal: BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>): Boolean =
        // we expect false result indicating all operations failure
        !model.bulkUpdateBuilder(testVal.testVals)
            .conflictAlgorithm(CONFLICT_IGNORE)
            .execute()

    override fun observeTest(model: TestModel<T>, testVal: BulkUpdateWithIgnoreConflictWhereAllFailTestVals<T>): Boolean = model
        .bulkUpdateBuilder(testVal.testVals)
        .conflictAlgorithm(CONFLICT_IGNORE)
        .observe()
        .blockingGet(1, TimeUnit.SECONDS) == null
  }
}
