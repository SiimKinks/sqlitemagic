package com.siimkinks.sqlitemagic.operation

import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.Delete
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeleteTest : DefaultConnectionTest {
  class SuccessfulSingleObjectOperation<T>(
      forModel: TestModel<T>,
      operation: DualOperation<T, T, Int> = DeleteSingleOperation()
  ) : DualOperationTestCase<T, T, Int>(
      "Delete single object",
      model = forModel,
      setUp = { insertNewRandom(it).first },
      operation = operation,
      assertResults = { model, _, deleteCount ->
        assertThat(deleteCount).isEqualTo(1)
        assertThat(Select
            .from(model.table)
            .count()
            .execute())
            .isEqualTo(0)
      })

  @Test
  fun deleteSingle() {
    assertThatDual {
      testCase { SuccessfulSingleObjectOperation(it) }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  class SuccessfulBulkObjectOperation<T>(
      forModel: TestModel<T>,
      operation: DualOperation<Pair<List<T>, Int>, T, Int> = DeleteBulkOperation()
  ) : DualOperationTestCase<Pair<List<T>, Int>, T, Int>(
      "Delete bulk objects",
      model = forModel,
      setUp = {
        val all = createVals(20) { insertNewRandom(it).first }
        all.subList(5, 15) to all.size
      },
      operation = operation,
      assertResults = { model, testVal, deleteCount ->
        val (deletedVals, allCount) = testVal
        assertThat(deleteCount).isEqualTo(deletedVals.size)
        assertThat(Select
            .from(model.table)
            .count()
            .execute())
            .isEqualTo(allCount - deletedVals.size)
      })

  @Test
  fun deleteBulk() {
    assertThatDual {
      testCase { SuccessfulBulkObjectOperation(it) }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  class SuccessfulWholeTableOperation<T>(
      forModel: TestModel<T>,
      operation: DualOperation<Int, T, Int> = DeleteTableOperation()
  ) : DualOperationTestCase<Int, T, Int>(
      "Delete table",
      model = forModel,
      setUp = { createVals { insertNewRandom(it) }.size },
      operation = operation,
      assertResults = { model, insertCount, deleteCount ->
        assertThat(deleteCount).isEqualTo(insertCount)
        assertThat(Select
            .from(model.table)
            .count()
            .execute())
            .isEqualTo(0)
      })

  @Test
  fun deleteTable() {
    assertThatDual {
      testCase { SuccessfulWholeTableOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class SuccessfulDeleteAllWithBuilderOperation<T>(
      forModel: TestModel<T>,
      operation: DualOperation<Int, T, Int> = DeleteAllWithBuilderOperation()
  ) : DualOperationTestCase<Int, T, Int>(
      "Delete all with builder",
      model = forModel,
      setUp = { createVals { insertNewRandom(it) }.size },
      operation = operation,
      assertResults = { model, insertCount, deleteCount ->
        assertThat(deleteCount).isEqualTo(insertCount)
        assertThat(Select
            .from(model.table)
            .count()
            .execute())
            .isEqualTo(0)
      })

  @Test
  fun deleteAllWithBuilder() {
    assertThatDual {
      testCase { SuccessfulDeleteAllWithBuilderOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class SuccessfulDeleteAllWithBuilderAndWhereClauseOperation<T>(
      forModel: TestModel<T>,
      operation: DualOperation<Pair<Long, Int>, T, Int> = DeleteAllWithBuilderAndWhereClauseOperation()
  ) : DualOperationTestCase<Pair<Long, Int>, T, Int>(
      "Delete all with builder and where clause",
      model = forModel,
      setUp = {
        val insertedVals = createVals { insertNewRandom(it) }
        val (_, id) = insertedVals.last()
        id to insertedVals.size
      },
      operation = operation,
      assertResults = { model, (_, insertedValCount), deleteCount ->
        assertThat(deleteCount).isEqualTo(1)
        assertThat(Select
            .from(model.table)
            .count()
            .execute())
            .isEqualTo(insertedValCount - 1)
      })

  @Test
  fun deleteAllWithBuilderAndWhereClause() {
    assertThatDual {
      testCase { SuccessfulDeleteAllWithBuilderAndWhereClauseOperation(it) }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  class DeleteSingleOperation<T> : DualOperation<T, T, Int> {
    override fun executeTest(model: TestModel<T>, testVal: T): Int = model
        .deleteBuilder(testVal)
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: T): Int = model
        .deleteBuilder(testVal)
        .observe()
        .blockingGet()
  }

  class DeleteBulkOperation<T> : DualOperation<Pair<List<T>, Int>, T, Int> {
    override fun executeTest(model: TestModel<T>, testVal: Pair<List<T>, Int>): Int = model
        .bulkDeleteBuilder(testVal.first)
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: Pair<List<T>, Int>): Int = model
        .bulkDeleteBuilder(testVal.first)
        .observe()
        .blockingGet()
  }

  class DeleteTableOperation<T> : DualOperation<Int, T, Int> {
    override fun executeTest(model: TestModel<T>, testVal: Int): Int = model
        .deleteTableBuilder()
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: Int): Int = model
        .deleteTableBuilder()
        .observe()
        .blockingGet()
  }

  class DeleteAllWithBuilderOperation<T> : DualOperation<Int, T, Int> {
    override fun executeTest(model: TestModel<T>, testVal: Int): Int = Delete
        .from(model.table)
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: Int): Int = Delete
        .from(model.table)
        .observe()
        .blockingGet()
  }

  class DeleteAllWithBuilderAndWhereClauseOperation<T> : DualOperation<Pair<Long, Int>, T, Int> {
    override fun executeTest(model: TestModel<T>, testVal: Pair<Long, Int>): Int = Delete
        .from(model.table)
        .where(model.idColumn.`is`(testVal.first))
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: Pair<Long, Int>): Int = Delete
        .from(model.table)
        .where(model.idColumn.`is`(testVal.first))
        .observe()
        .blockingGet()
  }
}