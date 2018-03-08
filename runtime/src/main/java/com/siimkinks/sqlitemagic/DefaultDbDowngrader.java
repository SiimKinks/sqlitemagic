package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.database.Cursor;

final class DefaultDbDowngrader implements DbDowngrader {
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

    SqlUtil.createSchema(db);
  }
}
