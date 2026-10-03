@file:Suppress("PropertyName", "NOTHING_TO_INLINE")
@file:OptIn(ExperimentalContracts::class)

package com.siimkinks.sqlitemagic.internal

import com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_OBJECTS
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

// Default empty set to avoid allocations
/** Returns a new typed array containing this set's elements in insertion order. */
internal inline fun <reified E> OrderedScatterSet<E>.toTypedArray(): Array<E> {
  val array = arrayOfNulls<E>(size)
  var index = 0
  forEach { array[index++] = it }

  @Suppress("UNCHECKED_CAST")
  return array as Array<E>
}

/**
 * [OrderedScatterSet] is a container with a [Set]-like interface based on a flat hash table
 * implementation that preserves the insertion order for iteration. The underlying implementation is
 * designed to avoid all allocations on insertion, removal, retrieval, and iteration. Allocations
 * may still happen on insertion when the underlying storage needs to grow to accommodate newly
 * added elements to the set.
 *
 * This implementation guarantees the order of the elements when iterating over them using [forEach]
 * using [forEach].
 *
 * Though [OrderedScatterSet] offers a read-only interface, it is always backed by a
 * [MutableOrderedScatterSet]. Read operations alone are thread-safe. However, any mutations done
 * through the backing [MutableScatterSet] while reading on another thread are not safe and the
 * developer must protect the set from such changes during read operations.
 *
 *
 * @see [MutableOrderedScatterSet]
 */
internal sealed class OrderedScatterSet<E> {
  // NOTE: Our arrays are marked internal to implement inlined forEach{}
  // The backing array for the metadata bytes contains
  // `capacity + 1 + ClonedMetadataCount` elements, including when
  // the set is empty (see [EmptyGroup]).
  @JvmField
  internal var metadata: LongArray = EmptyGroup

  @JvmField
  internal var elements: Array<Any?> = EMPTY_OBJECTS

  @JvmField
  internal var nodes: LongArray = EmptyNodes

  @JvmField
  internal var head: Int = NodeInvalidLink

  @JvmField
  internal var tail: Int = NodeInvalidLink

  // We use a backing field for capacity to avoid invokevirtual calls
  // every time we need to look at the capacity
  @JvmField
  internal var _capacity: Int = 0

  /**
   * Returns the number of elements that can be stored in this set without requiring internal
   * storage reallocation.
   */
  val capacity get() = _capacity

  // We use a backing field for capacity to avoid invokevirtual calls
  // every time we need to look at the size
  @JvmField
  protected var _size: Int = 0

  /** Returns the number of elements in this set. */
  val size get() = _size

  /** Indicates whether this set is empty. */
  fun isEmpty(): Boolean = _size == 0

  /** Returns `true` if this set is not empty. */
  fun isNotEmpty(): Boolean = _size != 0

