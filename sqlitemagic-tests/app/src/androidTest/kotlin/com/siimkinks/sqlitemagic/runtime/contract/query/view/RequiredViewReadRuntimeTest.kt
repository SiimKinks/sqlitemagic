package com.siimkinks.sqlitemagic.runtime.contract.query.view

import android.database.SQLException
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.RequiredReadViewTable.Companion.REQUIRED_READ_VIEW
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.fixture.model.SequenceId
import com.siimkinks.sqlitemagic.fixture.view.RequiredReadSource
import com.siimkinks.sqlitemagic.fixture.view.RequiredReadView
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test

private val ZEROS = RequiredReadSource(
  id = 1L,
  longValue = 0L,
  intValue = 0,
  shortValue = 0,
  doubleValue = 0.0,
  floatValue = 0f,
  booleanValue = false,
  textValue = "",
  byteValue = 0,
  byteArrayValue = byteArrayOf(),
  boxedByteArrayValue = arrayOf(),
  sequenceValue = SequenceId(0L)
)

internal class RequiredViewReadRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun requiredViewColumnsReadTheStoredValues() {
    val cases = listOf(
      ReadCase(
        label = "zeros and empty values",
        source = ZEROS,
        expected = listOf(
          1L,
          0L,
          0,
          0.toShort(),
          0.0,
          0f,
          false,
          "",
          0.toByte(),
          emptyList<Byte>(),
          emptyList<Byte>(),
          SequenceId(0L)
        )
      ),
      ReadCase(
        label = "non-zero values",
        source = RequiredReadSource(
          id = 2L,
          longValue = 7L,
          intValue = 7,
          shortValue = 7,
          doubleValue = 7.5,
          floatValue = 7.5f,
          booleanValue = true,
          textValue = "text",
          byteValue = 7,
          byteArrayValue = byteArrayOf(7),
          boxedByteArrayValue = arrayOf(7),
          sequenceValue = SequenceId(7L)
        ),
        expected = listOf(
          2L,
          7L,
          7,
          7.toShort(),
          7.5,
          7.5f,
          true,
          "text",
          7.toByte(),
          listOf<Byte>(7),
          listOf<Byte>(7),
          SequenceId(7L)
        )
      )
    )

    for ((label, source, expected) in cases) {
      source
        .insert()
        .execute()

      assertWithMessage(label)
        .that(read(source.id).storedValues())
        .isEqualTo(expected)
    }
  }

  @Test
  fun requiredViewColumnsRejectSqlNull() {
    val cases = listOf(
      NullCase(
        column = "long_value",
        source = ZEROS.copy(id = 1L, longValue = null)
      ),
      NullCase(
        column = "int_value",
        source = ZEROS.copy(id = 2L, intValue = null)
      ),
      NullCase(
        column = "short_value",
        source = ZEROS.copy(id = 3L, shortValue = null)
      ),
      NullCase(
        column = "double_value",
        source = ZEROS.copy(id = 4L, doubleValue = null)
      ),
      NullCase(
        column = "float_value",
        source = ZEROS.copy(id = 5L, floatValue = null)
      ),
      NullCase(
        column = "boolean_value",
        source = ZEROS.copy(id = 6L, booleanValue = null)
      ),
      NullCase(
        column = "text_value",
        source = ZEROS.copy(id = 7L, textValue = null)
      ),
      NullCase(
        column = "byte_value",
        source = ZEROS.copy(id = 8L, byteValue = null)
      ),
      NullCase(
        column = "byte_array_value",
        source = ZEROS.copy(id = 9L, byteArrayValue = null)
      ),
      NullCase(
        column = "boxed_byte_array_value",
        source = ZEROS.copy(id = 10L, boxedByteArrayValue = null)
      ),
      NullCase(
        column = "sequence_value",
        source = ZEROS.copy(id = 11L, sequenceValue = null)
      )
    )

    for ((column, source) in cases) {
      source
        .insert()
        .execute()

      val failure = assertThrows(column, SQLException::class.java) {
        read(source.id)
      }

      assertThat(failure)
        .hasMessageThat()
        .isEqualTo("""Column "required_read_source.$column" was NULL""")
    }
  }

  private fun read(id: Long) = SELECT
    .FROM(REQUIRED_READ_VIEW)
    .WHERE(REQUIRED_READ_VIEW.REQUIRED_READ_SOURCE_ID IS id)
    .takeFirst()
    .execute()
    .let(::checkNotNull)

  private fun RequiredReadView.storedValues() = listOf(
    id,
    longValue,
    intValue,
    shortValue,
    doubleValue,
    floatValue,
    booleanValue,
    textValue,
    byteValue,
    byteArrayValue.toList(),
    boxedByteArrayValue.toList(),
    sequenceValue
  )

  private data class ReadCase(
    val label: String,
    val source: RequiredReadSource,
    val expected: List<Any>
  )

  private data class NullCase(
    val column: String,
    val source: RequiredReadSource
  )
}
