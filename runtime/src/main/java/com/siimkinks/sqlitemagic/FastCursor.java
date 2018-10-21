package com.siimkinks.sqlitemagic;

import android.content.ContentResolver;
import android.database.AbstractWindowedCursor;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

/**
 * For internal use.
 * Modified implementation from <a href="https://github.com/greenrobot/greenDAO/blob/master/DaoCore/src/de/greenrobot/dao/internal/FastCursor.java">greenDAO</a>
 */
public final class FastCursor implements Cursor {
  @NonNull
  private final AbstractWindowedCursor backingCursor;

  private CursorWindow window;
  private int windowStart;
  private int windowEnd;

  private int position;
  private int count;

  private FastCursor(@NonNull AbstractWindowedCursor cursor) {
    backingCursor = cursor;
    count = cursor.getCount(); // fills cursor window
    window = cursor.getWindow();
    windowStart = window.getStartPosition();
    windowEnd = windowStart + window.getNumRows();
    position = -1;
  }

  static Cursor tryCreate(@NonNull Cursor cursor) {
    if (cursor instanceof AbstractWindowedCursor) {
      return new FastCursor((AbstractWindowedCursor) cursor);
    }
    return cursor;
  }

  void syncWith(@NonNull Cursor cursor) {
    final int position = cursor.getPosition();
    moveWindowIfNeeded(this.position, position);
    this.position = position;
  }

  @Override
  public void close() {
    backingCursor.close();
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
    return backingCursor.isBeforeFirst();
  }

  @Override
  public boolean isAfterLast() {
    return backingCursor.isAfterLast();
  }

  @Override
  public int getColumnIndex(String columnName) {
    return backingCursor.getColumnIndex(columnName);
  }

  @Override
  public int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException {
    return backingCursor.getColumnIndexOrThrow(columnName);
  }

  @Override
  public String getColumnName(int columnIndex) {
    return backingCursor.getColumnName(columnIndex);
  }

  @Override
  public String[] getColumnNames() {
    return backingCursor.getColumnNames();
  }

  @Override
  public int getColumnCount() {
    return backingCursor.getColumnCount();
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
    backingCursor.copyStringToBuffer(columnIndex, buffer);
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
    return backingCursor.getType(i);
  }

  @Override
  public boolean isNull(int columnIndex) {
    return window.getType(position, columnIndex) == Cursor.FIELD_TYPE_NULL;
  }

  @Deprecated
  @Override
  public void deactivate() {
    backingCursor.deactivate();
  }

  @Deprecated
  @Override
  public boolean requery() {
    return backingCursor.requery();
  }

  @Override
  public boolean isClosed() {
    return backingCursor.isClosed();
  }

  @Override
  public void registerContentObserver(ContentObserver observer) {
    backingCursor.registerContentObserver(observer);
  }

  @Override
  public void unregisterContentObserver(ContentObserver observer) {
    backingCursor.unregisterContentObserver(observer);
  }

  @Override
  public void registerDataSetObserver(DataSetObserver observer) {
    backingCursor.registerDataSetObserver(observer);
  }

  @Override
  public void unregisterDataSetObserver(DataSetObserver observer) {
    backingCursor.unregisterDataSetObserver(observer);
  }

  @Override
  public void setNotificationUri(ContentResolver cr, Uri uri) {
    backingCursor.setNotificationUri(cr, uri);
  }

  @Override
  public Uri getNotificationUri() {
    return backingCursor.getNotificationUri();
  }

  @Override
  public boolean getWantsAllOnMoveCalls() {
    return backingCursor.getWantsAllOnMoveCalls();
  }

  @Override
  public void setExtras(Bundle bundle) {
    backingCursor.setExtras(bundle);
  }

  @Override
  public Bundle getExtras() {
    return backingCursor.getExtras();
  }

  @Override
  public Bundle respond(Bundle extras) {
    return backingCursor.respond(extras);
  }
}
