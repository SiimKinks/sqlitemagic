package com.siimkinks.sqlitemagic;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.db.SupportSQLiteOpenHelper;
import android.content.Context;
import android.content.res.AssetManager;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.annotation.internal.Invokes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_PROCESSOR_DID_NOT_RUN;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_CONFIGURE_DATABASE;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_CREATE_TABLES;

final class DbCallback extends SupportSQLiteOpenHelper.Callback {

  private final Context context;

  DbCallback(@NonNull Context context, int version) {
    super(version);
    this.context = context;
  }

  @Invokes(INVOCATION_METHOD_CREATE_TABLES)
  @Override
  public void onCreate(SupportSQLiteDatabase db) {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }

  // this method already runs in transaction
  @Override
  public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
    executeUpgradeScripts(db, oldVersion, newVersion);
  }

  private void executeUpgradeScripts(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
    try {
      if (SqliteMagic.LOGGING_ENABLED) {
        LogUtil.logDebug("Executing upgrade scripts");
      }
      final AssetManager assets = context.getAssets();
      for (int i = oldVersion; i < newVersion; i++) {
        final String fileName = (i + 1) + ".sql";
        if (SqliteMagic.LOGGING_ENABLED) {
          LogUtil.logDebug("Executing script %s", fileName);
        }
        final BufferedReader bfr = new BufferedReader(new InputStreamReader(assets.open(fileName)));
        String sql;
        while ((sql = bfr.readLine()) != null) {
          db.execSQL(sql);
        }
        bfr.close();
      }
    } catch (IOException ioe) {
      LogUtil.logError("Error executing upgrade scripts");
      throw new RuntimeException(ioe);
    }
  }

  @Invokes(INVOCATION_METHOD_CONFIGURE_DATABASE)
  @Override
  public void onConfigure(SupportSQLiteDatabase db) {
    // filled with magic
    throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
  }
}