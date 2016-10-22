package com.siimkinks.sqlitemagic.model;

import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.SqliteMagic;
import com.siimkinks.sqlitemagic.Transaction;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;

@RunWith(AndroidJUnit4.class)
public final class SynchronousSubqueryTest {
  @Before
  public void setUp() {
    Author.deleteTable().execute();
    Magazine.deleteTable().execute();
    Book.deleteTable().execute();
    SimpleValueWithBuilder.deleteTable().execute();
    ComplexObjectWithSameLeafs.deleteTable().execute();
  }

  @Test
  public void simpleQueryWithSimpleSubquery() {
    final Author author = insertAuthors(1).get(0);
    final ArrayList<Book> expected = new ArrayList<>();

    for (int i = 0; i < 8; i++) {
      final Book book = Book.newRandom();
      book.author = null;
      if (i % 2 == 0) {
        book.title = author.name;
        expected.add(book);
      }
      book.insert().execute();
    }

    assertThat(Select
        .from(BOOK)
        .where(BOOK.TITLE.is(Select
            .column(AUTHOR.NAME)
            .from(AUTHOR)
            .where(AUTHOR.ID.is(author.id))
            .limit(1)))
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void complexQueryWithSimpleSubquery() {
    final Author author = insertAuthors(1).get(0);
    final ArrayList<Book> expected = new ArrayList<>();

    for (int i = 0; i < 8; i++) {
      final Book book = Book.newRandom();
      if (i % 2 == 0) {
        book.title = author.name;
        expected.add(book);
      }
      book.insert().execute();
    }

    assertThat(Select
        .from(BOOK)
        .where(BOOK.TITLE.is(Select
            .column(AUTHOR.NAME)
            .from(AUTHOR)
            .where(AUTHOR.ID.is(author.id))
            .limit(1)))
        .queryDeep()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void simpleQueryWithComplexSubquery() {
    final Author author = insertAuthors(1).get(0);
    final ArrayList<Book> expected = new ArrayList<>();

    for (int i = 0; i < 8; i++) {
      final Book book = Book.newRandom();
      book.author = null;
      if (i % 2 == 0) {
        book.title = author.name;
        expected.add(book);
      }
      book.insert().execute();

      final Magazine magazine = Magazine.newRandom();
      magazine.author = author;
      magazine.insert().execute();
    }

    assertThat(Select
        .from(BOOK)
        .where(BOOK.TITLE.is(Select
            .column(AUTHOR.NAME)
            .from(MAGAZINE)
            .where(AUTHOR.ID.is(author.id))
            .limit(1)))
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void complexQueryWithComplexSubquery() {
    final Author author = insertAuthors(1).get(0);
    final ArrayList<Book> expected = new ArrayList<>();

    for (int i = 0; i < 8; i++) {
      final Book book = Book.newRandom();
      if (i % 2 == 0) {
        book.title = author.name;
        expected.add(book);
      }
      book.insert().execute();

      final Magazine magazine = Magazine.newRandom();
      magazine.author = author;
      magazine.insert().execute();
    }

    assertThat(Select
        .from(BOOK)
        .where(BOOK.TITLE.is(Select
            .column(AUTHOR.NAME)
            .from(MAGAZINE)
            .where(AUTHOR.ID.is(author.id))
            .limit(1)))
        .queryDeep()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void complexQueryWithComplexNRowsSubquery() {
    final int count = 8;
    final ArrayList<Author> authors = insertAuthors(count);
    final ArrayList<Book> expected = new ArrayList<>();

    for (int i = 0; i < count; i++) {
      final Book book = Book.newRandom();
      final Author author = authors.get(i);
      if (i % 2 == 0) {
        book.title = author.name;
        expected.add(book);
      }
      book.insert().execute();

      final Magazine magazine = Magazine.newRandom();
      magazine.author = author;
      magazine.insert().execute();
    }

    assertThat(Select
        .from(BOOK)
        .where(BOOK.TITLE.in(Select
            .column(AUTHOR.NAME)
            .from(MAGAZINE)))
        .queryDeep()
        .execute())
        .isEqualTo(expected);
  }

  @Test
  public void complexQueryWithComplexSubqueryAndSystemRenamedTables() {
    final int count = 8;
    final ArrayList<ComplexObjectWithSameLeafs> expected = new ArrayList<>(count);
    final ArrayList<String> names = new ArrayList<>(count / 2);
    final Transaction transaction = SqliteMagic.newTransaction();
    try {
      for (int i = 0; i < count; i++) {
        ComplexObjectWithSameLeafs a = ComplexObjectWithSameLeafs.newRandom();
        if (i % 2 == 0) {
          final String title = Utils.randomTableName();
          names.add(title);
          a.magazine.name = title;
        }
        assertThat(a.persist().execute()).isNotEqualTo(-1);
        if (i % 2 == 0) {
          expected.add(a);
        }
      }
      Collections.sort(expected, new Comparator<ComplexObjectWithSameLeafs>() {
        @Override
        public int compare(ComplexObjectWithSameLeafs lhs, ComplexObjectWithSameLeafs rhs) {
          return Long.valueOf(lhs.id).compareTo(rhs.id);
        }
      });
      transaction.markSuccessful();
    } finally {
      transaction.end();
    }

    final ComplexObjectWithSameLeafsTable table = COMPLEX_OBJECT_WITH_SAME_LEAFS.as("cowsl");
    final List<ComplexObjectWithSameLeafs> result = Select
        .from(table)
        .where(table.MAGAZINE.in(Select
            .column(MAGAZINE._ID)
            .from(MAGAZINE)
            .where(MAGAZINE.NAME.in(names))))
        .queryDeep()
        .execute();

    for (int i = 0; i < result.size(); i++) {
      assertThat(result.get(i).equalsWithoutId(expected.get(i))).isTrue();
    }
  }
}
