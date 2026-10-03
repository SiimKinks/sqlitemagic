package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import android.database.sqlite.SQLiteDatabase.CONFLICT_ROLLBACK
import androidx.annotation.IntDef

@IntDef(
  CONFLICT_ABORT,
  CONFLICT_FAIL,
  CONFLICT_IGNORE,
  CONFLICT_NONE,
  CONFLICT_REPLACE,
  CONFLICT_ROLLBACK
)
@Retention(AnnotationRetention.SOURCE)
annotation class ConflictAlgorithm {
  companion object {
    val CONFLICT_VALUES = arrayOf("", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE")
  }
}
