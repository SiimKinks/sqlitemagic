package com.siimkinks.sqlitemagic.internal

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

internal class ScatterCollectionsTest {
  @Test
  fun `primitive map nullable lookup preserves all stored integers`() {
    val map: ObjectIntMap<String> = MutableObjectIntMap<String>().apply {
      this["zero"] = 0
      this["positive"] = 42
      this["negative"] = -2
      this["minus one"] = -1
      this["minimum"] = Int.MIN_VALUE
      this["maximum"] = Int.MAX_VALUE
    }
    val cases = mapOf(
      "missing" to null,
      "zero" to 0,
      "positive" to 42,
      "negative" to -2,
      "minus one" to -1,
      "minimum" to Int.MIN_VALUE,
      "maximum" to Int.MAX_VALUE
    )
    cases.forEach { (label, expected) ->
      assertWithMessage(label)
        .that(map.getOrNull(label))
        .isEqualTo(expected)
    }
  }

  @Test
  fun `ordered set toTypedArray preserves order, nulls and runtime component type`() {
    val cases = mapOf(
      "empty" to emptyArray<String?>(),
      "ordered" to arrayOf<String?>("second", "first", "third"),
      "nullable" to arrayOf("second", null, "first")
    )
    cases.forEach { (label, expected) ->
      val set = MutableOrderedScatterSet<String?>(initialCapacity = expected.size).apply {
        expected.forEach(::add)
      }
      val actual = set.toTypedArray()
      set.clear()

      assertWithMessage("$label: independent ordered snapshot")
        .that(actual)
        .isEqualTo(expected)
      assertWithMessage("$label: runtime component type")
        .that(actual.javaClass.componentType)
        .isEqualTo(String::class.java)
    }
  }

  @Test
  fun `scatter map matches reference through collision, deletion and growth traces`() {
    traceCases.forEach { case ->
      val actual = MutableScatterMap<CollisionKey?, String?>(case.initialCapacity)
      val expected = mutableMapOf<CollisionKey?, String?>()
      val random = Random(case.seed)
      repeat(3000) { step ->
        val key = key(
          id = random.nextInt(160),
          hashCount = case.hashCount
        )
        val value = when (step % 7) {
          0 -> null
          else -> "value-$step"
        }
        when (step % 13) {
          0, 1, 2, 3, 4, 5, 6 -> {
            assertWithMessage("${case.label}: put $step")
              .that(
                actual.put(
                  key = key,
                  value = value
                )
              )
              .isEqualTo(
                expected.put(
                  key = key,
                  value = value
                )
              )
          }
          7, 8, 9 -> assertWithMessage("${case.label}: remove $step")
            .that(actual.remove(key))
            .isEqualTo(expected.remove(key))
          10, 11 -> assertThat(actual[key]).isEqualTo(expected[key])
          12 -> if (step % 117 == 12) {
            actual.clear()
            expected.clear()
          }
        }
        assertWithMessage("${case.label}: contents $step")
          .that(contents(actual))
          .isEqualTo(expected)
        assertThat(actual.size).isEqualTo(expected.size)
        assertThat(actual.containsKey(key)).isEqualTo(expected.containsKey(key))
      }
    }
  }

  @Test
  fun `primitive map matches reference including zero and missing values`() {
    traceCases.forEach { case ->
      val actual = MutableObjectIntMap<CollisionKey?>(case.initialCapacity)
      val expected = mutableMapOf<CollisionKey?, Int>()
      val random = Random(case.seed)
      repeat(3000) { step ->
        val key = key(
          id = random.nextInt(160),
          hashCount = case.hashCount
        )
        val value = when (step % 7) {
          0 -> 0
          else -> -step
        }
        when (step % 13) {
          0, 1, 2, 3, 4, 5, 6 -> {
            actual[key] = value
            expected[key] = value
          }
          7, 8, 9 -> {
            actual.remove(key)
            expected.remove(key)
          }
          10, 11 -> assertThat(actual.getOrNull(key)).isEqualTo(expected[key])
          12 -> if (step % 117 == 12) {
            actual.clear()
            expected.clear()
          }
        }
        assertWithMessage("${case.label}: contents $step")
          .that(contents(actual))
          .isEqualTo(expected)
        assertThat(actual.size).isEqualTo(expected.size)
        assertThat(actual.containsKey(key)).isEqualTo(expected.containsKey(key))
      }
      val missing = CollisionKey(
        id = 1000,
        hash = 0
      )
      assertThrows(NoSuchElementException::class.java) { actual[missing] }
    }
  }

