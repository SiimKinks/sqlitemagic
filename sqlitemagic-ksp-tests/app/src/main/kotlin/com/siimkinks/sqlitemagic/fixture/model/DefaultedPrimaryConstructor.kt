package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Table

@Table
class DefaultedPrimaryConstructor(initialValue: String = "") {
  var value: String = initialValue

  override fun equals(other: Any?) =
    other is DefaultedPrimaryConstructor &&
        value == other.value

  override fun hashCode() = value.hashCode()
}
