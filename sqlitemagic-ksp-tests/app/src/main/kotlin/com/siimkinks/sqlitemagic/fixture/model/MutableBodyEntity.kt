package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
class MutableBodyEntity {
  @Id
  var id: Long? = null
  var value: String = ""

  override fun equals(other: Any?) =
    other is MutableBodyEntity
        && id == other.id
        && value == other.value

  override fun hashCode() = 31 * (id?.hashCode() ?: 0) + value.hashCode()
}
