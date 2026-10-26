package com.siimkinks.sqlitemagic.dsl

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.MutationQuery
import com.siimkinks.sqlitemagic.SelectQuery

internal const val SIMPLE_MUTABLE_ENTITY_SELECT =
  """SELECT "simple_mutable_entity"."id", "simple_mutable_entity"."value", """ +
      """"simple_mutable_entity"."boxed_boolean", "simple_mutable_entity"."primitive_boolean" """ +
      """FROM "main"."simple_mutable_entity""""

internal fun selectAll(qualifier: String) = """SELECT "$qualifier"."id", "$qualifier"."value", """ +
    """"$qualifier"."related_entity", "$qualifier"."count" FROM "main"."entity_with_relationship""""

internal fun SelectQuery<*, *>.assertSql(expected: String) = assertThat(compile().sql).isEqualTo(expected)

internal fun MutationQuery.assertSql(expected: String) = assertThat(compile().sql).isEqualTo(expected)

internal fun <T> assertEachSql(
  query: (T) -> SelectQuery<*, *>,
  expected: (String) -> String,
  cases: List<Pair<T, String>>
) = cases.forEach { (input, label) ->
  assertWithMessage(label)
    .that(
      query(input)
        .compile()
        .sql
    )
    .isEqualTo(expected(label))
}
