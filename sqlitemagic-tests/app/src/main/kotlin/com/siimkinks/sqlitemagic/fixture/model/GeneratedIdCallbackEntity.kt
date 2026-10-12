package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn
import com.siimkinks.sqlitemagic.annotation.Table

@Table
class GeneratedIdCallbackEntity {
  @Id
  var id: Long? = null
    set(value) {
      check(!rejectGeneratedId || value == null) { "Generated ID callback failed" }
      field = value
    }
  var value: String = ""

  @IgnoreColumn
  var rejectGeneratedId = false

  override fun equals(other: Any?) = other is GeneratedIdCallbackEntity && id == other.id && value == other.value

  override fun hashCode() = 31 * id.hashCode() + value.hashCode()

  override fun toString() = "GeneratedIdCallbackEntity(id=$id, value=$value)"
}
