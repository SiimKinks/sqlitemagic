package com.siimkinks.sqlitemagic;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

public final class SqlUtilTest {
  @Test
  public void quotesSqlStringLiteral() {
    final String[][] testCases = {
        {"empty", "", "''"},
        {"ordinary", "hello", "'hello'"},
        {"single apostrophe", "O'Brien", "'O''Brien'"},
        {"multiple apostrophes", "'quoted'", "'''quoted'''"},
        {"other characters", "line\n\"two\"", "'line\n\"two\"'"}
    };

    for (String[] testCase : testCases) {
      assertWithMessage(testCase[0])
          .that(SqlUtil.quoteSqlStringLiteral(testCase[1]))
          .isEqualTo(testCase[2]);
    }
  }

  @Test
  public void quotesInlineStringLiteralExpressions() {
    final Column<String, String, CharSequence, ?, NotNullable> value = Select.asColumn("O'Brien");

    assertSql(value, "'O''Brien'");
    assertSql(value.replace("'", "x'y"), "replace('O''Brien','''','x''y')");
    assertSql(value.trim("'"), "trim('O''Brien','''')");
    assertSql(Select.groupConcat(value, "'|"), "group_concat('O''Brien','''|')");
    assertSql(Select.groupConcatDistinct(value, "'|"), "group_concat(DISTINCT 'O''Brien','''|')");
    assertSql(Select.format("%'s", value), "printf('%''s', 'O''Brien')");
  }

  private static void assertSql(Column<?, ?, ?, ?, ?> column, String expected) {
    final StringBuilder sql = new StringBuilder();
    column.appendSql(sql);
    assertThat(sql.toString()).isEqualTo(expected);
  }
}
