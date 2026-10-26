package com.siimkinks.sqlitemagic.dsl

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.CONCAT
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.OR
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.TRIM
import com.siimkinks.sqlitemagic.expr
import org.junit.jupiter.api.Test

internal class SelectPredicateSqlTest {
  private fun assertWhereConditions(
    table: EntityWithRelationshipTable,
    select: String,
    qualifier: String
  ) {
    val value = """"$qualifier"."value""""
    val count = """"$qualifier"."count""""
    val titleIs = table.VALUE IS "asd"
    val intIs = table.COUNT IS 1920
    val titleIsNot = table.VALUE IS_NOT "asd"
    val titleIsNotNull = table.VALUE.IS_NOT_NULL()
    val titleIsNull = table.VALUE.IS_NULL()
    val titleGlob = table.VALUE GLOB "asd"
    val titleLike = table.VALUE LIKE "asd"
    val lessThan = table.COUNT LESS_THAN 1990
    val greaterThan = table.COUNT GREATER_THAN 1990
    val between = table.COUNT BETWEEN 1910 AND 2000
    val notBetween = table.COUNT NOT_BETWEEN 1910 AND 2000
    val `in` = table.COUNT IN arrayOf(1910, 1999, 1920)
    val notIn = table.COUNT NOT_IN arrayOf(1910, 1999, 1920)
    val oneIn = table.COUNT IN arrayOf(1910)
    val oneNotIn = table.COUNT NOT_IN arrayOf(1910)

    assertEachSql(
      query = { SELECT FROM table WHERE it },
      expected = { "$select WHERE $it" },
      cases = listOf(
        titleIs to "$value = ?",
        (titleIs AND intIs) to "($value = ?) AND ($count = ?)",
        between to "$count BETWEEN ? AND ?",
        notBetween to "$count NOT BETWEEN ? AND ?",
        `in` to "$count IN (?, ?, ?)",
        ((((((((titleIsNot AND titleIsNotNull) AND titleIsNull) AND titleGlob) AND titleLike) AND lessThan)
            AND greaterThan) AND between) AND notBetween) to
            "(((((((($value != ?) AND ($value IS NOT NULL)) AND ($value IS NULL)) AND ($value GLOB ?)) " +
            "AND ($value LIKE ?)) AND ($count < ?)) AND ($count > ?)) AND ($count BETWEEN ? AND ?)) " +
            "AND ($count NOT BETWEEN ? AND ?)",
        (((`in` AND notIn) AND oneIn) AND oneNotIn) to
            "((($count IN (?, ?, ?)) AND ($count NOT IN (?, ?, ?))) AND ($count IN (?))) AND ($count NOT IN (?))",
        (`in` OR titleIsNot) to "($count IN (?, ?, ?)) OR ($value != ?)",
        (`in` AND (titleIsNot AND titleIsNotNull)) to
            "($count IN (?, ?, ?)) AND (($value != ?) AND ($value IS NOT NULL))",
        (`in` OR (between AND titleIsNotNull)) to
            "($count IN (?, ?, ?)) OR (($count BETWEEN ? AND ?) AND ($value IS NOT NULL))",
        (`in` AND ((titleIsNot OR titleIsNotNull) OR titleIsNull)) to
            "($count IN (?, ?, ?)) AND ((($value != ?) OR ($value IS NOT NULL)) OR ($value IS NULL))"
      )
    )
  }

  @Test
  fun `WHERE conditions render SQL`() = assertWhereConditions(
    table = ENTITY_WITH_RELATIONSHIP,
    select = selectAll("entity_with_relationship"),
    qualifier = "entity_with_relationship"
  )

  @Test
  fun `WHERE conditions render SQL when aliased`() = assertWhereConditions(
    table = ENTITY_WITH_RELATIONSHIP AS "m",
    select = """${selectAll("m")} AS "m"""",
    qualifier = "m"
  )

