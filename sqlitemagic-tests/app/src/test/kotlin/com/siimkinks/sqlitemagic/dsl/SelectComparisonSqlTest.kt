package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.Expr
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.GREATER_OR_EQUAL
import com.siimkinks.sqlitemagic.GREATER_THAN
import com.siimkinks.sqlitemagic.IN
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.LESS_OR_EQUAL
import com.siimkinks.sqlitemagic.LESS_THAN
import com.siimkinks.sqlitemagic.NOT_IN
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.isEqualTo
import org.junit.jupiter.api.Test

class SelectComparisonSqlTest : DSLTests {
  @Test
  fun exprSimple() {
    assertSimpleExpr("=?", SIMPLE_MUTABLE_ENTITY.VALUE IS "asd")
    assertSimpleExpr("!=?", SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd")
    assertSimpleExpr(" IN (?)", SIMPLE_MUTABLE_ENTITY.VALUE IN arrayOf("asd"))
    assertSimpleExpr(" NOT IN (?)", SIMPLE_MUTABLE_ENTITY.VALUE NOT_IN arrayOf("asd"))

    assertSimpleColumnExpr("=", SIMPLE_MUTABLE_ENTITY.VALUE IS ENTITY_WITH_RELATIONSHIP.VALUE)
    assertSimpleColumnExpr("!=", SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT ENTITY_WITH_RELATIONSHIP.VALUE)
  }

  private fun assertSimpleExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM main.simple_mutable_entity WHERE simple_mutable_entity.value%s "

    (SELECT FROM SIMPLE_MUTABLE_ENTITY WHERE expr)
      .isEqualTo(expectedBase.format(operator), "asd")
  }

  private fun assertSimpleColumnExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM main.simple_mutable_entity WHERE simple_mutable_entity.value%s" +
          "entity_with_relationship.value "

