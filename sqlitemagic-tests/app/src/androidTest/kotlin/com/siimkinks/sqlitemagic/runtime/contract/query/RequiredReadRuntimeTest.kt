package com.siimkinks.sqlitemagic.runtime.contract.query

import android.database.SQLException
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.BLOB_TOKEN
import com.siimkinks.sqlitemagic.ReadContract
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SEQUENCE_ID
import com.siimkinks.sqlitemagic.SqlType
import com.siimkinks.sqlitemagic.fixture.model.BlobToken
import com.siimkinks.sqlitemagic.fixture.model.SequenceId
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import org.junit.Rule
import org.junit.Test

internal class RequiredReadRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun requiredReadsKeepZerosAndEmptyValuesAndRejectSqlNull() {
    val cases = listOf(
      ReadCase(
        label = "SHORT",
        type = SqlType.SHORT,
        value = "0",
        expected = 0.toShort()
      ),
      ReadCase(
        label = "INT",
        type = SqlType.INT,
        value = "0",
        expected = 0
      ),
      ReadCase(
        label = "LONG",
        type = SqlType.LONG,
        value = "0",
        expected = 0L
      ),
      ReadCase(
        label = "FLOAT",
        type = SqlType.FLOAT,
        value = "0.0",
        expected = 0f
      ),
      ReadCase(
        label = "DOUBLE",
        type = SqlType.DOUBLE,
        value = "0.0",
        expected = 0.0
      ),
      ReadCase(
        label = "BOOLEAN",
        type = SqlType.BOOLEAN,
        value = "0",
        expected = false
      ),
      ReadCase(
        label = "STRING",
        type = SqlType.STRING,
        value = "''",
        expected = ""
      ),
      ReadCase(
        label = "BYTE",
        type = SqlType.BYTE,
        value = "x'00'",
        expected = 0.toByte()
      ),
      ReadCase(
        label = "BYTE_ARRAY",
        type = SqlType.BYTE_ARRAY,
        value = "x''",
        expected = emptyList<Byte>()
      ),
      ReadCase(
        label = "BOXED_BYTE_ARRAY",
        type = SqlType.BOXED_BYTE_ARRAY,
        value = "x''",
        expected = emptyList<Byte>()
      ),
      ReadCase(
        label = "SEQUENCE_ID",
        type = SqlType.SEQUENCE_ID,
        value = "0",
        expected = SequenceId(0)
      ),
      ReadCase(
        label = "BLOB_TOKEN",
        type = SqlType.BLOB_TOKEN,
        value = "x''",
        expected = BlobToken(byteArrayOf())
      )
    )

    for ((label, type, value, expected) in cases) {
      val reads = listOf(value, "NULL").map { read(value = it, type = type) }

      assertWithMessage(label)
        .that(reads)
        .isEqualTo(listOf(expected, "Required SQL result returned SQL NULL"))
    }
  }

  private fun read(value: String, type: ReadContract<*, *, *>) = try {
    val read = SELECT
      .RAW("SELECT $value")
      .useRows { rows ->
        rows.step()
        rows.read(
          index = 0,
          type = type
        )
      }
    when (read) {
      is ByteArray -> read.toList()
      is Array<*> -> read.toList()
      else -> read
    }
  } catch (failure: SQLException) {
    failure.message
  }

  private data class ReadCase(
    val label: String,
    val type: ReadContract<*, *, *>,
    val value: String,
    val expected: Any
  )
}