  @Test
  fun `column is not redefined when aliased`() {
    val value = """"entity_with_relationship"."value""""
    val search = """($value || ?) || "entity_with_relationship"."count""""
    val searchColumn =
      (ENTITY_WITH_RELATIONSHIP.VALUE CONCAT " " CONCAT ENTITY_WITH_RELATIONSHIP.COUNT) AS "search_column"
    SELECT
      .COLUMNS(ENTITY_WITH_RELATIONSHIP.all(), searchColumn)
      .FROM(ENTITY_WITH_RELATIONSHIP)
      .WHERE(ENTITY_WITH_RELATIONSHIP.VALUE IS searchColumn)
      .assertSql(
        """SELECT "entity_with_relationship"."id", $value, "entity_with_relationship"."related_entity", """ +
            """"entity_with_relationship"."count", $search AS "search_column" """ +
            """FROM "main"."entity_with_relationship" WHERE $value = ($search)"""
      )

    val trimmedTitle = TRIM(ENTITY_WITH_RELATIONSHIP.VALUE) AS "trimmed_title"
    (SELECT
        COLUMN trimmedTitle
        FROM ENTITY_WITH_RELATIONSHIP
        WHERE (TRIM(ENTITY_WITH_RELATIONSHIP.VALUE) IS trimmedTitle))
      .assertSql(
        """SELECT TRIM($value) AS "trimmed_title" FROM "main"."entity_with_relationship" """ +
            "WHERE TRIM($value) = TRIM($value)"
      )
  }

  @Test
  fun `complex BETWEEN renders SQL`() {
    val related = ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY
    val relatedSql = """"entity_with_relationship"."related_entity""""
    val idSql = """"entity_with_relationship"."id""""
    val simpleIdSql = """"simple_mutable_entity"."id""""
    val relatedId = 101L
    val relatedId2 = 202L
    val select = selectAll("entity_with_relationship")

    assertEachSql(
      query = { SELECT FROM ENTITY_WITH_RELATIONSHIP WHERE it },
      expected = { "$select WHERE $it" },
      cases = listOf(
        (related BETWEEN relatedId AND relatedId2) to "$relatedSql BETWEEN ? AND ?",
        (related BETWEEN related AND ENTITY_WITH_RELATIONSHIP.ID) to "$relatedSql BETWEEN $relatedSql AND $idSql",
        (related BETWEEN related AND related) to "$relatedSql BETWEEN $relatedSql AND $relatedSql",
        (related BETWEEN related AND relatedId) to "$relatedSql BETWEEN $relatedSql AND ?"
      )
    )
    assertEachSql(
      query = { SELECT FROM ENTITY_WITH_RELATIONSHIP INNER_JOIN SIMPLE_MUTABLE_ENTITY WHERE it },
      expected = { """$select INNER JOIN "main"."simple_mutable_entity" WHERE $it""" },
      cases = listOf(
        (related BETWEEN relatedId AND SIMPLE_MUTABLE_ENTITY.ID) to "$relatedSql BETWEEN ? AND $simpleIdSql",
        (related BETWEEN SIMPLE_MUTABLE_ENTITY.ID AND relatedId) to "$relatedSql BETWEEN $simpleIdSql AND ?",
        (related BETWEEN SIMPLE_MUTABLE_ENTITY.ID AND ENTITY_WITH_RELATIONSHIP.ID) to
            "$relatedSql BETWEEN $simpleIdSql AND $idSql"
      )
    )
  }

