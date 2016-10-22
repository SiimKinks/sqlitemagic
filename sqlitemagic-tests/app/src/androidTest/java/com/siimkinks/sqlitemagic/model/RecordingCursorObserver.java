package com.siimkinks.sqlitemagic.model;

import android.database.Cursor;
import android.util.Log;

import com.siimkinks.sqlitemagic.Query;

import java.util.ArrayList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import rx.Subscriber;
import rx.functions.Func1;

import static com.google.common.truth.Truth.assertThat;

class RecordingCursorObserver extends Subscriber<Query> {
  private static final Object COMPLETED = "<completed>";
  private static final String TAG = RecordingCursorObserver.class.getSimpleName();

  final BlockingDeque<Object> events = new LinkedBlockingDeque<>();

  @Override
  public final void onCompleted() {
    Log.d(TAG, "onCompleted");
    events.add(COMPLETED);
  }

  @Override
  public final void onError(Throwable e) {
    Log.d(TAG, "onError " + e.getClass().getSimpleName() + " " + e.getMessage());
    events.add(e);
  }

  @Override
  public final void onNext(Query value) {
    Log.d(TAG, "onNext " + value);
    events.add(value.runBlocking());
  }

  public final void doRequest(long amount) {
    request(amount);
  }

  protected Object takeEvent() {
    Object item = events.removeFirst();
    if (item == null) {
      throw new AssertionError("No items.");
    }
    return item;
  }

  public final CursorAssert assertCursor() {
    Object event = takeEvent();
    return new CursorAssert((Cursor) event);
  }

  public final void assertErrorContains(String expected) {
    Object event = takeEvent();
    assertThat(event).isInstanceOf(Throwable.class);
    assertThat(((Throwable) event).getMessage()).contains(expected);
  }

  public void assertNoMoreEvents() {
    assertThat(events).isEmpty();
  }

  static final class CursorAssert {
    private final Cursor cursor;

    CursorAssert(Cursor cursor) {
      this.cursor = cursor;
    }

    public CursorAssert hasRows(Func1<Cursor, ?> map, ArrayList values) {
      final int size = values.size();
      assertThat(cursor.getCount()).named("column count").isEqualTo(size);
      for (int i = 0; i < size; i++) {
        assertThat(cursor.moveToNext()).named("row " + (i + 1) + " exists").isTrue();
        assertThat(map.call(cursor)).isEqualTo(values.get(i));
      }
      return this;
    }

    public void isExhausted() {
      if (cursor.moveToNext()) {
        StringBuilder data = new StringBuilder();
        for (int i = 0; i < cursor.getColumnCount(); i++) {
          if (i > 0) data.append(", ");
          data.append(cursor.getString(i));
        }
        throw new AssertionError("Expected no more rows but was: " + data);
      }
      cursor.close();
    }
  }
}
