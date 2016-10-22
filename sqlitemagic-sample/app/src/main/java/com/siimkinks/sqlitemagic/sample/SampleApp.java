package com.siimkinks.sqlitemagic.sample;

import android.app.Application;

import com.siimkinks.sqlitemagic.SqliteMagic;

public final class SampleApp extends Application {
  @Override
  public void onCreate() {
    super.onCreate();
    SqliteMagic.init(this);
  }
}
