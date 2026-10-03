@file:Suppress("PropertyName", "NOTHING_TO_INLINE")
@file:OptIn(ExperimentalContracts::class)

package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_OBJECTS
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// This is a copy of ScatterMap, but without values
sealed class ScatterSet<E> {
  // NOTE: Our arrays are marked internal to implement inlined forEach{}
  // The backing array for the metadata bytes contains
  // `capacity + 1 + ClonedMetadataCount` elements, including when
  // the set is empty (see [EmptyGroup]).
  @PublishedApi
  @JvmField
  internal var metadata: LongArray = EmptyGroup

  @PublishedApi
  @JvmField
  internal var elements: Array<Any?> = EMPTY_OBJECTS

  // We use a backing field for capacity to avoid invokevirtual calls
  // every time we need to look at the capacity
  @JvmField
  internal var _capacity: Int = 0
  val capacity get() = _capacity

  // We use a backing field for capacity to avoid invokevirtual calls
  // every time we need to look at the size
  @JvmField
  internal var _size: Int = 0
  val size get() = _size

  private var setWrapper: Set<E>? = null

  fun isEmpty(): Boolean = _size == 0

  fun isNotEmpty(): Boolean = _size != 0

  /** Iterates over every element stored in this set by invoking the specified [block] lambda. */
  @PublishedApi
  internal inline fun forEachIndex(block: (index: Int) -> Unit) {
    contract { callsInPlace(block) }
    val m = metadata
    val lastIndex = m.size - 2 // We always have 0 or at least 2 elements

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

  inline fun forEach(block: (element: E) -> Unit) {
    contract { callsInPlace(block) }
    val elements = elements
    forEachIndex { index -> @Suppress("UNCHECKED_CAST") block(elements[index] as E) }
  }

  operator fun contains(element: E): Boolean = findElementIndex(element) >= 0

  override fun hashCode(): Int {
    var hash = 0

    forEach { element -> hash += element.hashCode() }

    return hash
  }

  override fun equals(other: Any?): Boolean {
    if (other === this) {
      return true
    }

    if (other !is ScatterSet<*>) {
      return false
    }
    if (other.size != size) {
      return false
    }

    @Suppress("UNCHECKED_CAST") val o = other as ScatterSet<Any?>

    forEach { element ->
      if (element !in o) {
        return false
      }
    }

    return true
  }

  override fun toString(): String = buildString {
    append('[')
    var index = 0
    this@ScatterSet.forEach { element ->
      if (index++ > 0) {
        append(", ")
      }
      append(
        when {
          element === this@ScatterSet -> "(this)"
          else -> element.toString()
        }
      )
    }
    append(']')
  }

  /**
   * Scans the set to find the index in the backing arrays of the specified [element]. Returns -1
   * if the element is not present.
   */
  internal inline fun findElementIndex(element: E): Int {
    val hash = hash(element)
    val hash2 = h2(hash)

    val probeMask = _capacity
    var probeOffset = h1(hash) and probeMask
    var probeIndex = 0
    while (true) {
      val g = group(metadata, probeOffset)
      var m = g.match(hash2)
      while (m.hasNext()) {
        val index = (probeOffset + m.get()) and probeMask
        if (elements[index] == element) {
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

  /**
   * Wraps this set with a read-only Kotlin [Set] view. Changes to this set are reflected in the view.
   * Modifying this set during view iteration has undefined results. Prefer native [forEach] when possible;
   * the standard [Set] interface requires iterator allocation.
   */
  fun asSet(): Set<E> = setWrapper ?: SetWrapper(this).also { setWrapper = it }
}

open class MutableScatterSet<E>(initialCapacity: Int = DefaultScatterCapacity) :
  ScatterSet<E>() {
  // Number of elements we can add before we need to grow
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
    elements = if (newCapacity == 0) EMPTY_OBJECTS else arrayOfNulls(newCapacity)
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

  fun add(element: E): Boolean {
    val oldSize = size
    val index = findAbsoluteInsertIndex(element)
    elements[index] = element
    return size != oldSize
  }

  operator fun plusAssign(element: E) {
    val index = findAbsoluteInsertIndex(element)
    elements[index] = element
  }

  fun addAll(@Suppress("ArrayReturn") elements: Array<out E>): Boolean {
    val oldSize = size
    plusAssign(elements)
    return oldSize != size
  }

  fun addAll(elements: ScatterSet<E>): Boolean {
    val oldSize = size
    plusAssign(elements)
    return oldSize != size
  }

  operator fun plusAssign(@Suppress("ArrayReturn") elements: Array<out E>) {
    elements.forEach(::plusAssign)
  }

  operator fun plusAssign(elements: ScatterSet<E>) {
    elements.forEach(::plusAssign)
  }

  fun remove(element: E): Boolean {
    val index = findElementIndex(element)
    val exists = index >= 0
    if (exists) {
      removeElementAt(index)
    }
    return exists
  }

  private fun removeElementAt(index: Int) {
    _size -= 1

    // TODO: We could just mark the element as empty if there's a group
    //       window around this element that was already empty
    writeMetadata(metadata, _capacity, index, Deleted)
    elements[index] = null
  }

  fun clear() {
    _size = 0
    if (metadata !== EmptyGroup) {
      metadata.fill(AllEmpty)
      writeRawMetadata(metadata, _capacity, Sentinel)
    }
    elements.fill(null, 0, _capacity)
    initializeGrowth()
  }

  /**
   * Scans the set to find the index at which we can store the given [element]. If the element
   * already exists in the set, its index will be returned, otherwise the index of an empty slot
   * will be returned. Calling this function may cause the internal storage to be reallocated if
   * the set is full.
   */
  private fun findAbsoluteInsertIndex(element: E): Int {
    val hash = hash(element)
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
        if (elements[index] == element) {
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
    if (growthLimit == 0 && !isDeleted(metadata, index)
    ) {
      adjustStorage()
      index = findFirstAvailableSlot(hash1)
    }

    _size += 1
    growthLimit -= if (isEmpty(metadata, index)
    ) 1 else 0
    writeMetadata(metadata, _capacity, index, hash2.toLong())

    return index
  }

  /**
   * Finds the first empty or deleted slot in the set in which we can store an element without
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
   * Grow internal storage if necessary. This function can instead opt to remove deleted elements
   * from the set to avoid an expensive reallocation of the underlying storage. This "rehash in
   * place" occurs when the current size is <= 25/32 of the set capacity. The choice of 25/32 is
   * detailed in the implementation of abseil's `raw_hash_map`.
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
    val elements = elements

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

      val hash = hash(elements[index])
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
        metadata[metadata.lastIndex] =
          (Empty shl 56) or (metadata[0] and 0x00ffffff_ffffffffL)

        index++
        continue
      }

      m = readRawMetadata(metadata, targetIndex)
      if (m == Empty) {
        // The target is empty so we can transfer directly
        val hash2 = h2(hash)
        writeRawMetadata(metadata, targetIndex, hash2.toLong())
        writeRawMetadata(metadata, index, Empty)

        elements[targetIndex] = elements[index]
        elements[index] = null
      } else /* m == Deleted */ {
        // The target isn't empty so we use an empty slot denoted by
        // swapIndex to perform the swap
        val hash2 = h2(hash)
        writeRawMetadata(metadata, targetIndex, hash2.toLong())

        val oldElement = elements[targetIndex]
        elements[targetIndex] = elements[index]
        elements[index] = oldElement

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
    val previousElements = elements
    val previousCapacity = _capacity

    initializeStorage(newCapacity)

    val newMetadata = metadata
    val newElements = elements
    val capacity = _capacity

    for (i in 0 until previousCapacity) {
      if (isFull(previousMetadata, i)
      ) {
        val previousElement = previousElements[i]
        val hash = hash(previousElement)
        val index = findFirstAvailableSlot(h1(hash))

        writeMetadata(newMetadata, capacity, index, h2(hash).toLong())
        newElements[index] = previousElement
      }
    }
  }
}

private class SetWrapper<E>(private val parent: ScatterSet<E>) : Set<E> {
  override val size: Int
    get() = parent._size

  override fun containsAll(elements: Collection<E>): Boolean {
    elements.forEach { element ->
      if (!parent.contains(element)) {
        return false
      }
    }
    return true
  }

  @Suppress("KotlinOperator")
  override fun contains(element: E): Boolean = parent.contains(element)

  override fun isEmpty(): Boolean = parent.isEmpty()

  override fun iterator(): Iterator<E> = iterator {
    parent.forEach { element -> yield(element) }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || this::class != other::class) return false

    other as SetWrapper<*>

    return parent == other.parent
  }

  override fun hashCode(): Int = parent.hashCode()

  override fun toString(): String = parent.toString()
}
