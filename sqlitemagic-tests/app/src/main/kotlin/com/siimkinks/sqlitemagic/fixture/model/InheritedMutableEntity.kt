package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

open class InheritedMutableBase {
  var inheritedValue: String = ""
}

@Table
class InheritedMutableEntity : InheritedMutableBase() {
  @Id
  var id: Long? = null
  var value: String = ""

  override fun equals(other: Any?) =
    other is InheritedMutableEntity &&
        id == other.id &&
        inheritedValue == other.inheritedValue
        && value == other.value

  override fun hashCode() = 31 * (31 * (id?.hashCode() ?: 0) + inheritedValue.hashCode()) + value.hashCode()
}
