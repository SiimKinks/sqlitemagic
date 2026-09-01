package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY

@Table(options = [TEMPORARY])
data class TemporaryAccountEntry(
  @Id val id: String,
  @Column(handleRecursively = false) val account: Account,
  val value: String
)
