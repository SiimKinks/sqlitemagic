package com.siimkinks.sqlitemagic.runtime.fixture.submodule

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class SubmodulePersistentValue(
  @Id val id: String,
  val value: String
)
