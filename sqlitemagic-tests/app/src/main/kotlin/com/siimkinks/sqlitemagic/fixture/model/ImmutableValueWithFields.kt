package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class ImmutableValueWithFields(
  @Id var id: Long?,
  val stringValue: String,
  val aBoolean: Boolean,
  val integer: Int,
  val aDouble: Double,
  val aShort: Short,
  val transformableObject: TransformableObject
)
