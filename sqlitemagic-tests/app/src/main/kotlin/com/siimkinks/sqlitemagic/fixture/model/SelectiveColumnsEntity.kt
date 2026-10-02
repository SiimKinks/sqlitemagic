package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table(persistAll = false)
class SelectiveColumnsEntity {
  @Id
  var id: Long? = null
  @Column
  var persistedValue: String = ""
  var transientValue: String = "selective-default"

  override fun equals(other: Any?) = when {
    this === other -> true
    other !is SelectiveColumnsEntity -> false
    else -> id == other.id &&
        persistedValue == other.persistedValue &&
        transientValue == other.transientValue
  }

  override fun hashCode(): Int {
    var result = id?.hashCode() ?: 0
    result = 31 * result + persistedValue.hashCode()
    result = 31 * result + transientValue.hashCode()
    return result
  }
}
