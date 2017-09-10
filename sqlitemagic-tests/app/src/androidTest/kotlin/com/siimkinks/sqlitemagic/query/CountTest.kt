package com.siimkinks.sqlitemagic.query

import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Select.count
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.model.TestUtil.createVals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountTest : DefaultConnectionTest {
  class SuccessfulOperation<T>(
      forModel: TestModel<T>,
      operation: DualOperation<Int, T, Long> = CountDualOperation()
  ) : DualOperationTestCase<Int, T, Long>(
      "Counting rows works",
      model = forModel,
      setUp = { model ->
        createVals { insertNewRandom(model) }.size
      },
      operation = operation,
      assertResults = { _, insertValsCount, operationResult ->
        assertThat(insertValsCount).isEqualTo(operationResult)
      })

  @Test
  fun successfulCount() {
    assertThatDual {
      testCase { SuccessfulOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulCountOfColumnQuery() {
    assertThatDual {
      testCase {
        SuccessfulOperation(
            forModel = it,
            operation = ColumnQueryCountDualOperation())
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulCountAsColumn() {
    assertThatDual {
      testCase {
        SuccessfulOperation(
            forModel = it,
            operation = CountAsColumnDualOperation())
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class EmptyTableOperation<T>(
      forModel: TestModel<T>,
      operation: DualOperation<Int, T, Long> = CountDualOperation()
  ) : DualOperationTestCase<Int, T, Long>(
      "Counting empty table works",
      model = forModel,
      setUp = { 0 },
      operation = operation,
      assertResults = { _, _, operationResult ->
        assertThat(operationResult).isEqualTo(0)
      })

  @Test
  fun successfulEmptyCount() {
    assertThatDual {
      testCase { EmptyTableOperation(it) }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulEmptyCountOfColumnQuery() {
    assertThatDual {
      testCase {
        EmptyTableOperation(
            forModel = it,
            operation = ColumnQueryCountDualOperation())
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  @Test
  fun successfulEmptyCountAsColumn() {
    assertThatDual {
      testCase {
        EmptyTableOperation(
            forModel = it,
            operation = CountAsColumnDualOperation())
      }
      isSuccessfulFor(*ALL_AUTO_ID_MODELS)
    }
  }

  class SuccessfulOperationWithWhereClause<T>(
      forModel: TestModel<T>,
      operation: DualOperation<Long, T, Long> = CountDualOperationWithWhereClause()
  ) : DualOperationTestCase<Long, T, Long>(
      "Counting rows with where clause works",
      model = forModel,
      setUp = { model ->
        val values = createVals { insertNewRandom(model) }
        val (lastVal, _) = values.last()
        model.getId(lastVal)!!
      },
      operation = operation,
      assertResults = { _, _, operationResult ->
        assertThat(operationResult).isEqualTo(1)
      })

  @Test
  fun successfulCountWithWhereClause() {
    assertThatDual {
      testCase { SuccessfulOperationWithWhereClause(it) }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  @Test
  fun successfulCountAsColumnWithWhereClause() {
    assertThatDual {
      testCase {
        SuccessfulOperationWithWhereClause(
            forModel = it,
            operation = CountAsColumnDualOperationWithWhereClause())
      }
      isSuccessfulFor(
          simpleMutableAutoIdTestModel,
          complexMutableAutoIdTestModel)
    }
  }

  @Test
  fun queryComplexChildColumnCount() =
      QueryModelTestCase(
          "Querying complex child column count",
          testModel = complexMutableAutoIdTestModel,
          setUp = { model ->
            createVals { insertNewRandom(model) }.size.toLong()
          },
          operation = object: QueryOperation<Long, Long> {
            override fun executeTest(testVal: Long): Long = Select
                .column(AUTHOR.NAME)
                .from(MAGAZINE)
                .count()
                .execute()

            override fun observeTest(testVal: Long): Long = Select
                .column(AUTHOR.NAME)
                .from(MAGAZINE)
                .count()
                .observe()
                .runQueryOnce()
                .blockingGet()
          },
          assertResults = resultIsEqualToExpected())
          .test()

  class CountDualOperation<T> : DualOperation<Int, T, Long> {
    override fun executeTest(model: TestModel<T>, testVal: Int): Long = Select
        .from(model.table)
        .count()
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: Int): Long = Select
        .from(model.table)
        .count()
        .observe()
        .runQueryOnce()
        .blockingGet()
  }

  class ColumnQueryCountDualOperation<T> : DualOperation<Int, T, Long> {
    override fun executeTest(model: TestModel<T>, testVal: Int): Long = Select
        .column(model.idColumn)
        .from(model.table)
        .count()
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: Int): Long = Select
        .column(model.idColumn)
        .from(model.table)
        .count()
        .observe()
        .runQueryOnce()
        .blockingGet()
  }

  class CountAsColumnDualOperation<T> : DualOperation<Int, T, Long> {
    override fun executeTest(model: TestModel<T>, testVal: Int): Long = Select
        .column(count())
        .from(model.table)
        .takeFirst()
        .execute()!!

    override fun observeTest(model: TestModel<T>, testVal: Int): Long = Select
        .column(count())
        .from(model.table)
        .takeFirst()
        .observe()
        .runQueryOnce()
        .blockingGet()
  }

  class CountDualOperationWithWhereClause<T> : DualOperation<Long, T, Long> {
    override fun executeTest(model: TestModel<T>, testVal: Long): Long = Select
        .from(model.table)
        .where(model.idColumn.`is`(testVal))
        .count()
        .execute()

    override fun observeTest(model: TestModel<T>, testVal: Long): Long = Select
        .from(model.table)
        .where(model.idColumn.`is`(testVal))
        .count()
        .observe()
        .runQueryOnce()
        .blockingGet()
  }

  class CountAsColumnDualOperationWithWhereClause<T> : DualOperation<Long, T, Long> {
    override fun executeTest(model: TestModel<T>, testVal: Long): Long = Select
        .column(count())
        .from(model.table)
        .where(model.idColumn.`is`(testVal))
        .takeFirst()
        .execute()!!

    override fun observeTest(model: TestModel<T>, testVal: Long): Long = Select
        .column(count())
        .from(model.table)
        .where(model.idColumn.`is`(testVal))
        .takeFirst()
        .observe()
        .runQueryOnce()
        .blockingGet()
  }
}