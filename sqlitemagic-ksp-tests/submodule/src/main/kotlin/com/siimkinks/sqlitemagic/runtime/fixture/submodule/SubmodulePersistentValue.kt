package com.siimkinks.sqlitemagic.runtime.fixture.submodule

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class SubmodulePersistentValue(
  @Id val id: String,
  @Index("submodule_persistent_value_index")
  val value: String
)
