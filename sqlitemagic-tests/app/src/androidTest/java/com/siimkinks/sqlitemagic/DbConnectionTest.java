package com.siimkinks.sqlitemagic;

import android.database.Cursor;

import com.siimkinks.sqlitemagic.Select.SelectN;
import com.siimkinks.sqlitemagic.model.Author;
import com.siimkinks.sqlitemagic.model.RecordingObserver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.runner.AndroidJUnit4;
import io.reactivex.functions.Function;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE;
import static android.database.sqlite.SQLiteDatabase.CONFLICT_ROLLBACK;
import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
import static com.siimkinks.sqlitemagic.model.TestUtil.createVals;
import static com.siimkinks.sqlitemagic.model.TestUtil.updateVals;
import static java.util.Collections.singletonList;

@RunWith(AndroidJUnit4.class)
public final class DbConnectionTest {
  private final RecordingObserver defaultObserver = new RecordingObserver();
  private final RecordingObserver newObserver = new RecordingObserver();

  static final CompiledSelect<Author, SelectN> SELECT_AUTHORS = Select.from(AUTHOR).compile();
  private DbConnectionImpl newConnection;

  @Before
  public void setUp() {
    this.newConnection = (DbConnectionImpl) openNewConnection();
    Author.deleteTable().execute();
    Author.deleteTable().usingConnection(newConnection).execute();
  }

  @After
  public void tearDown() {
    defaultObserver.assertNoMoreEvents();
    newObserver.assertNoMoreEvents();
    assertTriggersHaveNoObservers(newConnection);
    newConnection.close();
    assertTriggersHaveNoObservers();
  }

  @Test
  public void closeConnection() {
    final TestObserver<List<Author>> ts = Select
        .from(AUTHOR)
        .usingConnection(newConnection)
        .observe()
        .runQuery()
        .test();
    final SupportSQLiteDatabase writableDatabase = newConnection.getWritableDatabase();
    assertThat(writableDatabase.isOpen()).isTrue();
    newConnection.close();
    assertThat(writableDatabase.isOpen()).isFalse();
    ts.assertComplete();
  }

  @Test
  public void selectFirst() {
    final TestObserver<Author> ts1 = SELECT_AUTHORS
        .takeFirst()
        .observe()
        .runQuery()
        .test();

    final TestObserver<Author> ts2 = Select
        .from(AUTHOR)
        .usingConnection(newConnection)
        .takeFirst()
        .observe()
        .runQuery()
        .test();

    assertEventsOnlyOnNewConnection(0, ts1, ts2);

    ts1.dispose();
    ts2.dispose();
  }

  @Test
  public void selectCount() {
    final TestObserver<Long> ts1 = SELECT_AUTHORS
        .count()
        .observe()
        .runQuery()
        .test();

    final TestObserver<Long> ts2 = Select
        .from(AUTHOR)
        .usingConnection(newConnection)
        .count()
        .observe()
        .runQuery()
        .test();

    assertEventsOnlyOnNewConnection(1, ts1, ts2);

    ts1.dispose();
    ts2.dispose();
  }

  @Test
  public void selectCursor() {
    final TestObserver<Cursor> ts1 = SELECT_AUTHORS
        .toCursor()
        .observe()
        .runQuery()
        .test();

    final TestObserver<Cursor> ts2 = Select
        .from(AUTHOR)
        .usingConnection(newConnection)
        .toCursor()
        .observe()
        .runQuery()
        .test();

    assertEventsOnlyOnNewConnection(1, ts1, ts2);

    ts1.dispose();
    ts2.dispose();
  }

  @Test
  public void selectColumnList() {
    final TestObserver<List<Long>> ts1 = Select
        .column(AUTHOR.ID)
        .from(AUTHOR)
        .observe()
        .runQuery()
        .test();

    final TestObserver<List<Long>> ts2 = Select
        .column(AUTHOR.ID)
        .from(AUTHOR)
        .usingConnection(newConnection)
        .observe()
        .runQuery()
        .test();

    assertEventsOnlyOnNewConnection(1, ts1, ts2);

    ts1.dispose();
    ts2.dispose();
  }

