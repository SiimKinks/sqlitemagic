package com.siimkinks.sqlitemagic.utils

class FormatData(
  val format: String,
  vararg val args: Any
) {
  fun formatInto(stm: String): String = stm.format(format)

  fun getWithOtherArgsBefore(vararg otherArgs: Any): Array<Any> = otherArgs + args

  fun getWithOtherArgsAfter(vararg otherArgs: Any): Array<Any> = args + otherArgs

  fun getArgsBetween(vararg beforeArgs: Any) = Intermediate(
    format = format,
    args = args,
    beforeArgs = beforeArgs
  )

  class Intermediate internal constructor(
    val format: String,
    val args: Array<out Any>,
    val beforeArgs: Array<out Any>
  ) {
    fun and(vararg afterArgs: Any): Array<Any> = beforeArgs + args + afterArgs
  }
}

@Suppress("UNCHECKED_CAST")
private operator fun Array<out Any>.plus(other: Array<out Any>): Array<Any> {
  val thisSize = size
  val otherSize = other.size
  return (copyOf(thisSize + otherSize) as Array<Any>).apply {
    other.copyInto(
      destination = this@apply,
      destinationOffset = thisSize,
      startIndex = 0,
      endIndex = otherSize
    )
  }
}
