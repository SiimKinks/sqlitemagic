package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteDatabase;
import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.model.Author;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import rx.Subscription;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
import static com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder.setupDatabase;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;

@RunWith(AndroidJUnit4.class)
public final class DbDefaultConnectionTest {
	@Before
	public void setUp() {
		Author.deleteTable().execute();
	}

	@After
	public void tearDown() {
		Author.deleteTable().execute();
		final SqliteMagic instance = SqliteMagic.SingletonHolder.instance;
		TestApp.initDb(instance.context);
	}

	@Test
	public void reInitClosesPrev() {
		final SqliteMagic instance = SqliteMagic.SingletonHolder.instance;
		final DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();
		final DbHelper dbHelper = dbConnection.dbHelper;
		final SQLiteDatabase readableDatabase = dbHelper.getReadableDatabase();
		final SQLiteDatabase writableDatabase = dbHelper.getWritableDatabase();

		initDbWithNewConnection(instance);

		assertThat(readableDatabase.isOpen()).isFalse();
		assertThat(writableDatabase.isOpen()).isFalse();
		assertThat(dbConnection.triggers.hasObservers()).isFalse();
		assertThat(dbConnection.triggers.hasCompleted()).isTrue();
	}

	@Test
	public void reInitCompletesQueries() {
		final SqliteMagic instance = SqliteMagic.SingletonHolder.instance;
		final DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();

		final TestSubscriber<List<Author>> ts = new TestSubscriber<>();
		final Subscription subscription = Select
				.from(AUTHOR)
				.observe()
				.runQuery()
				.subscribe(ts);
		final ArrayList<Author> authors = insertAuthors(5);

		initDbWithNewConnection(instance);

		insertAuthors(5);

		ts.awaitTerminalEvent();
		ts.assertValues(Collections.<Author>emptyList(), authors);
		ts.assertCompleted();
		ts.assertNoErrors();
		assertThat(subscription.isUnsubscribed()).isTrue();
		assertThat(dbConnection.triggers.hasObservers()).isFalse();
	}

	@Test
	public void queryAfterReInit() {
		final SqliteMagic instance = SqliteMagic.SingletonHolder.instance;
		final DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();

		final TestSubscriber<List<Author>> tsBefore = new TestSubscriber<>();
		final Subscription subscriptionBefore = Select
				.from(AUTHOR)
				.observe()
				.runQuery()
				.subscribe(tsBefore);
		final ArrayList<Author> authorsBefore = insertAuthors(5);

		initDbWithNewConnection(instance);

		final TestSubscriber<List<Author>> tsAfter = new TestSubscriber<>();
		final Subscription subscriptionAfter = Select
				.from(AUTHOR)
				.observe()
				.runQuery()
				.take(2)
				.subscribe(tsAfter);
		final ArrayList<Author> authorsAfter = insertAuthors(5);

		tsBefore.awaitTerminalEvent();
		tsBefore.assertValues(Collections.<Author>emptyList(), authorsBefore);
		tsBefore.assertCompleted();
		tsBefore.assertNoErrors();
		assertThat(subscriptionBefore.isUnsubscribed()).isTrue();
		assertThat(dbConnection.triggers.hasObservers()).isFalse();

		tsAfter.awaitTerminalEvent();
		tsAfter.assertValues(Collections.<Author>emptyList(), authorsAfter);
		tsAfter.assertCompleted();
		tsAfter.assertNoErrors();
		assertThat(subscriptionAfter.isUnsubscribed()).isTrue();
		assertTriggersHaveNoObservers();
	}

	private void initDbWithNewConnection(SqliteMagic instance) {
		SqliteMagic.init(instance.context, setupDatabase()
				.withName("new.db")
				.scheduleRxQueriesOn(Schedulers.immediate()));
	}
}