  @Test
  fun `scatter set matches reference through collision, deletion and growth traces`() {
    traceCases.forEach { case ->
      val actual = MutableScatterSet<CollisionKey?>(case.initialCapacity)
      val expected = mutableSetOf<CollisionKey?>()
      val random = Random(case.seed)
      repeat(3000) { step ->
        val key = key(
          id = random.nextInt(160),
          hashCount = case.hashCount
        )
        when (step % 11) {
          0, 1, 2, 3, 4, 5 -> assertThat(actual.add(key)).isEqualTo(expected.add(key))
          6, 7, 8 -> assertThat(actual.remove(key)).isEqualTo(expected.remove(key))
          9 -> assertThat(actual.isEmpty()).isEqualTo(expected.isEmpty())
          10 -> if (step % 99 == 10) {
            actual.clear()
            expected.clear()
          }
        }
        assertWithMessage("${case.label}: contents $step")
          .that(contents(actual))
          .isEqualTo(expected)
        assertThat(actual.size).isEqualTo(expected.size)
        assertThat(key in actual).isEqualTo(key in expected)
      }
    }
  }

  @Test
  fun `ordered set matches insertion order through deletion, reinsertion and growth traces`() {
    traceCases.forEach { case ->
      val actual = MutableOrderedScatterSet<CollisionKey?>(case.initialCapacity)
      val expected = linkedSetOf<CollisionKey?>()
      val random = Random(case.seed)
      repeat(3000) { step ->
        val key = key(
          id = random.nextInt(160),
          hashCount = case.hashCount
        )
        when (step % 11) {
          0, 1, 2, 3, 4, 5 -> assertThat(actual.add(key)).isEqualTo(expected.add(key))
          6, 7, 8 -> assertThat(actual.remove(key)).isEqualTo(expected.remove(key))
          9 -> assertThat(actual.isEmpty()).isEqualTo(expected.isEmpty())
          10 -> if (step % 99 == 10) {
            actual.clear()
            expected.clear()
          }
        }
        assertWithMessage("${case.label}: ordered contents $step")
          .that(contents(actual))
          .isEqualTo(expected.toList())
        assertThat(actual.size).isEqualTo(expected.size)
        assertThat(key in actual).isEqualTo(key in expected)
      }
    }
  }

  @Test
  fun `ordered set duplicate insertion preserves all node links and insertion order`() {
    val duplicates = mapOf(
      "first" to "first",
      "middle" to "middle",
      "last" to "last",
      "null" to null
    )
    duplicates.forEach { (position, duplicate) ->
      DuplicateOperation.entries.forEach { operation ->
        val set = MutableOrderedScatterSet<String?>().apply {
          arrayOf("first", "middle", null, "last").forEach(::add)
        }
        val expectedNodes = orderedNodes(set)
        when (operation) {
          DuplicateOperation.ADD -> assertThat(set.add(duplicate)).isFalse()
          DuplicateOperation.PLUS_ASSIGN -> set += duplicate
        }
        // Verify links before iteration: the unpatched duplicate-last case creates a self-cycle.
        assertWithMessage("$position duplicate via $operation")
          .that(orderedNodes(set))
          .isEqualTo(expectedNodes)
        assertWithMessage("$position insertion order via $operation")
          .that(contents(set))
          .isEqualTo(listOf("first", "middle", null, "last"))
      }
    }
  }

  @Test
  fun `integral float hashes spread across metadata tags`() {
    val tags = (1..256)
      .map(Int::toFloat)
      .map(::hash)
      .map(::h2)
      .toSet()

    // Integral Float hash codes leave their low bits empty; those bits alone produce one tag.
    assertThat(tags.size).isGreaterThan(64)
  }

