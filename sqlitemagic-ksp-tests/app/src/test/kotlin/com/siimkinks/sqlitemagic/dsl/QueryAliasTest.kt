package com.siimkinks.sqlitemagic.dsl

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.COLUMNS
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.Companion.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.DSLTests
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.LEFT_JOIN
import com.siimkinks.sqlitemagic.MagazineTable.Companion.MAGAZINE
import com.siimkinks.sqlitemagic.ON
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.compiledSql
import org.junit.jupiter.api.Test

class QueryAliasTest : DSLTests {
  @Test
  fun automaticAliasesAreDeterministicAndMinimal() {
    val firstSql = selectImmutableIds().compiledSql()
    val secondSql = selectImmutableIds().compiledSql()

    assertThat(firstSql).isEqualTo(secondSql)
    assertThat(firstSql).isEqualTo(
      """
      SELECT immutable_value_with_fields.id,sm_0.id FROM complex_object_with_same_leafs
      LEFT JOIN immutable_value_with_fields ON
      complex_object_with_same_leafs.simple_value=immutable_value_with_fields.id
      LEFT JOIN immutable_value_with_fields AS sm_0 ON
      complex_object_with_same_leafs.simple_value_duplicate=sm_0.id
      """.asCompiledSql()
    )
  }

  @Test
  fun compatibleUserJoinIsReusedBeforeCanonicalAutomaticJoin() {
    val leaf = IMMUTABLE_VALUE_WITH_FIELDS AS "leaf"
    val sql = (SELECT
        COLUMNS arrayOf(IMMUTABLE_VALUE_WITH_FIELDS.ID)
        FROM COMPLEX_OBJECT_WITH_SAME_LEAFS
        LEFT_JOIN (leaf ON (COMPLEX_OBJECT_WITH_SAME_LEAFS.SIMPLE_VALUE IS leaf.ID)))
      .compiledSql()

    assertThat(sql).isEqualTo(
      """
      SELECT immutable_value_with_fields.id FROM complex_object_with_same_leafs
      LEFT JOIN immutable_value_with_fields AS leaf ON
      complex_object_with_same_leafs.simple_value=leaf.id
      LEFT JOIN immutable_value_with_fields ON
      complex_object_with_same_leafs.simple_value_duplicate=immutable_value_with_fields.id
      """.asCompiledSql()
    )
  }

  @Test
  fun userAliasCollisionUsesNextAutomaticAlias() {
    val magazine = MAGAZINE AS "sm_0"
    val sql = (SELECT
        COLUMNS arrayOf(IMMUTABLE_VALUE_WITH_FIELDS.ID)
        FROM COMPLEX_OBJECT_WITH_SAME_LEAFS
        LEFT_JOIN (magazine ON (COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE IS magazine.ID)))
      .compiledSql()

    assertThat(sql).isEqualTo(
      """
      SELECT immutable_value_with_fields.id,sm_1.id FROM complex_object_with_same_leafs
      LEFT JOIN magazine AS sm_0 ON
      complex_object_with_same_leafs.magazine=sm_0.id
      LEFT JOIN immutable_value_with_fields ON
      complex_object_with_same_leafs.simple_value=immutable_value_with_fields.id
      LEFT JOIN immutable_value_with_fields AS sm_1 ON
      complex_object_with_same_leafs.simple_value_duplicate=sm_1.id
      """.asCompiledSql()
    )
  }

  private fun selectImmutableIds() =
    (SELECT
        COLUMNS arrayOf(IMMUTABLE_VALUE_WITH_FIELDS.ID)
        FROM COMPLEX_OBJECT_WITH_SAME_LEAFS)

  private fun String.asCompiledSql() =
    trimIndent()
      .lines()
      .joinToString(
        separator = " ",
        postfix = " "
      )
}
