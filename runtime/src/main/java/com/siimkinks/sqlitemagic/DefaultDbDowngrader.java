package com.siimkinks.sqlitemagic;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;

final class DefaultDbDowngrader implements DbDowngrader {
  @NonNull
  private final GeneratedDatabase database;

  DefaultDbDowngrader(@NonNull GeneratedDatabase database) {
    this.database = database;
  }

  @Override
  public void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
    if (SqliteMagic.LOGGING_ENABLED) {
      LogUtil.logDebug("Downgrading database from " + oldVersion + " to " + newVersion);
    }
    final Cursor c = db.query("SELECT name FROM sqlite_master " +
        "WHERE type='table' AND name != 'android_metadata' AND name NOT LIKE 'sqlite%'");
    try {
      while (c.moveToNext()) {
        final String tableName = c.getString(0);
        db.execSQL("DROP TABLE IF EXISTS " + tableName);
      }
    } finally {
      c.close();
    }

    database.createSchema(db);
  }
}
