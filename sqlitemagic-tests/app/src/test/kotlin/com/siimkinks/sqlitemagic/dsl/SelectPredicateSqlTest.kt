package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.BETWEEN
import com.siimkinks.sqlitemagic.COLUMN
import com.siimkinks.sqlitemagic.COLUMNS
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.GLOB
import com.siimkinks.sqlitemagic.GREATER_THAN
import com.siimkinks.sqlitemagic.IN
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.IS_NOT
import com.siimkinks.sqlitemagic.LESS_THAN
import com.siimkinks.sqlitemagic.LIKE
import com.siimkinks.sqlitemagic.NOT_BETWEEN
import com.siimkinks.sqlitemagic.NOT_IN
import com.siimkinks.sqlitemagic.OR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.Select.Companion.concat
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.asColumn
import com.siimkinks.sqlitemagic.expr
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.isEqualTo
import org.junit.jupiter.api.Test

class SelectPredicateSqlTest : DSLTests {
  @Test
  fun whereCondition() {
    val expectedBase = "SELECT * FROM main.entity_with_relationship WHERE "

    val titleIs = ENTITY_WITH_RELATIONSHIP.VALUE IS "asd"
    val intIs = ENTITY_WITH_RELATIONSHIP.COUNT IS 1920
    val titleIsNot = ENTITY_WITH_RELATIONSHIP.VALUE IS_NOT "asd"
    val titleIsNotNull = ENTITY_WITH_RELATIONSHIP.VALUE.isNotNull()
    val titleIsNull = ENTITY_WITH_RELATIONSHIP.VALUE.isNull()
    val titleGlob = ENTITY_WITH_RELATIONSHIP.VALUE GLOB "asd"
    val titleLike = ENTITY_WITH_RELATIONSHIP.VALUE LIKE "asd"
    val lessThan = ENTITY_WITH_RELATIONSHIP.COUNT LESS_THAN 1990
    val greaterThan = ENTITY_WITH_RELATIONSHIP.COUNT GREATER_THAN 1990
    val between = ENTITY_WITH_RELATIONSHIP.COUNT BETWEEN (1910 AND 2000)
    val notBetween = ENTITY_WITH_RELATIONSHIP.COUNT NOT_BETWEEN (1910 AND 2000)
    val `in` = ENTITY_WITH_RELATIONSHIP.COUNT IN arrayOf(1910, 1999, 1920)
    val notIn = ENTITY_WITH_RELATIONSHIP.COUNT NOT_IN arrayOf(1910, 1999, 1920)
    val oneIn = ENTITY_WITH_RELATIONSHIP.COUNT IN arrayOf(1910)
    val oneNotIn = ENTITY_WITH_RELATIONSHIP.COUNT NOT_IN arrayOf(1910)

    var expected = "${expectedBase}entity_with_relationship.value=? "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE titleIs)
      .isEqualTo(expected, "asd")

    expected = "$expectedBase(entity_with_relationship.value=? AND entity_with_relationship.count=?) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (titleIs AND intIs))
      .isEqualTo(expected, "asd", "1920")

