package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.ACCOUNT
import com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.ARTICLE
import com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.AccountId
import com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.Article
import com.siimkinks.sqlitemagic.Utils.STRING_PARSER
import org.junit.Test

class UpdateComplexNumericColumnTest {
  private val title: Column<String, String, CharSequence, Article, NotNullable> = Column(
    table = ARTICLE,
    name = "title",
    valueParser = STRING_PARSER,
    nullable = false,
    alias = null
  )

  @Test
  fun `initial relationship assignment uses declared ID type and transformer`() {
    val update = Update
      .table(ARTICLE)
      .set(ACCOUNT, AccountId(41L))

    assertUpdate(
      update = update,
      expectedSql = "UPDATE article SET account=? ",
      "42"
    )
  }

  @Test
  fun `chained relationship assignment uses declared ID type and transformer`() {
    val update = Update
      .table(ARTICLE)
      .set(title, "Title")
      .set(ACCOUNT, AccountId(41L))

    assertUpdate(
      update = update,
      expectedSql = "UPDATE article SET title=?,account=? ",
      "Title",
      "42"
    )
  }

  @Test
  fun `typed then raw assignments preserve SQL and argument order`() {
    val update = Update
      .table(ARTICLE)
      .set(title, "Title")
      .set("account", "42")

    assertUpdate(
      update = update,
      expectedSql = "UPDATE article SET title=?,account=? ",
      "Title",
      "42"
    )
  }

  @Test
  fun `raw then typed assignments preserve SQL and argument order`() {
    val update = Update
      .table(ARTICLE)
      .set("title", "Title")
      .set(ACCOUNT, AccountId(41L))

    assertUpdate(
      update = update,
      expectedSql = "UPDATE article SET title=?,account=? ",
      "Title",
      "42"
    )
  }

  private fun assertUpdate(
    update: Update.Set<Article>,
    expectedSql: String,
    vararg expectedArgs: String
  ) {
    assertThat(SqlCreator.getSql(update, update.updateBuilder.sqlNodeCount))
      .isEqualTo(expectedSql)
    assertThat(update.updateBuilder.args)
      .containsExactlyElementsIn(expectedArgs)
      .inOrder()
  }
}
