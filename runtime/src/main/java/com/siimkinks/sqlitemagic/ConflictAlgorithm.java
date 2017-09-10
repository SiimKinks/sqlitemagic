package com.siimkinks.sqlitemagic;

import android.support.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT;
import static android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL;
import static android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE;
import static android.database.sqlite.SQLiteDatabase.CONFLICT_NONE;
import static android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE;
import static android.database.sqlite.SQLiteDatabase.CONFLICT_ROLLBACK;

@IntDef({
    CONFLICT_ABORT,
    CONFLICT_FAIL,
    CONFLICT_IGNORE,
    CONFLICT_NONE,
    CONFLICT_REPLACE,
    CONFLICT_ROLLBACK
})
@Retention(RetentionPolicy.SOURCE)
public @interface ConflictAlgorithm {
  String[] CONFLICT_VALUES = new String[]{"", " OR ROLLBACK", " OR ABORT", " OR FAIL", " OR IGNORE", " OR REPLACE"};
}