    expected = "${expectedBase}entity_with_relationship.count BETWEEN ? AND ? "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE between)
      .isEqualTo(expected, "1910", "2000")

    expected = "${expectedBase}entity_with_relationship.count NOT BETWEEN ? AND ? "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE notBetween)
      .isEqualTo(expected, "1910", "2000")

    expected = "${expectedBase}entity_with_relationship.count IN (?,?,?) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE `in`)
      .isEqualTo(expected, "1910", "1999", "1920")

    val expectedLongWhereClause =
      "((((((((entity_with_relationship.value!=? AND " +
          "entity_with_relationship.value IS NOT NULL) AND entity_with_relationship.value IS NULL) " +
          "AND entity_with_relationship.value GLOB ?) AND entity_with_relationship.value LIKE ?) " +
          "AND entity_with_relationship.count<?) AND entity_with_relationship.count>?) " +
          "AND entity_with_relationship.count BETWEEN ? AND ?) AND entity_with_relationship.count NOT BETWEEN ? AND ?) "
    expected = expectedBase + expectedLongWhereClause
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE ((((((((titleIsNot AND titleIsNotNull) AND titleIsNull) AND titleGlob)
        AND titleLike) AND lessThan) AND greaterThan) AND between) AND notBetween))
      .isEqualTo(expected, "asd", "asd", "asd", "1990", "1990", "1910", "2000", "1910", "2000")

    expected =
      "$expectedBase(((entity_with_relationship.count IN (?,?,?) AND " +
          "entity_with_relationship.count NOT IN (?,?,?)) AND entity_with_relationship.count IN (?)) " +
          "AND entity_with_relationship.count NOT IN (?)) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (((`in` AND notIn) AND oneIn) AND oneNotIn))
      .isEqualTo(expected, "1910", "1999", "1920", "1910", "1999", "1920", "1910", "1910")

    expected = "$expectedBase(entity_with_relationship.count IN (?,?,?) OR entity_with_relationship.value!=?) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` OR titleIsNot))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = "$expectedBase(entity_with_relationship.count IN (?,?,?) AND " +
        "(entity_with_relationship.value!=? AND entity_with_relationship.value IS NOT NULL)) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` AND (titleIsNot AND titleIsNotNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected =
      "$expectedBase(entity_with_relationship.count IN (?,?,?) OR " +
          "(entity_with_relationship.count BETWEEN ? AND ? AND entity_with_relationship.value IS NOT NULL)) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` OR (between AND titleIsNotNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "1910", "2000")

    expected =
      "$expectedBase(entity_with_relationship.count IN (?,?,?) AND " +
          "((entity_with_relationship.value!=? OR entity_with_relationship.value IS NOT NULL) OR " +
          "entity_with_relationship.value IS NULL)) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` AND ((titleIsNot OR titleIsNotNull) OR titleIsNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = "$expectedBase(entity_with_relationship.count IN (?,?,?) OR entity_with_relationship.value!=?) "
    (SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE (`in` OR titleIsNot))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")
  }

  @Test
  fun whereConditionAliased() {
    val expectedBase = "SELECT * FROM main.entity_with_relationship AS m WHERE "

    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val titleIs = m.VALUE IS "asd"
    val intIs = m.COUNT IS 1920
    val titleIsNot = m.VALUE IS_NOT "asd"
    val titleIsNotNull = m.VALUE.isNotNull()
    val titleIsNull = m.VALUE.isNull()
    val titleGlob = m.VALUE GLOB "asd"
    val titleLike = m.VALUE LIKE "asd"
    val lessThan = m.COUNT LESS_THAN 1990
    val greaterThan = m.COUNT GREATER_THAN 1990
    val between = m.COUNT BETWEEN (1910 AND 2000)
    val notBetween = m.COUNT NOT_BETWEEN (1910 AND 2000)
    val `in` = m.COUNT IN arrayOf(1910, 1999, 1920)
    val notIn = m.COUNT NOT_IN arrayOf(1910, 1999, 1920)
    val oneIn = m.COUNT IN arrayOf(1910)
    val oneNotIn = m.COUNT NOT_IN arrayOf(1910)

    var expected = expectedBase + "m.value=? "
    (SELECT FROM m WHERE titleIs)
      .isEqualTo(expected, "asd")

    expected = "$expectedBase(m.value=? AND m.count=?) "
    (SELECT FROM m WHERE (titleIs AND intIs))
      .isEqualTo(expected, "asd", "1920")

    expected = expectedBase + "m.count BETWEEN ? AND ? "
    (SELECT FROM m WHERE between)
      .isEqualTo(expected, "1910", "2000")

    expected = expectedBase + "m.count NOT BETWEEN ? AND ? "
    (SELECT FROM m WHERE notBetween)
      .isEqualTo(expected, "1910", "2000")

    expected = expectedBase + "m.count IN (?,?,?) "
    (SELECT FROM m WHERE `in`)
      .isEqualTo(expected, "1910", "1999", "1920")

    val expectedLongWhereClause = "((((((((m.value!=? AND m.value IS NOT NULL) AND m.value IS NULL) " +
        "AND m.value GLOB ?) AND m.value LIKE ?) AND m.count<?) AND m.count>?) " +
        "AND m.count BETWEEN ? AND ?) AND m.count NOT BETWEEN ? AND ?) "
    expected = expectedBase + expectedLongWhereClause
    (SELECT FROM m WHERE ((((((((titleIsNot AND titleIsNotNull) AND titleIsNull) AND titleGlob)
        AND titleLike) AND lessThan) AND greaterThan) AND between) AND notBetween))
      .isEqualTo(expected, "asd", "asd", "asd", "1990", "1990", "1910", "2000", "1910", "2000")

    expected = expectedBase + "(((m.count IN (?,?,?) AND m.count NOT IN (?,?,?)) " +
        "AND m.count IN (?)) AND m.count NOT IN (?)) "
    (SELECT FROM m WHERE (((`in` AND notIn) AND oneIn) AND oneNotIn))
      .isEqualTo(expected, "1910", "1999", "1920", "1910", "1999", "1920", "1910", "1910")

    expected = "$expectedBase(m.count IN (?,?,?) OR m.value!=?) "
    (SELECT FROM m WHERE (`in` OR titleIsNot))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = "$expectedBase(m.count IN (?,?,?) AND (m.value!=? AND m.value IS NOT NULL)) "
    (SELECT FROM m WHERE (`in` AND (titleIsNot AND titleIsNotNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected =
      "$expectedBase(m.count IN (?,?,?) OR (m.count BETWEEN ? AND ? AND m.value IS NOT NULL)) "
    (SELECT FROM m WHERE (`in` OR (between AND titleIsNotNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "1910", "2000")

    expected = "$expectedBase(m.count IN (?,?,?) AND ((m.value!=? OR m.value IS NOT NULL) OR m.value IS NULL)) "
    (SELECT FROM m WHERE (`in` AND ((titleIsNot OR titleIsNotNull) OR titleIsNull)))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = "$expectedBase(m.count IN (?,?,?) OR m.value!=?) "
    (SELECT FROM m WHERE (`in` OR titleIsNot))
      .isEqualTo(expected, "1910", "1999", "1920", "asd")
  }

  @Test
  fun columnNotRedefinedWhenAliased() {
    var expected = "SELECT entity_with_relationship.*,entity_with_relationship.value || ' ' || " +
        "entity_with_relationship.count AS 'search_column' " +
        "FROM main.entity_with_relationship " +
        "WHERE entity_with_relationship.value=search_column "
    val searchColumn = concat(
      ENTITY_WITH_RELATIONSHIP.VALUE,
      " ".asColumn,
      ENTITY_WITH_RELATIONSHIP.COUNT
    ) AS "search_column"
    (SELECT
        COLUMNS arrayOf(ENTITY_WITH_RELATIONSHIP.all(), searchColumn)
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.VALUE IS (searchColumn)))
      .isEqualTo(expected)

    expected = "SELECT trim(entity_with_relationship.value) AS 'trimmed_title' " +
        "FROM main.entity_with_relationship " +
        "WHERE trim(entity_with_relationship.value)=trimmed_title "
    val trimmedTitle = ENTITY_WITH_RELATIONSHIP.VALUE.trim() AS "trimmed_title"
    (SELECT
        COLUMN trimmedTitle
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.VALUE.trim() IS trimmedTitle))
      .isEqualTo(expected)
  }

  @Test
  fun betweenComplex() {
    val expectedBase = "SELECT * FROM main.entity_with_relationship "
    val simpleMutableEntity = SimpleMutableEntity(id = 101L)
    val simpleMutableEntity2 = SimpleMutableEntity(id = 202L)
    val simpleMutableEntityId = checkNotNull(simpleMutableEntity.id)
    val simpleMutableEntityId2 = checkNotNull(simpleMutableEntity2.id)

    var expected = expectedBase + "WHERE entity_with_relationship.related_entity BETWEEN ? AND ? "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND simpleMutableEntityId2)))
      .isEqualTo(expected, simpleMutableEntityId.toString(), simpleMutableEntityId2.toString())

    expected = expectedBase + "WHERE entity_with_relationship.related_entity BETWEEN ? AND simple_mutable_entity.id "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND SIMPLE_MUTABLE_ENTITY.ID)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE entity_with_relationship.related_entity BETWEEN simple_mutable_entity.id AND ? "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (SIMPLE_MUTABLE_ENTITY.ID AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase +
        "WHERE entity_with_relationship.related_entity BETWEEN simple_mutable_entity.id AND " +
        "entity_with_relationship.id "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
        ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (
            SIMPLE_MUTABLE_ENTITY.ID AND ENTITY_WITH_RELATIONSHIP.ID
            )
        ))
      .isEqualTo(expected)

    expected = expectedBase +
        "WHERE entity_with_relationship.related_entity BETWEEN entity_with_relationship.related_entity AND " +
        "entity_with_relationship.id "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
        ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND ENTITY_WITH_RELATIONSHIP.ID
            )
        ))
      .isEqualTo(expected)

    expected = expectedBase +
        "WHERE entity_with_relationship.related_entity BETWEEN entity_with_relationship.related_entity AND " +
        "entity_with_relationship.related_entity "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
        ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
            )
        ))
      .isEqualTo(expected)

    expected = expectedBase +
        "WHERE entity_with_relationship.related_entity BETWEEN entity_with_relationship.related_entity AND ? "
    (SELECT
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (
        ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY BETWEEN (
            ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND simpleMutableEntityId
            )
        ))
      .isEqualTo(expected, simpleMutableEntityId.toString())
  }

  @Test
  fun betweenComplexAliased() {
    val expectedBase = "SELECT * FROM main.entity_with_relationship AS m "
    val simpleMutableEntity = SimpleMutableEntity(id = 101L)
    val simpleMutableEntity2 = SimpleMutableEntity(id = 202L)
    val simpleMutableEntityId = checkNotNull(simpleMutableEntity.id)
    val simpleMutableEntityId2 = checkNotNull(simpleMutableEntity2.id)
    val a = SIMPLE_MUTABLE_ENTITY AS "a"
    val m = ENTITY_WITH_RELATIONSHIP AS "m"

    var expected = expectedBase + "WHERE m.related_entity BETWEEN ? AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND simpleMutableEntityId2)))
      .isEqualTo(expected, simpleMutableEntityId.toString(), simpleMutableEntityId2.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN ? AND simple_mutable_entity.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND SIMPLE_MUTABLE_ENTITY.ID)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN ? AND a.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (simpleMutableEntityId AND a.ID)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN simple_mutable_entity.id AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (SIMPLE_MUTABLE_ENTITY.ID AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN a.id AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (a.ID AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN simple_mutable_entity.id AND m.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (SIMPLE_MUTABLE_ENTITY.ID AND m.ID)))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity BETWEEN a.id AND m.id "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (a.ID AND m.ID)))
      .isEqualTo(expected)

    expected = expectedBase +
        "WHERE m.related_entity BETWEEN entity_with_relationship.related_entity AND m.related_entity "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND m.RELATED_ENTITY)))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity BETWEEN m.related_entity AND m.related_entity "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (m.RELATED_ENTITY AND m.RELATED_ENTITY)))
      .isEqualTo(expected)

    expected = expectedBase + "WHERE m.related_entity BETWEEN entity_with_relationship.related_entity AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())

    expected = expectedBase + "WHERE m.related_entity BETWEEN m.related_entity AND ? "
    (SELECT
        FROM m
        WHERE (m.RELATED_ENTITY BETWEEN (m.RELATED_ENTITY AND simpleMutableEntityId)))
      .isEqualTo(expected, simpleMutableEntityId.toString())
  }

  @Test
  fun rawExpr() {
    var sql = "simple_mutable_entity.value IS NOT NULL"
    sql.expr.isEqualTo(sql)

    sql = "simple_mutable_entity.value = ?"
    sql.expr("asd").isEqualTo(sql, "asd")

    sql = "simple_mutable_entity.value = ? AND simple_mutable_entity.value != ?"
    sql.expr("asd", "dsa").isEqualTo(sql, "asd", "dsa")
  }

  @Test
  fun rawExprWithNonRawExpr() {
    var sql = "simple_mutable_entity.value = ?"
    var expr = (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "dsa") AND sql.expr("asd")
    expr.isEqualTo("(simple_mutable_entity.value!=? AND $sql)", "dsa", "asd")

    sql = "simple_mutable_entity.value = ?"
    expr = sql.expr("asd") AND (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "dsa")
    expr.isEqualTo("($sql AND simple_mutable_entity.value!=?)", "asd", "dsa")

    sql = "simple_mutable_entity.value IS NOT NULL"
    expr = SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd" AND sql.expr
    expr.isEqualTo("(simple_mutable_entity.value!=? AND $sql)", "asd")

    sql = "simple_mutable_entity.value IS NOT NULL"
    expr = sql.expr AND (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd")
    expr.isEqualTo("($sql AND simple_mutable_entity.value!=?)", "asd")
  }
}
