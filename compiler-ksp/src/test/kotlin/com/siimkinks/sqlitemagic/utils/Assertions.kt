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