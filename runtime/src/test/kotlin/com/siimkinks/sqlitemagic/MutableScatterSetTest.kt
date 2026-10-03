package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import org.junit.Test

class MutableScatterSetTest {
  @Test
  fun constructionAndCopyPreserveMembershipWithoutSharingStorage() {
    val keys = Array(
      size = 100,
      init = { "key-$it" }
    )
    val source = MutableScatterSet<String>(initialCapacity = keys.size).apply { addAll(keys) }
    val copy = MutableScatterSet<String>(initialCapacity = source.size).apply { addAll(source) }
    assertThat(contents(source)).isEqualTo(keys.toSet())
    assertThat(contents(copy)).isEqualTo(keys.toSet())
    copy.remove(keys.first())
    copy.add("independent")
    keys[0] = "changed input"
    assertThat(source.contains("key-0")).isTrue()
    assertThat(source.contains("independent")).isFalse()
    assertThat(source.contains("changed input")).isFalse()
  }

  @Test
  fun bulkAdditionDeduplicatesAndSupportsEmptyStringsAndCollidingHashes() {
    val set = MutableScatterSet<String>(0)
    assertThat(set.addAll(arrayOf("", "Aa", "BB", "Aa", "", "other"))).isTrue()
    assertThat(contents(set)).isEqualTo(setOf("", "Aa", "BB", "other"))
    assertThat(set.addAll(arrayOf("Aa", "BB", ""))).isFalse()
    assertThat(set.remove("Aa")).isTrue()
    assertThat(set.remove("missing")).isFalse()
    assertThat(contents(set)).isEqualTo(setOf("", "BB", "other"))
    assertThat(set.contains("Aa")).isFalse()
    assertThat(set.contains("BB")).isTrue()
  }

  @Test
  fun emptySetAllocatesStorageOnlyOnFirstInsertion() {
    val set = MutableScatterSet<String>(0)
    assertThat(set.capacity).isEqualTo(0)
    assertThat(set.size).isEqualTo(0)
    assertThat(set.add("table")).isTrue()
    assertThat(set.capacity).isGreaterThan(0)
    assertThat(contents(set)).isEqualTo(setOf("table"))
  }

  private fun contents(set: MutableScatterSet<String>) = mutableSetOf<String>().apply {
    set.forEach(::add)
  }
}
