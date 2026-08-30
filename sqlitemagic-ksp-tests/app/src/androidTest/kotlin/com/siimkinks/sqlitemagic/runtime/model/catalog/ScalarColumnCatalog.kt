package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldss
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldss
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithNullableFields
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.ScalarColumnCase

internal object ScalarColumnCatalog {
  private val nonNullRows = listOf(
    ImmutableValueWithFields(
      id = null,
      stringValue = "non-null-string-1",
      aBoolean = true,
      integer = 101,
      aDouble = 1.25,
      aShort = 11,
      transformableObject = TransformableObject(value = 1001)
    ),
    ImmutableValueWithFields(
      id = null,
      stringValue = "non-null-string-2",
      aBoolean = false,
      integer = 202,
      aDouble = 2.5,
      aShort = 22,
      transformableObject = TransformableObject(value = 1002)
    ),
    ImmutableValueWithFields(
      id = null,
      stringValue = "non-null-string-3",
      aBoolean = true,
      integer = 303,
      aDouble = 3.75,
      aShort = 33,
      transformableObject = TransformableObject(value = 1003)
    )
  )

  private val nullableRows = listOf(
    ImmutableValueWithNullableFields(
      id = null,
      string = null,
      aBoolean = null,
      integer = null
    ),
    ImmutableValueWithNullableFields(
      id = null,
      string = "nullable-string-2",
      aBoolean = false,
      integer = 202
    ),
    ImmutableValueWithNullableFields(
      id = null,
      string = "nullable-string-3",
      aBoolean = true,
      integer = 303
    )
  )

  val cases = listOf(
    NonNullStringCase,
    NonNullBooleanCase,
    NonNullIntCase,
    NonNullDoubleCase,
    NonNullShortCase,
    NonNullTransformableObjectCase,
    NullableStringCase,
    NullableBooleanCase,
    NullableIntCase,
    EmptyNonNullStringCase,
    EmptyNullableStringCase
  )

  private object NonNullStringCase : ScalarColumnCase<String>(
    name = "non-null String",
    expectedValues = nonNullRows.map(ImmutableValueWithFields::stringValue),
    seed = ::seedNonNullRows,
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
    }
  )

  private object NonNullBooleanCase : ScalarColumnCase<Boolean>(
    name = "non-null Boolean",
    expectedValues = nonNullRows.map(ImmutableValueWithFields::aBoolean),
    seed = ::seedNonNullRows,
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
    }
  )

  private object NonNullIntCase : ScalarColumnCase<Int>(
    name = "non-null Int",
    expectedValues = nonNullRows.map(ImmutableValueWithFields::integer),
    seed = ::seedNonNullRows,
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
    }
  )

  private object NonNullDoubleCase : ScalarColumnCase<Double>(
    name = "non-null Double",
    expectedValues = nonNullRows.map(ImmutableValueWithFields::aDouble),
    seed = ::seedNonNullRows,
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
    }
  )

  private object NonNullShortCase : ScalarColumnCase<Short>(
    name = "non-null Short",
    expectedValues = nonNullRows.map(ImmutableValueWithFields::aShort),
    seed = ::seedNonNullRows,
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
    }
  )

  private object NonNullTransformableObjectCase : ScalarColumnCase<TransformableObject>(
    name = "non-null TransformableObject",
    expectedValues = nonNullRows.map(ImmutableValueWithFields::transformableObject),
    seed = ::seedNonNullRows,
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.TRANSFORMABLE_OBJECT)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
    }
  )

  private object NullableStringCase : ScalarColumnCase<String?>(
    name = "nullable String with null row",
    expectedValues = nullableRows.map(ImmutableValueWithNullableFields::string),
    seed = ::seedNullableRows,
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
        .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
    }
  )

  private object NullableBooleanCase : ScalarColumnCase<Boolean?>(
    name = "nullable Boolean with null row",
    expectedValues = nullableRows.map(ImmutableValueWithNullableFields::aBoolean),
    seed = ::seedNullableRows,
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN)
        .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
    }
  )

  private object NullableIntCase : ScalarColumnCase<Int?>(
    name = "nullable Int with null row",
    expectedValues = nullableRows.map(ImmutableValueWithNullableFields::integer),
    seed = ::seedNullableRows,
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER)
        .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
    }
  )

  private object EmptyNonNullStringCase : ScalarColumnCase<String>(
    name = "empty non-null String table",
    expectedValues = emptyList(),
    seed = {},
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
        .from(IMMUTABLE_VALUE_WITH_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
    }
  )

  private object EmptyNullableStringCase : ScalarColumnCase<String?>(
    name = "empty nullable String table",
    expectedValues = emptyList(),
    seed = {},
    query = {
      Select
        .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
        .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
        .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
    }
  )

  private fun seedNonNullRows() {
    check(
      ImmutableValueWithFieldss
        .insert(nonNullRows)
        .execute()
    )
  }

  private fun seedNullableRows() {
    check(
      ImmutableValueWithNullableFieldss
        .insert(nullableRows)
        .execute()
    )
  }
}
