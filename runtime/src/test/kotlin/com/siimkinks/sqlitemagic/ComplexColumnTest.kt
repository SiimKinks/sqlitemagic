package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.ARTICLE
import com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.Article
import com.siimkinks.sqlitemagic.SqlTestAssertions.assertExpression
import org.junit.Test

class ComplexColumnTest {
  @Test
  fun `text affinity uses declared ID type and transformer`() {
    val column = AccountTextColumn()

    assertExpression(
      expression = column.`is`(AccountId("first")),
      expectedSql = "article.account=?",
      "db:first"
    )
    assertExpression(
      expression = column.`in`(listOf(AccountId("first"), AccountId("second"))),
      expectedSql = "article.account IN (?,?)",
      "db:first",
      "db:second"
    )
  }

  @Test
  fun `generic complex column retains null predicates`() {
    val column = NullableAccountTextColumn()

    assertExpression(
      expression = column.isNull(),
      expectedSql = "article.account IS NULL"
    )
    assertExpression(
      expression = column.isNotNull(),
      expectedSql = "article.account IS NOT NULL"
    )
  }

  private data class AccountId(val value: String)

  private class AccountTextColumn : ComplexColumn<AccountId, AccountId, CharSequence, Article, NotNullable>(
    table = ARTICLE,
    name = "account",
    valueParser = Utils.STRING_PARSER,
    nullable = false,
    alias = null
  ) {
    override fun toSqlArg(value: AccountId) = "db:${value.value}"
  }

  private class NullableAccountTextColumn :
    ComplexColumn<AccountId, AccountId, CharSequence, Article, Nullable>(
      table = ARTICLE,
      name = "account",
      valueParser = Utils.STRING_PARSER,
      nullable = true,
      alias = null
    ) {
    override fun toSqlArg(value: AccountId) = "db:${value.value}"
  }
}