  @Test
  fun `complex BETWEEN renders SQL when aliased`() {
    val relatedId = 101L
    val relatedId2 = 202L
    val a = SIMPLE_MUTABLE_ENTITY AS "a"
    val m = ENTITY_WITH_RELATIONSHIP AS "m"
    val related = m.RELATED_ENTITY
    val relatedSql = """"m"."related_entity""""
    val select = """${selectAll("m")} AS "m""""

    assertEachSql(
      query = { SELECT FROM m WHERE it },
      expected = { "$select WHERE $it" },
      cases = listOf(
        (related BETWEEN relatedId AND relatedId2) to "$relatedSql BETWEEN ? AND ?",
        (related BETWEEN related AND related) to "$relatedSql BETWEEN $relatedSql AND $relatedSql",
        (related BETWEEN related AND relatedId) to "$relatedSql BETWEEN $relatedSql AND ?"
      )
    )
    assertEachSql(
      query = { SELECT FROM m INNER_JOIN SIMPLE_MUTABLE_ENTITY WHERE it },
      expected = { """$select INNER JOIN "main"."simple_mutable_entity" WHERE $it""" },
      cases = listOf(
        (related BETWEEN relatedId AND SIMPLE_MUTABLE_ENTITY.ID) to
            """$relatedSql BETWEEN ? AND "simple_mutable_entity"."id"""",
        (related BETWEEN SIMPLE_MUTABLE_ENTITY.ID AND relatedId) to
            """$relatedSql BETWEEN "simple_mutable_entity"."id" AND ?""",
        (related BETWEEN SIMPLE_MUTABLE_ENTITY.ID AND m.ID) to
            """$relatedSql BETWEEN "simple_mutable_entity"."id" AND "m"."id""""
      )
    )
    assertEachSql(
      query = { SELECT FROM m INNER_JOIN a WHERE it },
      expected = { """$select INNER JOIN "main"."simple_mutable_entity" AS "a" WHERE $it""" },
      cases = listOf(
        (related BETWEEN relatedId AND a.ID) to """$relatedSql BETWEEN ? AND "a"."id"""",
        (related BETWEEN a.ID AND relatedId) to """$relatedSql BETWEEN "a"."id" AND ?""",
        (related BETWEEN a.ID AND m.ID) to """$relatedSql BETWEEN "a"."id" AND "m"."id""""
      )
    )
    assertEachSql(
      query = { SELECT FROM m INNER_JOIN ENTITY_WITH_RELATIONSHIP WHERE it },
      expected = { """$select INNER JOIN "main"."entity_with_relationship" WHERE $it""" },
      cases = listOf(
        (related BETWEEN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND related) to
            """$relatedSql BETWEEN "entity_with_relationship"."related_entity" AND $relatedSql""",
        (related BETWEEN ENTITY_WITH_RELATIONSHIP.RELATED_ENTITY AND relatedId) to
            """$relatedSql BETWEEN "entity_with_relationship"."related_entity" AND ?"""
      )
    )
  }

  @Test
  fun `raw expressions render SQL`() {
    val notNull = "simple_mutable_entity.value IS NOT NULL"
    val bound = "simple_mutable_entity.value = ?"
    val twoBound = "simple_mutable_entity.value = ? AND simple_mutable_entity.value != ?"

    assertEachSql(
      query = { SELECT FROM SIMPLE_MUTABLE_ENTITY WHERE it },
      expected = { "$SIMPLE_MUTABLE_ENTITY_SELECT WHERE $it" },
      cases = listOf(
        notNull.expr(BOOLEAN) to notNull,
        bound.expr(BOOLEAN, "asd") to bound,
        twoBound.expr(BOOLEAN, "asd", "dsa") to twoBound
      )
    )
  }

  @Test
  fun `raw expressions with non-raw expressions render SQL`() {
    val value = """"simple_mutable_entity"."value""""
    val bound = "simple_mutable_entity.value = ?"
    val notNull = "simple_mutable_entity.value IS NOT NULL"

    assertEachSql(
      query = { SELECT FROM SIMPLE_MUTABLE_ENTITY WHERE it },
      expected = { "$SIMPLE_MUTABLE_ENTITY_SELECT WHERE $it" },
      cases = listOf(
        ((SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "dsa") AND bound.expr(BOOLEAN, "asd")) to
            "($value != ?) AND ($bound)",
        (bound.expr(BOOLEAN, "asd") AND (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "dsa")) to
            "($bound) AND ($value != ?)",
        (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd" AND notNull.expr(BOOLEAN)) to
            "($value != ?) AND ($notNull)",
        (notNull.expr(BOOLEAN) AND (SIMPLE_MUTABLE_ENTITY.VALUE IS_NOT "asd")) to
            "($notNull) AND ($value != ?)"
      )
    )
  }
}
