@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.query.column

import android.annotation.SuppressLint
import android.database.sqlite.SQLiteException
import androidx.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleAllValuesMutableTable.SIMPLE_ALL_VALUES_MUTABLE
import com.siimkinks.sqlitemagic.SimpleMutableWithUniqueTable.SIMPLE_MUTABLE_WITH_UNIQUE
import com.siimkinks.sqlitemagic.assertThrows
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.query.QueryModelTestCase
import com.siimkinks.sqlitemagic.query.QueryOperation
import com.siimkinks.sqlitemagic.query.SelectListQueryOperation
import com.siimkinks.sqlitemagic.query.resultIsEqualToExpected
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColumnListQueryTest : DefaultConnectionTest {
  class QueryEmptyList<ModelType, TestReturnType>(
      testModel: TestModel<ModelType>,
      operation: QueryOperation<Unit, TestReturnType>
  ) : QueryModelTestCase<Unit, ModelType, TestReturnType>(
      "Querying column from empty table returns empty list",
      testModel = testModel,
      setUp = {},
      operation = operation,
      assertResults = { _, result -> assertThat(result as List<Any>).isEmpty() })

  @SuppressLint("CheckResult")
  @Test
  fun queryNonNullColumnFromEmptyTable() =
      QueryEmptyList(
          testModel = simpleMutableAutoIdTestModel,
          operation = SelectListQueryOperation {
            Select.column(AUTHOR.PRIMITIVE_BOOLEAN)
                .from(AUTHOR)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun queryNullableColumnFromEmptyTable() =
      QueryEmptyList(
          testModel = simpleMutableFixedIdUniqueNullableTestModel,
          operation = SelectListQueryOperation {
            Select.column(SIMPLE_MUTABLE_WITH_UNIQUE.STRING)
                .from(SIMPLE_MUTABLE_WITH_UNIQUE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun successfulNonNullColumnListQuery() =
      QueryModelTestCase("Querying non-null column returns correct list",
          testModel = simpleMutableAutoIdTestModel,
          setUp = {
            createVals { insertNewRandom(it) }
                .map { (v) -> v.primitiveBoolean }
          },
          operation = SelectListQueryOperation {
            Select.column(AUTHOR.PRIMITIVE_BOOLEAN)
                .from(AUTHOR)
          },
          assertResults = resultIsEqualToExpected())
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun successfulNullableColumnListQuery() =
      QueryModelTestCase("Querying nullable column returns correct list",
          testModel = simpleMutableFixedIdUniqueNullableTestModel,
          setUp = {
            createVals { insertNewRandom(it) }
                .sortedBy { it.second }
                .map { (v) -> v.string }
          },
          operation = SelectListQueryOperation {
            Select.column(SIMPLE_MUTABLE_WITH_UNIQUE.STRING)
                .from(SIMPLE_MUTABLE_WITH_UNIQUE)
          },
          assertResults = resultIsEqualToExpected())
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun queryComplexList() =
      QueryModelTestCase(
          "Querying complex column returns complex value ID",
          testModel = complexMutableAutoIdTestModel,
          setUp = {
            createVals { insertNewRandom(it) }
                .map { (v) -> v.author.id }
          },
          operation = SelectListQueryOperation {
            Select.column(MAGAZINE.AUTHOR)
                .from(MAGAZINE)
          },
          assertResults = resultIsEqualToExpected())
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun queryComplexChildColumn() =
      QueryModelTestCase(
          "Querying complex column value returns correct list",
          testModel = complexMutableAutoIdTestModel,
          setUp = {
            createVals { insertNewRandom(it) }
                .map { (v) -> v.author.name }
          },
          operation = SelectListQueryOperation {
            Select.column(AUTHOR.NAME)
                .from(MAGAZINE)
          },
          assertResults = resultIsEqualToExpected())
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun queryTransformerList() =
      QueryModelTestCase(
          "Querying column with transformer converts to correct value",
          testModel = simpleMutableAutoIdTestModel,
          setUp = {
            createVals { insertNewRandom(it) }
                .map { (v) -> v.boxedBoolean }
          },
          operation = SelectListQueryOperation {
            Select.column(AUTHOR.BOXED_BOOLEAN)
                .from(AUTHOR)
          },
          assertResults = resultIsEqualToExpected())
          .test()

  @SuppressLint("CheckResult")
  fun queryColumnListFromIrrelevantTableThrows() {
    assertThrows<SQLiteException> {
      Select.column(AUTHOR.NAME)
          .from<SimpleAllValuesMutable>(SIMPLE_ALL_VALUES_MUTABLE)
          .execute()
    }
  }

  @SuppressLint("CheckResult")
  fun queryColumnFirstFromIrrelevantTableThrows() {
    assertThrows<SQLiteException> {
      Select.column(AUTHOR.NAME)
          .from(SIMPLE_ALL_VALUES_MUTABLE)
          .takeFirst()
          .execute()
    }
  }
}