package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import org.junit.Test

internal class MutableScatterSetTest {
  @Test
  fun `construction and copy preserve membership without sharing storage`() {
    val keys = Array(100) { "key-$it" }
    val source = MutableScatterSet<String>(initialCapacity = keys.size).apply { addAll(keys) }
    val copy = MutableScatterSet<String>(initialCapacity = source.size).apply { addAll(source) }
    assertThat(contents(source)).isEqualTo(keys.toSet())
    assertThat(contents(copy)).isEqualTo(keys.toSet())
    copy.remove(keys.first())
    copy.add("independent")
    keys[0] = "changed input"
    assertThat("key-0" in source).isTrue()
    assertThat("independent" in source).isFalse()
    assertThat("changed input" in source).isFalse()
  }

  @Test
  fun `bulk addition deduplicates and supports empty strings and colliding hashes`() {
    val set = MutableScatterSet<String>(0)
    assertThat(set.addAll(arrayOf("", "Aa", "BB", "Aa", "", "other"))).isTrue()
    assertThat(contents(set)).isEqualTo(setOf("", "Aa", "BB", "other"))
    assertThat(set.addAll(arrayOf("Aa", "BB", ""))).isFalse()
    assertThat(set.remove("Aa")).isTrue()
    assertThat(set.remove("missing")).isFalse()
    assertThat(contents(set)).isEqualTo(setOf("", "BB", "other"))
    assertThat("Aa" in set).isFalse()
    assertThat("BB" in set).isTrue()
  }

  @Test
  fun `set view follows the set equality contract`() {
    val view = MutableScatterSet<String>(0)
      .apply { addAll(arrayOf("books", "authors")) }
      .asSet()
    val cases = listOf(
      "same elements" to (setOf("authors", "books") to true),
      "missing element" to (setOf("books") to false),
      "other element" to (setOf("books", "series") to false),
      "empty" to (emptySet<String>() to false)
    )

    for ((label, case) in cases) {
      val (other, equal) = case
      assertWithMessage(label)
        .that(listOf(view == other, other == view, view.hashCode() == other.hashCode()))
        .isEqualTo(listOf(equal, equal, equal))
    }
  }

  @Test
  fun `empty set allocates storage only on first insertion`() {
    val set = MutableScatterSet<String>(0)
    assertThat(set.capacity).isEqualTo(0)
    assertThat(set.size).isEqualTo(0)
    assertThat(set.add("table")).isTrue()
    assertThat(set.capacity).isGreaterThan(0)
    assertThat(contents(set)).isEqualTo(setOf("table"))
  }

  private fun contents(set: MutableScatterSet<String>) = buildSet { set.forEach(::add) }
}
