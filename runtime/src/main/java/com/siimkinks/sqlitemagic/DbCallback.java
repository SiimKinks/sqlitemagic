package com.siimkinks.sqlitemagic;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

final class DbCallback extends SupportSQLiteOpenHelper.Callback {

  private final Context context;
  @NonNull
  private final GeneratedDatabase database;
  @NonNull
  private final DbDowngrader downgrader;

  DbCallback(
      @NonNull Context context,
      int version,
      @NonNull GeneratedDatabase database,
      @NonNull DbDowngrader downgrader
  ) {
    super(version);
    this.context = context;
    this.database = database;
    this.downgrader = downgrader;
  }

  @Override
  public void onCreate(@NonNull SupportSQLiteDatabase db) {
    database.createSchema(db);
  }

  // this method already runs in transaction
  @Override
  public void onUpgrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
    try {
      if (SqliteMagic.LOGGING_ENABLED) {
        LogUtil.logDebug("Executing upgrade scripts");
      }
      final AssetManager assets = context.getAssets();
      final String[] submoduleNames = database.getSubmoduleNames();
      for (int i = oldVersion; i < newVersion; i++) {
        final int version = i + 1;
        final String fileName = version + ".sql";
        if (submoduleNames != null) {
          final int submodulesCount = submoduleNames.length;
          for (int j = 0; j < submodulesCount; j++) {
            runMigrationScript(db, assets, submoduleNames[j] + fileName);
          }
        }
        runMigrationScript(db, assets, fileName);
      }
      database.migrateViews(db);
    } catch (IOException ioe) {
      LogUtil.logError("Error executing upgrade scripts");
      throw new RuntimeException(ioe);
    }
  }

  private void runMigrationScript(SupportSQLiteDatabase db, AssetManager assets, String fileName) throws IOException {
    BufferedReader bfr = null;
    try {
      bfr = new BufferedReader(new InputStreamReader(assets.open(fileName)));
      if (SqliteMagic.LOGGING_ENABLED) {
        LogUtil.logDebug("Executing script %s", fileName);
      }
      String sql;
      while ((sql = bfr.readLine()) != null) {
        db.execSQL(sql);
      }
    } catch (Throwable e) {
      // ignore
    } finally {
      if (bfr != null) {
        bfr.close();
      }
    }
  }

  @Override
  public void onDowngrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
    downgrader.onDowngrade(db, oldVersion, newVersion);
  }

  @Override
  public void onConfigure(@NonNull SupportSQLiteDatabase db) {
    database.configureDatabase(db);
  }
}
