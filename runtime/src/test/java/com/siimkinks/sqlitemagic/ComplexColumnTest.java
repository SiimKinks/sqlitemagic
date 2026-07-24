package com.siimkinks.sqlitemagic;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.Arrays;

import static com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.ARTICLE;
import static com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.Article;
import static com.siimkinks.sqlitemagic.SqlTestAssertions.assertExpression;
import static com.siimkinks.sqlitemagic.Utils.STRING_PARSER;

public final class ComplexColumnTest {
  @Test
  public void textAffinityUsesDeclaredIdTypeAndTransformer() {
    final ComplexColumn<AccountId, AccountId, CharSequence, Article, NotNullable> column =
        new AccountTextColumn();

    assertExpression(column.is(new AccountId("first")), "article.account=?", "db:first");
    assertExpression(
        column.in(Arrays.asList(new AccountId("first"), new AccountId("second"))),
        "article.account IN (?,?)",
        "db:first",
        "db:second"
    );
  }

  @Test
  public void genericComplexColumnRetainsNullPredicates() {
    final ComplexColumn<AccountId, AccountId, CharSequence, Article, Nullable> column =
        new NullableAccountTextColumn();

    assertExpression(column.isNull(), "article.account IS NULL");
    assertExpression(column.isNotNull(), "article.account IS NOT NULL");
  }

  private record AccountId(String value) {
  }

  private static class AccountTextColumn extends ComplexColumn<AccountId, AccountId, CharSequence, Article, NotNullable> {
    AccountTextColumn() {
      super(ARTICLE, "account", false, STRING_PARSER, false, null);
    }

    @NonNull
    @Override
    String toSqlArg(AccountId value) {
      return "db:" + value.value;
    }
  }

  private static final class NullableAccountTextColumn extends ComplexColumn<AccountId, AccountId, CharSequence, Article, Nullable> {
    NullableAccountTextColumn() {
      super(ARTICLE, "account", false, STRING_PARSER, true, null);
    }

    @NonNull
    @Override
    String toSqlArg(AccountId value) {
      return "db:" + value.value;
    }
  }
}
