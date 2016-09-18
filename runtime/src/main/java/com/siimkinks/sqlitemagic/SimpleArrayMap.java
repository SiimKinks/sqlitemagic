package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.Map;

/**
 * Base implementation of Android ArrayMap that doesn't include any standard Java
 * container API interoperability.  These features are generally heavier-weight ways
 * to interact with the container, so discouraged, but they can be useful to make it
 * easier to use as a drop-in replacement for HashMap.  If you don't need them, this
 * class can be preferrable since it doesn't bring in any of the implementation of those
 * APIs, allowing that code to be stripped by ProGuard.
 */
@SuppressWarnings("unchecked")
public class SimpleArrayMap<K, V> {
	/**
	 * The minimum amount by which the capacity of a ArrayMap will increase.
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

	int indexOf(Object key, int hash) {
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
		if (key.equals(array[index << 1])) {
			return index;
		}

		// Search for a matching key after the index.
		int end;
		final int[] hashes = this.hashes;
		final Object[] array = this.array;
		for (end = index + 1; end < N && hashes[end] == hash; end++) {
			if (key.equals(array[end << 1])) return end;
		}

		// Search for a matching key before the index.
		for (int i = index - 1; i >= 0 && hashes[i] == hash; i--) {
			if (key.equals(array[i << 1])) return i;
		}

		// Key not found -- return negative value indicating where a
		// new entry for this key should go.  We use the end of the
		// hash chain to reduce the number of array entries that will
		// need to be copied when inserting.
		return ~end;
	}

	int indexOfNull() {
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
		if (null == array[index << 1]) {
			return index;
		}

		// Search for a matching key after the index.
		int end;
		final int[] hashes = this.hashes;
		final Object[] array = this.array;
		for (end = index + 1; end < N && hashes[end] == 0; end++) {
			if (null == array[end << 1]) return end;
		}

		// Search for a matching key before the index.
		for (int i = index - 1; i >= 0 && hashes[i] == 0; i--) {
			if (null == array[i << 1]) return i;
		}

		// Key not found -- return negative value indicating where a
		// new entry for this key should go.  We use the end of the
		// hash chain to reduce the number of array entries that will
		// need to be copied when inserting.
		return ~end;
	}

	private void allocArrays(final int size) {
		if (size == (BASE_SIZE * 2)) {
			synchronized (SimpleArrayMap.class) {
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
			synchronized (SimpleArrayMap.class) {
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
		array = new Object[size << 1];
	}

	private static void freeArrays(final int[] hashes, final Object[] array, final int size) {
		if (hashes.length == (BASE_SIZE * 2)) {
			synchronized (SimpleArrayMap.class) {
				if (twiceBaseCacheSize < CACHE_SIZE) {
					array[0] = twiceBaseCache;
					array[1] = hashes;
					for (int i = (size << 1) - 1; i >= 2; i--) {
						array[i] = null;
					}
					twiceBaseCache = array;
					twiceBaseCacheSize++;
				}
			}
		} else if (hashes.length == BASE_SIZE) {
			synchronized (SimpleArrayMap.class) {
				if (baseCacheSize < CACHE_SIZE) {
					array[0] = baseCache;
					array[1] = hashes;
					for (int i = (size << 1) - 1; i >= 2; i--) {
						array[i] = null;
					}
					baseCache = array;
					baseCacheSize++;
				}
			}
		}
	}

	/**
	 * Create a new empty ArrayMap.  The default capacity of an array map is 0, and
	 * will grow once items are added to it.
	 */
	public SimpleArrayMap() {
		hashes = ContainerHelpers.EMPTY_INTS;
		array = ContainerHelpers.EMPTY_OBJECTS;
		size = 0;
	}

	/**
	 * Create a new ArrayMap with a given initial capacity.
	 */
	public SimpleArrayMap(int capacity) {
		if (capacity == 0) {
			hashes = ContainerHelpers.EMPTY_INTS;
			array = ContainerHelpers.EMPTY_OBJECTS;
		} else {
			allocArrays(capacity);
		}
		size = 0;
	}

