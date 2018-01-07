package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.arch.persistence.db.SupportSQLiteStatement;
import android.database.sqlite.SQLiteTransactionListener;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.internal.StringArraySet;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.reactivex.Scheduler;
import io.reactivex.functions.Consumer;
import io.reactivex.subjects.PublishSubject;

import static com.siimkinks.sqlitemagic.SqlUtil.getNrOfTables;

/**
 * Note: some parts are forked from <a href="https://github.com/square/sqlbrite">sqlbrite</a>
 * and modified to extended functionality and improvements
 */
public class DbConnectionImpl implements DbConnection {
  @NonNull
  final SupportSQLiteOpenHelper dbHelper;
  @NonNull
  final Scheduler queryScheduler;

  final EntityDbManager[] entityDbManagers;
  final ThreadLocal<SqliteTransaction> transactions = new ThreadLocal<>();
  /**
   * Publishes sets of tables which have changed.
   */
  final PublishSubject<Set<String>> triggers = PublishSubject.create();

  private final Transaction transaction = new Transaction() {
    @Override
    public void markSuccessful() {
      if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TXN SUCCESS %s", transactions.get());
      getWritableDatabase().setTransactionSuccessful();
    }

    @Override
    public boolean yieldIfContendedSafely() {
      return getWritableDatabase().yieldIfContendedSafely();
    }

    @Override
    public boolean yieldIfContendedSafely(long sleepAmount, TimeUnit sleepUnit) {
      return getWritableDatabase().yieldIfContendedSafely(sleepUnit.toMillis(sleepAmount));
    }

    @Override
    public void end() {
      final SqliteTransaction transaction = transactions.get();
      if (transaction == null) {
        throw new IllegalStateException("Not in transaction.");
      }
      final SqliteTransaction newTransaction = transaction.parent;
      transactions.set(newTransaction);
      if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TXN END %s", transaction);
      getWritableDatabase().endTransaction();
      // Send the triggers after ending the transaction in the DB.
      if (transaction.commit && !transaction.isEmpty()) {
        sendTableTriggers(transaction);
      }
    }

    @Override
    public void close() {
      end();
    }
  };

  final Consumer<Object> ensureNotInTransaction = new Consumer<Object>() {
    @Override
    public void accept(Object __) throws Exception {
      if (transactions.get() != null) {
        throw new IllegalStateException("Cannot subscribe to observable query in a transaction.");
      }
    }
  };

  DbConnectionImpl(@NonNull SupportSQLiteOpenHelper dbHelper, @NonNull Scheduler queryScheduler) {
    this.dbHelper = dbHelper;
    this.queryScheduler = queryScheduler;
    final int nrOfTables = getNrOfTables();
    final EntityDbManager[] cachedEntityData = new EntityDbManager[nrOfTables];
    for (int i = 0; i < nrOfTables; i++) {
      cachedEntityData[i] = new EntityDbManager(this);
    }
    this.entityDbManagers = cachedEntityData;
  }

  @Override
  public final void close() {
    if (triggers.hasComplete()) {
      return;
    }
    triggers.onComplete();
    final EntityDbManager[] cachedEntityData = this.entityDbManagers;
    for (int i = 0, length = cachedEntityData.length; i < length; i++) {
      cachedEntityData[i].close();
      cachedEntityData[i] = null;
    }
    dbHelper.close();
    LogUtil.logInfo("Closed database [name=%s]", dbHelper.getDatabaseName());
  }

  @NonNull
  @Override
  public final Transaction newTransaction() {
    final SqliteTransaction transaction = new SqliteTransaction(transactions.get());
    transactions.set(transaction);
    if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TXN BEGIN %s", transaction);
    getWritableDatabase().beginTransactionWithListener(transaction);

    return this.transaction;
  }

  @Override
  public final void clearData() {
    final String[] triggers = SqlUtil.clearData(getWritableDatabase());
    if (triggers != null) {
      sendTableTriggers(triggers);
    }
  }

  SupportSQLiteDatabase getReadableDatabase() {
    return dbHelper.getReadableDatabase();
  }

  SupportSQLiteDatabase getWritableDatabase() {
    return dbHelper.getWritableDatabase();
  }

  @NonNull
  @CheckResult
  public final EntityDbManager getEntityDbManager(int tablePos) {
    return entityDbManagers[tablePos];
  }

  SupportSQLiteStatement compileStatement(@NonNull final String sql) {
    return getWritableDatabase().compileStatement(sql);
  }

  void sendTableTrigger(@NonNull String table) {
    final SqliteTransaction transaction = transactions.get();
    if (transaction != null) {
      transaction.add(table);
    } else {
      final Set<String> tablesCollection = Collections.singleton(table);
      if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TRIGGER %s", tablesCollection);
      triggers.onNext(tablesCollection);
    }
  }

  void sendTableTriggers(@NonNull String... tables) {
    final SqliteTransaction transaction = transactions.get();
    if (transaction != null) {
      transaction.addAll(tables);
    } else {
      final StringArraySet tablesCollection = new StringArraySet(tables);
      if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TRIGGER %s", tablesCollection);
      triggers.onNext(tablesCollection);
    }
  }

  void sendTableTriggers(@NonNull StringArraySet tables) {
    final SqliteTransaction transaction = transactions.get();
    if (transaction != null) {
      transaction.addAll(tables);
    } else {
      if (SqliteMagic.LOGGING_ENABLED) LogUtil.logDebug("TRIGGER %s", tables);
      triggers.onNext(tables);
    }
  }

  private static final class SqliteTransaction extends StringArraySet implements SQLiteTransactionListener {
    final SqliteTransaction parent;
    boolean commit;

    SqliteTransaction(SqliteTransaction parent) {
      this.parent = parent;
    }

    @Override
    public void onBegin() {
    }

    @Override
    public void onCommit() {
      commit = true;
    }

    @Override
    public void onRollback() {
    }

    @Override
    public String toString() {
      String name = String.format("%08x", System.identityHashCode(this));
      return parent == null ? name : name + " [" + parent.toString() + ']';
    }
  }
}
