package com.siimkinks.sqlitemagic.runtime.contract.query

import android.database.Cursor
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AccountTable.Companion.ACCOUNT
import com.siimkinks.sqlitemagic.ArticleTable.Companion.ARTICLE
import com.siimkinks.sqlitemagic.AutomaticTransformedTable.Companion.AUTOMATIC_TRANSFORMED
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.fixture.model.Account
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import com.siimkinks.sqlitemagic.fixture.model.Article
import com.siimkinks.sqlitemagic.fixture.model.AutomaticTransformed
import com.siimkinks.sqlitemagic.fixture.model.SequenceId
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.queryDeep
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.insertedRowId
import com.siimkinks.sqlitemagic.runtime.support.readRows
import com.siimkinks.sqlitemagic.update
import org.junit.Rule
import org.junit.Test

internal class TransformedIdentityRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun reboundTransformedIdBindsPredicatesAndReadsDeclaredValueType() {
    val expected = Account(
      id = AccountId("rebound-account"),
      label = "rebound-account-label"
    )
    insertedResult(
      result = expected
        .insert()
        .execute(),
      modelName = "Account"
    )
    val reboundTable = ACCOUNT AS "rebound_account"

    assertThat(
      SELECT
        .FROM(reboundTable)
        .WHERE(reboundTable.ID IS expected.id)
        .execute()
    ).containsExactly(expected)
    assertThat(
      SELECT
        .COLUMN(reboundTable.ID)
        .FROM(reboundTable)
        .takeFirst()
        .execute()
    ).isEqualTo(expected.id)
  }

  @Test
  fun automaticTransformedInsertAssignsAndReconstructsTransformedId() {
    val value = AutomaticTransformed(
      id = SequenceId(0),
      value = "automatic-transformed-value"
    )
    val expected = value.copy(
      id = SequenceId(
        insertedRowId(
          result = value
            .insert()
            .execute(),
          modelName = "AutomaticTransformed"
        )
      )
    )
    val reboundTable = AUTOMATIC_TRANSFORMED AS "rebound_numeric"

    assertThat(
      SELECT
        .FROM(reboundTable)
        .WHERE(reboundTable.ID IS expected.id)
        .execute()
    ).containsExactly(expected)
    assertThat(
      SELECT
        .COLUMN(reboundTable.ID)
        .FROM(reboundTable)
        .takeFirst()
        .execute()
    ).isEqualTo(expected.id)

    assertThat(
      SELECT
        .RAW("SELECT id, typeof(id), value FROM automatic_transformed")
        .toCursor()
        .execute()
        .use(Cursor::readRows)
        .single()
    ).isEqualTo(listOf(expected.id.value.toString(), "integer", expected.value))
  }

  @Test
  fun articleUsesTransformedAccountKeyForStoragePredicatesAndReconstruction() {
    val account = Account(
      id = AccountId("account-runtime"),
      label = "account-label"
    )
    insertedResult(
      result = account
        .insert()
        .execute(),
      modelName = "Account"
    )

    val article = Article(
      id = "article-runtime",
      account = account,
      value = "article-value"
    )
    insertedResult(
      result = article
        .insert()
        .execute(),
      modelName = "Article"
    )

    assertThat(
      SELECT
        .FROM(ACCOUNT)
        .WHERE(ACCOUNT.ID IS account.id)
        .execute()
    ).containsExactly(account)
    assertThat(
      SELECT
        .FROM(ARTICLE)
        .WHERE(ARTICLE.ACCOUNT IS account.id)
        .execute()
    ).containsExactly(
      article.copy(account = Account(id = account.id))
    )
    assertThat(
      SELECT
        .FROM(ARTICLE)
        .WHERE(ARTICLE.ACCOUNT IS account.id)
        .queryDeep()
        .execute()
    ).containsExactly(
      article.copy(account = Account(id = account.id))
    )

    assertThat(
      listOf("SELECT id, typeof(id) FROM account", "SELECT account, typeof(account), value FROM article")
        .map {
          SELECT
            .RAW(it)
            .toCursor()
            .execute()
            .use(Cursor::readRows)
            .single()
        }
    ).isEqualTo(
      listOf(
        listOf(account.id.value, "text"),
        listOf(account.id.value, "text", article.value)
      )
    )
  }

  @Test
  fun articleUpdatePersistAndDeleteDoNotModifyAccountRecursively() {
    val account = Account(
      id = AccountId("account-operation-runtime"),
      label = "account-original-label"
    )
    insertedResult(
      result = account
        .insert()
        .execute(),
      modelName = "Account"
    )

    val article = Article(
      id = "article-operation-runtime",
      account = account,
      value = "article-original-value"
    )
    insertedResult(
      result = article
        .insert()
        .execute(),
      modelName = "Article"
    )

    val updatedArticle = article.copy(
      account = Account(
        id = account.id,
        label = "ignored-update-account-label"
      ),
      value = "article-updated-value"
    )
    assertThat(
      updatedArticle
        .update()
        .execute()
    ).isTrue()
    assertThat(
      SELECT
        .FROM(ARTICLE)
        .execute()
    ).containsExactly(
      updatedArticle.copy(account = Account(id = account.id))
    )
    assertThat(
      SELECT
        .FROM(ACCOUNT)
        .execute()
    ).containsExactly(account)

    val persistedArticle = updatedArticle.copy(
      account = Account(
        id = account.id,
        label = "ignored-persist-account-label"
      ),
      value = "article-persisted-value"
    )
    assertThat(
      persistedArticle
        .persist()
        .execute()
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(
      SELECT
        .FROM(ARTICLE)
        .execute()
    ).containsExactly(
      persistedArticle.copy(account = Account(id = account.id))
    )
    assertThat(
      SELECT
        .FROM(ACCOUNT)
        .execute()
    ).containsExactly(account)

    assertThat(
      persistedArticle
        .delete()
        .execute()
    ).isEqualTo(1)
    assertThat(
      SELECT
        .FROM(ARTICLE)
        .execute()
    ).isEmpty()
    assertThat(
      SELECT
        .FROM(ACCOUNT)
        .execute()
    ).containsExactly(account)
  }
}