  @Test
  public void selectFirstColumn() {
    final TestObserver<Long> ts1 = Select
        .column(AUTHOR.ID)
        .from(AUTHOR)
        .takeFirst()
        .observe()
        .runQuery()
        .test();

    final TestObserver<Long> ts2 = Select
        .column(AUTHOR.ID)
        .from(AUTHOR)
        .usingConnection(newConnection)
        .takeFirst()
        .observe()
        .runQuery()
        .test();

    assertEventsOnlyOnNewConnection(0, ts1, ts2);

    ts1.dispose();
    ts2.dispose();
  }

  @Test
  public void selectColumnCursor() {
    final TestObserver<Cursor> ts1 = Select
        .column(AUTHOR.ID)
        .from(AUTHOR)
        .toCursor()
        .observe()
        .runQuery()
        .test();

    final TestObserver<Cursor> ts2 = Select
        .column(AUTHOR.ID)
        .from(AUTHOR)
        .usingConnection(newConnection)
        .toCursor()
        .observe()
        .runQuery()
        .test();

    assertEventsOnlyOnNewConnection(1, ts1, ts2);

    ts1.dispose();
    ts2.dispose();
  }

  @Test
  public void selectRaw() {
    final TestObserver<Cursor> ts1 = Select.raw("SELECT * FROM author")
        .from(AUTHOR)
        .observe()
        .runQuery()
        .test();

    final TestObserver<Cursor> ts2 = Select.raw("SELECT * FROM author")
        .from(AUTHOR)
        .usingConnection(newConnection)
        .observe()
        .runQuery()
        .test();

    assertEventsOnlyOnNewConnection(1, ts1, ts2);

    ts1.dispose();
    ts2.dispose();
  }

  @Test
  public void deleteBuilder() {
    Author.newRandom()
        .insert()
        .execute();
    Author.newRandom()
        .insert()
        .usingConnection(newConnection)
        .execute();

    assertThat(SELECT_AUTHORS.count().execute()).isEqualTo(1);
    assertThat(Select.from(AUTHOR).usingConnection(newConnection).count().execute()).isEqualTo(1);

    Delete.from(AUTHOR)
        .usingConnection(newConnection)
        .execute();

    assertThat(SELECT_AUTHORS.count().execute()).isEqualTo(1);
    assertThat(Select.from(AUTHOR).usingConnection(newConnection).count().execute()).isEqualTo(0);
  }

  @Test
  public void deleteBuilderObserve() {
    Author.newRandom()
        .insert()
        .execute();
    Author.newRandom()
        .insert()
        .usingConnection(newConnection)
        .execute();

    final TestObserver<Long> ts1 = SELECT_AUTHORS
        .count()
        .observe()
        .runQuery()
        .test();

    final TestObserver<Long> ts2 = Select
        .from(AUTHOR)
        .usingConnection(newConnection)
        .count()
        .observe()
        .runQuery()
        .test();

    ts1.assertValue(1L);
    ts2.assertValue(1L);

    Delete.from(AUTHOR)
        .usingConnection(newConnection)
        .execute();

    ts1.assertValue(1L);
    ts2.assertValues(1L, 0L);

    ts1.dispose();
    ts2.dispose();
  }

  @Test
  public void updateBuilder() {
    Author.newRandom()
        .insert()
        .execute();
    Author.newRandom()
        .insert()
        .usingConnection(newConnection)
        .execute();

    assertThat(SELECT_AUTHORS.count().execute()).isEqualTo(1);
    assertThat(Select.from(AUTHOR).usingConnection(newConnection).count().execute()).isEqualTo(1);

    Update.table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .usingConnection(newConnection)
        .execute();

    assertThat(Select
        .column(AUTHOR.NAME)
        .from(AUTHOR)
        .takeFirst()
        .execute())
        .isNotEqualTo("asd");
    assertThat(Select
        .column(AUTHOR.NAME)
        .from(AUTHOR)
        .usingConnection(newConnection)
        .takeFirst()
        .execute())
        .isEqualTo("asd");
  }

