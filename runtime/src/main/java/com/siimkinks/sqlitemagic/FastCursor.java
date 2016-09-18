package com.siimkinks.sqlitemagic;

import android.content.ContentResolver;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;

/**
 * For internal use.
 * Modified implementation from <a href="https://github.com/greenrobot/greenDAO/blob/master/DaoCore/src/de/greenrobot/dao/internal/FastCursor.java">greenDAO</a>
 */
public final class FastCursor implements Cursor {
	@NonNull
	private final SqliteMagicCursor backingCursor;

	private CursorWindow window;
	private int windowStart;
	private int windowEnd;

	private int position;
	private int count;

	private FastCursor(@NonNull SqliteMagicCursor cursor) {
		backingCursor = cursor;
		count = cursor.getCount(); // fills cursor window
		window = cursor.getWindow();
		windowStart = window.getStartPosition();
		windowEnd = windowStart + window.getNumRows();
		position = -1;
	}

	static FastCursor from(@NonNull SqliteMagicCursor cursor) {
		return new FastCursor(cursor);
	}

	void syncWith(@NonNull SqliteMagicCursor cursor) {
		final int position = cursor.getPosition();
		moveWindowIfNeeded(this.position, position);
		this.position = position;
	}

	@Override
	public void close() {
		if (window != null) {
			window.close();
			window = null;
		}
	}

	@Override
	public int getCount() {
		return count;
	}

	@Override
	public int getPosition() {
		return position;
	}

	@Override
	public boolean move(int offset) {
		return moveToPosition(position + offset);
	}

	@Override
	public boolean moveToPosition(int position) {
		if (position >= 0 && position < count) {
			moveWindowIfNeeded(this.position, position);
			this.position = position;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean moveToFirst() {
		moveWindowIfNeeded(position, 0);
		position = 0;
		return count > 0;
	}

	@Override
	public boolean moveToLast() {
		if (count > 0) {
			final int position = count - 1;
			moveWindowIfNeeded(this.position, position);
			this.position = position;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean moveToNext() {
		int position = this.position;
		if (position < count - 1) {
			moveWindowIfNeeded(position, ++position);
			this.position = position;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean moveToPrevious() {
		int position = this.position;
		if (position > 0) {
			moveWindowIfNeeded(position, --position);
			this.position = position;
			return true;
		} else {
			return false;
		}
	}

	private void moveWindowIfNeeded(int oldPosition, int newPosition) {
		if (newPosition < windowStart || newPosition >= windowEnd) {
			backingCursor.onMove(oldPosition, newPosition);
			windowStart = window.getStartPosition();
			windowEnd = windowStart + window.getNumRows();
		}
	}

	@Override
	public boolean isFirst() {
		return position == 0;
	}

	@Override
	public boolean isLast() {
		return position == count - 1;
	}

	@Override
	public boolean isBeforeFirst() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isAfterLast() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getColumnIndex(String columnName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getColumnName(int columnIndex) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String[] getColumnNames() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getColumnCount() {
		throw new UnsupportedOperationException();
	}

	@Override
	public byte[] getBlob(int columnIndex) {
		return window.getBlob(position, columnIndex);
	}

	@Override
	public String getString(int columnIndex) {
		return window.getString(position, columnIndex);
	}

	@Override
	public void copyStringToBuffer(int columnIndex, CharArrayBuffer buffer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public short getShort(int columnIndex) {
		return (short) window.getLong(position, columnIndex);
	}

	@Override
	public int getInt(int columnIndex) {
		return (int) window.getLong(position, columnIndex);
	}

	@Override
	public long getLong(int columnIndex) {
		return window.getLong(position, columnIndex);
	}

	@Override
	public float getFloat(int columnIndex) {
		return (float) window.getDouble(position, columnIndex);
	}

	@Override
	public double getDouble(int columnIndex) {
		return window.getDouble(position, columnIndex);
	}

	@Override
	public int getType(int i) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isNull(int columnIndex) {
		return window.getType(position, columnIndex) == Cursor.FIELD_TYPE_NULL;
	}

	@Deprecated
	@Override
	public void deactivate() {
		throw new UnsupportedOperationException();
	}

	@Deprecated
	@Override
	public boolean requery() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isClosed() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void registerContentObserver(ContentObserver observer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void unregisterContentObserver(ContentObserver observer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setNotificationUri(ContentResolver cr, Uri uri) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Uri getNotificationUri() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean getWantsAllOnMoveCalls() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setExtras(Bundle bundle) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle getExtras() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Bundle respond(Bundle extras) {
		throw new UnsupportedOperationException();
	}
}
