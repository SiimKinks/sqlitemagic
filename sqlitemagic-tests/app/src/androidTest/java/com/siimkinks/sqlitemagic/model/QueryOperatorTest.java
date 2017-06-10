package com.siimkinks.sqlitemagic.model;

import android.database.Cursor;
import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.CompiledCountSelect;
import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Select;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.functions.Predicate;
import io.reactivex.observers.TestObserver;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
import static com.siimkinks.sqlitemagic.model.TestUtil.awaitTerminalEvent;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;

@RunWith(AndroidJUnit4.class)
public final class QueryOperatorTest {
  private final CompiledSelect<Author, Select.SelectN> selectAuthors = Select.from(AUTHOR).compile();
  private final CompiledCountSelect countAuthors = Select.from(AUTHOR).count();
  private final RecordingObserver o = new RecordingObserver();

  @Before
  public void setUp() {
    Author.deleteTable().execute();
  }

  @After
  public void tearDown() {
    o.assertNoMoreEvents();
    assertTriggersHaveNoObservers();
  }

  @Test
  public void runQueryOnceOperatorPropagatesDisposition() {
    final ArrayList<Author> authors = insertAuthors(6);
    final TestObserver<List<Author>> ts = Select
        .from(AUTHOR)
        .observe()
        .runQueryOnce()
        .filter(new Predicate<List<Author>>() {
          @Override
          public boolean test(List<Author> authors) throws Exception {
            return !authors.isEmpty();
          }
        })
        .test();

    ts.awaitTerminalEvent();
    ts.assertValue(authors);
  }

  @Test
  public void runQueryOperatorPropagatesDisposition() {
    final TestObserver<List<Author>> ts = Select
        .from(AUTHOR)
        .observe()
        .runQuery()
        .filter(new Predicate<List<Author>>() {
          @Override
          public boolean test(List<Author> authors) {
            return !authors.isEmpty();
          }
        })
        .take(1)
        .test();

    final ArrayList<Author> authors = insertAuthors(6);

    ts.awaitTerminalEvent();
    ts.assertValue(authors);
  }

  @Test
  public void isNotZeroOperatorPropagatesDisposition() {
    final TestObserver<Boolean> ts = Select
        .from(AUTHOR)
        .count()
        .observe()
        .isNotZero()
        .filter(new Predicate<Boolean>() {
          @Override
          public boolean test(Boolean aBoolean) {
            return aBoolean;
          }
        })
        .take(1)
        .test();

    insertAuthors(6);

    ts.awaitTerminalEvent();
    ts.assertValue(true);
  }

  @Test
  public void isZeroOperatorPropagatesDisposition() {
    final TestObserver<Boolean> ts = Select
        .from(AUTHOR)
        .count()
        .observe()
        .isZero()
        .filter(new Predicate<Boolean>() {
          @Override
          public boolean test(Boolean aBoolean) {
            return !aBoolean;
          }
        })
        .take(1)
        .test();

    insertAuthors(6);

    ts.awaitTerminalEvent();
    ts.assertValue(false);
  }

  @Test
  public void queryCountIsZero() {
    Boolean isZero = countAuthors.observe()
        .isNotZero()
        .blockingFirst();
    assertThat(isZero).isNotNull();
    assertThat(isZero).isFalse();

    isZero = countAuthors.observe()
        .isZero()
        .blockingFirst();
    assertThat(isZero).isNotNull();
    assertThat(isZero).isTrue();
  }

  @Test
  public void queryCountIsNotZero() {
    insertAuthors(3);

    Boolean isNotZero = countAuthors.observe()
        .isNotZero()
        .blockingFirst();
    assertThat(isNotZero).isNotNull();
    assertThat(isNotZero).isTrue();

    isNotZero = countAuthors.observe()
        .isZero()
        .blockingFirst();
    assertThat(isNotZero).isNotNull();
    assertThat(isNotZero).isFalse();
  }

  @Test
  public void runListQuery() {
    final List<Author> expected = insertAuthors(3);
    final List<Author> result = selectAuthors.observe()
        .runQuery()
        .blockingFirst();
    assertThat(result).containsExactlyElementsIn(expected);
  }

  @Test
  public void runListQueryObservesChanges() {
    final List<Author> expected = new ArrayList<>(6);
    final List<Author> expectedFirst = insertAuthors(3);
    expected.addAll(expectedFirst);
    final TestObserver<List<Author>> ts =
        selectAuthors.observe()
            .runQuery()
            .take(2)
            .test();

    expected.addAll(insertAuthors(3));

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertValues(expectedFirst, expected);
  }

  @Test
  public void runListQueryOnce() {
    final List<Author> expected = insertAuthors(3);
    final TestObserver<List<Author>> ts =
        selectAuthors.observe()
            .runQueryOnce()
            .test();

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertValue(expected);
  }

