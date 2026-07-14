package com.siimkinks.sqlitemagic.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class FormatDataTest {
  @Test
  fun `formats statement with stored format`() {
    val formatData = FormatData("Name = %s", "ignored")

    assertThat(
      formatData.formatInto("where %s")
    ).isEqualTo("where Name = %s")
  }

  @Test
  fun `returns stored args with other args before`() {
    val formatData = FormatData("format", "stored", 2)

    assertThat(
      formatData
        .getWithOtherArgsBefore("first", 1)
        .asList()
    ).isEqualTo(listOf("first", 1, "stored", 2))
  }

  @Test
  fun `returns stored args with other args after`() {
    val formatData = FormatData("format", "stored", 2)

    assertThat(
      formatData
        .getWithOtherArgsAfter("last", 3)
        .asList()
    ).isEqualTo(listOf("stored", 2, "last", 3))
  }

  @Test
  fun `returns stored args between before and after args`() {
    val formatData = FormatData("format", "stored", 2)

    assertThat(
      formatData
        .getArgsBetween("before", 1)
        .and("after", 3)
        .asList()
    ).isEqualTo(listOf("before", 1, "stored", 2, "after", 3))
  }

  @Test
  fun `copies vararg arrays when creating intermediate args`() {
    val storedArgs = arrayOf<Any>("stored")
    val beforeArgs = arrayOf<Any>("before")
    val formatData = FormatData("format", *storedArgs)

    val intermediate = formatData.getArgsBetween(*beforeArgs)
    storedArgs[0] = "changed stored"
    beforeArgs[0] = "changed before"

    assertThat(intermediate.and().asList())
      .isEqualTo(listOf("before", "stored"))
  }
}
