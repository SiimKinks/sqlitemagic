package com.siimkinks.sqlitemagic;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.ACCOUNT;
import static com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.AccountId;
import static com.siimkinks.sqlitemagic.SqlTestAssertions.assertExpression;

public final class ComplexNumericColumnTest {
  @Test
  public void numericAffinityIncludesNumericOperationsForTransformedId() {
    assertExpression(ACCOUNT.greaterThan(new AccountId(41L)), "article.account>?", "42");
    assertExpression(
        ACCOUNT.between(new AccountId(1L)).and(new AccountId(9L)),
        "article.account BETWEEN ? AND ?",
        "2",
        "10"
    );
    assertThat(Select.sum(ACCOUNT)).isInstanceOf(NumericColumn.class);
  }
}