  @Test
  fun `native traversal visits each live slot once and allows removing current entry`() {
    mapOf(
      "empty lazy storage" to 0,
      "single live slot" to 1,
      "minimum capacity and sentinel" to 6,
      "two metadata groups" to 14,
      "sparse groups with deleted slots" to 56
    ).forEach { (label, count) ->
      val map = MutableScatterMap<Int, Int>(initialCapacity = count)
      val primitiveMap = MutableObjectIntMap<Int>(initialCapacity = count)
      val set = MutableScatterSet<Int>(initialCapacity = count)
      val orderedSet = MutableOrderedScatterSet<Int>(initialCapacity = count)
      repeat(count) { key ->
        map[key] = key * 2
        primitiveMap[key] = key * 2
        set.add(key)
        orderedSet.add(key)
      }
      val expected = (0 until count).filter { it % 3 == 0 }
      (0 until count)
        .filter { it % 3 != 0 }
        .forEach { key ->
          map.remove(key)
          primitiveMap.remove(key)
          set.remove(key)
          orderedSet.remove(key)
        }

      val equalOrderedSet = MutableOrderedScatterSet<Int>(initialCapacity = count).apply {
        expected
          .asReversed()
          .forEach(::add)
      }
      assertWithMessage("$label: unordered equality")
        .that(orderedSet)
        .isEqualTo(equalOrderedSet)
      assertWithMessage("$label: unordered hash traversal")
        .that(orderedSet.hashCode())
        .isEqualTo(equalOrderedSet.hashCode())
      if (expected.isNotEmpty()) {
        equalOrderedSet.remove(expected.last())
        equalOrderedSet.add(Int.MIN_VALUE)
        assertWithMessage("$label: unordered equality exits at a missing element")
          .that(orderedSet)
          .isNotEqualTo(equalOrderedSet)
      }

      val mapFound = run scan@{
        map.forEach { _, _ -> return@scan true }
        false
      }
      val primitiveMapFound = run scan@{
        primitiveMap.forEach { _, _ -> return@scan true }
        false
      }
      val setFound = run scan@{
        set.forEach { return@scan true }
        false
      }
      assertWithMessage("$label: nonlocal return from native traversal")
        .that(listOf(mapFound, primitiveMapFound, setFound))
        .containsExactlyElementsIn(List(3) { expected.isNotEmpty() })

      val mapVisits = mutableListOf<Pair<Int, Int>>()
      map.forEach { key, value ->
        mapVisits += key to value
        map.remove(key)
      }
      val primitiveMapVisits = mutableListOf<Pair<Int, Int>>()
      primitiveMap.forEach { key, value ->
        primitiveMapVisits += key to value
        primitiveMap.remove(key)
      }
      val setVisits = mutableListOf<Int>()
      set.forEach { key ->
        setVisits += key
        set.remove(key)
      }
      val orderedVisits = mutableListOf<Int>()
      orderedSet.forEach { key ->
        orderedVisits += key
        orderedSet.remove(key)
      }
      val expectedEntries = expected.map { it to it * 2 }
      assertWithMessage("$label: map visits before removing current entry")
        .that(mapVisits)
        .containsExactlyElementsIn(expectedEntries)
      assertWithMessage("$label: primitive map visits before removing current entry")
        .that(primitiveMapVisits)
        .containsExactlyElementsIn(expectedEntries)
      assertWithMessage("$label: set visits before removing current entry")
        .that(setVisits)
        .containsExactlyElementsIn(expected)
      assertWithMessage("$label: ordered visits before removing current entry")
        .that(orderedVisits)
        .containsExactlyElementsIn(expected)
        .inOrder()
      assertWithMessage("$label: all current entries removed")
        .that(listOf(map.size, primitiveMap.size, set.size, orderedSet.size))
        .containsExactly(0, 0, 0, 0)
    }
  }

  @Test
  fun `read-only set view reflects native mutations and compares equal views`() {
    val set = MutableScatterSet<String?>().apply { addAll(arrayOf("Aa", "BB", null)) }
    val view = set.asSet()
    val equalSet = MutableScatterSet<String?>().apply { addAll(arrayOf(null, "BB", "Aa")) }
    assertThat(set.asSet()).isSameInstanceAs(view)
    assertThat(view.size).isEqualTo(3)
    assertThat(view.containsAll(listOf("Aa", "BB", null))).isTrue()
    assertThat(view.contains("missing")).isFalse()
    assertThat(view.toSet()).isEqualTo(setOf("Aa", "BB", null))
    assertThat(view).isEqualTo(equalSet.asSet())
    assertThat(view.hashCode()).isEqualTo(equalSet.asSet().hashCode())
    set.remove("Aa")
    set.add("new")
    assertThat(view.toSet()).isEqualTo(setOf("BB", null, "new"))
    assertThat(view).isNotEqualTo(equalSet.asSet())
    set.clear()
    assertThat(view.isEmpty()).isTrue()
  }

