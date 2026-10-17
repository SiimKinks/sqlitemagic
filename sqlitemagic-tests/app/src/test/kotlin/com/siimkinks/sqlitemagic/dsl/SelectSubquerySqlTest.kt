package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.COLUMN
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
import com.siimkinks.sqlitemagic.Select.Select1
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.isEqualTo
import org.junit.jupiter.api.Test

class SelectSubquerySqlTest : DSLTests {
  @Test
  fun simpleSubquery() {
    assertSimpleSubquery("=") { SIMPLE_MUTABLE_ENTITY.VALUE IS it }

    assertSimpleSubquery("!=") { SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT it }

    assertSimpleSubquery(" IN ") { SIMPLE_MUTABLE_ENTITY.VALUE IN it }

    assertSimpleSubquery(" NOT IN ") { SIMPLE_MUTABLE_ENTITY.VALUE NOT_IN it }
  }

  private fun assertSimpleSubquery(operator: String, callback: (SelectNode<String, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM main.simple_mutable_entity WHERE simple_mutable_entity.value%s" +
          "(SELECT immutable_value_with_fields.string_value FROM main.immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM SIMPLE_MUTABLE_ENTITY
        WHERE callback(subQuery))
      .isEqualTo(expectedBase.format(operator))
  }

  @Test
  fun numericSubquery() {
    assertSameTypeNumericSubquery("=") { ENTITY_WITH_RELATIONSHIP.COUNT IS it }
    assertSameTypeNumericSubquery("!=") { ENTITY_WITH_RELATIONSHIP.COUNT IS_NOT it }
    assertSameTypeNumericSubquery(" IN ") { ENTITY_WITH_RELATIONSHIP.COUNT IN it }
    assertSameTypeNumericSubquery(" NOT IN ") { ENTITY_WITH_RELATIONSHIP.COUNT NOT_IN it }
    assertSameTypeNumericSubquery(">") { ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN it }
    assertSameTypeNumericSubquery(">=") { ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL it }
    assertSameTypeNumericSubquery("<") { ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN it }
    assertSameTypeNumericSubquery("<=") { ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL it }

    assertEquivalentTypeNumericSubquery(">") { ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN it }
    assertEquivalentTypeNumericSubquery(">=") { ENTITY_WITH_RELATIONSHIP.COUNT GREATER_OR_EQUAL it }
    assertEquivalentTypeNumericSubquery("<") { ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN it }
    assertEquivalentTypeNumericSubquery("<=") { ENTITY_WITH_RELATIONSHIP.COUNT LESS_OR_EQUAL it }
  }

  private fun assertSameTypeNumericSubquery(operator: String, callback: (SelectNode<Int, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.count%s" +
          "(SELECT immutable_value_with_fields.integer FROM main.immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(expectedBase.format(operator))
  }

  private fun assertEquivalentTypeNumericSubquery(
    operator: String,
    callback: (SelectNode<out Number, Select1, *>) -> Expr
  ) {
    val expectedBase =
      "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.count%s" +
          "(SELECT immutable_value_with_fields.a_double FROM main.immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(expectedBase.format(operator))
  }

  @Test
  fun complexSubquery() {
    assertSameTypeComplexSubquery("=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS it }
    assertSameTypeComplexSubquery("!=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT it }
    assertSameTypeComplexSubquery(" IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IN it }
    assertSameTypeComplexSubquery(" NOT IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY NOT_IN it }
    assertSameTypeComplexSubquery(">") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN it }
    assertSameTypeComplexSubquery(">=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL it }
    assertSameTypeComplexSubquery("<") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN it }
    assertSameTypeComplexSubquery("<=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL it }


    assertIdTypeComplexSubquery("=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS it }
    assertIdTypeComplexSubquery("!=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT it }
    assertIdTypeComplexSubquery(" IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IN it }
    assertIdTypeComplexSubquery(" NOT IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY NOT_IN it }
    assertIdTypeComplexSubquery(">") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN it }
    assertIdTypeComplexSubquery(">=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL it }
    assertIdTypeComplexSubquery("<") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN it }
    assertIdTypeComplexSubquery("<=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL it }


    assertEquivalentTypeComplexSubquery("=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS it }
    assertEquivalentTypeComplexSubquery("!=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IS_NOT it }
    assertEquivalentTypeComplexSubquery(" IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY IN it }
    assertEquivalentTypeComplexSubquery(" NOT IN ") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY NOT_IN it }
    assertEquivalentTypeComplexSubquery(">") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_THAN it }
    assertEquivalentTypeComplexSubquery(">=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY GREATER_OR_EQUAL it }
    assertEquivalentTypeComplexSubquery("<") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_THAN it }
    assertEquivalentTypeComplexSubquery("<=") { ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY LESS_OR_EQUAL it }
  }

  private fun assertSameTypeComplexSubquery(operator: String, callback: (SelectNode<Long?, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "(SELECT entity_with_relationship.related_entity FROM main.entity_with_relationship ) "
    val subQuery = (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
        FROM ENTITY_WITH_RELATIONSHIP)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(expectedBase.format(operator))
  }

  private fun assertIdTypeComplexSubquery(operator: String, callback: (SelectNode<Long?, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "(SELECT entity_with_relationship.id FROM main.entity_with_relationship ) "
    val subQuery = (SELECT
        COLUMN ENTITY_WITH_RELATIONSHIP.ID
        FROM ENTITY_WITH_RELATIONSHIP)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(expectedBase.format(operator))
  }

  private fun assertEquivalentTypeComplexSubquery(operator: String, callback: (SelectNode<Int, Select1, *>) -> Expr) {
    val expectedBase =
      "SELECT * FROM main.entity_with_relationship WHERE entity_with_relationship.related_entity%s" +
          "(SELECT immutable_value_with_fields.integer FROM main.immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE callback(subQuery))
      .isEqualTo(expectedBase.format(operator))
  }
}
