package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_OBJECTS
import com.siimkinks.sqlitemagic.internal.ContainerHelpers.EmptyIntArray

/**
 * [ObjectIntMap] is a container with a [Map]-like interface for keys with reference types and [Int]
 * primitives for values.
 *
 * The underlying implementation is designed to avoid allocations from boxing, and insertion,
 * removal, retrieval, and iteration operations. Allocations may still happen on insertion when the
 * underlying storage needs to grow to accommodate newly added entries to the table. In addition,
 * this implementation minimizes memory usage by avoiding the use of separate objects to hold
 * key/value pairs.
 *
 * This implementation makes no guarantee as to the order of the keys and values stored, nor does it
 * make guarantees that the order remains constant over time.
 *
 * This implementation is not thread-safe: if multiple threads access this container concurrently,
 * and one or more threads modify the structure of the map (insertion or removal for instance), the
 * calling code must provide the appropriate synchronization. Multiple threads are safe to read from
 * this map concurrently if no write is happening.
 *
 * This implementation is read-only and only allows data to be queried. A mutable implementation is
 * provided by [MutableObjectIntMap].
 *
 * @see [MutableObjectIntMap]
 * @see ScatterMap
 */
sealed class ObjectIntMap<K> {
  // NOTE: Our arrays are marked internal to implement inlined forEach{}
  // The backing array for the metadata bytes contains
  // `capacity + 1 + ClonedMetadataCount` entries, including when
  // the table is empty (see [EmptyGroup]).
  @PublishedApi
  @JvmField
  internal var metadata: LongArray = EmptyGroup

  @PublishedApi
  @JvmField
  internal var keys: Array<Any?> = EMPTY_OBJECTS

  @PublishedApi
  @JvmField
  internal var values: IntArray = EmptyIntArray

  // We use a backing field for capacity to avoid invokevirtual calls
  // every time we need to look at the capacity
  @Suppress("PropertyName")
  @JvmField
  protected var _capacity: Int = 0

  /**
   * Returns the number of key-value pairs that can be stored in this map without requiring
   * internal storage reallocation.
   */
  val capacity get() = _capacity

  // We use a backing field for capacity to avoid invokevirtual calls
  // every time we need to look at the size
  @Suppress("PropertyName")
  @JvmField
  protected var _size: Int = 0

  /** Returns the number of key-value pairs in this map. */
  val size get() = _size

  /** Indicates whether this map is empty. */
  fun isEmpty(): Boolean = _size == 0

  /** Returns `true` if this map is not empty. */
  fun isNotEmpty(): Boolean = _size != 0

  /**
   * Returns the value corresponding to the given [key], or throws if the key is not present in
   * the map.
   *
   * @throws NoSuchElementException when [key] is not found
   */
  operator fun get(key: K): Int {
    val index = findKeyIndex(key)
    if (index < 0) {
      throw NoSuchElementException("There is no key $key in the map")
    }
    return values[index]
  }

  /** Returns the value for [key], or `null` if the key is not present in the map. */
  fun getOrNull(key: K): Int? = when (val index = findKeyIndex(key)) {
    -1 -> null
    else -> values[index]
  }

  /**
   * Iterates over every key/value pair stored in this map by invoking the specified [block]
   * lambda.
   */
  @PublishedApi
  internal inline fun forEachIndexed(block: (index: Int) -> Unit) {
    val m = metadata
    val lastIndex = m.size - 2 // We always have 0 or at least 2 entries

    for (i in 0..lastIndex) {
      var slot = m[i]
      if (slot.maskEmptyOrDeleted() != BitmaskMsb) {
        // Branch-less if (i == lastIndex) 7 else 8
        // i - lastIndex returns a negative value when i < lastIndex,
        // so 1 is set as the MSB. By inverting and shifting we get
        // 0 when i < lastIndex, 1 otherwise.
        val bitCount = 8 - ((i - lastIndex).inv() ushr 31)
        for (j in 0 until bitCount) {
          if (isFull(slot and 0xFFL)) {
            val index = (i shl 3) + j
            block(index)
          }
          slot = slot shr 8
        }
        if (bitCount != 8) return
      }
    }
  }

