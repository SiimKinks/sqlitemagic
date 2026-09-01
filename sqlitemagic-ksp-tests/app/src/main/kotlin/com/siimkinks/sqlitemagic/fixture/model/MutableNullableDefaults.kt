package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
class MutableNullableDefaults {
  @Id
  var id: Long = 0
  var label: String? = "default-label"
  var count: Int? = 42

  override fun equals(other: Any?) = when {
    this === other -> true
    other !is MutableNullableDefaults -> false
    else -> id == other.id &&
        label == other.label &&
        count == other.count
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + label.hashCode()
    result = 31 * result + (count ?: 0)
    return result
  }
}
