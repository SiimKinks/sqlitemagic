package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.COLUMN
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.GREATER_THAN
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.LESS_THAN
import com.siimkinks.sqlitemagic.NumericColumn
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.Select.Companion.avg
import com.siimkinks.sqlitemagic.Select.Companion.concat
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.ValueParserType.BYTE
import com.siimkinks.sqlitemagic.ValueParserType.DOUBLE
import com.siimkinks.sqlitemagic.ValueParserType.FLOAT
import com.siimkinks.sqlitemagic.ValueParserType.INTEGER
import com.siimkinks.sqlitemagic.ValueParserType.LONG
import com.siimkinks.sqlitemagic.ValueParserType.SHORT
import com.siimkinks.sqlitemagic.ValueParserType.STRING
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.asColumn
import com.siimkinks.sqlitemagic.concat
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.isEqualTo
import com.siimkinks.sqlitemagic.parsesWith
import com.siimkinks.sqlitemagic.replace
import com.siimkinks.sqlitemagic.with
import org.junit.jupiter.api.Test

class SelectFunctionSqlTest : DSLTests {
  @Test
  fun unaryMinus() {
    (SELECT
        COLUMN -(ENTITY_WITH_RELATIONSHIP.COUNT - 42)
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT -((entity_with_relationship.count-42)) FROM main.entity_with_relationship ")

    (SELECT
        COLUMN -(ENTITY_WITH_RELATIONSHIP.COUNT - -42)
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT -((entity_with_relationship.count-(-42))) FROM main.entity_with_relationship ")

    (SELECT
        COLUMN -(ENTITY_WITH_RELATIONSHIP.COUNT - 42.asColumn)
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT -((entity_with_relationship.count-42)) FROM main.entity_with_relationship ")

    (SELECT
        COLUMN -(ENTITY_WITH_RELATIONSHIP.COUNT - (-42).asColumn)
        FROM ENTITY_WITH_RELATIONSHIP)
      .isEqualTo("SELECT -((entity_with_relationship.count-(-42))) FROM main.entity_with_relationship ")
  }

  @Test
  fun avgFunction() {
    (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.ID
        FROM IMMUTABLE_VALUE_WITH_FIELDS
        WHERE ((avg(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) GREATER_THAN 5555.0)
        AND (avg(IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE) LESS_THAN 8888.8)))
      .isEqualTo(
        "SELECT immutable_value_with_fields.id FROM main.immutable_value_with_fields WHERE " +
            "(avg(immutable_value_with_fields.integer)>? AND " +
            "avg(immutable_value_with_fields.a_double)<?) "
      )
  }

  @Test
  fun concatFunction() {
    var expected = "SELECT simple_mutable_entity.id || simple_mutable_entity.value FROM main.simple_mutable_entity "
    (SELECT
        COLUMN (SIMPLE_MUTABLE_ENTITY.ID concat SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)

    expected =
      "SELECT simple_mutable_entity.id || simple_mutable_entity.value || " +
          "simple_mutable_entity.primitive_boolean FROM main.simple_mutable_entity "
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, SIMPLE_MUTABLE_ENTITY.VALUE, SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)

    (SELECT
        COLUMN (
        (SIMPLE_MUTABLE_ENTITY.ID concat SIMPLE_MUTABLE_ENTITY.VALUE) concat
            SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN
        )
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
  }

  @Test
  fun replaceFunction() {
    var expected = "SELECT replace(simple_mutable_entity.value,'a','____') FROM main.simple_mutable_entity "
    (SELECT
        COLUMN SIMPLE_MUTABLE_ENTITY.VALUE.replace("a" with "____")
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)

    expected = "SELECT replace(simple_mutable_entity.value,'a','____') AS 'asd' FROM main.simple_mutable_entity "
    (SELECT
        COLUMN (SIMPLE_MUTABLE_ENTITY.VALUE.replace("a" with "____") AS "asd")
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
  }

  @Test
  fun valColumn() {
    var expected = "SELECT simple_mutable_entity.id || ' ' || simple_mutable_entity.value " +
        "FROM main.simple_mutable_entity "
    val strVal = " ".asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, strVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    strVal.parsesWith(STRING)

    expected = "SELECT simple_mutable_entity.id || 3 || simple_mutable_entity.value FROM main.simple_mutable_entity "
    val intVal = 3.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, intVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    intVal.parsesWith(INTEGER)

    expected = "SELECT simple_mutable_entity.id || 3 || simple_mutable_entity.value FROM main.simple_mutable_entity "
    val longVal = 3L.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, longVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    longVal.parsesWith(LONG)

    val s: Short = 3
    expected = "SELECT simple_mutable_entity.id || 3 || simple_mutable_entity.value FROM main.simple_mutable_entity "
    val shortVal = s.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, shortVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    shortVal.parsesWith(SHORT)

    val b: Byte = 3
    expected = "SELECT simple_mutable_entity.id || 3 || simple_mutable_entity.value FROM main.simple_mutable_entity "
    val byteVal = b.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, byteVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    byteVal.parsesWith(BYTE)

    val f = 3.3f
    expected = "SELECT simple_mutable_entity.id || 3.3 || simple_mutable_entity.value FROM main.simple_mutable_entity "
    val floatVal = f.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, floatVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    floatVal.parsesWith(FLOAT)

    val d = 3.3
    expected = "SELECT simple_mutable_entity.id || 3.3 || simple_mutable_entity.value FROM main.simple_mutable_entity "
    val doubleVal = d.asColumn
    (SELECT
        COLUMN concat(SIMPLE_MUTABLE_ENTITY.ID, doubleVal, SIMPLE_MUTABLE_ENTITY.VALUE)
        FROM SIMPLE_MUTABLE_ENTITY)
      .isEqualTo(expected)
    doubleVal.parsesWith(DOUBLE)
  }

  @Test
  fun addFunction() {
    assertArithmeticExpression(
      '+',
      { v1, v2 -> v1 + v2 },
      { v1, v2 -> v1 + v2 },
      { v1, v2 -> v1 + v2 })
  }

  @Test
  fun subFunction() {
    assertArithmeticExpression(
      '-',
      { v1, v2 -> v1 - v2 },
      { v1, v2 -> v1 - v2 },
      { v1, v2 -> v1 - v2 })
  }

  @Test
  fun mulFunction() {
    assertArithmeticExpression(
      '*',
      { v1, v2 -> v1 * v2 },
      { v1, v2 -> v1 * v2 },
      { v1, v2 -> v1 * v2 })
  }

  @Test
  fun divFunction() {
    assertArithmeticExpression(
      '/',
      { v1, v2 -> v1 / v2 },
      { v1, v2 -> v1 / v2 },
      { v1, v2 -> v1 / v2 })
  }

  @Test
  fun modFunction() {
    assertArithmeticExpression(
      '%',
      { v1, v2 -> v1 % v2 },
      { v1, v2 -> v1 % v2 },
      { v1, v2 -> v1 % v2 })
  }

  @Test
  fun numericArithmeticExpressionsChained() {
    var expected = "SELECT ((((1+2)*(5-3))/2.0)%10.0) FROM main.immutable_value_with_fields "
    (SELECT
        COLUMN ((((1.asColumn + 2) * (5.asColumn - 3)) / 2.0) % 10.0)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = "SELECT ((((immutable_value_with_fields.integer+2)*(5-3))/2.0)%10.0) " +
        "FROM main.immutable_value_with_fields "
    (SELECT
        COLUMN ((((IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 2) * (5.asColumn - 3)) / 2.0) % 10.0)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)
  }

  private fun assertArithmeticExpression(
    op: Char,
    columnCallback: (
      NumericColumn<Int, Int, Number, ImmutableValueWithFields, *>,
      NumericColumn<Short, Short, Number, ImmutableValueWithFields, *>
    ) -> NumericColumn<*, *, *, *, *>,
    columnValueCallback: (
      NumericColumn<Int, Int, Number, ImmutableValueWithFields, *>,
      NumericColumn<Long, Long, Number, *, *>
    ) -> NumericColumn<*, *, *, *, *>,
    valueCallback: (NumericColumn<Int, Int, Number, ImmutableValueWithFields, *>, Int) -> NumericColumn<*, *, *, *, *>
  ) {
    var expected = "SELECT (immutable_value_with_fields.integer${op}immutable_value_with_fields.a_short) " +
        "FROM main.immutable_value_with_fields "
    (SELECT
        COLUMN columnCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = "SELECT (immutable_value_with_fields.integer${op}5) " +
        "FROM main.immutable_value_with_fields "
    (SELECT
        COLUMN columnValueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, 5L.asColumn)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = "SELECT (immutable_value_with_fields.integer${op}(-5)) " +
        "FROM main.immutable_value_with_fields "
    (SELECT
        COLUMN columnValueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, (-5L).asColumn)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = "SELECT (immutable_value_with_fields.integer${op}5) " +
        "FROM main.immutable_value_with_fields "
    (SELECT
        COLUMN valueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, 5)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)

    expected = "SELECT (immutable_value_with_fields.integer${op}(-5)) " +
        "FROM main.immutable_value_with_fields "
    (SELECT
        COLUMN valueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, -5)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
      .isEqualTo(expected)
  }
}
