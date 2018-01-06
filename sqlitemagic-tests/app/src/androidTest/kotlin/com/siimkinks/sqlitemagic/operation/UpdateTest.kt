@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.operation

import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableWithUniqueTable.SIMPLE_MUTABLE_WITH_UNIQUE
import com.siimkinks.sqlitemagic.Update
import com.siimkinks.sqlitemagic.model.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateTest : DefaultConnectionTest {
  @Test
  fun updateSingleRawColumn() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Single raw column update is successful",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = { insertNewRandom(it).first },
            operation = UpdateBuilderOperation {
              Update
                  .table("simple_mutable_with_unique")
                  .set("unique_val", "42") as Update.Set<SimpleMutableWithUnique>
            },
            assertResults = { model, insertedValue, updateCount ->
              assertThat(updateCount).isEqualTo(1)
              insertedValue.setUniqueVal(42)
              assertThat(Select
                  .from(model.table)
                  .takeFirst()
                  .execute())
                  .isEqualTo(insertedValue)
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  @Test
  fun updateSingleNotNullableColumn() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Single not nullable column update is successful",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = { insertNewRandom(it).first },
            operation = UpdateBuilderOperation {
              Update
                  .table(SIMPLE_MUTABLE_WITH_UNIQUE)
                  .set(SIMPLE_MUTABLE_WITH_UNIQUE.UNIQUE_VAL, 42)
            },
            assertResults = { model, insertedValue, updateCount ->
              assertThat(updateCount).isEqualTo(1)
              insertedValue.setUniqueVal(42)
              assertThat(Select
                  .from(model.table)
                  .takeFirst()
                  .execute())
                  .isEqualTo(insertedValue)
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  @Test
  fun updateSingleNullableColumn() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Single nullable column update is successful",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = { insertNewRandom(it).first },
            operation = UpdateBuilderOperation {
              Update
                  .table(SIMPLE_MUTABLE_WITH_UNIQUE)
                  .setNullable(SIMPLE_MUTABLE_WITH_UNIQUE.STRING, "foo")
            },
            assertResults = { model, insertedValue, updateCount ->
              assertThat(updateCount).isEqualTo(1)
              insertedValue.string = "foo"
              assertThat(Select
                  .from(model.table)
                  .takeFirst()
                  .execute())
                  .isEqualTo(insertedValue)
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  @Test
  fun nullSingleNullableColumn() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Single nullable column update with null is successful",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = { insertNewRandom(it).first },
            operation = UpdateBuilderOperation {
              Update
                  .table(SIMPLE_MUTABLE_WITH_UNIQUE)
                  .setNullable(SIMPLE_MUTABLE_WITH_UNIQUE.STRING, null)
            },
            assertResults = { model, insertedValue, updateCount ->
              assertThat(updateCount).isEqualTo(1)
              insertedValue.string = null
              assertThat(Select
                  .from(model.table)
                  .takeFirst()
                  .execute())
                  .isEqualTo(insertedValue)
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  @Test
  fun updateMultipleColumns() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Multiple column update is successful",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = {
              val insertCount = 5
              for (i in 0 until 5) {
                insertNewRandom(it)
              }
              insertCount
            },
            operation = UpdateBuilderOperation {
              Update
                  .table(SIMPLE_MUTABLE_WITH_UNIQUE)
                  .setNullable(SIMPLE_MUTABLE_WITH_UNIQUE.STRING, "foo")
            },
            assertResults = { model, insertCount, updateCount ->
              assertThat(updateCount).isEqualTo(insertCount)
              Select
                  .from(model.table)
                  .execute()
                  .forEach {
                    assertThat(it.string).isEqualTo("foo")
                  }
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  @Test
  fun updateRawWithWhereClause() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Raw update single row with where clause is successful",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = { model ->
              (0 until 5)
                  .map { insertNewRandom(model).first }
                  .last()
            },
            operation = object: DualOperation<SimpleMutableWithUnique, SimpleMutableWithUnique, Int> {
              override fun executeTest(model: TestModel<SimpleMutableWithUnique>, testVal: SimpleMutableWithUnique): Int =
                  Update
                      .table("simple_mutable_with_unique")
                      .set("string", "foo")
                      .where("id=?", testVal.id.toString())
                      .execute()

              override fun observeTest(model: TestModel<SimpleMutableWithUnique>, testVal: SimpleMutableWithUnique): Int =
                  Update
                      .table("simple_mutable_with_unique")
                      .set("string", "foo")
                      .where("id=?", testVal.id.toString())
                      .observe()
                      .blockingGet()

            },
            assertResults = { model, updatedValue, updateCount ->
              assertThat(updateCount).isEqualTo(1)
              val updatedValueId = updatedValue.id
              Select
                  .from(model.table)
                  .execute()
                  .forEach {
                    if (it.id == updatedValueId) {
                      assertThat(it.string).isEqualTo("foo")
                    } else {
                      assertThat(it.string).isNotEqualTo("foo")
                    }
                  }
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  @Test
  fun updateSingleWithWhereClause() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Update single row with where clause is successful",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = { model ->
              (0 until 5)
                  .map { insertNewRandom(model).first }
                  .last()
            },
            operation = UpdateWithWhereClauseBuilderOperation {
              Update
                  .table(SIMPLE_MUTABLE_WITH_UNIQUE)
                  .setNullable(SIMPLE_MUTABLE_WITH_UNIQUE.STRING, "foo")
                  .where(SIMPLE_MUTABLE_WITH_UNIQUE.ID.`is`(it.id))
            },
            assertResults = { model, updatedValue, updateCount ->
              assertThat(updateCount).isEqualTo(1)
              val updatedValueId = updatedValue.id
              Select
                  .from(model.table)
                  .execute()
                  .forEach {
                    if (it.id == updatedValueId) {
                      assertThat(it.string).isEqualTo("foo")
                    } else {
                      assertThat(it.string).isNotEqualTo("foo")
                    }
                  }
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  @Test
  fun updateMultipleWithWhereClause() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Update multiple rows with where clause is successful",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = { model ->
              (0 until 5)
                  .map { insertNewRandom(model).first.id }
                  .takeLast(3)
            },
            operation = UpdateWithWhereClauseBuilderOperation {
              Update
                  .table(SIMPLE_MUTABLE_WITH_UNIQUE)
                  .setNullable(SIMPLE_MUTABLE_WITH_UNIQUE.STRING, "foo")
                  .where(SIMPLE_MUTABLE_WITH_UNIQUE.ID.`in`(it))
            },
            assertResults = { model, updatedIds, updateCount ->
              assertThat(updateCount).isEqualTo(updatedIds.size)
              Select
                  .from(model.table)
                  .execute()
                  .forEach {
                    if (updatedIds.contains(it.id)) {
                      assertThat(it.string).isEqualTo("foo")
                    } else {
                      assertThat(it.string).isNotEqualTo("foo")
                    }
                  }
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  @Test
  fun nothingUpdated() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Table is untouched when nothing is updated",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = { model ->
              (0 until 5)
                  .map { insertNewRandom(model).first }
            },
            operation = UpdateWithWhereClauseBuilderOperation {
              Update
                  .table(SIMPLE_MUTABLE_WITH_UNIQUE)
                  .setNullable(SIMPLE_MUTABLE_WITH_UNIQUE.STRING, "foo")
                  .where(SIMPLE_MUTABLE_WITH_UNIQUE.ID.`is`(-1))
            },
            assertResults = { model, insertedValues, updateCount ->
              assertThat(updateCount).isEqualTo(0)
              assertThat(Select
                  .from(model.table)
                  .execute())
                  .containsExactlyElementsIn(insertedValues)
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  @Test
  fun updateEmptyTable() {
    assertThatDual {
      testCase {
        DualOperationTestCase(
            "Update on empty table",
            model = simpleMutableFixedIdUniqueNullableTestModel,
            setUp = {},
            operation = UpdateBuilderOperation {
              Update
                  .table(SIMPLE_MUTABLE_WITH_UNIQUE)
                  .setNullable(SIMPLE_MUTABLE_WITH_UNIQUE.STRING, "foo")
            },
            assertResults = { model, _, updateCount ->
              assertThat(updateCount).isEqualTo(0)
              assertThat(Select
                  .from(model.table)
                  .count()
                  .execute())
                  .isEqualTo(0)
            }
        )
      }
      isSuccessfulFor(simpleMutableFixedIdUniqueNullableTestModel)
    }
  }

  class UpdateBuilderOperation<PrepType, ModelType>(
      private val builder: () -> Update.Set<ModelType>
  ) : DualOperation<PrepType, ModelType, Int> {
    override fun executeTest(model: TestModel<ModelType>, testVal: PrepType): Int =
        builder().execute()

    override fun observeTest(model: TestModel<ModelType>, testVal: PrepType): Int =
        builder().observe().blockingGet()
  }

  class UpdateWithWhereClauseBuilderOperation<PrepType, ModelType>(
      private val builder: (PrepType) -> Update.Where
  ) : DualOperation<PrepType, ModelType, Int> {
    override fun executeTest(model: TestModel<ModelType>, testVal: PrepType): Int =
        builder(testVal).execute()

    override fun observeTest(model: TestModel<ModelType>, testVal: PrepType): Int =
        builder(testVal).observe().blockingGet()
  }
}