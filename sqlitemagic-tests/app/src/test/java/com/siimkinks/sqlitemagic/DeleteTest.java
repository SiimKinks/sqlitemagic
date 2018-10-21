package com.siimkinks.sqlitemagic;

import androidx.annotation.Nullable;

import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static org.mockito.Mockito.mock;

public final class DeleteTest {
  @Before
  public void setUp() {
    final SqliteMagic instance = SqliteMagic.SingletonHolder.instance;
    instance.defaultConnection = mock(DbConnectionImpl.class);
  }

  @Test
  public void deleteFromBuilder() {
    String expected = "DELETE FROM book ";
    assertSqlBuilder(Delete.from(BOOK), expected);
  }

  @Test
  public void deleteFromBuilderWithAlias() {
    String expected = "DELETE FROM book ";
    assertSqlBuilder(Delete.from(BOOK.as("foo")), expected);
  }

  @Test
  public void deleteRawFromBuilder() {
    String expected = "DELETE FROM book ";
    assertSqlBuilder(Delete.from("book"), expected);
  }

  @Test
  public void deleteWhereBuilder() {
    final String expectedBase = "DELETE FROM book ";

    String expected = expectedBase + "WHERE book.title=? ";
    Delete.Where sqlNode = Delete.from(BOOK).where(BOOK.TITLE.is("asd"));
    assertSqlBuilder(sqlNode, expected, "asd");

    sqlNode = Delete
        .from(BOOK)
        .where(BOOK.TITLE.is("asd").and(BOOK.TITLE.isNotNull()).or(BOOK.NR_OF_RELEASES.greaterThan(2)));
    expected = expectedBase + "WHERE ((book.title=? AND book.title IS NOT NULL) OR book.nr_of_releases>?) ";
    assertSqlBuilder(sqlNode, expected, "asd", "2");

    sqlNode = Delete
        .from(BOOK)
        .where(BOOK.TITLE.is("asd").and(BOOK.TITLE.isNotNull()).and(BOOK.NR_OF_RELEASES.is(2)));
    expected = expectedBase + "WHERE ((book.title=? AND book.title IS NOT NULL) AND book.nr_of_releases=?) ";
    assertSqlBuilder(sqlNode, expected, "asd", "2");

    sqlNode = Delete
        .from(BOOK)
        .where(BOOK.TITLE.is("asd").and(BOOK.TITLE.isNotNull()).and(BOOK.NR_OF_RELEASES.is(2))
            .and(BOOK.BASE_ID.is(2L).or(BOOK.AUTHOR.isNotNull()).or(BOOK.NR_OF_RELEASES.lessThan(55)))
            .or(BOOK.BASE_ID.greaterThan(2L).and(BOOK.AUTHOR.isNull()).and(BOOK.NR_OF_RELEASES.isNot(55))));
    expected = expectedBase + "WHERE ((((book.title=? AND book.title IS NOT NULL) AND book.nr_of_releases=?) " +
        "AND ((book.base_id=? OR book.author IS NOT NULL) OR book.nr_of_releases<?)) " +
        "OR ((book.base_id>? AND book.author IS NULL) AND book.nr_of_releases!=?)) ";
    assertSqlBuilder(sqlNode, expected, "asd", "2", "2", "55", "2", "55");
  }

  @Test
  public void deleteRawWhereBuilder() {
    final String expected = "DELETE FROM book WHERE book.title IS NOT NULL ";
    final Delete.RawWhere sqlNode = Delete.from("book").where("book.title IS NOT NULL");
    assertSqlBuilder(sqlNode, expected);
  }

  @Test
  public void deleteRawWhereWithArgsBuilder() {
    final String expected = "DELETE FROM book WHERE book.title=? ";
    final Delete.RawWhere sqlNode = Delete.from("book").where("book.title=?", "asd");
    assertSqlBuilder(sqlNode, expected, "asd");
  }

  private void assertSqlBuilder(DeleteSqlNode sqlNode, String expected, @Nullable String... expectedArgs) {
    final String sql = SqlCreator.getSql(sqlNode, 3);
    assertThat(sql).isEqualTo(expected);
    assertThat(sqlNode.deleteBuilder.args).containsExactly(expectedArgs);
  }
}