  /** Iterates over every element stored in this set by invoking the specified [block] lambda. */
  private inline fun unorderedForEachIndex(block: (index: Int) -> Unit) {
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

  /**
   * Iterates over every element stored in this set by invoking the specified [block] lambda. The
   * iteration order is the same as the insertion order. It is safe to remove the element passed
   * to [block] during iteration.
   *
   * @param block called with each element in the set
   */
  inline fun forEach(block: (element: E) -> Unit) {
    contract { callsInPlace(block) }
    val elements = elements
    val nodes = nodes

    var candidate = tail
    while (candidate != NodeInvalidLink) {
      val previousNode = nodes[candidate].previousNode
      @Suppress("UNCHECKED_CAST") block(elements[candidate] as E)
      candidate = previousNode
    }
  }

  /**
   * Iterates over every element stored in this set by invoking the specified [block] lambda. The
   * elements are iterated in arbitrary order.
   *
   * @param block called with each element in the set
   */
  private inline fun unorderedForEach(block: (element: E) -> Unit) {
    contract { callsInPlace(block) }
    val elements = elements
    unorderedForEachIndex { index -> @Suppress("UNCHECKED_CAST") block(elements[index] as E) }
  }

  /**
   * Returns true if the specified [element] is present in this hash set, false otherwise.
   *
   * @param element The element to look for in this set
   */
  operator fun contains(element: E): Boolean = findElementIndex(element) >= 0

  /**
   * Returns the hash code value for this set. The hash code of a set is based on the sum of the
   * hash codes of the elements in the set, where the hash code of a null element is defined to be
   * zero.
   */
  override fun hashCode(): Int {
    var hash = _capacity
    hash = 31 * hash + _size

    unorderedForEach { element ->
      if (element != this) {
        hash += element.hashCode()
      }
    }

    return hash
  }

  /**
   * Compares the specified object [other] with this hash set for equality. The two objects are
   * considered equal if [other]:
   * - Is a [OrderedScatterSet]
   * - Has the same [size] as this set
   * - Contains elements equal to this set's elements
   */
  override fun equals(other: Any?): Boolean {
    if (other === this) {
      return true
    }

    if (other !is OrderedScatterSet<*>) {
      return false
    }
    if (other.size != size) {
      return false
    }

    @Suppress("UNCHECKED_CAST") val o = other as OrderedScatterSet<Any?>

    unorderedForEach { element ->
      if (element !in o) {
        return false
      }
    }

    return true
  }

  override fun toString(): String = buildString {
    append('[')
    var index = 0
    this@OrderedScatterSet.forEach { element ->
      if (index++ > 0) {
        append(", ")
      }
      append(
        when {
          element === this@OrderedScatterSet -> "(this)"
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
}

/**
 * [MutableOrderedScatterSet] is a container with a [MutableSet]-like interface based on a flat hash
 * table implementation that preserves the insertion order for iteration. The underlying
 * implementation is designed to avoid all allocations on insertion, removal, retrieval, and
 * iteration. Allocations may still happen on insertion when the underlying storage needs to grow to
 * accommodate newly added elements to the set.
 *
 * This implementation guarantees the order of the elements when iterating over them using [forEach]
 * using [forEach].
 *
 * This implementation is not thread-safe: if multiple threads access this container concurrently,
 * and one or more threads modify the structure of the set (insertion or removal for instance), the
 * calling code must provide the appropriate synchronization. Concurrent reads are however safe.
 *
 *
 *
 * @param initialCapacity The initial desired capacity for this container. the container will honor
 *   this value by guaranteeing its internal structures can hold that many elements without
 *   requiring any allocations. The initial capacity can be set to 0.
 * @constructor Creates a new [MutableOrderedScatterSet]
 * @see Set
 */
internal class MutableOrderedScatterSet<E>(initialCapacity: Int = DefaultScatterCapacity) : OrderedScatterSet<E>() {
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
    nodes = if (newCapacity == 0) EmptyNodes else LongArray(newCapacity).apply { fill(EmptyNode) }
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
   * Adds the specified element to the set.
   *
   * @param element The element to add to the set.
   * @return `true` if the element has been added or `false` if the element is already contained
   *   within the set.
   */
  fun add(element: E): Boolean {
    val oldSize = size
    val index = findAbsoluteInsertIndex(element)
    elements[index] = element
    if (size != oldSize) {
      moveNodeToHead(index)
    }
    return size != oldSize
  }

  /**
   * Adds the specified element to the set.
   *
   * @param element The element to add to the set.
   */
  operator fun plusAssign(element: E) {
    val oldSize = size
    val index = findAbsoluteInsertIndex(element)
    elements[index] = element
    if (size != oldSize) {
      moveNodeToHead(index)
    }
  }

  /**
   * Removes the specified [element] from the set.
   *
   * @param element The element to be removed from the set.
   * @return `true` if the [element] was present in the set, or `false` if it wasn't present
   *   before removal.
   */
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

    removeNode(index)
  }

  private inline fun moveNodeToHead(index: Int) {
    nodes[index] = createLinkToNext(head)

    if (head != NodeInvalidLink) {
      nodes[head] = setLinkToPrevious(nodes[head], index)
    }
    head = index

    if (tail == NodeInvalidLink) {
      tail = index
    }
  }

  private inline fun removeNode(index: Int) {
    val nodes = nodes
    val node = nodes[index]
    val previousIndex = node.previousNode
    val nextIndex = node.nextNode

    if (previousIndex != NodeInvalidLink) {
      nodes[previousIndex] = setLinkToNext(nodes[previousIndex], nextIndex)
    } else {
      head = nextIndex
    }

    if (nextIndex != NodeInvalidLink) {
      nodes[nextIndex] = setLinkToPrevious(nodes[nextIndex], previousIndex)
    } else {
      tail = previousIndex
    }

    nodes[index] = EmptyNode
  }

  /** Removes all elements from this set. */
  fun clear() {
    _size = 0
    if (metadata !== EmptyGroup) {
      metadata.fill(AllEmpty)
      writeRawMetadata(metadata, _capacity, Sentinel)
    }
    elements.fill(null, 0, _capacity)
    nodes.fill(EmptyNode)
    head = NodeInvalidLink
    tail = NodeInvalidLink
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
    growthLimit -= if (isEmpty(metadata, index)) 1 else 0
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
    // TODO: This shouldn't be required, but without it the compiler generates an extra
    //       200+ aarch64 instructions to generate a NullPointerException.
    @Suppress("SENSELESS_COMPARISON") if (metadata == null) return

    val capacity = _capacity
    val elements = elements
    val nodes = nodes

    // In this function, we are swapping values in place in the keys/values/nodes arrays.
    // This requires us to track where the values came from original in the array and
    // where they moved. You can think of this as an allocation-free double-linked list.
    //
    // We need this mapping to fix the links encoded in the nodes array. The nodes array
    // is itself an allocation-free double-linked list which uses indices to indicate where
    // to find the next/previous node. Since this method will move the values inside the
    // data structure, we need to patch the nodes array when we're done. We could skip the
    // mapping array but that would require scanning the entire nodes array every time we move
    // a value inside the data structure which would be more expensive. Instead we traverse
    // the nodes array only once in [fixup].
    //
    // Each index mapping is a (src, dst) pair. The source index indicates which
    // index the current value came, and the destination index indicates where the
    // value previously held was moved. For instance we want to swap the values
    // at index 4 and 21:
    //
    // indexMapping[4] = (21, 21)
    // The value at index 4 came from index 21 (src) and the value previously at index 4
    // is now at index 21.
    //
    // indexMapping[21] = (4, 4)
    // The value at index 21 came from index 4 (src) and the value previously at index 21
    // is now at index 4.
    //
    // Now let's imagine we want to swap the values at index 4 and 22 (following the previous
    // swap):
    //
    // indexMapping[4] = (22, 21)
    // The value at index 4 came from index 22 (src) and the value previously at index 4
    // is now at index 21.
    //
    // indexMapping[21] = (4, 22)
    // The value at index 21 came from index 4 (src) and the value previously at index 21
    // is now at index 22.
    //
    // indexMapping[22] = (21, 4)
    // The value at index 22 came from index 21 (src) and the value previously at index 22
    // is now at index 4.
    //
    // If a src or dst mapping is set to be invalid ([InvalidMappingLink]), the mapping does
    // not exist. We initialize the array to (0x7fff_ffff, 0x7fff_ffff).
    val indexMapping = LongArray(capacity)
    indexMapping.fill(InvalidMapping, 0, capacity)

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

      // Test if the current index (index) and the new index (targetIndex) fall
      // within the same group based on the hash. If the group doesn't change,
      // we don't move the entry
      val probeOffset = hash1 and capacity
      val newProbeIndex = ((targetIndex - probeOffset) and capacity) / GroupWidth
      val oldProbeIndex = ((index - probeOffset) and capacity) / GroupWidth

      if (newProbeIndex == oldProbeIndex) {
        val hash2 = h2(hash)
        writeRawMetadata(metadata, index, hash2.toLong())

        // Don't erase an existing mapping created from a previous swap
        if (indexMapping[index] == InvalidMapping) {
          indexMapping[index] = createMapping(index, index)
        }

        // Copies the metadata into the clone area
        metadata[metadata.size - 1] = metadata[0]

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

        nodes[targetIndex] = nodes[index]
        nodes[index] = EmptyNode

        val mapping = indexMapping[index]
        val src = mapping.src
        if (src != InvalidMappingLink) {
          indexMapping[src] = createDstMapping(indexMapping[src], targetIndex)
          indexMapping[index] = eraseSrcMapping(indexMapping[index])
        } else {
          indexMapping[index] = createMapping(InvalidMappingLink, targetIndex)
        }
        indexMapping[targetIndex] = createMapping(index, InvalidMappingLink)
      } else /* m == Deleted */ {
        // The target isn't empty
        val hash2 = h2(hash)
        writeRawMetadata(metadata, targetIndex, hash2.toLong())

        val oldElement = elements[targetIndex]
        elements[targetIndex] = elements[index]
        elements[index] = oldElement

        val oldNode = nodes[targetIndex]
        nodes[targetIndex] = nodes[index]
        nodes[index] = oldNode

        val mapping = indexMapping[index]
        var src = mapping.src
        if (src != InvalidMappingLink) {
          indexMapping[src] = createDstMapping(indexMapping[src], targetIndex)
          indexMapping[index] = createSrcMapping(indexMapping[index], targetIndex)
        } else {
          indexMapping[index] = createMapping(targetIndex, targetIndex)
          src = index
        }

        indexMapping[targetIndex] = createMapping(src, index)

        // Since we exchanged two slots we must repeat the process with
        // element we just moved in the current location
        index--
      }

      // Copies the metadata into the clone area
      metadata[metadata.size - 1] = metadata[0]

      index++
    }

    initializeGrowth()

    fixupNodes(indexMapping)
  }

  // Internal to prevent inlining
  private fun resizeStorage(newCapacity: Int) {
    val previousMetadata = metadata
    val previousElements = elements
    val previousNodes = nodes
    val previousCapacity = _capacity

    val indexMapping = IntArray(previousCapacity)

    initializeStorage(newCapacity)

    val newMetadata = metadata
    val newElements = elements
    val newNodes = nodes
    val capacity = _capacity

    for (i in 0 until previousCapacity) {
      if (isFull(previousMetadata, i)
      ) {
        val previousKey = previousElements[i]
        val hash = hash(previousKey)
        val index = findFirstAvailableSlot(h1(hash))

        writeMetadata(newMetadata, capacity, index, h2(hash).toLong())
        newElements[index] = previousKey
        newNodes[index] = previousNodes[i]

        indexMapping[i] = index
      }
    }

    fixupNodes(indexMapping)
  }

  private fun fixupNodes(mapping: LongArray) {
    val nodes = nodes
    for (i in nodes.indices) {
      val node = nodes[i]
      val previous = node.previousNode
      val next = node.nextNode
      nodes[i] = createLinks(node, previous, next, mapping)
    }
    if (head != NodeInvalidLink) head = mapping[head].dst
    if (tail != NodeInvalidLink) tail = mapping[tail].dst
  }

  private fun fixupNodes(mapping: IntArray) {
    val nodes = nodes
    for (i in nodes.indices) {
      val node = nodes[i]
      val previous = node.previousNode
      val next = node.nextNode
      nodes[i] = createLinks(node, previous, next, mapping)
    }
    if (head != NodeInvalidLink) head = mapping[head]
    if (tail != NodeInvalidLink) tail = mapping[tail]
  }
}
