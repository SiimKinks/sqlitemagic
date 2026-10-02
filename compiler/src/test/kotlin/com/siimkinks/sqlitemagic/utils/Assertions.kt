package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertThat

fun String.assertContains(
  vararg expected: String
) {
  val subject = assertThat(this)
  expected.forEach(subject::contains)
}

fun String.assertDoesNotContain(
  vararg unexpected: String
) {
  val subject = assertThat(this)
  unexpected.forEach(subject::doesNotContain)
}

fun String.assertContainsInOrder(
  vararg expected: String
) {
  var previousIndex = -1
  expected.forEach { value ->
    val index = indexOf(
      string = value,
      startIndex = previousIndex + 1
    )
    assertThat(index).isGreaterThan(previousIndex)
    previousIndex = index
  }
}
