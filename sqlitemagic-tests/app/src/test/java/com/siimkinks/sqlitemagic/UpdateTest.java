package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.model.Author;

import org.junit.Before;
import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.UnitTestUtil.assertStringsAreEqualOrMatching;
import static com.siimkinks.sqlitemagic.UnitTestUtil.replaceRandomTableNames;
import static org.mockito.Mockito.mock;

public final class UpdateTest {
  @Before
  public void setUp() {
    final SqliteMagic instance = SqliteMagic.SingletonHolder.instance;
    instance.defaultConnection = mock(DbConnectionImpl.class);
  }

  @Test
  public void updateSqlBuilder() {
    assertSqlBuilder(Update
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd"),
        "UPDATE author SET name=? ", 3, "asd");

    assertSqlBuilder(Update
            .table(AUTHOR)
            .set(AUTHOR.BOXED_BOOLEAN, Boolean.TRUE),
        "UPDATE author SET boxed_boolean=? ", 3, "1");

    assertSqlBuilder(Update
            .withConflictAlgorithm(SQLiteDatabase.CONFLICT_FAIL)
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd"),
        "UPDATE  OR FAIL author SET name=? ", 4, "asd");

    assertSqlBuilder(Update
            .withConflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd"),
        "UPDATE  OR IGNORE author SET name=? ", 4, "asd");

    assertSqlBuilder(Update
            .withConflictAlgorithm(SQLiteDatabase.CONFLICT_ROLLBACK)
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd")
            .set(AUTHOR.BOXED_BOOLEAN, Boolean.TRUE)
            .set(AUTHOR.ID, 2L)
            .set(AUTHOR.PRIMITIVE_BOOLEAN, false),
        "UPDATE  OR ROLLBACK author SET name=?,boxed_boolean=?,id=?,primitive_boolean=? ", 4,
        "asd", "1", "2", "0");

    assertSqlBuilder(Update
            .withConflictAlgorithm(SQLiteDatabase.CONFLICT_ROLLBACK)
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd")
            .set(AUTHOR.BOXED_BOOLEAN, Boolean.TRUE),
        "UPDATE  OR ROLLBACK author SET name=?,boxed_boolean=? ", 4, "asd", "1");
  }

  @Test
  public void updateWhereBuilder() {
    assertSqlBuilder(Update
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd")
            .where(AUTHOR.ID.is(2L)),
        "UPDATE author SET name=? WHERE author.id=? ", 4, "asd", "2");

    assertSqlBuilder(Update
            .withConflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd")
            .set(AUTHOR.BOXED_BOOLEAN, Boolean.FALSE)
            .where(AUTHOR.ID.is(2L).and(AUTHOR.NAME.isNot("asd"))),
        "UPDATE  OR IGNORE author SET name=?,boxed_boolean=? WHERE (author.id=? AND author.name!=?) ", 5,
        "asd", "0", "2", "asd");

    assertSqlBuilder(Update
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd")
            .set(AUTHOR.BOXED_BOOLEAN, Boolean.FALSE)
            .where(AUTHOR.ID.is(2L).or(AUTHOR.NAME.isNot("asd"))),
        "UPDATE author SET name=?,boxed_boolean=? WHERE (author.id=? OR author.name!=?) ", 4,
        "asd", "0", "2", "asd");

    assertSqlBuilder(Update
            .withConflictAlgorithm(SQLiteDatabase.CONFLICT_FAIL)
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd")
            .where(AUTHOR.ID.is(2L).and(AUTHOR.NAME.isNot("asd"))),
        "UPDATE  OR FAIL author SET name=? WHERE (author.id=? AND author.name!=?) ", 5,
        "asd", "2", "asd");

    assertSqlBuilder(Update
            .withConflictAlgorithm(SQLiteDatabase.CONFLICT_FAIL)
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd")
            .where(AUTHOR.ID.is(2L)
                .and(AUTHOR.NAME.isNotNull())
                .and(AUTHOR.NAME.isNot("asd"))
                .and(AUTHOR.PRIMITIVE_BOOLEAN.is(false))),
        "UPDATE  OR FAIL author SET name=? WHERE (((author.id=? AND author.name IS NOT NULL) AND author.name!=?) AND author.primitive_boolean=?) ", 5,
        "asd", "2", "asd", "0");

    assertSqlBuilder(Update
            .withConflictAlgorithm(SQLiteDatabase.CONFLICT_FAIL)
            .table(AUTHOR)
            .set(AUTHOR.NAME, "asd")
            .where(AUTHOR.ID.is(2L)
                .and(AUTHOR.NAME.isNotNull())
                .or(AUTHOR.NAME.isNot("asd"))
                .or(AUTHOR.PRIMITIVE_BOOLEAN.is(false)
                    .and(AUTHOR.BOXED_BOOLEAN.isNotNull()))),
        "UPDATE  OR FAIL author SET name=? WHERE (((author.id=? AND author.name IS NOT NULL) OR author.name!=?) OR (author.primitive_boolean=? AND author.boxed_boolean IS NOT NULL)) ", 5,
        "asd", "2", "asd", "0");
  }

