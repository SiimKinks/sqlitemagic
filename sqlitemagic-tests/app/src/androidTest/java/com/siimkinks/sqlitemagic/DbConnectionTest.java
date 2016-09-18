package com.siimkinks.sqlitemagic;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.support.annotation.NonNull;
import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.Select.SelectN;
import com.siimkinks.sqlitemagic.model.Author;
import com.siimkinks.sqlitemagic.model.RecordingObserver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import rx.Subscription;
import rx.functions.Func0;
import rx.functions.Func1;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE;
import static android.database.sqlite.SQLiteDatabase.CONFLICT_ROLLBACK;
import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
import static com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder.setupDatabase;
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
		final TestSubscriber<List<Author>> ts = new TestSubscriber<>();
		final Subscription subscription = Select
				.from(AUTHOR)
				.usingConnection(newConnection)
				.observe()
				.runQuery()
				.subscribe(ts);
		final SQLiteDatabase writableDatabase = newConnection.getWritableDatabase();
		assertThat(writableDatabase.isOpen()).isTrue();
		newConnection.close();
		assertThat(writableDatabase.isOpen()).isFalse();
		ts.assertCompleted();
		assertThat(subscription.isUnsubscribed()).isTrue();
	}

	@Test
	public void selectFirst() {
		final TestSubscriber<Author> ts1 = new TestSubscriber<>();
		final Subscription s1 = SELECT_AUTHORS
				.takeFirst()
				.observe()
				.runQuery()
				.subscribe(ts1);

		final TestSubscriber<Author> ts2 = new TestSubscriber<>();
		final Subscription s2 = Select
				.from(AUTHOR)
				.usingConnection(newConnection)
				.takeFirst()
				.observe()
				.runQuery()
				.subscribe(ts2);

		assertEventsOnlyOnNewConnection(0, ts1, ts2);

		s1.unsubscribe();
		s2.unsubscribe();
	}

	@Test
	public void selectCount() {
		final TestSubscriber<Long> ts1 = new TestSubscriber<>();
		final Subscription s1 = SELECT_AUTHORS
				.count()
				.observe()
				.runQuery()
				.subscribe(ts1);

		final TestSubscriber<Long> ts2 = new TestSubscriber<>();
		final Subscription s2 = Select
				.from(AUTHOR)
				.usingConnection(newConnection)
				.count()
				.observe()
				.runQuery()
				.subscribe(ts2);

		assertEventsOnlyOnNewConnection(1, ts1, ts2);

		s1.unsubscribe();
		s2.unsubscribe();
	}

	@Test
	public void selectCursor() {
		final TestSubscriber<Cursor> ts1 = new TestSubscriber<>();
		final Subscription s1 = SELECT_AUTHORS
				.toCursor()
				.observe()
				.runQuery()
				.subscribe(ts1);

		final TestSubscriber<Cursor> ts2 = new TestSubscriber<>();
		final Subscription s2 = Select
				.from(AUTHOR)
				.usingConnection(newConnection)
				.toCursor()
				.observe()
				.runQuery()
				.subscribe(ts2);

		assertEventsOnlyOnNewConnection(1, ts1, ts2);

		s1.unsubscribe();
		s2.unsubscribe();
	}

	@Test
	public void selectColumnList() {
		final TestSubscriber<List<Long>> ts1 = new TestSubscriber<>();
		final Subscription s1 = Select
				.column(AUTHOR.ID)
				.from(AUTHOR)
				.observe()
				.runQuery()
				.subscribe(ts1);

		final TestSubscriber<List<Long>> ts2 = new TestSubscriber<>();
		final Subscription s2 = Select
				.column(AUTHOR.ID)
				.from(AUTHOR)
				.usingConnection(newConnection)
				.observe()
				.runQuery()
				.subscribe(ts2);

		assertEventsOnlyOnNewConnection(1, ts1, ts2);

		s1.unsubscribe();
		s2.unsubscribe();
	}

	@Test
	public void selectFirstColumn() {
		final TestSubscriber<Long> ts1 = new TestSubscriber<>();
		final Subscription s1 = Select
				.column(AUTHOR.ID)
				.from(AUTHOR)
				.takeFirst()
				.observe()
				.runQuery()
				.subscribe(ts1);

		final TestSubscriber<Long> ts2 = new TestSubscriber<>();
		final Subscription s2 = Select
				.column(AUTHOR.ID)
				.from(AUTHOR)
				.usingConnection(newConnection)
				.takeFirst()
				.observe()
				.runQuery()
				.subscribe(ts2);

		assertEventsOnlyOnNewConnection(0, ts1, ts2);

		s1.unsubscribe();
		s2.unsubscribe();
	}

	@Test
	public void selectColumnCursor() {
		final TestSubscriber<Cursor> ts1 = new TestSubscriber<>();
		final Subscription s1 = Select
				.column(AUTHOR.ID)
				.from(AUTHOR)
				.toCursor()
				.observe()
				.runQuery()
				.subscribe(ts1);

		final TestSubscriber<Cursor> ts2 = new TestSubscriber<>();
		final Subscription s2 = Select
				.column(AUTHOR.ID)
				.from(AUTHOR)
				.usingConnection(newConnection)
				.toCursor()
				.observe()
				.runQuery()
				.subscribe(ts2);

		assertEventsOnlyOnNewConnection(1, ts1, ts2);

		s1.unsubscribe();
		s2.unsubscribe();
	}

	@Test
	public void selectRaw() {
		final TestSubscriber<Cursor> ts1 = new TestSubscriber<>();
		final Subscription s1 = Select.raw("SELECT * FROM author")
				.from(AUTHOR)
				.observe()
				.runQuery()
				.subscribe(ts1);

		final TestSubscriber<Cursor> ts2 = new TestSubscriber<>();
		final Subscription s2 = Select.raw("SELECT * FROM author")
				.from(AUTHOR)
				.usingConnection(newConnection)
				.observe()
				.runQuery()
				.subscribe(ts2);

		assertEventsOnlyOnNewConnection(1, ts1, ts2);

		s1.unsubscribe();
		s2.unsubscribe();
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

		final TestSubscriber<Long> ts1 = new TestSubscriber<>();
		final Subscription s1 = SELECT_AUTHORS
				.count()
				.observe()
				.runQuery()
				.subscribe(ts1);

		final TestSubscriber<Long> ts2 = new TestSubscriber<>();
		final Subscription s2 = Select
				.from(AUTHOR)
				.usingConnection(newConnection)
				.count()
				.observe()
				.runQuery()
				.subscribe(ts2);

		ts1.assertValue(1L);
		ts2.assertValue(1L);

		Delete.from(AUTHOR)
				.usingConnection(newConnection)
				.execute();

		ts1.assertValue(1L);
		ts2.assertValues(1L, 0L);

		s1.unsubscribe();
		s2.unsubscribe();
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
				.set(AUTHOR.NAME, "asd")
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

		final TestSubscriber<String> ts1 = new TestSubscriber<>();
		final Subscription s1 = Select
				.column(AUTHOR.NAME)
				.from(AUTHOR)
				.takeFirst()
				.observe()
				.runQuery()
				.subscribe(ts1);

		final TestSubscriber<String> ts2 = new TestSubscriber<>();
		final Subscription s2 = Select
				.column(AUTHOR.NAME)
				.from(AUTHOR)
				.usingConnection(newConnection)
				.takeFirst()
				.observe()
				.runQuery()
				.subscribe(ts2);

		ts1.assertValue(a1.name);
		ts2.assertValue(a2.name);

		Update.table(AUTHOR)
				.set(AUTHOR.NAME, "asd")
				.usingConnection(newConnection)
				.execute();

		ts1.assertValue(a1.name);
		ts2.assertValues(a2.name, "asd");

		s1.unsubscribe();
		s2.unsubscribe();
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
		final List<Author> vals = createVals(new Func1<Integer, Author>() {
			@Override
			public Author call(Integer integer) {
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
		final List<Author> vals = createVals(new Func1<Integer, Author>() {
			@Override
			public Author call(Integer integer) {
				return Author.newRandom();
			}
		});
		Author.insert(vals)
				.usingConnection(newConnection)
				.execute();

		final List<Author> updateVals = updateVals(vals, new Func1<Author, Author>() {
			@Override
			public Author call(Author author) {
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
		final List<Author> vals = createVals(new Func1<Integer, Author>() {
			@Override
			public Author call(Integer integer) {
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
		final List<Author> vals = createVals(new Func1<Integer, Author>() {
			@Override
			public Author call(Integer integer) {
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
		final List<Author> vals = createVals(new Func1<Integer, Author>() {
			@Override
			public Author call(Integer integer) {
				return Author.newRandom();
			}
		});
		Author.insert(vals)
				.usingConnection(newConnection)
				.execute();

		final List<Author> updateVals = updateVals(vals, new Func1<Author, Author>() {
			@Override
			public Author call(Author author) {
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
		final List<Author> vals = createVals(new Func1<Integer, Author>() {
			@Override
			public Author call(Integer integer) {
				return Author.newRandom();
			}
		});
		Author.insert(vals)
				.usingConnection(newConnection)
				.execute();

		final List<Author> updateVals = updateVals(vals, new Func1<Author, Author>() {
			@Override
			public Author call(Author author) {
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
	                                             @NonNull TestSubscriber<?> defConnTs,
	                                             @NonNull TestSubscriber<?> newConnTs) {
		defConnTs.assertValueCount(initialValueCount);
		newConnTs.assertValueCount(initialValueCount);

		final Author author = Author.newRandom();
		author.insert().usingConnection(newConnection).execute();

		defConnTs.assertValueCount(initialValueCount);
		newConnTs.assertValueCount(initialValueCount + 1);
	}

	private void assertOperationOnNewConnection(@NonNull Func0<List<Author>> operation) {
		final Subscription defS = SELECT_AUTHORS.observe()
				.subscribe(defaultObserver);

		final Subscription newS = Select
				.from(AUTHOR)
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

		defS.unsubscribe();
		newS.unsubscribe();
	}

	private void assertOperationOnNewConnection(@NonNull List<Author> initialVal, @NonNull Func0<List<Author>> operation) {
		final Subscription defS = SELECT_AUTHORS.observe()
				.subscribe(defaultObserver);

		final Subscription newS = Select
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

		defS.unsubscribe();
		newS.unsubscribe();
	}

	@NonNull
	private DbConnection openNewConnection() {
		return SqliteMagic.openNewConnection(setupDatabase()
				.withName("newConnection.db")
				.scheduleRxQueriesOn(Schedulers.immediate()));
	}
}
