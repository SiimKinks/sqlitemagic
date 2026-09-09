package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class NoIdEntity(
  @Index(
    value = "no_id_value_unique_index",
    unique = true
  )
  val value: String
)
