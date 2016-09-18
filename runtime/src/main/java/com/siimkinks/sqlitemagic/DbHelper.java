package com.siimkinks.sqlitemagic;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.annotation.internal.Invokes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_PROCESSOR_DID_NOT_RUN;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_CONFIGURE_DATABASE;
import static com.siimkinks.sqlitemagic.GlobalConst.INVOCATION_METHOD_CREATE_TABLES;

final class DbHelper extends SQLiteOpenHelper {

	private final Context context;

	DbHelper(@NonNull Context context, @NonNull String name, int version) {
		super(context, name, new SqliteMagicCursorFactory(), version);
		this.context = context;
	}

	@Invokes(INVOCATION_METHOD_CREATE_TABLES)
	@Override
	public void onCreate(SQLiteDatabase db) {
		// filled with magic
		throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
	}

	// this method already runs in transaction
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		executeUpgradeScripts(db, oldVersion, newVersion);
	}

	private void executeUpgradeScripts(SQLiteDatabase db, int oldVersion, int newVersion) {
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
	public void onConfigure(SQLiteDatabase db) {
		// filled with magic
		throw new RuntimeException(ERROR_PROCESSOR_DID_NOT_RUN);
	}

	static final class SqliteMagicCursorFactory implements SQLiteDatabase.CursorFactory {
		@Override
		public Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
			return new SqliteMagicCursor(driver, editTable, query);
		}
	}
}
