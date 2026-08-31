package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.Unique

@Table
data class NoIdUniqueAccount(
  @Unique
  @Column(handleRecursively = false)
  val account: Account,
  val value: String
)
