package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Utils.toByteArray
import org.junit.Test

class UtilsTest {
  @Test
  fun `converts boxed byte array to unboxed`() {
    val expected = byteArrayOf(0x55, 0x66, 0x14)

    assertThat(toByteArray(arrayOf(0x55, 0x66, 0x14)))
      .isEqualTo(expected)
  }

  @Test
  fun `converts unboxed byte array to boxed`() {
    val expected = arrayOf<Byte>(0x55, 0x66, 0x14)

    assertThat(toByteArray(byteArrayOf(0x55, 0x66, 0x14)))
      .isEqualTo(expected)
  }
}