  /**
   * Iterates over every key/value pair stored in this map by invoking the specified [block]
   * lambda.
   */
  inline fun forEach(block: (key: K, value: Int) -> Unit) {
    val k = keys
    val v = values

    forEachIndexed { index -> @Suppress("UNCHECKED_CAST") block(k[index] as K, v[index]) }
  }

  /** Returns true if the specified [key] is present in this map, false otherwise. */
  fun containsKey(key: K): Boolean = findKeyIndex(key) >= 0

  /**
   * Returns the hash code value for this map. The hash code the sum of the hash codes of each
   * key/value pair.
   */
  override fun hashCode(): Int {
    var hash = 0

    forEach { key, value -> hash += key.hashCode() xor value.hashCode() }

    return hash
  }

  /**
   * Compares the specified object [other] with this hash map for equality. The two objects are
   * considered equal if [other]:
   * - Is a [ObjectIntMap]
   * - Has the same [size] as this map
   * - Contains key/value pairs equal to this map's pair
   */
  override fun equals(other: Any?): Boolean {
    if (other === this) {
      return true
    }

    if (other !is ObjectIntMap<*>) {
      return false
    }
    if (other.size != size) {
      return false
    }

    @Suppress("UNCHECKED_CAST") val o = other as ObjectIntMap<Any?>

    forEach { key, value ->
      val index = o.findKeyIndex(key)
      if (index < 0 || value != o.values[index]) {
        return false
      }
    }

    return true
  }

  /**
   * Returns a string representation of this map. The map is denoted in the string by the `{}`.
   * Each key/value pair present in the map is represented inside '{}` by a substring of the form
   * `key=value`, and pairs are separated by `, `.
   */
  override fun toString(): String {
    if (isEmpty()) {
      return "{}"
    }

    val s = StringBuilder().append('{')
    var i = 0
    forEach { key, value ->
      s.append(if (key === this) "(this)" else key)
      s.append("=")
      s.append(value)
      i++
      if (i < _size) {
        s.append(',')
          .append(' ')
      }
    }

    return s.append('}')
      .toString()
  }

  /**
   * Scans the hash table to find the index in the backing arrays of the specified [key]. Returns
   * -1 if the key is not present.
   */
  protected fun findKeyIndex(key: K): Int {
    val hash = hash(key)
    val hash2 = h2(hash)

    val probeMask = _capacity
    var probeOffset = h1(hash) and probeMask
    var probeIndex = 0

    while (true) {
      val g = group(metadata, probeOffset)
      var m = g.match(hash2)
      while (m.hasNext()) {
        val index = (probeOffset + m.get()) and probeMask
        if (keys[index] == key) {
          return index
        }
        m = m.next()
      }

      if (g.maskEmpty() != 0L) {
        break
      }

      probeIndex += GroupWidth
      probeOffset = (probeOffset + probeIndex) and probeMask
    }

    return -1
  }
}

/**
 * [MutableObjectIntMap] is a container with a [MutableMap]-like interface for keys with reference
 * types and [Int] primitives for values.
 *
 * The underlying implementation is designed to avoid allocations from boxing, and insertion,
 * removal, retrieval, and iteration operations. Allocations may still happen on insertion when the
 * underlying storage needs to grow to accommodate newly added entries to the table. In addition,
 * this implementation minimizes memory usage by avoiding the use of separate objects to hold
 * key/value pairs.
 *
 * This implementation is not thread-safe: if multiple threads access this container concurrently,
 * and one or more threads modify the structure of the map (insertion or removal for instance), the
 * calling code must provide the appropriate synchronization. Multiple threads are safe to read from
 * this map concurrently if no write is happening.
 *
 * @param initialCapacity The initial desired capacity for this container. the container will honor
 *   this value by guaranteeing its internal structures can hold that many entries without requiring
 *   any allocations. The initial capacity can be set to 0.
 * @constructor Creates a new [MutableObjectIntMap]
 * @see MutableScatterMap
 */
class MutableObjectIntMap<K>(initialCapacity: Int = DefaultScatterCapacity) : ObjectIntMap<K>() {
  // Number of entries we can add before we need to grow
  private var growthLimit = 0

  init {
    requirePrecondition(initialCapacity >= 0) { "Capacity must be a positive value." }
    initializeStorage(unloadedCapacity(initialCapacity))
  }

