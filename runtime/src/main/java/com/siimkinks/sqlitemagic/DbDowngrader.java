package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteDatabase;

public interface DbDowngrader {
  void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion);
}
