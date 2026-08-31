package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.Unique

@Table
data class NoIdMultiUniqueEntity(
  @Unique val slug: String,
  @Unique val externalKey: String,
  val value: String
)
