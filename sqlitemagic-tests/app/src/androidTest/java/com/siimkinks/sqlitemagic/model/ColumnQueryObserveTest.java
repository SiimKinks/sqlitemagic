package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilderAndNullableFields;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import rx.Subscription;
import rx.functions.Func1;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertComplexValuesWithSameLeafs;
import static rx.Observable.from;

public final class ColumnQueryObserveTest {

  private final RecordingObserver o = new RecordingObserver();

  @Before
  public void setUp() {
    Author.deleteTable().execute();
    Magazine.deleteTable().execute();
    Book.deleteTable().execute();
    ComplexObjectWithSameLeafs.deleteTable().execute();
    SimpleMutable.deleteTable().execute();
    SimpleAllValuesMutable.deleteTable().execute();
    SimpleValueWithBuilder.deleteTable().execute();
    SimpleValueWithBuilderAndNullableFields.deleteTable().execute();
  }

  @After
  public void tearDown() {
    o.assertNoMoreEvents();
    assertTriggersHaveNoObservers();
  }

  @Test
  public void simpleColumnListQueryObservesChanges() {
    final List<String> expected = from(insertAuthors(9))
        .map(new Func1<Author, String>() {
          @Override
          public String call(Author author) {
            return author.name;
          }
        })
        .toList()
        .toBlocking()
        .first();

    final Subscription subscription = Select
        .column(AUTHOR.NAME)
        .from(AUTHOR)
        .observe()
        .subscribe(o);

    o.assertElements()
        .hasElements(expected)
        .isExhausted();

    // New irrelevant table doesn't trigger
    final SimpleAllValuesMutable v = SimpleAllValuesMutable.newRandom();
    assertThat(v.persist().execute()).isNotEqualTo(-1);
    o.assertNoMoreEvents();

    final Author a = Author.newRandom();
    assertThat(a.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(expected)
        .hasSingleElement(a.name)
        .isExhausted();

    subscription.unsubscribe();
  }

  @Test
  public void simpleColumnFirstQueryObservesChanges() {
    final String expected = from(insertAuthors(9))
        .map(new Func1<Author, String>() {
          @Override
          public String call(Author author) {
            return author.name;
          }
        })
        .toBlocking()
        .last();

    final Subscription subscription = Select.column(AUTHOR.NAME)
        .from(AUTHOR)
        .orderBy(AUTHOR.ID.desc())
        .takeFirst()
        .observe()
        .subscribe(o);

    o.assertSingleElement(expected);

    // New irrelevant table doesn't trigger
    final SimpleAllValuesMutable v = SimpleAllValuesMutable.newRandom();
    assertThat(v.persist().execute()).isNotEqualTo(-1);
    o.assertNoMoreEvents();

    final Author a = Author.newRandom();
    assertThat(a.persist().execute()).isNotEqualTo(-1);
    o.assertSingleElement(a.name);

    subscription.unsubscribe();
  }

  @Test
  public void complexColumnListQueryObservesChanges() {
    final List<String> expected = from(insertComplexValuesWithSameLeafs(9))
        .map(new Func1<ComplexObjectWithSameLeafs, String>() {
          @Override
          public String call(ComplexObjectWithSameLeafs v) {
            return v.magazine.name;
          }
        })
        .toList()
        .toBlocking()
        .first();

    final Subscription subscription = Select
        .column(MAGAZINE.NAME)
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .observe()
        .subscribe(o);

    o.assertElements()
        .hasElements(expected)
        .isExhausted();

    // New irrelevant table doesn't trigger
    final Book v = Book.newRandom();
    assertThat(v.persist().execute()).isNotEqualTo(-1);
    o.assertNoMoreEvents();

    // New irrelevant table doesn't trigger
    final Author a = Author.newRandom();
    assertThat(a.persist().execute()).isNotEqualTo(-1);
    o.assertNoMoreEvents();

    // New magazine triggers, despite the fact that it's not in our result set.
    final Magazine m = Magazine.newRandom();
    assertThat(m.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(expected)
        .isExhausted();

    // New exact table triggers
    final ComplexObjectWithSameLeafs c = ComplexObjectWithSameLeafs.newRandom();
    assertThat(c.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(expected)
        .hasSingleElement(c.magazine.name)
        .isExhausted();

    subscription.unsubscribe();
  }

  @Test
  public void complexColumnFirstQueryObservesChanges() {
    final String expected = from(insertComplexValuesWithSameLeafs(9))
        .map(new Func1<ComplexObjectWithSameLeafs, String>() {
          @Override
          public String call(ComplexObjectWithSameLeafs v) {
            return v.magazine.name;
          }
        })
        .toBlocking()
        .last();

    final Subscription subscription = Select
        .column(MAGAZINE.NAME)
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .orderBy(COMPLEX_OBJECT_WITH_SAME_LEAFS.ID.desc())
        .takeFirst()
        .observe()
        .subscribe(o);

    o.assertSingleElement(expected);

    // New irrelevant table doesn't trigger
    final Book v = Book.newRandom();
    assertThat(v.persist().execute()).isNotEqualTo(-1);
    o.assertNoMoreEvents();

    // New irrelevant table doesn't trigger
    final Author a = Author.newRandom();
    assertThat(a.persist().execute()).isNotEqualTo(-1);
    o.assertNoMoreEvents();

    // New magazine triggers, despite the fact that it's not in our result set.
    final Magazine m = Magazine.newRandom();
    assertThat(m.persist().execute()).isNotEqualTo(-1);
    o.assertSingleElement(expected);

    // New exact table triggers
    final ComplexObjectWithSameLeafs c = ComplexObjectWithSameLeafs.newRandom();
    assertThat(c.persist().execute()).isNotEqualTo(-1);
    o.assertSingleElement(c.magazine.name);

    subscription.unsubscribe();
  }
}
