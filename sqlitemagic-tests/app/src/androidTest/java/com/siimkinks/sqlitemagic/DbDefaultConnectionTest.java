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

import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
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
    TestApp.initDb(TestApp.INSTANCE);
  }

  @Test
  public void reInitClosesPrev() {
    final DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();
    final DbHelper dbHelper = dbConnection.dbHelper;
    final SQLiteDatabase readableDatabase = dbHelper.getReadableDatabase();
    final SQLiteDatabase writableDatabase = dbHelper.getWritableDatabase();

    initDbWithNewConnection();

    assertThat(readableDatabase.isOpen()).isFalse();
    assertThat(writableDatabase.isOpen()).isFalse();
    assertThat(dbConnection.triggers.hasObservers()).isFalse();
    assertThat(dbConnection.triggers.hasComplete()).isTrue();
  }

  @Test
  public void reInitCompletesQueries() {
    final DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();

    final TestObserver<List<Author>> ts = Select
        .from(AUTHOR)
        .observe()
        .runQuery()
        .test();
    final ArrayList<Author> authors = insertAuthors(5);

    initDbWithNewConnection();

    insertAuthors(5);

    ts.awaitTerminalEvent();
    ts.assertValues(Collections.<Author>emptyList(), authors);
    ts.assertComplete();
    ts.assertNoErrors();
    assertThat(dbConnection.triggers.hasObservers()).isFalse();
  }

  @Test
  public void queryAfterReInit() {
    final DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();

    final TestObserver<List<Author>> tsBefore = Select
        .from(AUTHOR)
        .observe()
        .runQuery()
        .test();
    final ArrayList<Author> authorsBefore = insertAuthors(5);

    initDbWithNewConnection();

    final TestObserver<List<Author>> tsAfter = Select
        .from(AUTHOR)
        .observe()
        .runQuery()
        .take(2)
        .test();
    final ArrayList<Author> authorsAfter = insertAuthors(5);

    tsBefore.awaitTerminalEvent();
    tsBefore.assertValues(Collections.<Author>emptyList(), authorsBefore);
    tsBefore.assertComplete();
    tsBefore.assertNoErrors();
    assertThat(dbConnection.triggers.hasObservers()).isFalse();

    tsAfter.awaitTerminalEvent();
    tsAfter.assertValues(Collections.<Author>emptyList(), authorsAfter);
    tsAfter.assertComplete();
    tsAfter.assertNoErrors();
    assertTriggersHaveNoObservers();
  }

  private void initDbWithNewConnection() {
    SqliteMagic.setup(TestApp.INSTANCE)
        .withName("new.db")
        .scheduleRxQueriesOn(Schedulers.trampoline())
        .init();
  }
}
