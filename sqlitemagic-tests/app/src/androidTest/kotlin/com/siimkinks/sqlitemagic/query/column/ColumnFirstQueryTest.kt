@file:Suppress("UNCHECKED_CAST")

package com.siimkinks.sqlitemagic.query.column

import android.annotation.SuppressLint
import android.support.test.runner.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.DefaultConnectionTest
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SelectSqlNode
import com.siimkinks.sqlitemagic.SimpleAllValuesMutableTable.SIMPLE_ALL_VALUES_MUTABLE
import com.siimkinks.sqlitemagic.SimpleMutableWithNullableFieldsTable.SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.createVals
import com.siimkinks.sqlitemagic.model.*
import com.siimkinks.sqlitemagic.query.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ColumnFirstQueryTest : DefaultConnectionTest {
  private val SQLITE_DOUBLE_TOLERANCE = 0.9999999999

  class QueryEmptyFirst<TestReturnType>(
      operation: QueryOperation<Unit, TestReturnType>
  ) : QueryTestCase<Unit, TestReturnType>(
      "Querying first column from empty table returns null",
      setUp = {},
      operation = operation,
      assertResults = resultIsNull())

  class QueryFirstFromFilledSimpleAllValuesMutableTable<T>(
      value: (SimpleAllValuesMutable) -> T,
      selection: () -> SelectSqlNode.SelectNode<T, Select.Select1, *>,
      assertResults: (T, T) -> Unit = resultIsEqualToExpected()
  ) : QueryTestCase<T, T>(
      "Querying first value from table returns expected result",
      setUp = {
        val firstVal = createVals {
          val v = SimpleAllValuesMutable.newRandom()
          assertThat(v.insert()
              .execute())
              .isNotEqualTo(-1)
          return@createVals v
        }.first()
        value(firstVal)
      },
      operation = SelectFirstQueryOperation<T, T, Select.Select1>(selection) as QueryOperation<T, T>,
      assertResults = assertResults)

  class QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable<T>(
      nullValue: (SimpleAllValuesMutable) -> Unit,
      selection: () -> SelectSqlNode.SelectNode<T, Select.Select1, *>
  ) : QueryTestCase<Unit, T>(
      "Querying first nullable column from filled table returns null",
      setUp = {
        createVals {
          val v = SimpleAllValuesMutable.newRandom()
          nullValue(v)
          assertThat(v.insert()
              .execute())
              .isNotEqualTo(-1)
          return@createVals v
        }.first()
      },
      operation = SelectFirstQueryOperation<Unit, T, Select.Select1>(selection) as QueryOperation<Unit, T>,
      assertResults = resultIsNull())

  @SuppressLint("CheckResult")
  @Test
  fun firstString() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.string },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.STRING)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableString() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.string = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.STRING)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableStringFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NULLABLE_STRING)
                .from(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableStringFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS.NON_NULL_STRING)
                .from(SIMPLE_MUTABLE_WITH_NULLABLE_FIELDS)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstPrimitiveLong() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.primitiveLong },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstBoxedLong() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.boxedLong },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_LONG)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableLong() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.boxedLong = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_LONG)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableLongFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableLongFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_LONG)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstPrimitiveInteger() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.primitiveInt },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstBoxedInteger() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.boxedInteger },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableInteger() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.boxedInteger = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableIntegerFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableIntegerFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstPrimitiveShort() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.primitiveShort },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstBoxedShort() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.boxedShort },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_SHORT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableShort() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.boxedShort = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_SHORT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableShortFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableShortFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_SHORT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstPrimitiveDouble() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.primitiveDouble },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_DOUBLE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          },
          assertResults = { expected, result ->
            assertThat(result)
                .isWithin(SQLITE_DOUBLE_TOLERANCE)
                .of(expected)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstBoxedDouble() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.boxedDouble },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_DOUBLE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          },
          assertResults = { expected, result ->
            assertThat(result)
                .isWithin(SQLITE_DOUBLE_TOLERANCE)
                .of(expected)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableDouble() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.boxedDouble = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_DOUBLE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableDoubleFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_DOUBLE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableDoubleFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_DOUBLE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstPrimitiveFloat() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.primitiveFloat },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_FLOAT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstBoxedFloat() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.boxedFloat },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_FLOAT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableFloat() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.boxedFloat = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_FLOAT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableFloatFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_FLOAT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableFloatFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_FLOAT)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstPrimitiveByte() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.primitiveByte },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BYTE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstBoxedByte() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.boxedByte },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableByte() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.boxedByte = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableByteFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BYTE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableByteFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstPrimitiveByteArray() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.primitiveByteArray },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BYTE_ARRAY)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          },
          assertResults = { expected, result -> assertThat(result).isEqualTo(expected) })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstBoxedByteArray() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.boxedByteArray },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE_ARRAY)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          },
          assertResults = { expected, result -> assertThat(result).isEqualTo(expected) })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullablePrimitiveByteArray() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.primitiveByteArray = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BYTE_ARRAY)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableBoxedByteArray() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.boxedByteArray = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE_ARRAY)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableByteArrayFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BYTE_ARRAY)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableByteArrayFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BYTE_ARRAY)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()


  @SuppressLint("CheckResult")
  @Test
  fun firstPrimitiveBoolean() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.isPrimitiveBoolean },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BOOLEAN)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstBoxedBoolean() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.boxedBoolean },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BOOLEAN)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableBoolean() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.boxedBoolean = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BOOLEAN)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableBooleanFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_BOOLEAN)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableBooleanFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BOOLEAN)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstTransformableObject() =
      QueryFirstFromFilledSimpleAllValuesMutableTable(
          value = { it.utilDate },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.UTIL_DATE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableTransformableObject() =
      QueryFirstNullableColumnFromFilledSimpleAllValuesMutableTable(
          nullValue = { it.utilDate = null },
          selection = {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.UTIL_DATE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNotNullableTransformableObjectFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.UTIL_DATE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableTransformableObjectFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(SIMPLE_ALL_VALUES_MUTABLE.UTIL_DATE)
                .from(SIMPLE_ALL_VALUES_MUTABLE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstComplexChild() =
      QueryModelTestCase(
          "Querying first complex column returns complex column ID",
          testModel = complexMutableAutoIdTestModel,
          setUp = {
            createVals { insertNewRandom(it) }
                .asSequence()
                .map { (v) -> v.author.id }
                .first()
          },
          operation = SelectFirstQueryOperation {
            Select.column(MAGAZINE.AUTHOR)
                .from(MAGAZINE)
          },
          assertResults = resultIsEqualToExpected())
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableComplexChild() =
      QueryModelTestCase(
          "Querying first nullable complex column returns null",
          testModel = complexMutableAutoIdTestModel,
          setUp = {
            createVals {
              val v = (it as ComplexNullableColumns<Magazine>)
                  .nullSomeComplexColumns(it.newRandom())
              it.insertBuilder(v).execute()
            }
          },
          operation = SelectFirstQueryOperation {
            Select.column(MAGAZINE.AUTHOR)
                .from(MAGAZINE)
          },
          assertResults = resultIsNull())
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstComplexChildFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(MAGAZINE.AUTHOR)
                .from(MAGAZINE)
          })
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstComplexChildColumn() =
      QueryModelTestCase(
          "Querying first complex column value returns correct value",
          testModel = complexMutableAutoIdTestModel,
          setUp = {
            createVals { insertNewRandom(it) }
                .asSequence()
                .map { (v) -> v.author.name }
                .first()
          },
          operation = SelectFirstQueryOperation {
            Select.column(AUTHOR.NAME)
                .from(MAGAZINE)
          },
          assertResults = resultIsEqualToExpected())
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstNullableComplexChildColumn() =
      QueryTestCase(
          "Querying first nullable complex column value returns null",
          setUp = {
            createVals {
              val v = Magazine.newRandom()
              v.author.name = null
              v.insert().execute()
            }
          },
          operation = SelectFirstQueryOperation {
            Select.column(AUTHOR.NAME)
                .from(MAGAZINE)
          },
          assertResults = resultIsNull())
          .test()

  @SuppressLint("CheckResult")
  @Test
  fun firstComplexChildColumnFromEmptyTable() =
      QueryEmptyFirst(
          operation = SelectFirstQueryOperation {
            Select.column(AUTHOR.NAME)
                .from(MAGAZINE)
          })
          .test()
}