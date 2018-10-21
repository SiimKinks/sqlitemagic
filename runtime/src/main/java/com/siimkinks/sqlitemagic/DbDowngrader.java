package com.siimkinks.sqlitemagic;

import androidx.sqlite.db.SupportSQLiteDatabase;

public interface DbDowngrader {
  void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion);
}
