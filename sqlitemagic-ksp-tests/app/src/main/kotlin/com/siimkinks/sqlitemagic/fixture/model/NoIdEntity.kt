package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class NoIdEntity(
  val value: String
)