  @Test
  fun `clear retains backing storage for reuse`() {
    val map = MutableScatterMap<Int, Int>(0)
    val primitiveMap = MutableObjectIntMap<Int>(0)
    val set = MutableScatterSet<Int>(0)
    val orderedSet = MutableOrderedScatterSet<Int>(0)
    repeat(100) { index ->
      map[index] = index
      primitiveMap[index] = index
      set.add(index)
      orderedSet.add(index)
    }
    val mapKeys = map.keys
    val mapValues = map.values
    val primitiveKeys = primitiveMap.keys
    val primitiveValues = primitiveMap.values
    val setElements = set.elements
    val orderedElements = orderedSet.elements
    val orderedNodes = orderedSet.nodes
    map.clear()
    primitiveMap.clear()
    set.clear()
    orderedSet.clear()
    assertThat(map.keys).isSameInstanceAs(mapKeys)
    assertThat(map.values).isSameInstanceAs(mapValues)
    assertThat(primitiveMap.keys).isSameInstanceAs(primitiveKeys)
    assertThat(primitiveMap.values).isSameInstanceAs(primitiveValues)
    assertThat(set.elements).isSameInstanceAs(setElements)
    assertThat(orderedSet.elements).isSameInstanceAs(orderedElements)
    assertThat(orderedSet.nodes).isSameInstanceAs(orderedNodes)
    assertThat(mapKeys.toList()).containsExactlyElementsIn(List(map.capacity) { null })
    assertThat(mapValues.toList()).containsExactlyElementsIn(List(map.capacity) { null })
    assertThat(primitiveKeys.toList()).containsExactlyElementsIn(List(primitiveMap.capacity) { null })
    assertThat(setElements.toList()).containsExactlyElementsIn(List(set.capacity) { null })
    assertThat(orderedElements.toList()).containsExactlyElementsIn(List(orderedSet.capacity) { null })
    map[0] = 1
    primitiveMap[0] = 1
    set.add(0)
    orderedSet.add(0)
    assertThat(contents(map)).isEqualTo(mapOf(0 to 1))
    assertThat(contents(primitiveMap)).isEqualTo(mapOf(0 to 1))
    assertThat(contents(set)).isEqualTo(setOf(0))
    assertThat(contents(orderedSet)).isEqualTo(listOf(0))
    assertThat(map.keys).isSameInstanceAs(mapKeys)
    assertThat(map.values).isSameInstanceAs(mapValues)
    assertThat(primitiveMap.keys).isSameInstanceAs(primitiveKeys)
    assertThat(primitiveMap.values).isSameInstanceAs(primitiveValues)
    assertThat(set.elements).isSameInstanceAs(setElements)
    assertThat(orderedSet.elements).isSameInstanceAs(orderedElements)
    assertThat(orderedSet.nodes).isSameInstanceAs(orderedNodes)
  }

  private fun key(id: Int, hashCount: Int) = when (id) {
    0 -> null
    else -> CollisionKey(
      id = id,
      hash = id % hashCount
    )
  }

  private fun <K, V> contents(map: ScatterMap<K, V>) = mutableMapOf<K, V>().apply {
    map.forEach { key, value -> this[key] = value }
  }

  private fun <K> contents(map: ObjectIntMap<K>) = mutableMapOf<K, Int>().apply {
    map.forEach { key, value -> this[key] = value }
  }

  private fun <E> contents(set: ScatterSet<E>) = mutableSetOf<E>().apply {
    set.forEach(::add)
  }

  private fun <E> contents(set: OrderedScatterSet<E>) = mutableListOf<E>().apply {
    set.forEach(::add)
  }

  private fun orderedNodes(set: MutableOrderedScatterSet<String?>) = OrderedNodes(
    size = set.size,
    head = set.head,
    tail = set.tail,
    nodes = set.nodes.toList()
  )

  private data class OrderedNodes(val size: Int, val head: Int, val tail: Int, val nodes: List<Long>)

  private enum class DuplicateOperation {
    ADD,
    PLUS_ASSIGN
  }

  private data class CollisionKey(val id: Int, val hash: Int) {
    override fun hashCode() = hash
  }

  private data class TraceCase(val label: String, val initialCapacity: Int, val hashCount: Int, val seed: Int)

  private val traceCases = listOf(
    TraceCase(
      label = "all keys collide, lazy capacity",
      initialCapacity = 0,
      hashCount = 1,
      seed = 71
    ),
    TraceCase(
      label = "four hash groups, minimum capacity",
      initialCapacity = 6,
      hashCount = 4,
      seed = 83
    ),
    TraceCase(
      label = "mixed hashes, preallocated capacity",
      initialCapacity = 100,
      hashCount = 160,
      seed = 97
    )
  )
}
