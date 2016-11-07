package com.siimkinks.sqlitemagic.internal;

public final class ContainerHelpers {
  private ContainerHelpers() {
    throw new AssertionError("no instances");
  }

  public static final byte[] EMPTY_PRIMITIVE_BYTES = new byte[0];
  public static final Byte[] EMPTY_BYTES = new Byte[0];
  static final int[] EMPTY_INTS = new int[0];
  static final Object[] EMPTY_OBJECTS = new Object[0];

  // This is Arrays.binarySearch(), but doesn't do any argument validation.
  static int binarySearch(int[] array, int size, int value) {
    int lo = 0;
    int hi = size - 1;

    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      int midVal = array[mid];

      if (midVal < value) {
        lo = mid + 1;
      } else if (midVal > value) {
        hi = mid - 1;
      } else {
        return mid;  // value found
      }
    }
    return ~lo;  // value not present
  }
}