  private fun initializeStorage(initialCapacity: Int) {
    val newCapacity =
      if (initialCapacity > 0) {
        // Since we use longs for storage, our capacity is never < 7, enforce
        // it here. We do have a special case for 0 to create small empty maps
        maxOf(7, normalizeCapacity(initialCapacity))
      } else {
        0
      }
    _capacity = newCapacity
    initializeMetadata(newCapacity)
    keys = arrayOfNulls(newCapacity)
    values = IntArray(newCapacity)
  }

  private fun initializeMetadata(capacity: Int) {
    metadata =
      if (capacity == 0) {
        EmptyGroup
      } else {
        // Round up to the next multiple of 8 and find how many longs we need
        val size = (((capacity + 1 + ClonedMetadataCount) + 7) and 0x7.inv()) shr 3
        LongArray(size).apply { fill(AllEmpty) }
      }
    writeRawMetadata(metadata, capacity, Sentinel)
    initializeGrowth()
  }

  private fun initializeGrowth() {
    growthLimit = loadedCapacity(capacity) - _size
  }

  /**
   * Creates a new mapping from [key] to [value] in this map. If [key] is already present in the
   * map, the association is modified and the previously associated value is replaced with
   * [value]. If [key] is not present, a new entry is added to the map, which may require to grow
   * the underlying storage and cause allocations.
   */
  operator fun set(key: K, value: Int) {
    var index = findIndex(key)
    if (index < 0) index = index.inv()
    keys[index] = key
    values[index] = value
  }

  /**
   * Creates a new mapping from [key] to [value] in this map. If [key] is already present in the
   * map, the association is modified and the previously associated value is replaced with
   * [value]. If [key] is not present, a new entry is added to the map, which may require to grow
   * the underlying storage and cause allocations.
   */
  fun put(key: K, value: Int) {
    set(key, value)
  }

  /** Removes the specified [key] and its associated value from the map. */
  fun remove(key: K) {
    val index = findKeyIndex(key)
    if (index >= 0) {
      removeValueAt(index)
    }
  }

  private fun removeValueAt(index: Int) {
    _size -= 1

    // TODO: We could just mark the entry as empty if there's a group
    //       window around this entry that was already empty
    writeMetadata(metadata, _capacity, index, Deleted)
    keys[index] = null
  }

  /** Removes all mappings from this map. */
  fun clear() {
    _size = 0
    if (metadata !== EmptyGroup) {
      metadata.fill(AllEmpty)
      writeRawMetadata(metadata, _capacity, Sentinel)
    }
    keys.fill(null, 0, _capacity)
    initializeGrowth()
  }

  /**
   * Scans the hash table to find the index at which we can store a value for the give [key]. If
   * the key already exists in the table, its index will be returned, otherwise the index of an
   * empty slot will be returned. Calling this function may cause the internal storage to be
   * reallocated if the table is full.
   */
  private fun findIndex(key: K): Int {
    val hash = hash(key)
    val hash1 = h1(hash)
    val hash2 = h2(hash)

    val probeMask = _capacity
    var probeOffset = hash1 and probeMask
    var probeIndex = 0

    while (true) {
      val g = group(metadata, probeOffset)
      var m = g.match(hash2)
      while (m.hasNext()) {
        val index = (probeOffset + m.get()) and probeMask
        if (keys[index] == key) {
          return index
        }
        m = m.next()
      }

      if (g.maskEmpty() != 0L) {
        break
      }

      probeIndex += GroupWidth
      probeOffset = (probeOffset + probeIndex) and probeMask
    }

    var index = findFirstAvailableSlot(hash1)
    if (growthLimit == 0 && !isDeleted(metadata, index)) {
      adjustStorage()
      index = findFirstAvailableSlot(hash1)
    }

    _size += 1
    growthLimit -= if (isEmpty(metadata, index)) 1 else 0
    writeMetadata(metadata, _capacity, index, hash2.toLong())

    return index.inv()
  }

  /**
   * Finds the first empty or deleted slot in the table in which we can store a value without
   * resizing the internal storage.
   */
  private fun findFirstAvailableSlot(hash1: Int): Int {
    val probeMask = _capacity
    var probeOffset = hash1 and probeMask
    var probeIndex = 0

    while (true) {
      val g = group(metadata, probeOffset)
      val m = g.maskEmptyOrDeleted()
      if (m != 0L) {
        return (probeOffset + m.lowestBitSet()) and probeMask
      }
      probeIndex += GroupWidth
      probeOffset = (probeOffset + probeIndex) and probeMask
    }
  }

