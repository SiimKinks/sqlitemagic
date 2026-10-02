package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.Unique

@Table
data class NoIdUniqueEntity(
  @Unique val uniqueValue: String,
  val value: String
)
