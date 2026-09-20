package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.ACCOUNT
import com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.AccountId
import com.siimkinks.sqlitemagic.SqlTestAssertions.assertExpression
import org.junit.Test

class ComplexNumericColumnTest {
  @Test
  fun `numeric affinity includes numeric operations for transformed ID`() {
    assertExpression(
      expression = ACCOUNT.greaterThan(AccountId(41L)),
      expectedSql = "article.account>?",
      "42"
    )
    assertExpression(
      expression = ACCOUNT.between(AccountId(1L)).and(AccountId(9L)),
      expectedSql = "article.account BETWEEN ? AND ?",
      "2",
      "10"
    )
    assertThat(Select.sum(ACCOUNT)).isInstanceOf(NumericColumn::class.java)
  }
}
