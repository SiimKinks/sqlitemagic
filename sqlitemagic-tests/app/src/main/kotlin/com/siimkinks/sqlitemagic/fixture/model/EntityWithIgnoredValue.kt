package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
import com.siimkinks.sqlitemagic.annotation.Table

@Table
class EntityWithIgnoredValue {
  @Id
  var id: Long? = null
  var persistedValue: String = ""
  @IgnoreColumn
  var ignoredValue: String = "ignored-default"

  override fun equals(other: Any?) = when {
    this === other -> true
    other !is EntityWithIgnoredValue -> false
    else -> id == other.id &&
        persistedValue == other.persistedValue &&
        ignoredValue == other.ignoredValue
  }

  override fun hashCode(): Int {
    var result = id?.hashCode() ?: 0
    result = 31 * result + persistedValue.hashCode()
    result = 31 * result + ignoredValue.hashCode()
    return result
  }
}
