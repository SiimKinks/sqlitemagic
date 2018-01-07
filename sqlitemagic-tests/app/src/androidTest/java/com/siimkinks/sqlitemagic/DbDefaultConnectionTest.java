package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory;
import android.support.test.runner.AndroidJUnit4;

import com.siimkinks.sqlitemagic.model.Author;
import com.siimkinks.sqlitemagic.model.Magazine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.InternalTester.assertTriggersHaveNoObservers;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertAuthors;
import static com.siimkinks.sqlitemagic.model.TestUtil.insertMagazines;
import static org.junit.Assert.fail;

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
  public void missingContextThrows() {
    try {
      SqliteMagic.builder(null);
      fail("Creating new connection without context did not throw");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).isNotEmpty();
    }
  }

  @Test
  public void missingSchedulerThrows() {
    try {
      SqliteMagic.builder(TestApp.INSTANCE)
          .scheduleRxQueriesOn(null);
      fail("Creating new connection without scheduler did not throw");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).isNotEmpty();
    }
  }

  @Test
  public void missingFactoryThrowsWhenBuildingDefaultConnection() {
    try {
      SqliteMagic
          .builder(TestApp.INSTANCE)
          .openDefaultConnection();
      fail("Creating new connection without factory did not throw");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).isNotEmpty();
    }
  }

  @Test
  public void missingFactoryThrowsWhenBuildingNewConnection() {
    try {
      SqliteMagic
          .builder(TestApp.INSTANCE)
          .openNewConnection();
      fail("Creating new connection without factory did not throw");
    } catch (NullPointerException e) {
      assertThat(e.getMessage()).isNotEmpty();
    }
  }

  @Test
  public void reInitClosesPrev() {
    final DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();
    final SupportSQLiteOpenHelper dbHelper = dbConnection.dbHelper;
    final SupportSQLiteDatabase readableDatabase = dbHelper.getReadableDatabase();
    final SupportSQLiteDatabase writableDatabase = dbHelper.getWritableDatabase();

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

  @Test
  public void clearData() {
    insertMagazines(10);
    assertThat(Select.from(MAGAZINE).count().execute()).isGreaterThan(0L);
    assertThat(Select.from(AUTHOR).count().execute()).isGreaterThan(0L);

    final TestObserver<List<Magazine>> ts = Select
        .from(MAGAZINE)
        .observe()
        .runQuery()
        .skip(1)
        .take(1)
        .test();

    SqliteMagic.getDefaultConnection().clearData();

    ts.awaitTerminalEvent(2, TimeUnit.SECONDS);
    ts.assertNoErrors()
        .assertValue(Collections.<Magazine>emptyList());

    assertThat(Select.from(MAGAZINE).execute()).isEmpty();
    assertThat(Select.from(AUTHOR).execute()).isEmpty();
  }

  private void initDbWithNewConnection() {
    SqliteMagic.builder(TestApp.INSTANCE)
        .name("new.db")
        .sqliteFactory(new FrameworkSQLiteOpenHelperFactory())
        .scheduleRxQueriesOn(Schedulers.trampoline())
        .openDefaultConnection();
  }
}
