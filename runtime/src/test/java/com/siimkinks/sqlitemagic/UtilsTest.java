package com.siimkinks.sqlitemagic;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.Utils.toByteArray;

public final class UtilsTest {
  @Test
  public void boxedByteArrayToUnboxed() {
    final byte[] expected = new byte[]{0x55, 0x66, 0x14};
    assertThat(toByteArray(new Byte[]{0x55, 0x66, 0x14})).isEqualTo(expected);
  }

  @Test
  public void unboxedByteArrayToBoxed() {
    final Byte[] expected = new Byte[]{0x55, 0x66, 0x14};
    assertThat(toByteArray(new byte[]{0x55, 0x66, 0x14})).isEqualTo(expected);
  }
}
