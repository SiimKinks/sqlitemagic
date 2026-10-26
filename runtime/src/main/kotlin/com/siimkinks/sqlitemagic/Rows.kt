package com.siimkinks.sqlitemagic

/** The immutable result of selecting two expressions, in selection order. */
data class Row2<out A, out B>(
  val first: A,
  val second: B
)

/** The immutable result of selecting three expressions, in selection order. */
data class Row3<out A, out B, out C>(
  val first: A,
  val second: B,
  val third: C
)

/** The immutable result of selecting four expressions, in selection order. */
data class Row4<out A, out B, out C, out D>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D
)

/** The immutable result of selecting five expressions, in selection order. */
data class Row5<out A, out B, out C, out D, out E>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
  val fifth: E
)

/** The immutable result of selecting six expressions, in selection order. */
data class Row6<out A, out B, out C, out D, out E, out F>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
  val fifth: E,
  val sixth: F
)

/** The immutable result of selecting seven expressions, in selection order. */
data class Row7<out A, out B, out C, out D, out E, out F, out G>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
  val fifth: E,
  val sixth: F,
  val seventh: G
)

/** The immutable result of selecting eight expressions, in selection order. */
data class Row8<out A, out B, out C, out D, out E, out F, out G, out H>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
  val fifth: E,
  val sixth: F,
  val seventh: G,
  val eighth: H
)

/** The immutable result of selecting nine expressions, in selection order. */
data class Row9<out A, out B, out C, out D, out E, out F, out G, out H, out I>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
  val fifth: E,
  val sixth: F,
  val seventh: G,
  val eighth: H,
  val ninth: I
)
