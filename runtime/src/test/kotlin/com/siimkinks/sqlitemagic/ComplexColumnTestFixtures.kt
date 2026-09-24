package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.Utils.LONG_PARSER

internal object ComplexColumnTestFixtures {
  val ARTICLE: Table<Article> = testTable(
    name = "article",
    alias = null,
    nrOfColumns = 2
  )
  val ACCOUNT = AccountNumericColumn()

  class Article

  data class AccountId(val value: Long)

  class AccountNumericColumn : ComplexNumericColumn<AccountId, AccountId, Number, Article, NotNullable>(
    table = ARTICLE,
    name = "account",
    valueParser = LONG_PARSER,
    nullable = false,
    alias = null
  ) {
    override fun toSqlArg(value: AccountId) = (value.value + 1L).toString()
  }
}
