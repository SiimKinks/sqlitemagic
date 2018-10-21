package com.siimkinks.sqlitemagic;

import com.siimkinks.sqlitemagic.internal.StringArraySet;

import org.junit.Test;

import java.util.UUID;

import androidx.annotation.NonNull;

import static com.google.common.truth.Truth.assertThat;

public class StringArraySetTest {
  @Test
  public void containsAllKeys() {
    final StringArraySet set = new StringArraySet();
    final int testSize = 100;
    final String[] keys = new String[testSize];

    for (int i = 0; i < testSize; i++) {
      final String key = UUID.randomUUID().toString();
      keys[i] = key;
      set.add(key);
    }

    assertThatSetContainsAllKeys(set, keys);
  }

  @Test
  public void createWithStringArray() {
    final int testSize = 100;
    final String[] keys = createRandomKeys(testSize);

    final StringArraySet set = new StringArraySet(keys);
    assertThatSetContainsAllKeys(set, keys);
  }

  @Test
  public void addAllFromStringArray() {
    final int testSize = 100;
    final String[] keys = createRandomKeys(testSize);

    final StringArraySet set = new StringArraySet(testSize);
    set.addAll(keys);

    assertThatSetContainsAllKeys(set, keys);
  }

  @Test
  public void containsWorksAfterClear() {
    final int testSize = 100;
    final String[] keys = createRandomKeys(testSize);

    final StringArraySet set = new StringArraySet(keys);
    set.clear();

    set.addAll(keys);

    assertThatSetContainsAllKeys(set, keys);
  }

  @Test
  public void twoSetsAreEqual() {
    final int testSize = 100;
    final String[] keys = createRandomKeys(testSize);

    final StringArraySet set1 = new StringArraySet(keys);
    final StringArraySet set2 = new StringArraySet(keys);

    assertThat(set1).isEqualTo(set2);
  }

  @Test
  public void twoObjectsAreEqual() {
    final int testSize = 100;
    final String[] keys = createRandomKeys(testSize);

    final StringArraySet set = new StringArraySet(keys);

    assertThat(set).isEqualTo(set);
  }

  @NonNull
  private String[] createRandomKeys(int testSize) {
    final String[] keys = new String[testSize];
    for (int i = 0; i < testSize; i++) {
      final String key = UUID.randomUUID().toString();
      keys[i] = key;
    }
    return keys;
  }

  private void assertThatSetContainsAllKeys(StringArraySet set, String[] keys) {
    final int testSize = keys.length;
    assertThat(set.size()).isEqualTo(testSize);
    for (int i = 0; i < testSize; i++) {
      assertThat(set.contains(keys[i])).isTrue();
    }
  }
}