    (SELECT FROM SIMPLE_MUTABLE_ENTITY WHERE expr)
      .isEqualTo(expectedBase.format(operator))
  }

  @Test
  fun unaryExpr() {
    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE !(SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN.isNotNull()))
      .isEqualTo(
        "SELECT * FROM main.simple_mutable_entity " +
            "WHERE NOT(simple_mutable_entity.primitive_boolean IS NOT NULL) "
      )

    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE !((SIMPLE_MUTABLE_ENTITY.PRIMITIVE_BOOLEAN GREATER_THAN SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN) AND
        SIMPLE_MUTABLE_ENTITY.BOXED_BOOLEAN.isNotNull()))
      .isEqualTo(
        "SELECT * FROM main.simple_mutable_entity WHERE NOT((simple_mutable_entity.primitive_boolean>" +
            "simple_mutable_entity.boxed_boolean AND simple_mutable_entity.boxed_boolean IS NOT NULL)) "
      )
  }

  @Test
  fun numericExprWithSameType() {
    assertNumericExpr("=?", (ENTITY_WITH_RELATIONSHIP.COUNT IS 4))
    assertNumericExpr("!=?", (ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT 4))
    assertNumericExpr(" IN (?)", (ENTITY_WITH_RELATIONSHIP.COUNT IN arrayOf(4)))
    assertNumericExpr(" NOT IN (?)", (ENTITY_WITH_RELATIONSHIP.COUNT NOT_IN arrayOf(4)))
    assertNumericExpr(">?", (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN 4))
    assertNumericExpr(">=?", (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL 4))
    assertNumericExpr("<?", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN 4))
    assertNumericExpr("<=?", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL 4))
  }

  @Test
  fun numericExprWithSameColumnType() {
    assertNumericSameTypeExpr("=", (ENTITY_WITH_RELATIONSHIP.COUNT IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr("!=", (ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr(">", (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr(
      operator = ">=",
      expr = (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.INTEGER)
    )
    assertNumericSameTypeExpr("<", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr("<=", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
  }

  @Test
  fun numericExprWithEquivalentColumnType() {
    assertNumericEquivalentTypeExpr("=", (ENTITY_WITH_RELATIONSHIP.COUNT IS IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr("!=", (ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr(">", (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr(
      operator = ">=",
      expr = (ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.ID)
    )
    assertNumericEquivalentTypeExpr("<", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr("<=", (ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.ID))
  }

  private fun assertNumericExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.count%s "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(expectedBase.format(operator), "4")
  }

  private fun assertNumericSameTypeExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.count%s" +
          "immutable_value_with_fields.integer "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(expectedBase.format(operator))
  }

  private fun assertNumericEquivalentTypeExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.count%s" +
          "immutable_value_with_fields.id "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(expectedBase.format(operator))
  }

  @Test
  fun complexExprWithSameType() {
    val simpleMutableEntity = SimpleMutableEntity(id = 101L)
    val simpleMutableEntityId = checkNotNull(simpleMutableEntity.id)

    assertComplexSameTypeExpr(
      operator = "=?",
      simple_mutable_entity = simpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS simpleMutableEntityId
    )
    assertComplexSameTypeExpr(
      operator = "!=?",
      simple_mutable_entity = simpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.isNot(simpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = " IN (?)",
      simple_mutable_entity = simpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.`in`(simpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = " IN (?)",
      simple_mutable_entity = simpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.`in`(simpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = " NOT IN (?)",
      simple_mutable_entity = simpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.notIn(simpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = ">?",
      simple_mutable_entity = simpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.greaterThan(simpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = ">=?",
      simple_mutable_entity = simpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.greaterOrEqual(simpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = "<?",
      simple_mutable_entity = simpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.lessThan(simpleMutableEntityId)
    )
    assertComplexSameTypeExpr(
      operator = "<=?",
      simple_mutable_entity = simpleMutableEntity,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY.lessOrEqual(simpleMutableEntityId)
    )
  }

  @Test
  fun complexExprWithEquivalentType() {
    val simpleMutableEntityId: Long = 42

    assertComplexEquivalentTypeExpr(
      operator = "=?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = "!=?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = " IN (?)",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IN arrayOf(simpleMutableEntityId)
    )
    assertComplexEquivalentTypeExpr(
      operator = " NOT IN (?)",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY NOT_IN arrayOf(simpleMutableEntityId)
    )
    assertComplexEquivalentTypeExpr(
      operator = ">?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = ">=?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = "<?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN simpleMutableEntityId
    )
    assertComplexEquivalentTypeExpr(
      operator = "<=?",
      value = simpleMutableEntityId,
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL simpleMutableEntityId
    )
  }

  @Test
  fun complexExprWithSameColumnType() {
    assertComplexSameColumnTypeExpr(
      operator = "=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = "!=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = ">",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = ">=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = "<",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
    assertComplexSameColumnTypeExpr(
      operator = "<=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    )
  }

  @Test
  fun complexExprWithEquivalentColumnType() {
    assertComplexEquivalentColumnTypeExpr(
      operator = "=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = "!=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = ">",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = ">=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = "<",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN ENTITY_WITH_RELATIONSHIP.ID
    )
    assertComplexEquivalentColumnTypeExpr(
      operator = "<=",
      expr = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL ENTITY_WITH_RELATIONSHIP.ID
    )
  }

  private fun assertComplexSameTypeExpr(operator: String, simple_mutable_entity: SimpleMutableEntity, expr: Expr) {
    val expectedBase = "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.related_entity%s "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(
        expectedBase.format(operator),
        simple_mutable_entity.id.toString()
      )
  }

  private fun assertComplexEquivalentTypeExpr(operator: String, value: Long, expr: Expr) {
    val expectedBase = "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.related_entity%s "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(
        expectedBase.format(operator),
        value.toString()
      )
  }

  private fun assertComplexSameColumnTypeExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "entity_with_relationship.related_entity "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(expectedBase.format(operator))
  }

  private fun assertComplexEquivalentColumnTypeExpr(operator: String, expr: Expr) {
    val expectedBase =
      "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "entity_with_relationship.id "

    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE expr)
      .isEqualTo(expectedBase.format(operator))
  }

  @Test
  fun exprComplexAliased() {
    val expectedBase = "SELECT * FROM main.entity_with_relationship AS m "
    val simpleMutableEntity = SimpleMutableEntity(id = 101L)
    val m = ENTITY_WITH_RELATIONSHIP AS "m"

    var expected = expectedBase + "WHERE m.related_entity=? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS checkNotNull(simpleMutableEntity.id)))
      .isEqualTo(expected, simpleMutableEntity.id.toString())

    expected = expectedBase + "WHERE m.related_entity=entity_with_relationship.related_entity "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity=m.related_entity "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS m.RELATED_ENTITY))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity=entity_with_relationship.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS ENTITY_WITH_RELATIONSHIP.ID))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity=m.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY IS m.ID))
      .isEqualTo(expected)
  }
}
