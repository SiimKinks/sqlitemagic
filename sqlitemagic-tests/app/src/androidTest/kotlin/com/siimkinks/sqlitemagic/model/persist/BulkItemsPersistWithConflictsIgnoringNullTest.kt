@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.model.persist

import android.database.sqlite.SQLiteDatabase
import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.insert.BulkItemsInsertTest
import com.siimkinks.sqlitemagic.model.insert.BulkItemsInsertWithConflictsTest
import com.siimkinks.sqlitemagic.model.insert.assertEarlyUnsubscribeFromInsertRollbackedAllValues
import com.siimkinks.sqlitemagic.model.insert.assertEarlyUnsubscribeFromInsertStoppedAnyFurtherWork
import com.siimkinks.sqlitemagic.model.persist.BulkItemsPersistWithConflictsTest.*
import com.siimkinks.sqlitemagic.model.update.BulkItemsUpdateTest
import com.siimkinks.sqlitemagic.model.update.BulkItemsUpdateWithConflictsTest
import com.siimkinks.sqlitemagic.model.update.assertEarlyUnsubscribeFromUpdateRollbackedAllValues
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

@RunWith(AndroidJUnit4::class)
class BulkItemsPersistWithConflictsIgnoringNullTest : DefaultConnectionTest {
  @Test
  fun mutableModelBulkPersistWithInsertAndIgnoreConflictSetsIdsIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsInsertTest.MutableModelBulkOperationSetsIds(
            forModel = it,
            setUp = {
              val model = it as TestModelWithNullableColumns
              createVals {
                var newRandom = it.newRandom()
                newRandom = it.setId(newRandom, -1)
                model.nullSomeColumns(newRandom)
              }
            },
            operation = BulkPersistForInsertWithIgnoreConflictDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  @Test
  fun simpleModelBulkPersistWithInsertAndIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.SimpleModelBulkOperationWithIgnoreConflict(
            forModel = it as TestModelWithUniqueColumn,
            setUp = {
              val nullableModel = it as NullableColumns<Any>
              val testVals = createVals {
                val (random) = insertNewRandom(it)
                nullableModel.nullSomeColumns(random)
              }
              for (i in 0..10) {
                testVals.add(nullableModel.nullSomeColumns(it.newRandom()))
              }
              Collections.shuffle(testVals)
              testVals
            },
            operation = BulkPersistForInsertWithIgnoreConflictDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*SIMPLE_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun simpleModelBulkPersistWithUpdateAndIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.SimpleModelBulkOperationWithIgnoreConflict(
            forModel = it as TestModelWithUniqueColumn,
            setUp = {
              val model = it as TestModelWithUniqueColumn
              BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictTestVals(
                  model = model,
                  nullSomeColumns = true)
            },
            operation = BulkPersistForUpdateWithIgnoreConflictDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*SIMPLE_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexBulkPersistWithInsertAndIgnoreConflictIgnoringNullWhereParentFails() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.ComplexModelBulkOperationWithIgnoreConflictWhereParentFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            setUp = {
              val nullableModel = it as NullableColumns<Any>
              val model = it as ComplexTestModelWithUniqueColumn
              val testVals = createVals {
                val (v1, _) = insertNewRandom(model)
                val newRandom = nullableModel.nullSomeColumns(model.newRandom())
                model.transferUniqueVal(v1, newRandom)
              }
              for (i in 0..10) {
                testVals.add(nullableModel.nullSomeColumns(model.newRandom()))
              }
              Collections.shuffle(testVals)
              testVals
            },
            operation = BulkPersistForInsertWithIgnoreConflictDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexBulkPersistWithUpdateAndIgnoreConflictIgnoringNullWhereParentFails() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.ComplexModelBulkOperationWithIgnoreConflictWhereParentFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            setUp = {
              BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictTestVals(
                  model = it as ComplexTestModelWithUniqueColumn,
                  nullSomeColumns = true)
            },
            operation = BulkPersistForUpdateWithIgnoreConflictDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexBulkPersistWithInsertAndIgnoreConflictIgnoringNullWhereChildFails() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.ComplexModelBulkOperationWithIgnoreConflictWhereChildFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            setUp = {
              val nullableModel = it as NullableColumns<Any>
              val model = it as ComplexTestModelWithUniqueColumn
              val testVals = createVals {
                val (newRandom, _) = insertNewRandom(model)
                val newVal = nullableModel.nullSomeColumns(model.newRandom())
                model.transferComplexColumnUniqueVal(newRandom, newVal)
              }
              for (i in 0..10) {
                testVals.add(nullableModel.nullSomeColumns(it.newRandom()))
              }
              Collections.shuffle(testVals)
              testVals
            },
            operation = BulkPersistForInsertWithIgnoreConflictDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexBulkPersistWithUpdateAndIgnoreConflictIgnoringNullWhereChildFails() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.ComplexBulkOperationWithIgnoreConflictWhereChildFails(
            forModel = it as ComplexTestModelWithUniqueColumn,
            setUp = {
              BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictTestVals(
                  model = it as ComplexTestModelWithUniqueColumn,
                  nullSomeColumns = true,
                  transferUniqueVal = { model, src, target ->
                    (model as ComplexTestModelWithUniqueColumn).transferComplexColumnUniqueVal(src, target)
                  })
            },
            operation = BulkPersistForUpdateWithIgnoreConflictDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexModelBulkPersistWithInsertAndIgnoreConflictIgnoringNullWhereAllChildrenFail() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.ComplexBulkOperationWithIgnoreConflictWhereAllChildrenFail(
            forModel = it as ComplexTestModelWithUniqueColumn,
            setUp = {
              val model = it as ComplexTestModelWithUniqueColumn
              val nullableModel = it as NullableColumns<Any>
              createVals {
                val (newRandom) = insertNewRandom(it)
                val newVal = nullableModel.nullSomeColumns(it.newRandom())
                model.transferComplexColumnUniqueVal(newRandom, newVal)
              }
            },
            operation = BulkPersistForInsertWithIgnoreConflictWhereAllFailDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun complexModelBulkPersistWithUpdateAndIgnoreConflictIgnoringNullWhereAllChildrenFail() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.ComplexBulkOperationWithIgnoreConflictWhereAllChildrenFail(
            forModel = it as ComplexTestModelWithUniqueColumn,
            setUp = {
              BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictWhereAllFailTestVals(
                  model = it as ComplexTestModelWithUniqueColumn,
                  nullSomeColumns = true,
                  transferUniqueVal = { model, src, target ->
                    (model as ComplexTestModelWithUniqueColumn).transferComplexColumnUniqueVal(src, target)
                  })
            },
            operation = BulkPersistForUpdateWithIgnoreConflictWhereAllFailDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithInsertAndIgnoreConflictIgnoringNullWhereAllFail() {
    assertThatDual {
      testCase {
        BulkItemsInsertWithConflictsTest.BulkOperationWithIgnoreConflictWhereAllFail(
            forModel = it,
            setUp = {
              val model = it as TestModelWithUniqueColumn
              val nullableModel = it as NullableColumns<Any>
              createVals {
                val (v, _) = insertNewRandom(model)
                val newRandom = nullableModel.nullSomeColumns(it.newRandom())
                model.transferUniqueVal(v, newRandom)
              }
            },
            operation = BulkPersistForInsertWithIgnoreConflictWhereAllFailDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateAndIgnoreConflictIgnoringNullWhereAllFail() {
    assertThatDual {
      testCase {
        BulkItemsUpdateWithConflictsTest.BulkOperationWithIgnoreConflictWhereAllFail(
            forModel = it as TestModelWithUniqueColumn,
            setUp = {
              BulkItemsUpdateWithConflictsTest.BulkUpdateWithIgnoreConflictWhereAllFailTestVals(
                  model = it as TestModelWithUniqueColumn,
                  nullSomeColumns = true)
            },
            operation = BulkPersistForUpdateWithIgnoreConflictWhereAllFailDualOperation(
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_NULLABLE_FIXED_ID_MODELS)
    }
  }

  @Test
  fun earlyUnsubscribeFromSimpleModelPersistWithInsertAndIgnoreConflictIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsInsertWithConflictsTest.EarlyUnsubscribeWithIgnoreConflict(
            "Unsubscribing in-flight bulk persist with insert and ignore conflict algorithm " +
                "with ignoring null values on simple models rollbacks all values",
            forModel = it,
            setUp = { createVals(count = 500) { newRandomWithNulledColumns()(it) } },
            test = BulkPersistEarlyUnsubscribeWithIgnoreConflict(ignoreNullValues = true),
            assertResults = assertEarlyUnsubscribeFromInsertRollbackedAllValues())
      }
      isSuccessfulFor(*SIMPLE_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun earlyUnsubscribeFromSimpleModelPersistWithUpdateAndIgnoreConflictIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateWithConflictsTest.EarlyUnsubscribeWithIgnoreConflict(
            "Unsubscribing in-flight bulk persist with update and ignore conflict algorithm " +
                "with ignoring null values on simple models rollbacks all values",
            forModel = it,
            setUp = { createVals(count = 500) { newUpdatableRandomWithNulledColumns()(it) } },
            test = BulkPersistEarlyUnsubscribeWithIgnoreConflict(ignoreNullValues = true),
            assertResults = assertEarlyUnsubscribeFromUpdateRollbackedAllValues())
      }
      isSuccessfulFor(*SIMPLE_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun earlyUnsubscribeFromComplexModelPersistWithInsertAndIgnoreConflictIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsInsertWithConflictsTest.EarlyUnsubscribeWithIgnoreConflict(
            "Unsubscribing in-flight bulk persist with insert and ignore conflict algorithm " +
                "with ignoring null values on complex models stops any further work",
            forModel = it,
            setUp = { createVals(count = 500) { newRandomWithNulledColumns()(it) } },
            test = BulkPersistEarlyUnsubscribeWithIgnoreConflict(ignoreNullValues = true),
            assertResults = assertEarlyUnsubscribeFromInsertStoppedAnyFurtherWork())
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun earlyUnsubscribeFromComplexModelPersistWithUpdateAndIgnoreConflictIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateWithConflictsTest.EarlyUnsubscribeWithIgnoreConflict(
            "Unsubscribing in-flight bulk persist with update and ignore conflict algorithm " +
                "with ignoring null values on complex models stops any further work",
            forModel = it,
            setUp = { createVals(count = 500) { newUpdatableRandomWithNulledColumns()(it) } },
            test = BulkPersistEarlyUnsubscribeWithIgnoreConflict(ignoreNullValues = true),
            assertResults = { model, testVals, eventsCount ->
              val firstDbValue = Select
                  .from(model.table)
                  .queryDeep()
                  .takeFirst()
                  .execute()!!
              val firstTestVal = testVals
                  .sortedBy { model.getId(it) }
                  .first()
              (model as ComplexNullableColumns<Any>)
                  .assertAllExceptNulledColumnsAreUpdated(firstDbValue, firstTestVal)
              assertThat(eventsCount.get()).isEqualTo(0)
            })
      }
      isSuccessfulFor(*COMPLEX_NULLABLE_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedBulkPersistWithInsertAndIgnoreConflictIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsInsertTest.StreamedBulkOperation(
            forModel = it,
            test = BulkItemsPersistTest.StreamedBulkPersistWithInsertOperation(
                ignoreConflict = true,
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun streamedBulkPersistWithUpdateAndIgnoreConflictIgnoringNull() {
    assertThatSingle {
      testCase {
        BulkItemsUpdateTest.StreamedBulkOperation(
            forModel = it,
            test = BulkItemsPersistTest.StreamedBulkPersistWithUpdateOperation(
                ignoreConflict = true,
                ignoreNullValues = true))
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByUniqueColumnAndIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsPersistIgnoringNullTest.BulkOperationByUniqueColumnIgnoringNull(
            forModel = it,
            operation = BulkPersistDualOperation(ignoreNullValues = true) { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
                  .byColumn((model as TestModelWithUniqueColumn).uniqueColumn)
            })
      }
      isSuccessfulFor(*ALL_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByComplexUniqueColumnAndIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsPersistIgnoringNullTest.BulkOperationByComplexUniqueColumnIgnoringNull(
            forModel = it,
            operation = BulkPersistDualOperation(ignoreNullValues = true) { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  @Test
  fun bulkPersistWithUpdateByComplexColumnUniqueColumnAndIgnoreConflictIgnoringNull() {
    assertThatDual {
      testCase {
        BulkItemsPersistIgnoringNullTest.BulkOperationByComplexColumnUniqueColumnIgnoringNull(
            forModel = it,
            operation = BulkPersistDualOperation(ignoreNullValues = true) { model, testVals ->
              model.bulkPersistBuilder(testVals)
                  .conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
                  .byColumn((model as ComplexTestModelWithUniqueColumn).complexColumnUniqueColumn)
            })
      }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }
}