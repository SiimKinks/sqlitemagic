package com.siimkinks.sqlitemagic.model.immutable

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class ImmutableValueWithNullableFields(
  @Id val id: Long?,
  val string: String?,
  val aBoolean: Boolean?,
  val integer: Int?
)