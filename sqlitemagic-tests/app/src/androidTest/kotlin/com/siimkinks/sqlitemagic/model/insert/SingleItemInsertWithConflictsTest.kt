package com.siimkinks.sqlitemagic.model.insert

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.TestUtil.assertTableCount
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SingleItemInsertWithConflictsTest : DefaultConnectionTest {
  class SuccessfulOperationWithConflictAlgorithm<T>(
      forModel: TestModel<T>,
      setUp: (TestModel<T>) -> T = TestModel<T>::newRandom,
      operation: DualOperation<T, T, Long> = InsertWithConflictAlgortihmDualOperation(CONFLICT_FAIL)
  ) : DualOperationTestCase<T, T, Long>(
      "Insert with arbitrary conflict algorithm succeeds",
      model = forModel,
      setUp = setUp,
      operation = operation,
      assertResults = assertInsertSuccess())

  @Test
  fun successfulInsertWithConflictAlgorithm() {
    assertThatDual {
      testCase { SuccessfulOperationWithConflictAlgorithm(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class SimpleModelFailWithIgnoreConflict<T>(
      forModel: TestModelWithUniqueColumn<T>,
      operation: DualOperation<T, T, Long> = InsertWithConflictAlgortihmDualOperation()
  ) : DualOperationTestCase<T, T, Long>(
      "Failed simple model insert with CONFLICT_IGNORE does not throw, but emits error value (-1)",
      model = forModel,
      setUp = {
        val model = it as TestModelWithUniqueColumn
        val (v) = insertNewRandom(model)
        model.transferUniqueVal(v, model.newRandom())
      },
      operation = operation,
      assertResults = { model, _, id ->
        assertTableCount(1L, model.table)
        assertThat(id).isEqualTo(-1)
      })

  @Test
  fun simpleModelInsertFailWithIgnoreConflict() {
    assertThatDual {
      testCase { SimpleModelFailWithIgnoreConflict(it as TestModelWithUniqueColumn) }
      isSuccessfulFor(*SIMPLE_FIXED_ID_MODELS)
    }
  }

  class ComplexModelFailWithIgnoreConflictWhereParentFails<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      operation: DualOperation<T, T, Long> = InsertWithConflictAlgortihmDualOperation()
  ) : DualOperationTestCase<T, T, Long>(
      "Failed complex model insert with CONFLICT_IGNORE where conflict happens in parent " +
          "does not throw, emits error value (-1) and has no affect on child tables",
      model = forModel,
      setUp = {
        val model = it as ComplexTestModelWithUniqueColumn
        val (v1) = insertNewRandom(model)
        val newRandom = model.newRandom()
        model.transferUniqueVal(v1, newRandom)
      },
      operation = operation,
      assertResults = { model, testVal, id ->
        assertThat(id).isEqualTo(-1)
        assertTableCount(1L, model.table)
        val complexModel = model as ComplexTestModelWithUniqueColumn
        complexModel.assertNoChildrenInDb(testVal)
      })

  @Test
  fun complexModelInsertFailWithIgnoreConflictWhereParentFails() {
    assertThatDual {
      testCase { ComplexModelFailWithIgnoreConflictWhereParentFails(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class ComplexModelFailWithIgnoreConflictWhereChildFails<T>(
      forModel: ComplexTestModelWithUniqueColumn<T>,
      operation: DualOperation<T, T, Long> = InsertWithConflictAlgortihmDualOperation()
  ) : DualOperationTestCase<T, T, Long>(
      "Failed complex model insert with CONFLICT_IGNORE where conflict happens in child " +
          "does not throw, emits error value (-1), rollbacks all changes to children and keeps" +
          "parent table untouched",
      model = forModel,
      setUp = {
        val model = it as ComplexTestModelWithUniqueColumn
        val (v1) = insertNewRandom(model)
        val newRandom = model.newRandom()
        model.transferComplexUniqueVal(v1, newRandom)
      },
      operation = operation,
      assertResults = { model, testVal, id ->
        assertThat(id).isEqualTo(-1)
        assertTableCount(1L, model.table)
        assertThat(Select
            .from(model.table)
            .where(model.idColumn.`is`(model.getId(testVal)!!))
            .count()
            .execute())
            .isEqualTo(0)
        val complexModel = model as ComplexTestModelWithUniqueColumn
        complexModel.assertNoChildrenInDb(testVal)
      })

  @Test
  fun complexModelInsertFailWithIgnoreConflictWhereChildFails() {
    assertThatDual {
      testCase { ComplexModelFailWithIgnoreConflictWhereChildFails(it as ComplexTestModelWithUniqueColumn) }
      isSuccessfulFor(*COMPLEX_FIXED_ID_MODELS)
    }
  }

  class InsertWithConflictAlgortihmDualOperation<T>(
      private val algorithm: Int = SQLiteDatabase.CONFLICT_IGNORE
  ) : DualOperation<T, T, Long> {
    override fun executeTest(model: TestModel<T>, testVal: T): Long = model
        .insertBuilder(testVal)
        .conflictAlgorithm(algorithm)
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: T): Long = model
        .insertBuilder(testVal)
        .conflictAlgorithm(algorithm)
        .observe()
        .blockingGet()
  }
}