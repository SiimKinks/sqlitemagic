package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
class BlobEntity(
  @Id val id: Long?,
  val payload: ByteArray
) {
  override fun equals(other: Any?) = when {
    this === other -> true
    other !is BlobEntity -> false
    else -> id == other.id && payload.contentEquals(other.payload)
  }

  override fun hashCode(): Int {
    var result = id?.hashCode() ?: 0
    result = 31 * result + payload.contentHashCode()
    return result
  }
}
