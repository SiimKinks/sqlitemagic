package com.siimkinks.sqlitemagic.model;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.CompiledCountSelect;
import com.siimkinks.sqlitemagic.CompiledSelect;
import com.siimkinks.sqlitemagic.Delete;
import com.siimkinks.sqlitemagic.Func1;
import com.siimkinks.sqlitemagic.ListQueryObservable;
import com.siimkinks.sqlitemagic.Query;
import com.siimkinks.sqlitemagic.Select;
import com.siimkinks.sqlitemagic.Select.SelectN;
import com.siimkinks.sqlitemagic.SqliteMagic;
import com.siimkinks.sqlitemagic.TestScheduler;
import com.siimkinks.sqlitemagic.Transaction;
import com.siimkinks.sqlitemagic.Update;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilderAndNullableFields;
import com.siimkinks.sqlitemagic.model.view.SimpleCreatorView;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.observers.TestObserver;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.SimpleAllValuesMutableTable.SIMPLE_ALL_VALUES_MUTABLE;
import static com.siimkinks.sqlitemagic.SimpleCreatorViewTable.SIMPLE_CREATOR_VIEW;
import static com.siimkinks.sqlitemagic.SimpleMutableTable.SIMPLE_MUTABLE;
import static com.siimkinks.sqlitemagic.model.TestUtil.awaitTerminalEvent;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertComplexValues;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertSimpleValues;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class QueryObserveTest {

  private final CompiledSelect<Author, SelectN> selectAuthors = Select.from(AUTHOR).compile();
  private final CompiledCountSelect countAuthors = Select.from(AUTHOR).count();
  private final CompiledSelect<Magazine, SelectN> selectMagazines = Select.from(MAGAZINE).queryDeep().compile();
  private final CompiledSelect<SimpleMutable, SelectN> selectSimple = Select.from(SIMPLE_MUTABLE).compile();
  private final RecordingObserver o = new RecordingObserver();
  private final RecordingCursorObserver co = new RecordingCursorObserver();
  private TestScheduler scheduler;

  @Before
  public void setUp() {
    Author.deleteTable().execute();
    Magazine.deleteTable().execute();
    Book.deleteTable().execute();
    SimpleMutable.deleteTable().execute();
    SimpleAllValuesMutable.deleteTable().execute();
    SimpleValueWithBuilder.deleteTable().execute();
    SimpleValueWithBuilderAndNullableFields.deleteTable().execute();
    scheduler = new TestScheduler();
  }

  @After
  public void tearDown() {
    o.assertNoMoreEvents();
    assertTriggersHaveNoObservers();
  }

  @Test
  public void runListQueryInFlatMap() {
    final List<Author> expected = insertAuthors(3);
    final List<Author> result = selectAuthors.observe()
        .flatMap(new Function<Query<List<Author>>, Observable<List<Author>>>() {
          @Override
          public Observable<List<Author>> apply(Query<List<Author>> listQuery) {
            return listQuery.run();
          }
        })
        .blockingFirst();

    assertThat(result).containsExactlyElementsIn(expected);
  }

  @Test
  public void runEmptyListQueryInFlatMap() {
    insertAuthors(3);
    final List<Author> result = Select
        .from(AUTHOR)
        .where(AUTHOR.NAME.is("asd"))
        .observe()
        .flatMap(new Function<Query<List<Author>>, Observable<List<Author>>>() {
          @Override
          public Observable<List<Author>> apply(Query<List<Author>> query) {
            return query.run();
          }
        })
        .blockingFirst();
    assertThat(result).isNotNull();
    assertThat(result).isEmpty();
  }

  @Test
  public void runFirstQueryInFlatMap() {
    final Author expected = insertAuthors(3).get(0);
    final Author result = selectAuthors
        .takeFirst()
        .observe()
        .flatMap(new Function<Query<Author>, Observable<Author>>() {
          @Override
          public Observable<Author> apply(Query<Author> query) {
            return query.run();
          }
        })
        .blockingFirst();
    assertThat(result).isEqualTo(expected);
  }

  @Test
  public void runEmptyFirstQueryInFlatMap() {
    insertAuthors(3);
    final TestObserver<Author> ts = Select
        .from(AUTHOR)
        .where(AUTHOR.NAME.is("asd"))
        .takeFirst()
        .observe()
        .take(1)
        .flatMap(new Function<Query<Author>, Observable<Author>>() {
          @Override
          public Observable<Author> apply(Query<Author> query) {
            return query.run();
          }
        })
        .test();

    awaitTerminalEvent(ts);
    ts.assertNoErrors();
    ts.assertNoValues();
  }

  @Test
  public void runCountQueryInFlatMap() {
    insertAuthors(3);
    final Long result = countAuthors.observe()
        .flatMap(new Function<Query<Long>, Observable<Long>>() {
          @Override
          public Observable<Long> apply(Query<Long> query) {
            return query.run();
          }
        })
        .blockingFirst();
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(3);
  }

  @Test
  public void queryObservesInsert() {
    List<Author> authors = insertAuthors(3);
    selectAuthors.observe()
        .subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    final Author newRandom = Author.newRandom();
    assertThat(newRandom.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(authors)
        .hasSingleElement(newRandom)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void queryNotNotifiedWhenInsertFails() {
    List<SimpleMutable> values = insertSimpleValues(3);
    selectSimple.observe().subscribe(o);
    o.assertElements()
        .hasElements(values)
        .isExhausted();

    assertThat(values.get(1).insert().conflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE).execute()).isEqualTo(-1);
    o.assertNoMoreEvents();

    o.dispose();
  }

  @Test
  public void queryObservesUpdate() {
    List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    final Author author = authors.get(1);
    author.name = "asd";
    assertThat(author.update().execute()).isTrue();
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    assertThat(Update
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "dsa")
        .where(AUTHOR.ID.is(author.id))
        .execute())
        .isEqualTo(1);
    author.name = "dsa";
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void queryNotNotifiedWhenUpdateAffectsZeroRows() {
    List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    assertThat(Update
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "dsa")
        .where(AUTHOR.NAME.is("asd"))
        .execute())
        .isEqualTo(0);
    o.assertNoMoreEvents();

    o.dispose();
  }

  @Test
  public void queryObservesDelete() {
    List<Author> authors = insertAuthors(4);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    final Author a = authors.remove(1);
    assertThat(a.delete().execute()).isEqualTo(1);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    final Author a2 = authors.remove(1);
    assertThat(Delete.from(AUTHOR)
        .where(AUTHOR.ID.is(a2.id))
        .execute())
        .isEqualTo(1);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void queryObservesDeleteFromRelatedTable() {
    final List<Magazine> magazines = insertComplexValues(4);
    final ArrayList<Author> authors = new ArrayList<>(magazines.size());
    for (Magazine magazine : magazines) {
      authors.add(magazine.author);
    }
    selectMagazines.observe().subscribe(o);
    o.assertElements()
        .hasElements(magazines)
        .isExhausted();

    magazines.remove(1);
    Author author = authors.remove(1);
    assertThat(author.delete().execute()).isEqualTo(1);
    o.assertElements()
        .hasElements(magazines)
        .isExhausted();

    magazines.remove(1);
    author = authors.remove(1);
    assertThat(Delete
        .from(AUTHOR)
        .where(AUTHOR.ID.is(author.id))
        .execute()).isEqualTo(1);
    o.assertElements()
        .hasElements(magazines)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void queryNotNotifiedWhenDeleteAffectsZeroRows() {
    List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    assertThat(Author.newRandom().delete().execute())
        .isEqualTo(0);
    o.assertNoMoreEvents();

    assertThat(Delete
        .from(AUTHOR)
        .where(AUTHOR.NAME.is("asd"))
        .execute())
        .isEqualTo(0);
    o.assertNoMoreEvents();

    o.dispose();
  }

  @Test
  public void queryMultipleTables() {
    final List<Magazine> magazines = insertComplexValues(3);
    selectMagazines.observe().subscribe(o);
    o.assertElements()
        .hasElements(magazines)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void queryMultipleTablesObservesChanges() {
    final List<Magazine> magazines = insertComplexValues(3);
    selectMagazines.observe().subscribe(o);
    o.assertElements()
        .hasElements(magazines)
        .isExhausted();

    // A new author triggers, despite the fact that it's not in our result set.
    Author a = Author.newRandom();
    assertThat(a.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(magazines)
        .isExhausted();

    // A new magazine also triggers and it is in our result set.
    Magazine m = Magazine.newRandom();
    assertThat(m.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(magazines)
        .hasSingleElement(m)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void queryWithSimpleSubqueryObservesChanges() {
    final int count = 9;
    final Book book = Book.newRandom();
    book.insert().execute();
    final ArrayList<Author> authors = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final Author author = Author.newRandom();
      author.name = book.title;
      authors.add(author);
    }
    Author.insert(authors).execute();

    Select.from(AUTHOR)
        .where(AUTHOR.NAME.is(Select
            .column(BOOK.TITLE)
            .from(BOOK)
            .where(BOOK.BASE_ID.is(book.getBaseId()))))
        .observe()
        .subscribe(o);

    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    // A new book triggers, despite the fact that it's not in our result set.
    Book b = Book.newRandom();
    assertThat(b.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    // A new author also triggers and it is in our result set.
    Author a = Author.newRandom();
    a.name = book.title;
    assertThat(a.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(authors)
        .hasSingleElement(a)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void queryWithComplexSubqueryObservesChanges() {
    final int count = 9;
    final Book book = Book.newRandom();
    book.insert().execute();
    final ArrayList<SimpleAllValuesMutable> vals = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      final SimpleAllValuesMutable val = SimpleAllValuesMutable.newRandom();
      val.string = book.author.name;
      vals.add(val);
    }
    SimpleAllValuesMutable.insert(vals).execute();

    Select.from(SIMPLE_ALL_VALUES_MUTABLE)
        .where(SIMPLE_ALL_VALUES_MUTABLE.STRING.is(Select
            .column(AUTHOR.NAME)
            .from(BOOK)
            .where(BOOK.BASE_ID.is(book.getBaseId()))))
        .observe()
        .subscribe(o);

    o.assertElements()
        .hasElements(vals)
        .isExhausted();

    // A new book triggers, despite the fact that it's not in our result set.
    final Book b = Book.newRandom();
    assertThat(b.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(vals)
        .isExhausted();

    // A new author triggers, despite the fact that it's not in our result set.
    final Author a = Author.newRandom();
    assertThat(a.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(vals)
        .isExhausted();

    // A new simple mutable also triggers and it is in our result set.
    final SimpleAllValuesMutable s = SimpleAllValuesMutable.newRandom();
    s.string = book.author.name;
    assertThat(s.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(vals)
        .hasSingleElement(s)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void viewQueryObservesChanges() {
    final List<Magazine> magazines = insertComplexValues(3);
    final List<SimpleCreatorView> expected = new ArrayList<>(magazines.size());
    for (Magazine magazine : magazines) {
      expected.add(SimpleCreatorView.create(magazine.author.name, magazine.name));
    }
    Select.from(SIMPLE_CREATOR_VIEW).observe().subscribe(o);
    o.assertElements()
        .hasElements(expected)
        .isExhausted();

    // A new author triggers, despite the fact that it's not in our result set.
    final Author a = Author.newRandom();
    assertThat(a.persist().execute()).isNotEqualTo(-1);
    o.assertElements()
        .hasElements(expected)
        .isExhausted();

    // A new magazine also triggers and it is in our result set.
    final Magazine m = Magazine.newRandom();
    assertThat(m.persist().execute()).isNotEqualTo(-1);
    expected.add(SimpleCreatorView.create(m.author.name, m.name));
    o.assertElements()
        .hasElements(expected)
        .isExhausted();

    o.dispose();
  }

  static final Func1<Cursor, Object> mapAuthor = new Func1<Cursor, Object>() {
    @Override
    public Object call(Cursor cursor) {
      return Author.getFromCursorPosition(cursor);
    }
  };

  @Test
  public void rawQueryObservesChanges() {
    final ArrayList<Author> authors = insertAuthors(3);
    Select.raw("SELECT * FROM author")
        .from(AUTHOR)
        .observe()
        .subscribe(co);
    co.assertCursor()
        .hasRows(mapAuthor, authors)
        .isExhausted();

    final Author newRandom = Author.newRandom();
    assertThat(newRandom.persist().execute()).isNotEqualTo(-1);
    authors.add(newRandom);
    co.assertCursor()
        .hasRows(mapAuthor, authors)
        .isExhausted();

    co.dispose();
  }

  @Test
  public void rawQueryNotNotifiedForIrrelevantTableChange() {
    final ArrayList<Author> authors = insertAuthors(3);
    Select.raw("SELECT * FROM author")
        .from(AUTHOR)
        .observe()
        .subscribe(co);
    co.assertCursor()
        .hasRows(mapAuthor, authors)
        .isExhausted();

    final SimpleMutable newRandom = SimpleMutable.newRandom();
    assertThat(newRandom.persist().execute()).isNotEqualTo(-1);

    co.assertNoMoreEvents();

    co.dispose();
  }

  @Test
  public void rawQueryForMissingTableCallsError() {
    Select.raw("SELECT * FROM missing")
        .from(AUTHOR)
        .observe()
        .subscribe(o);

    o.assertErrorContains("no such table: missing");

    o.dispose();
  }

  @Test
  public void queryNotNotifiedAfterUnsubscribe() {
    List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();
    o.dispose();

    assertThat(Author.newRandom().insert().execute()).isNotEqualTo(-1);
    o.assertNoMoreEvents();
  }

  @Test
  public void queryOnlyNotifiedAfterSubscribe() {
    List<Author> authors = insertAuthors(3);
    final ListQueryObservable<Author> query = selectAuthors.observe();
    o.assertNoMoreEvents();

    final Author a = Author.newRandom();
    assertThat(a.insert().execute()).isNotEqualTo(-1);
    o.assertNoMoreEvents();

    query.subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .hasSingleElement(a)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void transactionOnlyNotifiesOnce() {
    List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    Transaction transaction = SqliteMagic.newTransaction();
    try {
      Author author = Author.newRandom();
      assertThat(author.persist().execute()).isNotEqualTo(-1);
      authors.add(author);
      author = Author.newRandom();
      assertThat(author.persist().execute()).isNotEqualTo(-1);
      authors.add(author);
      o.assertNoMoreEvents();

      transaction.markSuccessful();
    } finally {
      transaction.end();
    }

    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void transactionCreatedFromTransactionNotificationWorks() {
    // Tests the case where a transaction is created in the subscriber to a query which gets
    // notified as the result of another transaction being committed. With improper ordering, this
    // can result in creating a new transaction before the old is committed on the underlying DB.
    final Disposable disposable = selectAuthors.observe()
        .subscribe(new Consumer<Query>() {
          @Override
          public void accept(Query query) {
            SqliteMagic.newTransaction().end();
          }
        });

    Transaction transaction = SqliteMagic.newTransaction();
    try {
      final Author author = Author.newRandom();
      author.insert().execute();
      transaction.markSuccessful();
    } finally {
      transaction.end();
    }
    disposable.dispose();
  }

  @Test
  public void transactionIsCloseable() throws IOException {
    List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    Transaction transaction = SqliteMagic.newTransaction();
    //noinspection UnnecessaryLocalVariable
    Closeable closeableTransaction = transaction; // Verify type is implemented.
    try {
      Author a = Author.newRandom();
      assertThat(a.persist().execute()).isNotEqualTo(-1);
      authors.add(a);
      a = Author.newRandom();
      assertThat(a.persist().execute()).isNotEqualTo(-1);
      authors.add(a);
      transaction.markSuccessful();
    } finally {
      closeableTransaction.close();
    }

    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void transactionDoesNotThrow() {
    List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    Transaction transaction = SqliteMagic.newTransaction();
    try {
      Author a = Author.newRandom();
      assertThat(a.insert().execute()).isNotEqualTo(-1);
      authors.add(a);
      a = Author.newRandom();
      assertThat(a.insert().execute()).isNotEqualTo(-1);
      authors.add(a);
      transaction.markSuccessful();
    } finally {
      transaction.close(); // Transactions should not throw on close().
    }

    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void querySubscribedToDuringTransactionThrows() {
    final Transaction transaction = SqliteMagic.newTransaction();
    selectAuthors.observe().subscribe(o);
    o.assertErrorContains("Cannot subscribe to observable query in a transaction.");
    transaction.end();
  }

  @Test
  public void querySubscribedToDuringTransactionThrowsWithBackpressure() {
    final ListQueryObservable<Author> query = selectAuthors.observe();

    final Transaction transaction = SqliteMagic.newTransaction();
    query.subscribe(o);
    o.assertErrorContains("Cannot subscribe to observable query in a transaction.");
    transaction.end();
  }

  @Test
  public void callingEndMultipleTimesThrows() {
    Transaction transaction = SqliteMagic.newTransaction();
    transaction.end();
    try {
      transaction.end();
      fail();
    } catch (IllegalStateException e) {
      assertThat(e).hasMessage("Not in transaction.");
    }
  }

  @Test
  public void querySubscribedToDuringTransactionOnDifferentThread() throws InterruptedException {
    List<Author> authors = insertAuthors(3);
    final Transaction transaction = SqliteMagic.newTransaction();

    final CountDownLatch latch = new CountDownLatch(1);
    new Thread() {
      @Override
      public void run() {
        selectAuthors.observe().subscribe(o);
        latch.countDown();
        o.dispose();
      }
    }.start();

    Thread.sleep(500); // Wait for the thread to block on initial query.
    o.assertNoMoreEvents();

    transaction.end(); // Allow other queries to continue.
    latch.await(500, MILLISECONDS); // Wait for thread to observe initial query.

    o.assertElements()
        .hasElements(authors)
        .isExhausted();
  }

  @Test
  public void synchronousQueryDuringTransaction() {
    List<Author> authors = insertAuthors(3);
    Transaction transaction = SqliteMagic.newTransaction();
    try {
      transaction.markSuccessful();
      assertThat(selectAuthors.execute()).containsExactlyElementsIn(authors);
    } finally {
      transaction.end();
    }
  }

  @Test
  public void synchronousQueryDuringTransactionSeesChanges() {
    List<Author> authors = insertAuthors(3);
    Transaction transaction = SqliteMagic.newTransaction();
    try {
      assertThat(selectAuthors.execute()).containsExactlyElementsIn(authors);

      Author a = Author.newRandom();
      assertThat(a.insert().execute()).isNotEqualTo(-1);
      authors.add(a);
      a = Author.newRandom();
      assertThat(a.insert().execute()).isNotEqualTo(-1);
      authors.add(a);

      assertThat(selectAuthors.execute()).containsExactlyElementsIn(authors);

      transaction.markSuccessful();
    } finally {
      transaction.end();
    }
  }

  @Test
  public void nestedTransactionsOnlyNotifyOnce() {
    List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    Transaction transactionOuter = SqliteMagic.newTransaction();
    try {
      Author a = Author.newRandom();
      assertThat(a.insert().execute()).isNotEqualTo(-1);
      authors.add(a);

      Transaction transactionInner = SqliteMagic.newTransaction();
      try {
        a = Author.newRandom();
        assertThat(a.insert().execute()).isNotEqualTo(-1);
        authors.add(a);
        transactionInner.markSuccessful();
      } finally {
        transactionInner.end();
      }

      transactionOuter.markSuccessful();
    } finally {
      transactionOuter.end();
    }

    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void nestedTransactionsOnMultipleTables() {
    final List<Magazine> magazines = insertComplexValues(3);
    selectMagazines.observe().subscribe(o);
    o.assertElements()
        .hasElements(magazines)
        .isExhausted();

    Transaction transactionOuter = SqliteMagic.newTransaction();
    try {

      Author a = Author.newRandom();
      Transaction transactionInner = SqliteMagic.newTransaction();
      try {
        assertThat(a.insert().execute()).isNotEqualTo(-1);
        transactionInner.markSuccessful();
      } finally {
        transactionInner.end();
      }

      transactionInner = SqliteMagic.newTransaction();
      try {
        final long aId = a.id;
        Magazine m = Magazine.newRandom();
        m.author = a;
        assertThat(m.persist().execute()).isNotEqualTo(-1);
        magazines.add(m);
        assertThat(m.author.id).isEqualTo(aId);
        transactionInner.markSuccessful();
      } finally {
        transactionInner.end();
      }

      transactionOuter.markSuccessful();
    } finally {
      transactionOuter.end();
    }

    o.assertElements()
        .hasElements(magazines)
        .isExhausted();

    o.dispose();
  }

  @Test
  public void emptyTransactionDoesNotNotify() {
    final List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    Transaction transaction = SqliteMagic.newTransaction();
    try {
      transaction.markSuccessful();
    } finally {
      transaction.end();
    }
    o.assertNoMoreEvents();

    o.dispose();
  }

  @Test
  public void transactionRollbackDoesNotNotify() {
    final List<Author> authors = insertAuthors(3);
    selectAuthors.observe().subscribe(o);
    o.assertElements()
        .hasElements(authors)
        .isExhausted();

    Transaction transaction = SqliteMagic.newTransaction();
    try {
      assertThat(Author.newRandom().insert().execute()).isNotEqualTo(-1);
      assertThat(Author.newRandom().insert().execute()).isNotEqualTo(-1);
      // No call to set successful.
    } finally {
      transaction.end();
    }
    o.assertNoMoreEvents();

    o.dispose();
  }
}
