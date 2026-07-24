package com.siimkinks.sqlitemagic;

import androidx.annotation.NonNull;

import static com.siimkinks.sqlitemagic.Utils.LONG_PARSER;

final class ComplexColumnTestFixtures {
  static final Table<Article> ARTICLE = new Table<>("article", null, 2);
  static final AccountNumericColumn ACCOUNT = new AccountNumericColumn();

  private ComplexColumnTestFixtures() {
  }

  static final class Article {
  }

  record AccountId(long value) {
  }

  static final class AccountNumericColumn extends ComplexNumericColumn<AccountId, AccountId, Number, Article, NotNullable> {
    AccountNumericColumn() {
      super(ARTICLE, "account", false, LONG_PARSER, false, null);
    }

    @NonNull
    @Override
    String toSqlArg(AccountId value) {
      return Long.toString(value.value + 1L);
    }
  }
}
