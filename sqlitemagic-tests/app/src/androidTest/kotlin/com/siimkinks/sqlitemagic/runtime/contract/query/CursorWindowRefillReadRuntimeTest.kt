package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DELETE
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlType.BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlType.DOUBLE
import com.siimkinks.sqlitemagic.SqlType.FLOAT
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.NUMBER
import com.siimkinks.sqlitemagic.SqlType.SHORT
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.mapTo
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Test

private const val WINDOW_DATABASE_NAME = "cursor-window-refill-runtime.db"
private const val FRAMEWORK_CURSOR_WINDOW_SIZE = 2 * 1024 * 1024
private const val PAYLOAD_SIZE = 64 * 1024
private const val ROW_COUNT = 6 * FRAMEWORK_CURSOR_WINDOW_SIZE / PAYLOAD_SIZE
private const val LARGE_INTEGER = 1L shl 40
private const val SHORT_VALUE_LENGTH = 16

internal class CursorWindowRefillReadRuntimeTest {
  @Test
  fun typedRowsMapInOrderAcrossWindowRefills() = withWindowRows { connection, rows ->
    assertThat(
      SELECT
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
        .usingConnection(connection)
        .execute()
    ).isEqualTo(rows)
  }

  @Test
  fun useRowsReadsInOrderAcrossWindowRefills() = withWindowRows { connection, rows ->
    val typed = SELECT
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
      .usingConnection(connection)
      .useRows(Sequence<SimpleMutableEntity>::toList)
    val raw = SELECT
      .RAW("SELECT id, value FROM simple_mutable_entity ORDER BY id")
      .observes(SIMPLE_MUTABLE_ENTITY)
      .usingConnection(connection)
      .useRows { rawRows ->
        buildList {
          while (rawRows.step()) {
            add(
              Pair(
                first = rawRows.read(
                  index = 0,
                  type = LONG
                ),
                second = rawRows.read(
                  index = 1,
                  type = STRING
                )
              )
            )
          }
        }
      }

    assertThat(typed to raw).isEqualTo(rows to rows.map { checkNotNull(it.id) to checkNotNull(it.value) })
  }

  @Test
  fun useRowsReadsOneSnapshotWhileAWriteCommits() = withWindowRows { connection, rows ->
    val stored = SELECT
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
      .usingConnection(connection)

    val read = stored.useRows { entities ->
      entities
        .onEachIndexed { index, _ ->
          if (index == 0) {
            (DELETE FROM SIMPLE_MUTABLE_ENTITY)
              .usingConnection(connection)
              .execute()
          }
        }
        .toList()
    }

    assertThat(read to stored.execute()).isEqualTo(rows to emptyList<SimpleMutableEntity>())
  }

  @Test
  fun rawScalarReadsStayExactAcrossWindowRefills() = withWindowRows { connection, rows ->
    val actual = SELECT
      .RAW(
        """
        SELECT id, id, value,
          CASE WHEN id % 2 = 0 THEN NULL ELSE substr(value, 1, $SHORT_VALUE_LENGTH) END,
          CAST(CASE WHEN id % 2 = 0 THEN NULL ELSE substr(value, 1, $SHORT_VALUE_LENGTH) END AS BLOB),
          id + $LARGE_INTEGER, id + 0.25, id + 0.25,
          CASE id % 3 WHEN 0 THEN NULL WHEN 1 THEN id + $LARGE_INTEGER ELSE id + 0.5 END
        FROM simple_mutable_entity ORDER BY id
        """.trimIndent()
      )
      .observes(SIMPLE_MUTABLE_ENTITY)
      .readAs(SHORT, INT, STRING, STRING.nullable, BYTE_ARRAY.nullable, LONG, FLOAT, DOUBLE, NUMBER.nullable)
      .mapTo { short, int, value, nullableText, nullableBlob, large, float, double, mixed ->
        RawScalars(
          short = short,
          int = int,
          value = value,
          nullableText = nullableText,
          nullableBlob = nullableBlob?.toList(),
          large = large,
          float = float,
          double = double,
          mixed = mixed
        )
      }
      .usingConnection(connection)
      .execute()

    assertThat(actual).isEqualTo(
      rows.map { row ->
        val id = checkNotNull(row.id)
        val value = checkNotNull(row.value)
        val nullableText = when {
          id % 2 == 0L -> null
          else -> value.take(SHORT_VALUE_LENGTH)
        }
        RawScalars(
          short = id.toShort(),
          int = id.toInt(),
          value = value,
          nullableText = nullableText,
          nullableBlob = nullableText
            ?.toByteArray()
            ?.toList(),
          large = id + LARGE_INTEGER,
          float = id.toFloat() + 0.25f,
          double = id.toDouble() + 0.25,
          mixed = when (id % 3) {
            0L -> null
            1L -> id + LARGE_INTEGER
            else -> id.toDouble() + 0.5
          }
        )
      }
    )
  }

  private fun withWindowRows(
    assertion: (DbConnection, List<SimpleMutableEntity>) -> Unit
  ) = withNamedDatabase(databaseName = WINDOW_DATABASE_NAME) { application ->
    openNamedConnection(
      application = application,
      databaseName = WINDOW_DATABASE_NAME
    ).use { connection ->
      val payload = "x".repeat(PAYLOAD_SIZE)
      val rows = List(size = ROW_COUNT) { position ->
        SimpleMutableEntity(
          id = position.toLong() + 1,
          value = "$position:$payload",
          boxedBoolean = when {
            position % 2 == 0 -> null
            else -> position % 4 == 1
          },
          primitiveBoolean = position % 3 == 0
        )
      }
      connection.inTransaction {
        rows.forEach {
          insertedResult(
            result = it
              .insert()
              .usingConnection(connection)
              .execute(),
            modelName = "cursor-window-row"
          )
        }
      }
      assertion(connection, rows)
    }
  }

  private data class RawScalars(
    val short: Short,
    val int: Int,
    val value: String,
    val nullableText: String?,
    val nullableBlob: List<Byte>?,
    val large: Long,
    val float: Float,
    val double: Double,
    val mixed: Number?
  )
}
