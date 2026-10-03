@file:Suppress("NOTHING_TO_INLINE")

package com.siimkinks.sqlitemagic.internal

internal const val NodeInvalidLink: Int = 0x7fff_ffff

private const val NodeLinkMask: Long = 0x7fff_ffffL
private const val NodeMetaMask = -0x40000000_00000000L // 0xc0000000_00000000UL.toLong()
private const val NodeMetaAndNextMask = -0x3fffffff_80000001L // 0xc0000000_7fffffffUL.toLong()
private const val NodeMetaAndPreviousMask = -0x00000000_80000000L // 0xffffffff_80000000UL.toLong()

internal const val EmptyNode = 0x3fffffff_ffffffffL
internal val EmptyNodes = LongArray(0)

internal const val InvalidMappingLink: Int = 0x7fff_ffff
internal const val InvalidMapping: Long = 0x7fff_ffff_7fff_ffffL

internal inline fun createLinks(node: Long, previous: Int, next: Int, mapping: LongArray): Long {
  return (node and NodeMetaMask) or
      (if (previous == NodeInvalidLink) NodeInvalidLink else mapping[previous].dst).toLong() shl
      31 or
      (if (next == NodeInvalidLink) NodeInvalidLink else mapping[next].dst).toLong()
}

internal inline fun createLinks(node: Long, previous: Int, next: Int, mapping: IntArray): Long {
  return (node and NodeMetaMask) or
      (if (previous == NodeInvalidLink) NodeInvalidLink else mapping[previous]).toLong() shl
      31 or
      (if (next == NodeInvalidLink) NodeInvalidLink else mapping[next]).toLong()
}

// set meta to 0 (visited = false) and previous to NodeInvalidLink
internal inline fun createLinkToNext(next: Int) =
  0x3fffffff_80000000L or (next.toLong() and NodeLinkMask)

internal inline fun setLinkToPrevious(node: Long, previous: Int) =
  (node and NodeMetaAndNextMask) or ((previous.toLong() and NodeLinkMask) shl 31)

internal inline fun setLinkToNext(node: Long, next: Int) =
  (node and NodeMetaAndPreviousMask) or (next.toLong() and NodeLinkMask)

internal inline val Long.previousNode: Int
  get() = ((this shr 31) and NodeLinkMask).toInt()

internal inline val Long.nextNode: Int
  get() = (this and NodeLinkMask).toInt()

internal inline fun createMapping(src: Int, dst: Int) = (src.toLong() shl 32) or dst.toLong()

internal inline fun createSrcMapping(mapping: Long, src: Int) =
  (src.toLong() shl 32) or (mapping and 0xffff_ffffL)

internal inline fun createDstMapping(mapping: Long, dst: Int) =
  (mapping and 0xffff_ffff_0000_0000UL.toLong()) or dst.toLong()

internal inline fun eraseSrcMapping(mapping: Long) =
  0xffff_ffff_0000_0000UL.toLong() or (mapping and 0xffff_ffffL)

internal inline val Long.src: Int
  get() = ((this shr 32) and 0xffff_ffffL).toInt()

internal inline val Long.dst: Int
  get() = (this and 0xffff_ffffL).toInt()
