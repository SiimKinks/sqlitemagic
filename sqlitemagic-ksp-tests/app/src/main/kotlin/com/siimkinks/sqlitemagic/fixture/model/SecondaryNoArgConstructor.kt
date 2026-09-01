package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Table

@Table
class SecondaryNoArgConstructor(initialValue: String) {
  var value: String = initialValue

  constructor() : this("")

  override fun equals(other: Any?) =
    other is SecondaryNoArgConstructor &&
        value == other.value

  override fun hashCode() = value.hashCode()
}
