package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class QueryAliasContextTest {
  @Test
  fun `first canonical occurrence uses canonical table`() {
    val rootTable = Table<Any>("root", null, 1)
    val canonicalTable = Table<Any>("child", null, 1)
    val context = QueryAliasContext(
      rootTable = rootTable,
      joins = emptyList()
    )

    val actual = context.tableForAutomaticJoin(canonicalTable)

    assertThat(actual).isSameInstanceAs(canonicalTable)
    assertTable(
      table = actual,
      expectedName = "child",
      expectedAlias = null,
      expectedNameInQuery = "child"
    )
  }

  @Test
  fun `repeated canonical table uses deterministic aliases`() {
    val rootTable = Table<Any>("root", null, 1)
    val canonicalTable = Table<Any>("child", null, 1)
    val context = QueryAliasContext(
      rootTable = rootTable,
      joins = emptyList()
    )

    val first = context.tableForAutomaticJoin(canonicalTable)
    val second = context.tableForAutomaticJoin(canonicalTable)
    val third = context.tableForAutomaticJoin(canonicalTable)

    assertThat(listOf(first.nameInQuery, second.nameInQuery, third.nameInQuery))
      .containsExactly("child", "sm_0", "sm_1")
      .inOrder()
    assertTable(
      table = second,
      expectedName = "child",
      expectedAlias = "sm_0",
      expectedNameInQuery = "sm_0"
    )
    assertTable(
      table = third,
      expectedName = "child",
      expectedAlias = "sm_1",
      expectedNameInQuery = "sm_1"
    )
  }

  @Test
  fun `self join avoids root identifier`() {
    val rootTable = Table<Any>("node", null, 1)
    val context = QueryAliasContext(
      rootTable = rootTable,
      joins = emptyList()
    )

    val actual = context.tableForAutomaticJoin(rootTable)

    assertTable(
      table = actual,
      expectedName = "node",
      expectedAlias = "sm_0",
      expectedNameInQuery = "sm_0"
    )
    assertTable(
      table = rootTable,
      expectedName = "node",
      expectedAlias = null,
      expectedNameInQuery = "node"
    )
  }

  @Test
  fun `aliased root leaves its physical canonical name available`() {
    val rootTable = Table<Any>("node", "root_alias", 1)
    val canonicalTable = Table<Any>("node", null, 1)
    val context = QueryAliasContext(
      rootTable = rootTable,
      joins = emptyList()
    )

    val actual = context.tableForAutomaticJoin(canonicalTable)

    assertThat(actual).isSameInstanceAs(canonicalTable)
    assertTable(
      table = actual,
      expectedName = "node",
      expectedAlias = null,
      expectedNameInQuery = "node"
    )
  }

  @Test
  fun `existing unaliased join reserves target physical name`() {
    val rootTable = Table<Any>("root", null, 1)
    val existingJoin = Table<Any>("target", null, 1)
    val context = QueryAliasContext(
      rootTable = rootTable,
      joins = listOf(JoinClause(existingJoin, "", null))
    )

    val actual = context.tableForAutomaticJoin(Table<Any>("target", null, 1))

    assertTable(
      table = actual,
      expectedName = "target",
      expectedAlias = "sm_0",
      expectedNameInQuery = "sm_0"
    )
  }

  @Test
  fun `ordinary user alias equal to target canonical name is reserved`() {
    val rootTable = Table<Any>("root", null, 1)
    val userAlias = Table<Any>("other", "target", 1)
    val context = QueryAliasContext(
      rootTable = rootTable,
      joins = listOf(JoinClause(userAlias, "", null))
    )

    val actual = context.tableForAutomaticJoin(Table<Any>("target", null, 1))

    assertTable(
      table = actual,
      expectedName = "target",
      expectedAlias = "sm_0",
      expectedNameInQuery = "sm_0"
    )
  }

  @Test
  fun `pre-reserved aliases advance to the lowest available alias`() {
    val rootTable = Table<Any>("target", null, 1)
    val smZero = Table<Any>("other_zero", "sm_0", 1)
    val smOne = Table<Any>("other_one", "sm_1", 1)
    val context = QueryAliasContext(
      rootTable = rootTable,
      joins = listOf(
        JoinClause(smZero, "", null),
        JoinClause(smOne, "", null)
      )
    )

    val actual = context.tableForAutomaticJoin(Table<Any>("target", null, 1))

    assertTable(
      table = actual,
      expectedName = "target",
      expectedAlias = "sm_2",
      expectedNameInQuery = "sm_2"
    )
  }

  @Test
  fun `user alias sm zero is reserved`() {
    val rootTable = Table<Any>("root", null, 1)
    val userAlias = Table<Any>("other", "sm_0", 1)
    val joins = arrayListOf(JoinClause(userAlias, "", null))
    val context = QueryAliasContext(
      rootTable = rootTable,
      joins = joins
    )

    val actual = context.tableForAutomaticJoin(rootTable)

    assertTable(
      table = actual,
      expectedName = "root",
      expectedAlias = "sm_1",
      expectedNameInQuery = "sm_1"
    )
  }

  @Test
  fun `independent contexts produce identical aliases`() {
    val firstRoot = Table<Any>("root", null, 1)
    val secondRoot = Table<Any>("root", null, 1)
    val firstCanonical = Table<Any>("child", null, 1)
    val secondCanonical = Table<Any>("child", null, 1)
    val firstContext = QueryAliasContext(
      rootTable = firstRoot,
      joins = emptyList()
    )
    val secondContext = QueryAliasContext(
      rootTable = secondRoot,
      joins = emptyList()
    )

    val firstNames = listOf(
      firstContext.tableForAutomaticJoin(firstCanonical).nameInQuery,
      firstContext.tableForAutomaticJoin(firstCanonical).nameInQuery
    )
    val secondNames = listOf(
      secondContext.tableForAutomaticJoin(secondCanonical).nameInQuery,
      secondContext.tableForAutomaticJoin(secondCanonical).nameInQuery
    )

    assertThat(firstNames).containsExactlyElementsIn(secondNames).inOrder()
    assertThat(firstNames).containsExactly("child", "sm_0").inOrder()
  }

  private fun assertTable(
    table: Table<*>,
    expectedName: String,
    expectedAlias: String?,
    expectedNameInQuery: String
  ) {
    assertThat(table.name).isEqualTo(expectedName)
    assertThat(table.alias).isEqualTo(expectedAlias)
    assertThat(table.nameInQuery).isEqualTo(expectedNameInQuery)
  }
}
