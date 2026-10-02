package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

class NestedModelContainer {
  @Table
  data class NestedEntity(
    @Id val id: String,
    val value: String
  )
}

@JvmInline
@Table
value class ValueClassEntity(
  @Id val value: String
)
