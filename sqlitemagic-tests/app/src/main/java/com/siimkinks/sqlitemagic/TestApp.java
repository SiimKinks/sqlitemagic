package com.siimkinks.sqlitemagic;

import android.app.Application;
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory;
import android.support.annotation.NonNull;

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
