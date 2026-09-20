package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat

object SqlTestAssertions {
  @JvmStatic
  fun assertExpression(
    expression: Expr,
    expectedSql: String,
    vararg expectedArgs: String
  ) {
    val sql = StringBuilder()
    val args = ArrayList<String>()
    expression.appendToSql(sql)
    expression.addArgs(args)

    assertThat(sql.toString()).isEqualTo(expectedSql)
    assertThat(args).containsExactlyElementsIn(expectedArgs).inOrder()
  }
}
