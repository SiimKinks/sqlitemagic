package com.siimkinks.sqlitemagic.submodule

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table(persistAll = true, useAccessMethods = true)
data class ImmutableValueWithNullableFields(
  @Id val id: Long?,
  val string: String?,
  val aBoolean: Boolean?,
  val integer: Int?
)