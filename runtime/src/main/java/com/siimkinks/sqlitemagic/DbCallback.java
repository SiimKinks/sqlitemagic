package com.siimkinks.sqlitemagic;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
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

  @Override
  public void onOpen(@NonNull SupportSQLiteDatabase db) {
    super.onOpen(db);
    database.createTemporarySchema(db);
  }

  // this method already runs in transaction
  @Override
  public void onUpgrade(@NonNull SupportSQLiteDatabase db, int oldVersion, int newVersion) {
    if (SqliteMagic.LOGGING_ENABLED) {
      LogUtil.logDebug("Executing upgrade scripts");
    }
    final AssetManager assets = context.getAssets();
    final String[] submoduleNames = database.getSubmoduleNames();
    for (int i = oldVersion; i < newVersion; i++) {
      final int version = i + 1;
      final String fileName = version + ".sql";
      if (submoduleNames != null) {
        for (String submoduleName : submoduleNames) {
          runMigrationScript(db, assets, submoduleName + fileName);
        }
      }
      runMigrationScript(db, assets, fileName);
    }
    database.migrateViews(db);
  }

  private void runMigrationScript(SupportSQLiteDatabase db, AssetManager assets, String fileName) {
    final InputStream inputStream;
    try {
      inputStream = assets.open(fileName);
    } catch (FileNotFoundException ignored) {
      return;
    } catch (IOException e) {
      throw new IllegalStateException("Error opening migration script " + fileName, e);
    }

    int lineNumber = 0;
    try (BufferedReader bfr = new BufferedReader(new InputStreamReader(inputStream))) {
      if (SqliteMagic.LOGGING_ENABLED) {
        LogUtil.logDebug("Executing script %s", fileName);
      }
      String sql;
      while ((sql = bfr.readLine()) != null) {
        lineNumber++;
        try {
          db.execSQL(sql);
        } catch (RuntimeException e) {
          throw migrationException(fileName, lineNumber, e);
        }
      }
    } catch (IOException e) {
      throw migrationException(fileName, Math.max(1, lineNumber), e);
    }
  }

  private IllegalStateException migrationException(String fileName, int lineNumber, Exception cause) {
    return new IllegalStateException(
        "Error executing migration script " + fileName + " at line " + lineNumber,
        cause
    );
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
