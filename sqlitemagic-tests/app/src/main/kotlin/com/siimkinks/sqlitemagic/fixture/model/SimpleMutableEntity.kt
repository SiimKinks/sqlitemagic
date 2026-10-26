package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class SimpleMutableEntity(
  @Id var id: Long? = null,
  var value: String? = null,
  var boxedBoolean: Boolean? = null,
  var primitiveBoolean: Boolean = false
)
