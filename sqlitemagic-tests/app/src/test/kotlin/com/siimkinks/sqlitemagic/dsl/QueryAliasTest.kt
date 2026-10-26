package com.siimkinks.sqlitemagic.dsl

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.mapTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class QueryAliasTest {
  private val root = """"main"."complex_object_with_same_leafs""""
  private val leafJoin = """LEFT JOIN "main"."immutable_value_with_fields" ON """ +
      """"complex_object_with_same_leafs"."simple_value" = "immutable_value_with_fields"."id""""

  private fun duplicateJoin(alias: String, table: String = alias): String {
    val aliasClause = when (alias) {
      table -> ""
      else -> """ AS "$alias""""
    }
    return """LEFT JOIN "main"."immutable_value_with_fields"$aliasClause ON """ +
        """"complex_object_with_same_leafs"."simple_value_duplicate" = "$alias"."id""""
  }

  private fun SelectQuery<*, *>.fromClause() = compile().sql.substringAfter(" FROM ")

  @Test
  fun `automatic join names are deterministic and follow the relationship path`() {
    val firstSql = (SELECT FROM COMPLEX_OBJECT_WITH_SAME_LEAFS).fromClause()
    val secondSql = (SELECT FROM COMPLEX_OBJECT_WITH_SAME_LEAFS).fromClause()

    assertThat(firstSql).isEqualTo(secondSql)
    assertThat(firstSql).isEqualTo(
      "$root $leafJoin " +
          duplicateJoin(
            alias = "simple_value_duplicate",
            table = "immutable_value_with_fields"
          )
    )
  }

  @Test
  fun `an exact user join is reused before the automatic join`() {
    val leaf = IMMUTABLE_VALUE_WITH_FIELDS AS "leaf"
    val sql = (SELECT
        FROM COMPLEX_OBJECT_WITH_SAME_LEAFS
        LEFT_JOIN leaf
        ON (COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE IS leaf.ID))
      .fromClause()

    assertThat(sql).isEqualTo(
      """$root LEFT JOIN "main"."immutable_value_with_fields" AS "leaf" ON """ +
          """"complex_object_with_same_leafs"."simple_value" = "leaf"."id" """ +
          duplicateJoin("immutable_value_with_fields")
    )
  }

  @Test
  fun `a user alias collision takes the next numbered name`() {
    val entity = ENTITY_WITH_RELATIONSHIP AS "simple_value_duplicate"
    val sql = (SELECT
        FROM COMPLEX_OBJECT_WITH_SAME_LEAFS
        LEFT_JOIN entity
        ON (COMPLEX_OBJECT_WITH_SAME_LEAFS.ENTITY_WITH_RELATIONSHIP IS entity.ID))
      .fromClause()

    assertThat(sql).isEqualTo(
      """$root LEFT JOIN "main"."entity_with_relationship" AS "simple_value_duplicate" ON """ +
          """"complex_object_with_same_leafs"."entity_with_relationship" = "simple_value_duplicate"."id" """ +
          "$leafJoin " +
          duplicateJoin(
            alias = "simple_value_duplicate_2",
            table = "immutable_value_with_fields"
          )
    )
  }

  @Test
  fun `an unrelated same-table user join keeps both automatic joins`() {
    val unrelatedLeaf = IMMUTABLE_VALUE_WITH_FIELDS AS "unrelated_leaf"
    val sql = SELECT
      .FROM(COMPLEX_OBJECT_WITH_SAME_LEAFS)
      .LEFT_JOIN(unrelatedLeaf)
      .ON(unrelatedLeaf.INTEGER IS -1)
      .fromClause()

    assertThat(sql).isEqualTo(
      """$root LEFT JOIN "main"."immutable_value_with_fields" AS "unrelated_leaf" ON """ +
          """"unrelated_leaf"."integer" = ? $leafJoin """ +
          duplicateJoin(
            alias = "simple_value_duplicate",
            table = "immutable_value_with_fields"
          )
    )
  }

  @Test
  fun `partial mappings read the same table through the exact join of each relationship`() {
    val leaf = IMMUTABLE_VALUE_WITH_FIELDS AS "leaf"
    val duplicate = IMMUTABLE_VALUE_WITH_FIELDS AS "duplicate"
    val both = SELECT
      .COLUMNS(COMPLEX_OBJECT_WITH_SAME_LEAFS.all(), leaf.all(), duplicate.all())
      .FROM(COMPLEX_OBJECT_WITH_SAME_LEAFS)
      .INNER_JOIN(leaf)
      .ON(COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE IS leaf.ID)
      .INNER_JOIN(duplicate)
      .ON(COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE_DUPLICATE IS duplicate.ID)
    val oneMissing = SELECT
      .COLUMNS(COMPLEX_OBJECT_WITH_SAME_LEAFS.all(), leaf.all())
      .FROM(COMPLEX_OBJECT_WITH_SAME_LEAFS)
      .INNER_JOIN(leaf)
      .ON(COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE IS leaf.ID)
      .mapTo(COMPLEX_OBJECT_WITH_SAME_LEAFS)

    assertThat(both.mapTo(COMPLEX_OBJECT_WITH_SAME_LEAFS).fromClause()).isEqualTo(both.fromClause())
    assertThat(both.fromClause()).isEqualTo(
      """$root INNER JOIN "main"."immutable_value_with_fields" AS "leaf" ON """ +
          """"complex_object_with_same_leafs"."simple_value" = "leaf"."id" """ +
          """INNER JOIN "main"."immutable_value_with_fields" AS "duplicate" ON """ +
          """"complex_object_with_same_leafs"."simple_value_duplicate" = "duplicate"."id""""
    )
    assertThat(assertThrows<IllegalArgumentException> { oneMissing.compile() })
      .hasMessageThat()
      .isEqualTo(
        "ComplexObjectWithSameLeafs.simpleValueDuplicate is required; select its immutable_value_with_fields fields"
      )
  }
}
