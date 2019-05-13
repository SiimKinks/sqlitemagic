package com.siimkinks.sqlitemagic;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;

import io.reactivex.schedulers.Schedulers;

public class TestApp extends Application {
  public static Application INSTANCE;

  @Override
  public void onCreate() {
    super.onCreate();
    INSTANCE = this;
    deleteDatabase(SqlUtil.getDbName());
    initDb(this);
  }

  public static void initDb(@NonNull Application app) {
    SqliteMagic.setLoggingEnabled(true);
    SqliteMagic.builder(app)
        .sqliteFactory(new FrameworkSQLiteOpenHelperFactory())
        .scheduleRxQueriesOn(Schedulers.trampoline())
        .openDefaultConnection();
  }
}
