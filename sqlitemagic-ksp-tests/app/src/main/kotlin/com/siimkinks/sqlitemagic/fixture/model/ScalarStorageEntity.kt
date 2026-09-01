package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class ScalarStorageEntity(
  @Id var id: Long?,
  val longValue: Long,
  val nullableLong: Long?,
  val floatValue: Float,
  val nullableFloat: Float?,
  val byteValue: Byte,
  val nullableByte: Byte?,
  val byteArray: ByteArray,
  val nullableByteArray: ByteArray?,
  val boxedByteArray: Array<Byte>,
  val nullableBoxedByteArray: Array<Byte>?
) {
  override fun equals(other: Any?) = when {
    this === other -> true
    other !is ScalarStorageEntity -> false
    else -> id == other.id &&
        longValue == other.longValue &&
        nullableLong == other.nullableLong &&
        floatValue == other.floatValue &&
        nullableFloat == other.nullableFloat &&
        byteValue == other.byteValue &&
        nullableByte == other.nullableByte &&
        byteArray.contentEquals(other.byteArray) &&
        nullableByteArray.contentEquals(other.nullableByteArray) &&
        boxedByteArray.contentEquals(other.boxedByteArray) &&
        nullableBoxedByteArray.contentEquals(other.nullableBoxedByteArray)
  }

  override fun hashCode(): Int {
    var result = id?.hashCode() ?: 0
    result = 31 * result + longValue.hashCode()
    result = 31 * result + (nullableLong?.hashCode() ?: 0)
    result = 31 * result + floatValue.hashCode()
    result = 31 * result + (nullableFloat?.hashCode() ?: 0)
    result = 31 * result + byteValue.hashCode()
    result = 31 * result + (nullableByte?.hashCode() ?: 0)
    result = 31 * result + byteArray.contentHashCode()
    result = 31 * result + (nullableByteArray?.contentHashCode() ?: 0)
    result = 31 * result + boxedByteArray.contentHashCode()
    result = 31 * result + (nullableBoxedByteArray?.contentHashCode() ?: 0)
    return result
  }
}
