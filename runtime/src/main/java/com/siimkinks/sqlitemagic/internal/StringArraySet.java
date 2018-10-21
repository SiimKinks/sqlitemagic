package com.siimkinks.sqlitemagic.internal;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class StringArraySet implements Set<String> {
  /**
   * The minimum amount by which the capacity of a ArraySet will increase.
   * This is tuned to be relatively space-efficient.
   */
  public static final int BASE_SIZE = 4;

  /**
   * Maximum number of entries to have in array caches.
   */
  private static final int CACHE_SIZE = 10;

  /**
   * Caches of small array objects to avoid spamming garbage.  The cache
   * Object[] variable is a pointer to a linked list of array objects.
   * The first entry in the array is a pointer to the next array in the
   * list; the second entry is a pointer to the int[] hash code array for it.
   */
  static Object[] baseCache;
  static int baseCacheSize;
  static Object[] twiceBaseCache;
  static int twiceBaseCacheSize;

  int[] hashes;
  Object[] array;
  int size;

  private int indexOf(Object key, int hash) {
    final int N = size;

    // Important fast case: if nothing is in here, nothing to look for.
    if (N == 0) {
      return ~0;
    }

    int index = ContainerHelpers.binarySearch(hashes, N, hash);

    // If the hash code wasn't found, then we have no entry for this key.
    if (index < 0) {
      return index;
    }

    // If the key at the returned index matches, that's what we want.
    if (key.equals(array[index])) {
      return index;
    }

    // Search for a matching key after the index.
    int end;
    final int[] hashes = this.hashes;
    final Object[] array = this.array;
    for (end = index + 1; end < N && hashes[end] == hash; end++) {
      if (key.equals(array[end])) return end;
    }

    // Search for a matching key before the index.
    for (int i = index - 1; i >= 0 && hashes[i] == hash; i--) {
      if (key.equals(array[i])) return i;
    }

    // Key not found -- return negative value indicating where a
    // new entry for this key should go.  We use the end of the
    // hash chain to reduce the number of array entries that will
    // need to be copied when inserting.
    return ~end;
  }

  private int indexOfNull() {
    final int N = size;

    // Important fast case: if nothing is in here, nothing to look for.
    if (N == 0) {
      return ~0;
    }

    int index = ContainerHelpers.binarySearch(hashes, N, 0);

    // If the hash code wasn't found, then we have no entry for this key.
    if (index < 0) {
      return index;
    }

    // If the key at the returned index matches, that's what we want.
    if (null == array[index]) {
      return index;
    }

    // Search for a matching key after the index.
    int end;
    final int[] hashes = this.hashes;
    final Object[] array = this.array;
    for (end = index + 1; end < N && hashes[end] == 0; end++) {
      if (null == array[end]) return end;
    }

    // Search for a matching key before the index.
    for (int i = index - 1; i >= 0 && hashes[i] == 0; i--) {
      if (null == array[i]) return i;
    }

    // Key not found -- return negative value indicating where a
    // new entry for this key should go.  We use the end of the
    // hash chain to reduce the number of array entries that will
    // need to be copied when inserting.
    return ~end;
  }

  private void allocArrays(final int size) {
    if (size == (BASE_SIZE * 2)) {
      synchronized (StringArraySet.class) {
        if (twiceBaseCache != null) {
          final Object[] array = twiceBaseCache;
          this.array = array;
          twiceBaseCache = (Object[]) array[0];
          hashes = (int[]) array[1];
          array[0] = array[1] = null;
          twiceBaseCacheSize--;
          return;
        }
      }
    } else if (size == BASE_SIZE) {
      synchronized (StringArraySet.class) {
        if (baseCache != null) {
          final Object[] array = baseCache;
          this.array = array;
          baseCache = (Object[]) array[0];
          hashes = (int[]) array[1];
          array[0] = array[1] = null;
          baseCacheSize--;
          return;
        }
      }
    }

    hashes = new int[size];
    array = new Object[size];
  }

  private static void freeArrays(final int[] hashes, final Object[] array, final int size) {
    if (hashes.length == (BASE_SIZE * 2)) {
      synchronized (StringArraySet.class) {
        if (twiceBaseCacheSize < CACHE_SIZE) {
          array[0] = twiceBaseCache;
          array[1] = hashes;
          for (int i = size - 1; i >= 2; i--) {
            array[i] = null;
          }
          twiceBaseCache = array;
          twiceBaseCacheSize++;
        }
      }
    } else if (hashes.length == BASE_SIZE) {
      synchronized (StringArraySet.class) {
        if (baseCacheSize < CACHE_SIZE) {
          array[0] = baseCache;
          array[1] = hashes;
          for (int i = size - 1; i >= 2; i--) {
            array[i] = null;
          }
          baseCache = array;
          baseCacheSize++;
        }
      }
    }
  }

  /**
   * Create a new empty StringArraySet.  The default capacity of an array map is 0, and
   * will grow once items are added to it.
   */
  public StringArraySet() {
    hashes = ContainerHelpers.EMPTY_INTS;
    array = ContainerHelpers.EMPTY_OBJECTS;
    size = 0;
  }

  /**
   * Create a new StringArraySet with a given initial capacity.
   */
  public StringArraySet(int capacity) {
    if (capacity == 0) {
      hashes = ContainerHelpers.EMPTY_INTS;
      array = ContainerHelpers.EMPTY_OBJECTS;
    } else {
      allocArrays(capacity);
    }
    size = 0;
  }

  /**
   * Create a new StringArraySet with the mappings from the given StringArraySet.
   */
  public StringArraySet(@NonNull StringArraySet set) {
    this();
    addAll(set);
  }

  /**
   * Create a new StringArraySet with the mappings from the given String array.
   */
  public StringArraySet(@NonNull String[] array) {
    this();
    addAll(array);
  }

  /**
   * Make the array map empty.  All storage is released.
   */
  @Override
  public void clear() {
    if (size != 0) {
      freeArrays(hashes, array, size);
      hashes = ContainerHelpers.EMPTY_INTS;
      array = ContainerHelpers.EMPTY_OBJECTS;
      size = 0;
    }
  }

  /**
   * Ensure the array map can hold at least <var>minimumCapacity</var>
   * items.
   */
  public void ensureCapacity(int minimumCapacity) {
    if (hashes.length < minimumCapacity) {
      final int[] ohashes = hashes;
      final Object[] oarray = array;
      allocArrays(minimumCapacity);
      final int size = this.size;
      if (size > 0) {
        System.arraycopy(ohashes, 0, hashes, 0, size);
        System.arraycopy(oarray, 0, array, 0, size);
      }
      freeArrays(ohashes, oarray, size);
    }
  }

  /**
   * Check whether a value exists in the set.
   *
   * @param key The value to search for.
   * @return Returns true if the value exists, else false.
   */
  @Override
  public boolean contains(@Nullable Object key) {
    return indexOf(key) >= 0;
  }

  /**
   * Returns the index of a value in the set.
   *
   * @param key The value to search for.
   * @return Returns the index of the value if it exists, else a negative integer.
   */
  public int indexOf(@Nullable Object key) {
    return key == null ? indexOfNull() : indexOf(key, key.hashCode());
  }

  /**
   * Return the value at the given index in the array.
   *
   * @param index The desired index, must be between 0 and {@link #size()}-1.
   * @return Returns the value stored at the given index.
   */
  public String valueAt(int index) {
    return (String) array[index];
  }

  /**
   * Return true if the array map contains no items.
   */
  @Override
  public boolean isEmpty() {
    return size <= 0;
  }

  /**
   * Adds the specified object to this set. The set is not modified if it
   * already contains the object.
   *
   * @param value the object to add.
   * @return {@code true} if this set is modified, {@code false} otherwise.
   * @throws ClassCastException when the class of the object is inappropriate for this set.
   */
  @Override
  public boolean add(@Nullable String value) {
    final int hash;
    int index;
    if (value == null) {
      hash = 0;
      index = indexOfNull();
    } else {
      hash = value.hashCode();
      index = indexOf(value, hash);
    }
    if (index >= 0) {
      return false;
    }

    index = ~index;
    if (size >= hashes.length) {
      final int n = size >= (BASE_SIZE * 2) ? (size + (size >> 1))
          : (size >= BASE_SIZE ? (BASE_SIZE * 2) : BASE_SIZE);

      final int[] ohashes = hashes;
      final Object[] oarray = array;
      allocArrays(n);

      if (hashes.length > 0) {
        System.arraycopy(ohashes, 0, hashes, 0, ohashes.length);
        System.arraycopy(oarray, 0, array, 0, oarray.length);
      }

      freeArrays(ohashes, oarray, size);
    }

    if (index < size) {
      System.arraycopy(hashes, index, hashes, index + 1, size - index);
      System.arraycopy(array, index, array, index + 1, size - index);
    }

    hashes[index] = hash;
    array[index] = value;
    size++;
    return true;
  }

  public void addAll(@NonNull StringArraySet array) {
    final int N = array.size;
    ensureCapacity(size + N);
    if (size == 0) {
      if (N > 0) {
        System.arraycopy(array.hashes, 0, hashes, 0, N);
        System.arraycopy(array.array, 0, this.array, 0, N);
        size = N;
      }
    } else {
      for (int i = 0; i < N; i++) {
        add(array.valueAt(i));
      }
    }
  }

  public void addAll(@NonNull String[] array) {
    final int N = array.length;
    ensureCapacity(size + N);
    for (int i = 0; i < N; i++) {
      add(array[i]);
    }
  }

  /**
   * Removes the specified object from this set.
   *
   * @param object the object to remove.
   * @return {@code true} if this set was modified, {@code false} otherwise.
   */
  @Override
  public boolean remove(Object object) {
    final int index = indexOf(object);
    if (index >= 0) {
      removeAt(index);
      return true;
    }
    return false;
  }

  /**
   * Remove the key/value mapping at the given index.
   *
   * @param index The desired index, must be between 0 and {@link #size()}-1.
   * @return Returns the value that was stored at this index.
   */
  public String removeAt(int index) {
    final Object old = array[index];
    if (size <= 1) {
      // Now empty.
      freeArrays(hashes, array, size);
      hashes = ContainerHelpers.EMPTY_INTS;
      array = ContainerHelpers.EMPTY_OBJECTS;
      size = 0;
    } else {
      if (hashes.length > (BASE_SIZE * 2) && size < hashes.length / 3) {
        // Shrunk enough to reduce size of arrays.  We don't allow it to
        // shrink smaller than (BASE_SIZE*2) to avoid flapping between
        // that and BASE_SIZE.
        final int n = size > (BASE_SIZE * 2) ? (size + (size >> 1)) : (BASE_SIZE * 2);

        final int[] ohashes = hashes;
        final Object[] oarray = array;
        allocArrays(n);

        size--;
        if (index > 0) {
          System.arraycopy(ohashes, 0, hashes, 0, index);
          System.arraycopy(oarray, 0, array, 0, index);
        }
        if (index < size) {
          System.arraycopy(ohashes, index + 1, hashes, index, size - index);
          System.arraycopy(oarray, index + 1, array, index, size - index);
        }
      } else {
        size--;
        if (index < size) {
          System.arraycopy(hashes, index + 1, hashes, index, size - index);
          System.arraycopy(array, index + 1, array, index, size - index);
        }
        array[size] = null;
      }
    }
    return (String) old;
  }

  /**
   * Return the number of items in this array map.
   */
  @Override
  public int size() {
    return size;
  }

  @Override
  public Object[] toArray() {
    Object[] result = new Object[size];
    System.arraycopy(array, 0, result, 0, size);
    return result;
  }

  @Override
  public <T> T[] toArray(T[] array) {
    if (array.length < size) {
      @SuppressWarnings("unchecked") T[] newArray
          = (T[]) Array.newInstance(array.getClass().getComponentType(), size);
      array = newArray;
    }
    System.arraycopy(this.array, 0, array, 0, size);
    if (array.length > size) {
      array[size] = null;
    }
    return array;
  }

  /**
   * {@inheritDoc}
   * <p>
   * This implementation returns false if the object is not a set, or
   * if the sets have different sizes.  Otherwise, for each value in this
   * set, it checks to make sure the value also exists in the other set.
   * If any value doesn't exist, the method returns false; otherwise, it
   * returns true.
   * </p>
   */
  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (object instanceof Set) {
      Set<?> set = (Set<?>) object;
      if (size() != set.size()) {
        return false;
      }

      try {
        for (int i = 0; i < size; i++) {
          String mine = valueAt(i);
          if (!set.contains(mine)) {
            return false;
          }
        }
      } catch (NullPointerException ignored) {
        return false;
      } catch (ClassCastException ignored) {
        return false;
      }
      return true;
    }
    return false;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int hashCode() {
    final int[] hashes = this.hashes;
    int result = 0;
    for (int i = 0, s = size; i < s; i++) {
      result += hashes[i];
    }
    return result;
  }

  /**
   * {@inheritDoc}
   * <p>
   * This implementation composes a string by iterating over its values. If
   * this set contains itself as a value, the string "(this Set)"
   * will appear in its place.
   * </p>
   */
  @Override
  public String toString() {
    if (isEmpty()) {
      return "{}";
    }

    final int size = this.size;
    StringBuilder buffer = new StringBuilder(size * 14);
    buffer.append('{');
    for (int i = 0; i < size; i++) {
      if (i > 0) {
        buffer.append(", ");
      }
      Object value = valueAt(i);
      if (value != this) {
        buffer.append(value);
      } else {
        buffer.append("(this Set)");
      }
    }
    buffer.append('}');
    return buffer.toString();
  }

  // ------------------------------------------------------------------------
  // Interop with traditional Java containers.  Not as efficient as using
  // specialized collection APIs.
  // ------------------------------------------------------------------------

  /**
   * Return an {@link java.util.Iterator} over all values in the set.
   * <p><b>Note:</b> this is a fairly inefficient way to access the array contents, it
   * requires generating a number of temporary objects and allocates additional state
   * information associated with the container that will remain for the life of the container.</p>
   */
  @NonNull
  @Override
  public Iterator<String> iterator() {
    throw new UnsupportedOperationException();
  }

  /**
   * Determine if the array set contains all of the values in the given collection.
   *
   * @param collection The collection whose contents are to be checked against.
   * @return Returns true if this array set contains a value for every entry
   * in <var>collection</var>, else returns false.
   */
  @Override
  public boolean containsAll(@NonNull Collection<?> collection) {
    Iterator<?> it = collection.iterator();
    while (it.hasNext()) {
      if (!contains(it.next())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Perform an {@link #add(Object)} of all values in <var>collection</var>
   *
   * @param collection The collection whose contents are to be retrieved.
   */
  @Override
  public boolean addAll(@NonNull Collection<? extends String> collection) {
    ensureCapacity(size + collection.size());
    boolean added = false;
    for (String value : collection) {
      added |= add(value);
    }
    return added;
  }

  /**
   * Remove all values in the array set that exist in the given collection.
   *
   * @param collection The collection whose contents are to be used to remove values.
   * @return Returns true if any values were removed from the array set, else false.
   */
  @Override
  public boolean removeAll(@NonNull Collection<?> collection) {
    boolean removed = false;
    for (Object value : collection) {
      removed |= remove(value);
    }
    return removed;
  }

  /**
   * Remove all values in the array set that do <b>not</b> exist in the given collection.
   *
   * @param collection The collection whose contents are to be used to determine which
   *                   values to keep.
   * @return Returns true if any values were removed from the array set, else false.
   */
  @Override
  public boolean retainAll(@NonNull Collection<?> collection) {
    boolean removed = false;
    final Object[] array = this.array;
    for (int i = size - 1; i >= 0; i--) {
      if (!collection.contains(array[i])) {
        removeAt(i);
        removed = true;
      }
    }
    return removed;
  }
}