  @Test
  public void updateBuilderObserve() {
    final Author a1 = Author.newRandom();
    a1.insert().execute();
    final Author a2 = Author.newRandom();
    a2.insert().usingConnection(newConnection).execute();

    final TestObserver<String> ts1 = Select
        .column(AUTHOR.NAME)
        .from(AUTHOR)
        .takeFirst()
        .observe()
        .runQuery()
        .test();

    final TestObserver<String> ts2 = Select
        .column(AUTHOR.NAME)
        .from(AUTHOR)
        .usingConnection(newConnection)
        .takeFirst()
        .observe()
        .runQuery()
        .test();

    ts1.assertValue(a1.name);
    ts2.assertValue(a2.name);

    Update.table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .usingConnection(newConnection)
        .execute();

    ts1.assertValue(a1.name);
    ts2.assertValues(a2.name, "asd");

    ts1.dispose();
    ts2.dispose();
  }

  @Test
  public void insert() {
    assertOperationOnNewConnection(new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        final Author author = Author.newRandom();
        author.insert()
            .usingConnection(newConnection)
            .execute();
        return singletonList(author);
      }
    });
  }

  @Test
  public void insertWithConflictAlgorithm() {
    assertOperationOnNewConnection(new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        final Author author = Author.newRandom();
        author.insert()
            .conflictAlgorithm(CONFLICT_REPLACE)
            .usingConnection(newConnection)
            .execute();
        return singletonList(author);
      }
    });
  }

  @Test
  public void bulkInsert() {
    final List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    assertOperationOnNewConnection(new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        Author.insert(vals)
            .usingConnection(newConnection)
            .execute();
        return vals;
      }
    });
  }

  @Test
  public void update() {
    final Author initial = Author.newRandom();
    final long id = initial.insert()
        .usingConnection(newConnection)
        .execute();
    assertOperationOnNewConnection(singletonList(initial), new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        final Author val = Author.newRandom();
        val.id = id;
        val.update()
            .usingConnection(newConnection)
            .execute();

        return singletonList(val);
      }
    });
  }

  @Test
  public void updateWithConflictAlgorithm() {
    final Author initial = Author.newRandom();
    final long id = initial.insert()
        .usingConnection(newConnection)
        .execute();
    assertOperationOnNewConnection(singletonList(initial), new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        final Author val = Author.newRandom();
        val.id = id;
        val.update()
            .conflictAlgorithm(CONFLICT_ROLLBACK)
            .usingConnection(newConnection)
            .execute();

        return singletonList(val);
      }
    });
  }

  @Test
  public void bulkUpdate() {
    final List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    Author.insert(vals)
        .usingConnection(newConnection)
        .execute();

    final List<Author> updateVals = updateVals(vals, new Function<Author, Author>() {
      @Override
      public Author apply(Author author) {
        final Author val = Author.newRandom();
        val.id = author.id;
        return val;
      }
    });

    assertOperationOnNewConnection(vals, new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        Author.update(updateVals)
            .usingConnection(newConnection)
            .execute();
        return updateVals;
      }
    });
  }

  @Test
  public void persistWithInsert() {
    assertOperationOnNewConnection(new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        final Author author = Author.newRandom();
        author.persist()
            .usingConnection(newConnection)
            .execute();
        return singletonList(author);
      }
    });
  }

  @Test
  public void persistWithInsertIgnoringNull() {
    assertOperationOnNewConnection(new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        final Author author = Author.newRandom();
        author.name = null;
        author.persist()
            .ignoreNullValues()
            .usingConnection(newConnection)
            .execute();
        return singletonList(author);
      }
    });
  }

  @Test
  public void bulkPersistWithInsert() {
    final List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    assertOperationOnNewConnection(new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        Author.persist(vals)
            .usingConnection(newConnection)
            .execute();
        return vals;
      }
    });
  }

  @Test
  public void bulkPersistWithInsertIgnoringNull() {
    final List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        final Author author = Author.newRandom();
        author.name = null;
        return author;
      }
    });
    assertOperationOnNewConnection(new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        Author.persist(vals)
            .ignoreNullValues()
            .usingConnection(newConnection)
            .execute();
        return vals;
      }
    });
  }

  @Test
  public void persistWithUpdate() {
    final Author initial = Author.newRandom();
    final long id = initial.insert()
        .usingConnection(newConnection)
        .execute();
    assertOperationOnNewConnection(singletonList(initial), new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        final Author val = Author.newRandom();
        val.id = id;
        val.persist()
            .usingConnection(newConnection)
            .execute();

        return singletonList(val);
      }
    });
  }

  @Test
  public void persistWithUpdateIgnoringNull() {
    final Author initial = Author.newRandom();
    final long id = initial.insert()
        .usingConnection(newConnection)
        .execute();
    assertOperationOnNewConnection(singletonList(initial), new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        final Author val = Author.newRandom();
        val.id = id;
        val.persist()
            .ignoreNullValues()
            .usingConnection(newConnection)
            .execute();

        return singletonList(val);
      }
    });
  }

  @Test
  public void bulkPersistWithUpdate() {
    final List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    Author.insert(vals)
        .usingConnection(newConnection)
        .execute();

    final List<Author> updateVals = updateVals(vals, new Function<Author, Author>() {
      @Override
      public Author apply(Author author) {
        final Author val = Author.newRandom();
        val.id = author.id;
        return val;
      }
    });

    assertOperationOnNewConnection(vals, new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        Author.persist(updateVals)
            .usingConnection(newConnection)
            .execute();
        return updateVals;
      }
    });
  }

  @Test
  public void bulkPersistWithUpdateIgnoringNull() {
    final List<Author> vals = createVals(new Function<Integer, Author>() {
      @Override
      public Author apply(Integer integer) {
        return Author.newRandom();
      }
    });
    Author.insert(vals)
        .usingConnection(newConnection)
        .execute();

    final List<Author> updateVals = updateVals(vals, new Function<Author, Author>() {
      @Override
      public Author apply(Author author) {
        final Author val = Author.newRandom();
        val.id = author.id;
        return val;
      }
    });

    assertOperationOnNewConnection(vals, new Func0<List<Author>>() {
      @Override
      public List<Author> call() {
        Author.persist(updateVals)
            .ignoreNullValues()
            .usingConnection(newConnection)
            .execute();
        return updateVals;
      }
    });
  }

  private void assertEventsOnlyOnNewConnection(int initialValueCount,
                                               @NonNull TestObserver<?> defConnTs,
                                               @NonNull TestObserver<?> newConnTs) {
    defConnTs.assertValueCount(initialValueCount);
    newConnTs.assertValueCount(initialValueCount);

    Author.newRandom()
        .insert()
        .usingConnection(newConnection)
        .execute();

    defConnTs.assertValueCount(initialValueCount);
    newConnTs.assertValueCount(initialValueCount + 1);
  }

  private void assertOperationOnNewConnection(@NonNull Func0<List<Author>> operation) {
    SELECT_AUTHORS.observe()
        .subscribe(defaultObserver);

    Select.from(AUTHOR)
        .usingConnection(newConnection)
        .observe()
        .subscribe(newObserver);

    defaultObserver.assertElements().isExhausted();
    newObserver.assertElements().isExhausted();

    final List<Author> vals = operation.call();

    defaultObserver.assertNoMoreEvents();
    newObserver.assertElements()
        .hasElements(vals)
        .isExhausted();

    defaultObserver.dispose();
    newObserver.dispose();
  }

  private void assertOperationOnNewConnection(@NonNull List<Author> initialVal, @NonNull Func0<List<Author>> operation) {
    SELECT_AUTHORS.observe()
        .subscribe(defaultObserver);

    Select
        .from(AUTHOR)
        .usingConnection(newConnection)
        .observe()
        .subscribe(newObserver);

    defaultObserver.assertElements().isExhausted();
    newObserver.assertElements()
        .hasElements(initialVal)
        .isExhausted();

    final List<Author> vals = operation.call();

    defaultObserver.assertNoMoreEvents();
    newObserver.assertElements()
        .hasElements(vals)
        .isExhausted();

    defaultObserver.dispose();
    newObserver.dispose();
  }

  @NonNull
  private DbConnection openNewConnection() {
    return SqliteMagic
        .builder(TestApp.INSTANCE)
        .name("newConnection.db")
        .sqliteFactory(new FrameworkSQLiteOpenHelperFactory())
        .scheduleRxQueriesOn(Schedulers.trampoline())
        .openNewConnection();
  }
}
