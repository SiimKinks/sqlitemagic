package com.siimkinks.sqlitemagic.runtime.contract.query

import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import android.os.Build
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import org.junit.Test

private const val WINDOW_DATABASE_NAME = "cursor-window-traversal-runtime.db"
private const val ROW_COUNT = 192
private const val PAYLOAD_SIZE = 64 * 1024
private const val LARGE_INTEGER = 1L shl 40

/**
 * Dedicated physical-storage coverage: ordinary three-row cursor contracts cannot observe SQLite window refills.
 * Uses an existing catalog fixture; no additional generated model or ordinary operation contract is introduced.
 */
class CursorWindowTraversalRuntimeTest {
  @Test
  fun typedMappingSurvivesWindowRefillsInBothDirectionsAndJumps() = withWindowRows { connection, rows, factory ->
    val query = Select
      .from(SIMPLE_MUTABLE_ENTITY)
      .orderBy(SIMPLE_MUTABLE_ENTITY.ID.asc())
      .usingConnection(connection)
      .toCursor()
    assertWindowTraversal(
      cursor = query.execute(),
      factory = factory,
      assertRow = { cursor, position ->
        assertThat(query.getFromCurrentPosition(cursor)).isEqualTo(rows[position])
      }
    )
  }

  @Test
  fun rawScalarReadsSurviveWindowRefillsInBothDirectionsAndJumps() = withWindowRows { connection, rows, factory ->
    val cursor = Select
      .raw(
        """
        SELECT id, value, CASE WHEN id % 2 = 0 THEN NULL ELSE value END,
          CAST(CASE WHEN id % 2 = 0 THEN NULL ELSE value END AS BLOB), id + $LARGE_INTEGER, id + 0.25
        FROM simple_mutable_entity ORDER BY id
        """.trimIndent()
      )
      .usingConnection(connection)
      .execute()
    assertWindowTraversal(
      cursor = cursor,
      factory = factory,
      assertRow = { current, position ->
        val expected = rows[position]
        val id = checkNotNull(expected.id)
        assertThat(current.getLong(0)).isEqualTo(id)
        assertThat(current.getShort(0)).isEqualTo(id.toShort())
        assertThat(current.getInt(0)).isEqualTo(id.toInt())
        assertThat(current.getString(1)).isEqualTo(expected.value)
        val nullableString = when {
          id % 2 == 0L -> null
          else -> expected.value
        }
        assertThat(current.isNull(2)).isEqualTo(nullableString == null)
        assertThat(current.getString(2)).isEqualTo(nullableString)
        assertThat(current.isNull(3)).isEqualTo(nullableString == null)
        assertThat(current.getBlob(3)).isEqualTo(nullableString?.toByteArray())
        assertThat(current.getLong(4)).isEqualTo(id + LARGE_INTEGER)
        assertThat(current.getFloat(5)).isEqualTo(id.toFloat() + 0.25f)
        assertThat(current.getDouble(5)).isEqualTo(id.toDouble() + 0.25)
      }
    )
  }

  private fun withWindowRows(
    assertion: (DbConnection, List<SimpleMutableEntity>, WindowTrackingFactory) -> Unit
  ) = withNamedDatabase(databaseName = WINDOW_DATABASE_NAME) { application ->
    val factory = WindowTrackingFactory()
    openNamedConnection(
      application = application,
      databaseName = WINDOW_DATABASE_NAME,
      sqliteFactory = factory
    ).use { connection ->
      // API 24-27 lacks the sized CursorWindow constructor. This corpus exceeds the framework's default window;
      // the traversal helper verifies that the real result actually spans windows on every tested device.
      val payload = "x".repeat(PAYLOAD_SIZE)
      val rows = List(
        size = ROW_COUNT,
        init = { position ->
          SimpleMutableEntity(
            id = position.toLong() + 1,
            value = "$position:$payload",
            boxedBoolean = when {
              position % 2 == 0 -> null
              else -> true
            },
            primitiveBoolean = position % 3 == 0
          )
        }
      )
      connection.inTransaction {
        rows.forEach { row ->
          assertSeedInserted(
            result = row
              .insert()
              .usingConnection(connection)
              .execute(),
            modelName = "cursor-window-row"
          )
        }
      }
      assertion(connection, rows, factory)
    }
  }

  private fun assertWindowTraversal(
    cursor: Cursor,
    factory: WindowTrackingFactory,
    assertRow: (Cursor, Int) -> Unit
  ) {
    cursor.use {
      val backingCursor = checkNotNull(factory.lastCursor)
      val window = checkNotNull(backingCursor.window)
      val windowStarts = mutableSetOf<Int>()
      assertThat(cursor.count).isEqualTo(ROW_COUNT)
      assertThat(window.numRows).isGreaterThan(0)
      assertThat(window.numRows).isLessThan(ROW_COUNT)
      assertThat(cursor.position).isEqualTo(-1)
      repeat(ROW_COUNT) { position ->
        assertThat(cursor.moveToNext()).isTrue()
        assertRow(cursor, position)
        windowStarts.add(window.startPosition)
      }
      assertThat(cursor.moveToNext()).isFalse()
      assertThat(cursor.position).isEqualTo(ROW_COUNT - 1)
      assertThat(window.startPosition).isGreaterThan(0)
      for (position in ROW_COUNT - 2 downTo 0) {
        assertThat(cursor.moveToPrevious()).isTrue()
        assertRow(cursor, position)
        windowStarts.add(window.startPosition)
      }
      assertThat(cursor.moveToPrevious()).isFalse()
      assertThat(cursor.position).isEqualTo(0)
      assertThat(window.startPosition).isEqualTo(0)
      listOf(ROW_COUNT - 1, 0, ROW_COUNT / 2, 1, ROW_COUNT - 2).forEach { position ->
        assertThat(cursor.moveToPosition(position)).isTrue()
        assertRow(cursor, position)
        windowStarts.add(window.startPosition)
      }
      assertThat(cursor.moveToFirst()).isTrue()
      assertRow(cursor, 0)
      assertThat(window.startPosition).isEqualTo(0)
      assertThat(cursor.moveToLast()).isTrue()
      assertRow(cursor, ROW_COUNT - 1)
      assertThat(window.startPosition).isGreaterThan(0)
      assertThat(windowStarts.size).isGreaterThan(1)
    }
    assertThat(cursor.isClosed).isTrue()
    assertThat(checkNotNull(factory.lastCursor).isClosed).isTrue()
  }
}

private class WindowTrackingFactory : SupportSQLiteOpenHelper.Factory {
  var lastCursor: AbstractWindowedCursor? = null
    private set

  override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
    val delegate = FrameworkSQLiteOpenHelperFactory().create(configuration)
    return object : SupportSQLiteOpenHelper by delegate {
      override val readableDatabase
        get() = TrackingDatabase(delegate.readableDatabase)

      override val writableDatabase
        get() = TrackingDatabase(delegate.writableDatabase)
    }
  }

  private fun capture(cursor: Cursor): Cursor {
    val windowed = cursor as AbstractWindowedCursor
    if (Build.VERSION.SDK_INT >= 28) {
      windowed.window = CursorWindow("cursor-window-regression", 256L * 1024)
    }
    lastCursor = windowed
    return cursor
  }

  private inner class TrackingDatabase(
    private val delegate: SupportSQLiteDatabase
  ) : SupportSQLiteDatabase by delegate {
    override fun query(query: String) = capture(delegate.query(query))

    override fun query(
      query: String,
      bindArgs: Array<out Any?>
    ) = capture(
      delegate.query(
        query = query,
        bindArgs = bindArgs
      )
    )
  }
}
