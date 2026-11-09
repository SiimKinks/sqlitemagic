package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class NullableLongKeyEntity(
  @Id(autoIncrement = false) val id: Long?,
  val name: String
)
