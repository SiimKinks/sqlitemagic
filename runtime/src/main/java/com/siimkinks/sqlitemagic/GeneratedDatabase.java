package com.siimkinks.sqlitemagic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.siimkinks.sqlitemagic.internal.StringArraySet;

public interface GeneratedDatabase {
  void configureDatabase(SupportSQLiteDatabase db);

  void createSchema(SupportSQLiteDatabase db);

  StringArraySet clearData(SupportSQLiteDatabase db);

  void migrateViews(SupportSQLiteDatabase db);

  String[] getSubmoduleNames();

  int getNrOfTables(@Nullable String moduleName);

  int getDbVersion();

  String getDbName();

  <V> Column<V, V, V, ?, NotNullable> columnForValue(@NonNull V val);

  boolean isDebug();
}