	/**
	 * Create a new ArrayMap with the mappings from the given ArrayMap.
	 */
	public SimpleArrayMap(@NonNull SimpleArrayMap map) {
		this();
		putAll(map);
	}

	/**
	 * Make the array map empty.  All storage is released.
	 */
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
			if (size > 0) {
				System.arraycopy(ohashes, 0, hashes, 0, size);
				System.arraycopy(oarray, 0, array, 0, size << 1);
			}
			freeArrays(ohashes, oarray, size);
		}
	}

	/**
	 * Check whether a key exists in the array.
	 *
	 * @param key The key to search for.
	 * @return Returns true if the key exists, else false.
	 */
	public boolean containsKey(@Nullable Object key) {
		return indexOfKey(key) >= 0;
	}

	/**
	 * Returns the index of a key in the set.
	 *
	 * @param key The key to search for.
	 * @return Returns the index of the key if it exists, else a negative integer.
	 */
	public int indexOfKey(@Nullable Object key) {
		return key == null ? indexOfNull() : indexOf(key, key.hashCode());
	}

	int indexOfValue(@Nullable Object value) {
		final int N = size * 2;
		final Object[] array = this.array;
		if (value == null) {
			for (int i = 1; i < N; i += 2) {
				if (array[i] == null) {
					return i >> 1;
				}
			}
		} else {
			for (int i = 1; i < N; i += 2) {
				if (value.equals(array[i])) {
					return i >> 1;
				}
			}
		}
		return -1;
	}

	/**
	 * Check whether a value exists in the array.  This requires a linear search
	 * through the entire array.
	 *
	 * @param value The value to search for.
	 * @return Returns true if the value exists, else false.
	 */
	public boolean containsValue(@Nullable Object value) {
		return indexOfValue(value) >= 0;
	}

	/**
	 * Retrieve a value from the array.
	 *
	 * @param key The key of the value to retrieve.
	 * @return Returns the value associated with the given key,
	 * or null if there is no such key.
	 */
	public V get(@Nullable Object key) {
		final int index = indexOfKey(key);
		return index >= 0 ? (V) array[(index << 1) + 1] : null;
	}

	/**
	 * Return the key at the given index in the array.
	 *
	 * @param index The desired index, must be between 0 and {@link #size()}-1.
	 * @return Returns the key stored at the given index.
	 */
	public K keyAt(int index) {
		return (K) array[index << 1];
	}

	/**
	 * Return the value at the given index in the array.
	 *
	 * @param index The desired index, must be between 0 and {@link #size()}-1.
	 * @return Returns the value stored at the given index.
	 */
	public V valueAt(int index) {
		return (V) array[(index << 1) + 1];
	}

	/**
	 * Set the value at a given index in the array.
	 *
	 * @param index The desired index, must be between 0 and {@link #size()}-1.
	 * @param value The new value to store at this index.
	 * @return Returns the previous value at the given index.
	 */
	public V setValueAt(int index, @NonNull V value) {
		index = (index << 1) + 1;
		V old = (V) array[index];
		array[index] = value;
		return old;
	}

	/**
	 * Return true if the array map contains no items.
	 */
	public boolean isEmpty() {
		return size <= 0;
	}

	/**
	 * Add a new value to the array map.
	 *
	 * @param key   The key under which to store the value. If
	 *              this key already exists in the array, its value will be replaced.
	 * @param value The value to store for the given key.
	 * @return Returns the old value that was stored for the given key, or null if there
	 * was no such key.
	 */
	public V put(@Nullable K key, @NonNull V value) {
		final int hash;
		int index;
		if (key == null) {
			hash = 0;
			index = indexOfNull();
		} else {
			hash = key.hashCode();
			index = indexOf(key, hash);
		}
		if (index >= 0) {
			index = (index << 1) + 1;
			final V old = (V) array[index];
			array[index] = value;
			return old;
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
			System.arraycopy(array, index << 1, array, (index + 1) << 1, (size - index) << 1);
		}

		hashes[index] = hash;
		array[index << 1] = key;
		array[(index << 1) + 1] = value;
		size++;
		return null;
	}

	/**
	 * Perform a {@link #put(Object, Object)} of all key/value pairs in <var>array</var>
	 *
	 * @param array The array whose contents are to be retrieved.
	 */
	public void putAll(@NonNull SimpleArrayMap<? extends K, ? extends V> array) {
		final int N = array.size;
		ensureCapacity(size + N);
		if (size == 0) {
			if (N > 0) {
				System.arraycopy(array.hashes, 0, hashes, 0, N);
				System.arraycopy(array.array, 0, this.array, 0, N << 1);
				size = N;
			}
		} else {
			for (int i = 0; i < N; i++) {
				put(array.keyAt(i), array.valueAt(i));
			}
		}
	}

	/**
	 * Remove an existing key from the array map.
	 *
	 * @param key The key of the mapping to remove.
	 * @return Returns the value that was stored under the key, or null if there
	 * was no such key.
	 */
	public V remove(@Nullable Object key) {
		final int index = indexOfKey(key);
		if (index >= 0) {
			return removeAt(index);
		}

		return null;
	}

	/**
	 * Remove the key/value mapping at the given index.
	 *
	 * @param index The desired index, must be between 0 and {@link #size()}-1.
	 * @return Returns the value that was stored at this index.
	 */
	public V removeAt(int index) {
		final Object old = array[(index << 1) + 1];
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
					System.arraycopy(oarray, 0, array, 0, index << 1);
				}
				if (index < size) {
					System.arraycopy(ohashes, index + 1, hashes, index, size - index);
					System.arraycopy(oarray, (index + 1) << 1, array, index << 1,
							(size - index) << 1);
				}
			} else {
				size--;
				if (index < size) {
					System.arraycopy(hashes, index + 1, hashes, index, size - index);
					System.arraycopy(array, (index + 1) << 1, array, index << 1,
							(size - index) << 1);
				}
				array[size << 1] = null;
				array[(size << 1) + 1] = null;
			}
		}
		return (V) old;
	}

	/**
	 * Return the number of items in this array map.
	 */
	public int size() {
		return size;
	}

	/**
	 * {@inheritDoc}
	 * <p>This implementation returns false if the object is not a map, or
	 * if the maps have different sizes. Otherwise, for each key in this map,
	 * values of both maps are compared. If the values for any key are not
	 * equal, the method returns false, otherwise it returns true.
	 * </p>
	 */
	@Override
	public boolean equals(@Nullable Object object) {
		if (this == object) {
			return true;
		}
		if (object instanceof Map) {
			Map<?, ?> map = (Map<?, ?>) object;
			if (size() != map.size()) {
				return false;
			}

			try {
				for (int i = 0; i < size; i++) {
					K key = keyAt(i);
					V mine = valueAt(i);
					Object theirs = map.get(key);
					if (mine == null) {
						if (theirs != null || !map.containsKey(key)) {
							return false;
						}
					} else if (!mine.equals(theirs)) {
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
		final Object[] array = this.array;
		int result = 0;
		for (int i = 0, v = 1, s = size; i < s; i++, v += 2) {
			Object value = array[v];
			result += hashes[i] ^ (value == null ? 0 : value.hashCode());
		}
		return result;
	}

	/**
	 * {@inheritDoc}
	 * <p>This implementation composes a string by iterating over its mappings. If
	 * this map contains itself as a key or a value, the string "(this Map)"
	 * will appear in its place.
	 * </p>
	 */
	@Override
	public String toString() {
		if (isEmpty()) {
			return "{}";
		}

		StringBuilder buffer = new StringBuilder(size * 28);
		buffer.append('{');
		for (int i = 0; i < size; i++) {
			if (i > 0) {
				buffer.append(", ");
			}
			Object key = keyAt(i);
			if (key != this) {
				buffer.append(key);
			} else {
				buffer.append("(this Map)");
			}
			buffer.append('=');
			Object value = valueAt(i);
			if (value != this) {
				buffer.append(value);
			} else {
				buffer.append("(this Map)");
			}
		}
		buffer.append('}');
		return buffer.toString();
	}
}

