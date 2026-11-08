package com.siimkinks.sqlitemagic.fixture.view

import com.siimkinks.sqlitemagic.RequiredReadSourceTable.Companion.REQUIRED_READ_SOURCE
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery
import com.siimkinks.sqlitemagic.fixture.model.SequenceId

@Table
data class RequiredReadSource(
  @Id(autoIncrement = false) val id: Long,
  val longValue: Long?,
  val intValue: Int?,
  val shortValue: Short?,
  val doubleValue: Double?,
  val floatValue: Float?,
  val booleanValue: Boolean?,
  val textValue: String?,
  val byteValue: Byte?,
  val byteArrayValue: ByteArray?,
  val boxedByteArrayValue: Array<Byte>?,
  val sequenceValue: SequenceId?
)

@View("required_read_view")
data class RequiredReadView(
  @ViewColumn("required_read_source.id") val id: Long,
  @ViewColumn("required_read_source.long_value") val longValue: Long,
  @ViewColumn("required_read_source.int_value") val intValue: Int,
  @ViewColumn("required_read_source.short_value") val shortValue: Short,
  @ViewColumn("required_read_source.double_value") val doubleValue: Double,
  @ViewColumn("required_read_source.float_value") val floatValue: Float,
  @ViewColumn("required_read_source.boolean_value") val booleanValue: Boolean,
  @ViewColumn("required_read_source.text_value") val textValue: String,
  @ViewColumn("required_read_source.byte_value") val byteValue: Byte,
  @ViewColumn("required_read_source.byte_array_value") val byteArrayValue: ByteArray,
  @ViewColumn("required_read_source.boxed_byte_array_value") val boxedByteArrayValue: Array<Byte>,
  @ViewColumn("required_read_source.sequence_value") val sequenceValue: SequenceId
) {
  companion object {
    @ViewQuery
    val query = SELECT.FROM(REQUIRED_READ_SOURCE)
  }
}
