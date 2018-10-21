package com.siimkinks.sqlitemagic.sample;

import android.app.Application;
import android.arch.persistence.db.framework.FrameworkSQLiteOpenHelperFactory;

import com.siimkinks.sqlitemagic.SqliteMagic;

public final class SampleApp extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    SqliteMagic.builder(this)
        .sqliteFactory(new FrameworkSQLiteOpenHelperFactory())
        .openDefaultConnection();
  }
}