  @Test
  public void updateComplexColumn() {
    final Author author = Author.newRandom();
    final Long id = author.id;
    final String idStr = Long.toString(id);
    assertSqlBuilder(Update
            .table(BOOK)
            .set(BOOK.AUTHOR, author)
            .set(BOOK.AUTHOR, author),
        "UPDATE book SET author=?,author=? ",
        3,
        idStr, idStr);

    assertSqlBuilder(Update
            .table(BOOK)
            .set(BOOK.AUTHOR, id)
            .set(BOOK.AUTHOR, id),
        "UPDATE book SET author=?,author=? ",
        3,
        idStr, idStr);
  }

  @Test
  public void updateWithColumn() {
    assertSqlBuilder(Update
            .table(BOOK)
            .set(BOOK.NR_OF_RELEASES, BOOK.NR_OF_RELEASES.add(6)),
        "UPDATE book SET nr_of_releases=(book.nr_of_releases+6) ",
        3);

    assertSqlBuilder(Update
            .table(BOOK)
            .set(BOOK.NR_OF_RELEASES, BOOK.BASE_ID),
        "UPDATE book SET nr_of_releases=book.base_id ",
        3);

    assertSqlBuilder(Update
            .table(AUTHOR)
            .set(AUTHOR.BOXED_BOOLEAN, AUTHOR.PRIMITIVE_BOOLEAN),
        "UPDATE author SET boxed_boolean=author.primitive_boolean ",
        3);

    assertSqlBuilder(Update
            .table(BOOK)
            .set(BOOK.AUTHOR, BOOK.AUTHOR),
        "UPDATE book SET author=book.author ",
        3);

    assertSqlBuilder(Update
            .table(BOOK)
            .set(BOOK.AUTHOR, BOOK.BASE_ID),
        "UPDATE book SET author=book.base_id ",
        3);
  }

  @Test
  public void updateWithSelect() {
    assertSqlBuilder(Update
            .table(BOOK)
            .set(BOOK.NR_OF_RELEASES, Select
                .column(MAGAZINE.NR_OF_RELEASES)
                .from(MAGAZINE)),
        "UPDATE book SET nr_of_releases=(SELECT magazine.nr_of_releases FROM magazine ) ",
        3);

    assertSqlBuilder(Update
            .table(BOOK)
            .set(BOOK.AUTHOR, Select
                .column(MAGAZINE.AUTHOR)
                .from(MAGAZINE)),
        "UPDATE book SET author=(SELECT magazine.author FROM magazine ) ",
        3);

    assertSqlBuilder(Update
            .table(BOOK)
            .set(BOOK.NR_OF_RELEASES, Select
                .column(BOOK.NR_OF_RELEASES)
                .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)),
        "UPDATE book SET nr_of_releases=(SELECT book.nr_of_releases FROM complex_object_with_same_leafs " +
            "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id ) ",
        3);

    assertSqlBuilderWithWildcards(Update
            .table(BOOK)
            .set(BOOK.NR_OF_RELEASES, Select
                .column(MAGAZINE.NR_OF_RELEASES)
                .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)),
        "UPDATE book SET nr_of_releases=(SELECT ?.nr_of_releases FROM complex_object_with_same_leafs " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ) ",
        3);
  }

  private void assertSqlBuilder(UpdateSqlNode node, String expectedSql, int expectedNodeCount, @Nullable String... expectedArgs) {
    final CompiledUpdate.Builder updateBuilder = node.updateBuilder;
    final String sql = SqlCreator.getSql(updateBuilder.sqlTreeRoot, updateBuilder.sqlNodeCount);
    assertThat(sql).isEqualTo(expectedSql);
    assertThat(updateBuilder.sqlNodeCount).isEqualTo(expectedNodeCount);
    assertThat(updateBuilder.args).isNotNull();
    assertThat(updateBuilder.args).containsExactly(expectedArgs);
  }

  private void assertSqlBuilderWithWildcards(UpdateSqlNode node, String expectedSql, int expectedNodeCount, @Nullable String... expectedArgs) {
    final CompiledUpdate.Builder updateBuilder = node.updateBuilder;
    final String sql = SqlCreator.getSql(updateBuilder.sqlTreeRoot, updateBuilder.sqlNodeCount);
    assertStringsAreEqualOrMatching(sql, replaceRandomTableNames(expectedSql));
    assertThat(updateBuilder.sqlNodeCount).isEqualTo(expectedNodeCount);
    assertThat(updateBuilder.args).isNotNull();
    assertThat(updateBuilder.args).containsExactly(expectedArgs);
  }
}
