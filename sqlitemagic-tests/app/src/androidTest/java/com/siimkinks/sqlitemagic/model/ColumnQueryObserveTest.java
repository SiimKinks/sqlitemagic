package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilderAndNullableFields;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import io.reactivex.Observable;
import io.reactivex.functions.Function;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertComplexValuesWithSameLeafs;

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
    final List<String> expected = Observable.fromIterable(insertAuthors(9))
        .map(new Function<Author, String>() {
          @Override
          public String apply(Author author) {
            return author.name;
          }
        })
        .toList()
        .blockingGet();

    Select
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

    o.dispose();
  }

  @Test
  public void simpleColumnFirstQueryObservesChanges() {
    final String expected = Observable.fromIterable(insertAuthors(9))
        .map(new Function<Author, String>() {
          @Override
          public String apply(Author author) {
            return author.name;
          }
        })
        .blockingLast();

    Select.column(AUTHOR.NAME)
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

    o.dispose();
  }

  @Test
  public void complexColumnListQueryObservesChanges() {
    final List<String> expected = Observable.fromIterable(insertComplexValuesWithSameLeafs(9))
        .map(new Function<ComplexObjectWithSameLeafs, String>() {
          @Override
          public String apply(ComplexObjectWithSameLeafs v) {
            return v.magazine.name;
          }
        })
        .toList()
        .blockingGet();

    Select
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

    o.dispose();
  }

  @Test
  public void complexColumnFirstQueryObservesChanges() {
    final String expected = Observable.fromIterable(insertComplexValuesWithSameLeafs(9))
        .map(new Function<ComplexObjectWithSameLeafs, String>() {
          @Override
          public String apply(ComplexObjectWithSameLeafs v) {
            return v.magazine.name;
          }
        })
        .blockingLast();

    Select
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

    o.dispose();
  }
}
