package com.siimkinks.sqlitemagic;

import java.util.ArrayList;

import static com.google.common.truth.Truth.assertThat;

final class SqlTestAssertions {
  private SqlTestAssertions() {
  }

  static void assertExpression(
      Expr expression,
      String expectedSql,
      String... expectedArgs
  ) {
    final StringBuilder sql = new StringBuilder();
    final ArrayList<String> args = new ArrayList<>();
    expression.appendToSql(sql);
    expression.addArgs(args);

    assertThat(sql.toString()).isEqualTo(expectedSql);
    assertThat(args).containsExactlyElementsIn(expectedArgs).inOrder();
  }
}
