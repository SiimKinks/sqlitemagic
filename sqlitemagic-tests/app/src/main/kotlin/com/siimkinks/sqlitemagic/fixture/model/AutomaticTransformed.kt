package com.siimkinks.sqlitemagic.fixture.model

import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue

@Table
data class AutomaticTransformed(
  @Id val id: SequenceId,
  val value: String
)

data class SequenceId(val value: Long)

@ObjectToDbValue
fun sequenceIdToLong(value: SequenceId): Long = value.value

@DbValueToObject
fun longToSequenceId(value: Long): SequenceId = SequenceId(value)
