package com.siimkinks.sqlitemagic.model;

import android.util.Log;

import com.siimkinks.sqlitemagic.Query;

import java.util.ArrayList;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import rx.Subscriber;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class RecordingObserver extends Subscriber<Query> {
  private static final Object COMPLETED = "<completed>";
  private static final String TAG = RecordingObserver.class.getSimpleName();

  private final BlockingDeque<Object> events = new LinkedBlockingDeque<>();

  @Override
  public void onCompleted() {
    Log.d(TAG, "onCompleted");
    events.add(COMPLETED);
  }

  @Override
  public void onError(Throwable e) {
    Log.d(TAG, "onError " + e.getClass().getSimpleName() + " " + e.getMessage());
    events.add(e);
  }

  @Override
  public void onNext(Query value) {
    Log.d(TAG, "onNext " + value);
    events.add(value.runBlocking());
  }

  public void doRequest(long amount) {
    request(amount);
  }

  private Object takeEvent() {
    try {
      Object item = events.pollFirst(1, SECONDS);
      if (item == null) {
        throw new AssertionError("Timeout expired waiting for item.");
      }
      return item;
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public void assertSingleElement(Object element) {
    Object event = takeEvent();
    assertThat(event).isEqualTo(element);
  }

  public ListAssert assertElements() {
    Object event = takeEvent();
    assertThat(event).isInstanceOf(ArrayList.class);
    return new ListAssert((ArrayList) event);
  }

  public void assertErrorContains(String expected) {
    Object event = takeEvent();
    assertThat(event).isInstanceOf(Throwable.class);
    assertThat(((Throwable) event).getMessage()).contains(expected);
  }

  public void assertNoMoreEvents() {
    try {
      assertThat(events.pollFirst(1, SECONDS)).isNull();
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  public static final class ListAssert {
    private final ArrayList elements;
    private int pos = 0;

    ListAssert(ArrayList elements) {
      this.elements = elements;
    }

    public ListAssert hasSingleElement(Object element) {
      assertThat(elements.get(pos)).isEqualTo(element);
      pos += 1;
      return this;
    }

    public ListAssert hasElements(Object... elements) {
      for (Object element : elements) {
        assertThat(this.elements.get(pos)).isEqualTo(element);
        pos += 1;
      }
      return this;
    }

    public ListAssert hasElements(Iterable elements) {
      for (Object element : elements) {
        assertThat(this.elements.get(pos)).isEqualTo(element);
        pos += 1;
      }
      return this;
    }

    public void isExhausted() {
      if (pos != elements.size()) {
        throw new AssertionError("Expected no more rows but was: " + elements.get(pos));
      }
    }
  }
}