  @Test
  public void runListQueryOnceRunsOnlyOnce() {
    final List<Author> expected = insertAuthors(3);
    final TestObserver<List<Author>> ts =
        selectAuthors.observe()
            .runQueryOnce()
            .test();

    insertAuthors(3);

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertValue(expected);
  }

  @Test
  public void runEmptyListQuery() {
    insertAuthors(3);
    final List<Author> result = Select
        .from(AUTHOR)
        .where(AUTHOR.NAME.is("asd"))
        .observe()
        .runQueryOnce()
        .blockingGet();
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  public void runFirstQuery() {
    final Author expected = insertAuthors(3).get(0);
    final Author result = selectAuthors
        .takeFirst()
        .observe()
        .runQuery()
        .blockingFirst();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void runFirstQueryObservesChanges() {
    final Author expected = insertAuthors(3).get(0);
    final TestObserver<Author> ts =
        selectAuthors
            .takeFirst()
            .observe()
            .runQuery()
            .take(2)
            .test();

    insertAuthors(3);

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertValues(expected, expected);
  }

  @Test
  public void runFirstQueryOnce() {
    final Author expected = insertAuthors(3).get(0);
    final TestObserver<Author> ts =
        selectAuthors
            .takeFirst()
            .observe()
            .runQueryOnce()
            .test();

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertValue(expected);
  }

  @Test
  public void runFirstQueryOnceRunsOnlyOnce() {
    final Author expected = insertAuthors(3).get(0);
    final TestObserver<Author> ts =
        selectAuthors
            .takeFirst()
            .observe()
            .runQueryOnce()
            .test();

    insertAuthors(3);

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertValue(expected);
  }

  @Test
  public void runEmptyFirstQuery() {
    insertAuthors(3);

    final TestObserver<Author> ts = Select.from(AUTHOR)
        .where(AUTHOR.NAME.is("asd"))
        .takeFirst()
        .observe()
        .runQueryOnce()
        .test();

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertNoValues();
  }

  @Test
  public void runRawQueryForMissingTableCallsError() {
    final TestObserver<Cursor> ts = Select
        .raw("SELECT * FROM missing")
        .from(AUTHOR)
        .observe()
        .runQuery()
        .test();

    awaitTerminalEvent(ts);
    final List<Throwable> errors = ts.errors();
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getMessage()).contains("no such table: missing");
    ts.dispose();
  }

  @Test
  public void runCountQuery() {
    final int expected = 6;
    insertAuthors(expected);
    final Long result = countAuthors.observe()
        .runQuery()
        .blockingFirst();
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void runCountQueryObservesChanges() {
    final int expected = 6;
    insertAuthors(expected / 2);
    final TestObserver<Long> ts =
        countAuthors.observe()
            .runQuery()
            .take(2)
            .test();

    insertAuthors(expected / 2);

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertValues((long) expected / 2, (long) expected);
  }

  @Test
  public void runCountQueryOnce() {
    final int expected = 6;
    insertAuthors(expected);
    final TestObserver<Long> ts =
        countAuthors.observe()
            .runQueryOnce()
            .test();

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertValue((long) expected);
  }

  @Test
  public void runCountQueryOnceRunsOnlyOnce() {
    final int expected = 6;
    insertAuthors(expected);
    final TestObserver<Long> ts =
        countAuthors.observe()
            .runQueryOnce()
            .test();

    insertAuthors(expected);

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertValue((long) expected);
  }

  @Test
  public void runQueryOperatorDropsItemButRequestsMore() {
    final TestObserver<Author> ts = selectAuthors
        .takeFirst()
        .observe()
        .runQuery()
        .take(1)
        .test();

    final Author author = Author.newRandom();
    author.insert().execute();

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertComplete();
    ts.assertValue(author);
  }

  @Test
  public void runQueryOrDefaultEmitsDefault() {
    final Author defaultVal = Author.newRandom();
    final TestObserver<Author> ts = selectAuthors
        .takeFirst()
        .observe()
        .runQueryOrDefault(defaultVal)
        .take(2)
        .test();

    final Author insertedVal = Author.newRandom();
    insertedVal.insert().execute();

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertComplete();
    ts.assertValues(defaultVal, insertedVal);
  }

  @Test
  public void runQueryOnceOrDefaultEmitsDefault() {
    final Author defaultVal = Author.newRandom();
    final TestObserver<Author> ts = selectAuthors
        .takeFirst()
        .observe()
        .runQueryOnceOrDefault(defaultVal)
        .test();

    final Author insertedVal = Author.newRandom();
    insertedVal.insert().execute();

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertComplete();
    ts.assertValues(defaultVal);
  }
}
