package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteCursorDriver;
import android.database.sqlite.SQLiteQuery;
import android.support.annotation.NonNull;

final class SqliteMagicCursor extends SQLiteCursor {
	private FastCursor cursor;

	public SqliteMagicCursor(SQLiteCursorDriver driver, String editTable, SQLiteQuery query) {
		super(driver, editTable, query);
	}

	@NonNull
	FastCursor getFastCursor() {
		if (cursor == null) {
			cursor = FastCursor.from(this);
		}
		return cursor;
	}

	FastCursor getFastCursorAndSync() {
		final FastCursor cursor = getFastCursor();
		cursor.syncWith(this);
		return cursor;
	}

	@Override
	public void close() {
		super.close();
		if (cursor != null) {
			cursor.close();
			cursor = null;
		}
	}
}
