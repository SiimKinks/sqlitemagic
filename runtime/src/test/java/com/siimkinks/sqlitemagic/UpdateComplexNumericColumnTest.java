package com.siimkinks.sqlitemagic;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.ACCOUNT;
import static com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.ARTICLE;
import static com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.AccountId;
import static com.siimkinks.sqlitemagic.ComplexColumnTestFixtures.Article;
import static com.siimkinks.sqlitemagic.Utils.STRING_PARSER;

public final class UpdateComplexNumericColumnTest {
  private static final Column<String, String, CharSequence, Article, NotNullable> TITLE = new Column<>(ARTICLE, "title", false, STRING_PARSER, false, null);

  @Test
  public void initialRelationshipAssignmentUsesDeclaredIdTypeAndTransformer() {
    final Update.Set<Article> update = Update
        .table(ARTICLE)
        .set(ACCOUNT, new AccountId(41L));

    assertUpdate(update, "UPDATE article SET account=? ", "42");
  }

  @Test
  public void chainedRelationshipAssignmentUsesDeclaredIdTypeAndTransformer() {
    final Update.Set<Article> update = Update
        .table(ARTICLE)
        .set(TITLE, "Title")
        .set(ACCOUNT, new AccountId(41L));

    assertUpdate(update, "UPDATE article SET title=?,account=? ", "Title", "42");
  }

  private static void assertUpdate(
      Update.Set<Article> update,
      String expectedSql,
      String... expectedArgs
  ) {
    assertThat(SqlCreator.getSql(update, update.updateBuilder.sqlNodeCount)).isEqualTo(expectedSql);
    assertThat(update.updateBuilder.args)
        .containsExactlyElementsIn(expectedArgs)
        .inOrder();
  }
}
