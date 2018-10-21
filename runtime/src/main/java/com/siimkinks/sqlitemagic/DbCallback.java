package com.siimkinks.sqlitemagic;

import android.content.Context;
import android.content.res.AssetManager;

import com.siimkinks.sqlitemagic.annotation.internal.Invokes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import androidx.annotation.NonNull;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;

import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_PROCESSOR_DID_NOT_RUN;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_CONFIGURE_DATABASE;

final class DbCallback extends SupportSQLiteOpenHelper.Callback {

  private final Context context;
  @NonNull
  private final DbDowngrader downgrader;

  DbCallback(@NonNull Context context, int version, @NonNull DbDowngrader downgrader) {
    super(version);
    this.context = context;
    this.downgrader = downgrader;
  }

  @Override
  public void onCreate(SupportSQLiteDatabase db) {
    SqlUtil.createSchema(db);
  }

  // this method already runs in transaction
  @Override
  public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
    try {
      if (SqliteMagic.LOGGING_ENABLED) {
        LogUtil.logDebug("Executing upgrade scripts");
      }
      final AssetManager assets = context.getAssets();
      final String[] submoduleNames = SqlUtil.getSubmoduleNames();
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
      SqlUtil.migrateViews(db);
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
  public void onDowngrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
    downgrader.onDowngrade(db, oldVersion, newVersion);
  }

  @Invokes(INVOCATION_METHOD_CONFIGURE_DATABASE)
  @Override
  public void onConfigure(SupportSQLiteDatabase db) {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }
}
