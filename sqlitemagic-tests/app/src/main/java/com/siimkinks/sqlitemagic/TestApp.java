package com.siimkinks.sqlitemagic;

import android.app.Application;
import android.support.annotation.NonNull;

import rx.schedulers.Schedulers;

public class TestApp extends Application {
  public static Application INSTANCE;

  @Override
  public void onCreate() {
    super.onCreate();
    INSTANCE = this;
    initDb(this);
  }

  public static void initDb(@NonNull Application app) {
    SqliteMagic.setLoggingEnabled(true);
    SqliteMagic.setup(app)
        .scheduleRxQueriesOn(Schedulers.immediate())
        .init();
  }

}
