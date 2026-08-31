package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table

@Table
data class Article(
  @Id val id: String,
  @Column(handleRecursively = false) val account: Account,
  val value: String
)
