package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

@Table
data class Account(
  @Id val id: AccountId,
  val label: String = ""
)

data class AccountId(val value: String)

@ObjectToDbValue
fun accountIdToString(value: AccountId): String = value.value

@DbValueToObject
fun stringToAccountId(value: String): AccountId = AccountId(value)
