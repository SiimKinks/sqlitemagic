package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Table

@Table
class NonDataConstructorEntity(
  val value: String
) {
  override fun equals(other: Any?) =
    other is NonDataConstructorEntity &&
        value == other.value

  override fun hashCode() = value.hashCode()
}
