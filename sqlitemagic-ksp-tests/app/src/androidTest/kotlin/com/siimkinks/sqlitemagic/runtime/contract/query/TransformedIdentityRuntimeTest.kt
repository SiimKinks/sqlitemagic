package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AccountTable.Companion.ACCOUNT
import com.siimkinks.sqlitemagic.ArticleTable.Companion.ARTICLE
import com.siimkinks.sqlitemagic.AutomaticTransformedTable.Companion.AUTOMATIC_TRANSFORMED
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.fixture.model.Account
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import com.siimkinks.sqlitemagic.fixture.model.Article
import com.siimkinks.sqlitemagic.fixture.model.AutomaticTransformed
import com.siimkinks.sqlitemagic.fixture.model.SequenceId
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.update
import org.junit.Test

class TransformedIdentityRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun automaticTransformedInsertAssignsAndReconstructsTransformedId() {
    val value = AutomaticTransformed(
      id = SequenceId(0),
      value = "automatic-transformed-value"
    )
    val result = value
      .insert()
      .execute()
    val inserted = when (result) {
      is EntityInsertResult.Inserted -> result
      EntityInsertResult.Ignored -> throw AssertionError("AutomaticTransformed insert was ignored")
    }
    val expected = value.copy(id = SequenceId(checkNotNull(inserted.rowId)))

    assertThat(
      Select
        .from(AUTOMATIC_TRANSFORMED)
        .where(AUTOMATIC_TRANSFORMED.ID IS expected.id)
        .execute()
    ).containsExactly(expected)

    val rawStorage = Select
      .raw("SELECT id, typeof(id), value FROM automatic_transformed")
      .from(AUTOMATIC_TRANSFORMED)
      .execute()
      .use { cursor ->
        check(cursor.moveToFirst())
        listOf(
          cursor.getLong(0),
          cursor.getString(1),
          cursor.getString(2)
        )
      }
    assertThat(rawStorage)
      .isEqualTo(listOf(expected.id.value, "integer", expected.value))
  }

  @Test
  fun automaticTransformedUpdatePersistAndDeleteUseTransformedIdentity() {
    val insertedValue = AutomaticTransformed(
      id = SequenceId(0),
      value = "automatic-transformed-original"
    )
    val result = insertedValue
      .insert()
      .execute()
    val inserted = when (result) {
      is EntityInsertResult.Inserted -> result
      EntityInsertResult.Ignored -> throw AssertionError("AutomaticTransformed insert was ignored")
    }
    val persistedValue = insertedValue.copy(id = SequenceId(checkNotNull(inserted.rowId)))

    val updatedValue = persistedValue.copy(value = "automatic-transformed-updated")
    assertThat(
      updatedValue
        .update()
        .execute()
    ).isTrue()
    assertThat(
      Select
        .from(AUTOMATIC_TRANSFORMED)
        .where(AUTOMATIC_TRANSFORMED.ID IS updatedValue.id)
        .execute()
    ).containsExactly(updatedValue)

    val persistedUpdateValue = updatedValue.copy(value = "automatic-transformed-persisted")
    assertThat(
      persistedUpdateValue
        .persist()
        .execute()
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(
      Select
        .from(AUTOMATIC_TRANSFORMED)
        .where(AUTOMATIC_TRANSFORMED.ID IS persistedUpdateValue.id)
        .execute()
    ).containsExactly(persistedUpdateValue)

    assertThat(
      persistedUpdateValue
        .delete()
        .execute()
    ).isEqualTo(1)
    assertThat(
      Select
        .from(AUTOMATIC_TRANSFORMED)
        .execute()
    ).isEmpty()
  }

  @Test
  fun articleUsesTransformedAccountKeyForStoragePredicatesAndReconstruction() {
    val account = Account(
      id = AccountId("account-runtime"),
      label = "account-label"
    )
    assertThat(
      account
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)

    val article = Article(
      id = "article-runtime",
      account = account,
      value = "article-value"
    )
    assertThat(
      article
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)

    assertThat(
      Select
        .from(ACCOUNT)
        .where(ACCOUNT.ID IS account.id)
        .execute()
    ).containsExactly(account)
    assertThat(
      Select
        .from(ARTICLE)
        .where(ARTICLE.ACCOUNT IS account.id)
        .execute()
    ).containsExactly(
      article.copy(account = Account(id = account.id))
    )
    assertThat(
      Select
        .from(ARTICLE)
        .where(ARTICLE.ACCOUNT IS account.id)
        .queryDeep()
        .execute()
    ).containsExactly(
      article.copy(account = Account(id = account.id))
    )

    val accountStorage = Select
      .raw("SELECT id, typeof(id) FROM account")
      .from(ACCOUNT)
      .execute()
      .use { cursor ->
        check(cursor.moveToFirst())
        listOf(
          cursor.getString(0),
          cursor.getString(1)
        )
      }
    assertThat(accountStorage)
      .isEqualTo(listOf(account.id.value, "text"))

    val articleStorage = Select
      .raw("SELECT account, typeof(account), value FROM article")
      .from(ARTICLE)
      .execute()
      .use { cursor ->
        check(cursor.moveToFirst())
        listOf(
          cursor.getString(0),
          cursor.getString(1),
          cursor.getString(2)
        )
      }
    assertThat(articleStorage)
      .isEqualTo(listOf(account.id.value, "text", article.value))
  }

  @Test
  fun articleUpdatePersistAndDeleteDoNotModifyAccountRecursively() {
    val account = Account(
      id = AccountId("account-operation-runtime"),
      label = "account-original-label"
    )
    assertThat(
      account
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)

    val article = Article(
      id = "article-operation-runtime",
      account = account,
      value = "article-original-value"
    )
    assertThat(
      article
        .insert()
        .execute()
    ).isInstanceOf(EntityInsertResult.Inserted::class.java)

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
      Select
        .from(ARTICLE)
        .execute()
    ).containsExactly(
      updatedArticle.copy(account = Account(id = account.id))
    )
    assertThat(
      Select
        .from(ACCOUNT)
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
      Select
        .from(ARTICLE)
        .execute()
    ).containsExactly(
      persistedArticle.copy(account = Account(id = account.id))
    )
    assertThat(
      Select
        .from(ACCOUNT)
        .execute()
    ).containsExactly(account)

    assertThat(
      persistedArticle
        .delete()
        .execute()
    ).isEqualTo(1)
    assertThat(
      Select
        .from(ARTICLE)
        .execute()
    ).isEmpty()
    assertThat(
      Select
        .from(ACCOUNT)
        .execute()
    ).containsExactly(account)
  }
}
