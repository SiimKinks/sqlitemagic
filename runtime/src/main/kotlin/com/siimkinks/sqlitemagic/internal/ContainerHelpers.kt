package com.siimkinks.sqlitemagic.internal

object ContainerHelpers {
  val EMPTY_PRIMITIVE_BYTES = byteArrayOf()
  val EMPTY_BYTES = emptyArray<Byte>()
  val EMPTY_STRINGS = emptyArray<String>()
  val EMPTY_INTS = intArrayOf()
  val EMPTY_OBJECTS = emptyArray<Any?>()

  // This is Arrays.binarySearch(), but doesn't do any argument validation.
  fun binarySearch(array: IntArray, size: Int, value: Int): Int {
    var lo = 0
    var hi = size - 1

    while (lo <= hi) {
      val mid = (lo + hi) ushr 1
      val midVal = array[mid]

      when {
        midVal < value -> lo = mid + 1
        midVal > value -> hi = mid - 1
        else -> return mid // value found
      }
    }
    return lo.inv() // value not present
  }
}