  /**
   * Grow internal storage if necessary. This function can instead opt to remove deleted entries
   * from the table to avoid an expensive reallocation of the underlying storage. This "rehash in
   * place" occurs when the current size is <= 25/32 of the table capacity. The choice of 25/32 is
   * detailed in the implementation of abseil's `raw_hash_set`.
   */
  private fun adjustStorage() {
    if (_capacity > GroupWidth && _size.toULong() * 32UL <= _capacity.toULong() * 25UL) {
      dropDeletes()
    } else {
      resizeStorage(nextCapacity(_capacity))
    }
  }

  // Internal to prevent inlining
  private fun dropDeletes() {
    val metadata = metadata
    val capacity = _capacity
    val keys = keys
    val values = values

    // Converts Sentinel and Deleted to Empty, and Full to Deleted
    convertMetadataForCleanup(metadata, capacity)

    var index = 0

    // Drop deleted items and re-hashes surviving entries
    while (index != capacity) {
      var m = readRawMetadata(metadata, index)
      // Formerly Deleted entry, we can use it as a swap spot
      if (m == Empty) {
        index++
        continue
      }

      // Formerly Full entries are now marked Deleted. If we see an
      // entry that's not marked Deleted, we can ignore it completely
      if (m != Deleted) {
        index++
        continue
      }

      val hash = hash(keys[index])
      val hash1 = h1(hash)
      val targetIndex = findFirstAvailableSlot(hash1)

      // Test if the current index (i) and the new index (targetIndex) fall
      // within the same group based on the hash. If the group doesn't change,
      // we don't move the entry
      val probeOffset = hash1 and capacity
      val newProbeIndex = ((targetIndex - probeOffset) and capacity) / GroupWidth
      val oldProbeIndex = ((index - probeOffset) and capacity) / GroupWidth

      if (newProbeIndex == oldProbeIndex) {
        val hash2 = h2(hash)
        writeRawMetadata(metadata, index, hash2.toLong())

        // Copies the metadata into the clone area
        metadata[metadata.lastIndex] = (Empty shl 56) or (metadata[0] and 0x00ffffff_ffffffffL)

        index++
        continue
      }

      m = readRawMetadata(metadata, targetIndex)
      if (m == Empty) {
        // The target is empty so we can transfer directly
        val hash2 = h2(hash)
        writeRawMetadata(metadata, targetIndex, hash2.toLong())
        writeRawMetadata(metadata, index, Empty)

        keys[targetIndex] = keys[index]
        keys[index] = null

        values[targetIndex] = values[index]
        values[index] = 0
      } else /* m == Deleted */ {
        // The target isn't empty so we use an empty slot denoted by
        // swapIndex to perform the swap
        val hash2 = h2(hash)
        writeRawMetadata(metadata, targetIndex, hash2.toLong())

        val oldKey = keys[targetIndex]
        keys[targetIndex] = keys[index]
        keys[index] = oldKey

        val oldValue = values[targetIndex]
        values[targetIndex] = values[index]
        values[index] = oldValue

        // Since we exchanged two slots we must repeat the process with
        // element we just moved in the current location
        index--
      }

      // Copies the metadata into the clone area
      metadata[metadata.lastIndex] = (Empty shl 56) or (metadata[0] and 0x00ffffff_ffffffffL)

      index++
    }

    initializeGrowth()
  }

  // Internal to prevent inlining
  private fun resizeStorage(newCapacity: Int) {
    val previousMetadata = metadata
    val previousKeys = keys
    val previousValues = values
    val previousCapacity = _capacity

    initializeStorage(newCapacity)

    val newMetadata = metadata
    val newKeys = keys
    val newValues = values
    val capacity = _capacity

    for (i in 0 until previousCapacity) {
      if (isFull(previousMetadata, i)) {
        val previousKey = previousKeys[i]
        val hash = hash(previousKey)
        val index = findFirstAvailableSlot(h1(hash))

        writeMetadata(newMetadata, capacity, index, h2(hash).toLong())
        newKeys[index] = previousKey
        newValues[index] = previousValues[i]
      }
    }
  }
}
