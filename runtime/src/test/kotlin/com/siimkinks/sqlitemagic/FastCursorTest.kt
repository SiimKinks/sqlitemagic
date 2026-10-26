package com.siimkinks.sqlitemagic

import android.database.AbstractWindowedCursor
import android.database.CharArrayBuffer
import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_FLOAT
import android.database.Cursor.FIELD_TYPE_INTEGER
import android.database.Cursor.FIELD_TYPE_NULL
import android.database.Cursor.FIELD_TYPE_STRING
import android.database.CursorWindow
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

internal class FastCursorTest {
  @Test
  fun `ordinary cursor is returned unchanged without inspection`() {
    val cursor = mock<Cursor>()

    assertThat(FastCursor.tryCreate(cursor)).isSameInstanceAs(cursor)
    verifyNoInteractions(cursor)
  }

  @Test
  fun `a failed first window fill closes the backing cursor`() {
    val failure = IllegalStateException("window fill failed")
    val backing = mock<AbstractWindowedCursor>()
    whenever(backing.count).thenThrow(failure)

    val thrown = assertThrows(IllegalStateException::class.java) {
      FastCursor.tryCreate(backing)
    }

    assertThat(thrown).isSameInstanceAs(failure)
    verify(backing).close()
  }

  @Test
  fun `count is evaluated once and movement within cached window does not touch backing cursor`() {
    val (backing, window, cursor) = fixture()
    clearInvocations(backing, window)

    repeat(3) {
      assertThat(cursor.count).isEqualTo(6)
    }
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.moveToNext()).isTrue()
    assertThat(cursor.moveToPrevious()).isTrue()
    assertThat(cursor.move(1)).isTrue()
    assertThat(cursor.moveToPosition(0)).isTrue()

    verifyNoInteractions(backing, window)
  }

  @Test
  fun `cached reads use window at wrapper position including null and narrowing conversions`() {
    val (backing, window, cursor) = fixture()
    val bytes = byteArrayOf(1, 2, 3)
    whenever(window.getLong(1, 0))
      .thenReturn(65537L)
    whenever(window.getDouble(1, 1))
      .thenReturn(1.25)
    whenever(window.getString(1, 2))
      .thenReturn("value")
    whenever(window.getBlob(1, 3))
      .thenReturn(bytes)
    whenever(window.getType(1, 4))
      .thenReturn(FIELD_TYPE_NULL)
    whenever(window.getType(1, 5))
      .thenReturn(FIELD_TYPE_STRING)
    clearInvocations(backing, window)

    assertThat(cursor.moveToPosition(1)).isTrue()
    assertThat(cursor.getShort(0)).isEqualTo(1.toShort())
    assertThat(cursor.getInt(0)).isEqualTo(65537)
    assertThat(cursor.getLong(0)).isEqualTo(65537L)
    assertThat(cursor.getFloat(1)).isEqualTo(1.25f)
    assertThat(cursor.getDouble(1)).isEqualTo(1.25)
    assertThat(cursor.getString(2)).isEqualTo("value")
    assertThat(cursor.getBlob(3)).isSameInstanceAs(bytes)
    assertThat(cursor.isNull(4)).isTrue()
    assertThat(cursor.isNull(5)).isFalse()
    assertThat(cursor.getString(4)).isNull()
    assertThat(cursor.getBlob(4)).isNull()

    verifyNoInteractions(backing)
    verify(window, times(3)).getLong(1, 0)
    verify(window, times(2)).getDouble(1, 1)
    verify(window).getString(1, 2)
    verify(window).getBlob(1, 3)
    verify(window).getType(1, 4)
    verify(window).getType(1, 5)
    verify(window).getString(1, 4)
    verify(window).getBlob(1, 4)
  }

  @Test
  fun `storage types and string buffers use wrapper position without moving backing cursor`() {
    val (backing, window, cursor) = fixture()
    val buffer = mock<CharArrayBuffer>()
    whenever(window.getType(0, 0))
      .thenReturn(FIELD_TYPE_INTEGER)
    whenever(window.getType(1, 0))
      .thenReturn(FIELD_TYPE_STRING)
    clearInvocations(backing, window)

    assertThat(cursor.moveToFirst())
      .isTrue()
    assertThat(cursor.getType(0))
      .isEqualTo(FIELD_TYPE_INTEGER)
    cursor.copyStringToBuffer(
      columnIndex = 0,
      buffer = buffer
    )
    assertThat(cursor.moveToNext())
      .isTrue()
    assertThat(cursor.getType(0))
      .isEqualTo(FIELD_TYPE_STRING)
    cursor.copyStringToBuffer(
      columnIndex = 0,
      buffer = buffer
    )
    assertThat(cursor.moveToPrevious())
      .isTrue()
    assertThat(cursor.getType(0))
      .isEqualTo(FIELD_TYPE_INTEGER)

    verifyNoInteractions(backing)
    verify(window)
      .copyStringToBuffer(0, 0, buffer)
    verify(window)
      .copyStringToBuffer(1, 0, buffer)
  }

  @Test
  fun `refills refresh window reference before reading types and values`() {
    val (backing, window, cursor) = fixture()
    val replacement = mock<CursorWindow>()
    whenever(backing.window)
      .thenReturn(replacement)
    whenever(replacement.startPosition)
      .thenReturn(2)
    whenever(replacement.numRows)
      .thenReturn(2)
    whenever(replacement.getType(2, 0))
      .thenReturn(FIELD_TYPE_FLOAT)
    whenever(replacement.getDouble(2, 0))
      .thenReturn(2.5)
    clearInvocations(backing, window)

    assertThat(cursor.moveToPosition(1))
      .isTrue()
    assertThat(cursor.moveToNext())
      .isTrue()
    assertThat(cursor.getType(0))
      .isEqualTo(FIELD_TYPE_FLOAT)
    assertThat(cursor.getDouble(0))
      .isEqualTo(2.5)
    assertThat(cursor.moveToNext())
      .isTrue()

    verify(backing)
      .onMove(1, 2)
    verify(backing)
      .window
    verifyNoMoreInteractions(backing)
    verifyNoInteractions(window)
  }

  @Test
  fun `position flags use wrapper position including empty cursors`() {
    for ((label, count) in listOf("empty" to 0, "two rows" to 2)) {
      val (backing, window, cursor) = fixture(count = count)
      clearInvocations(backing, window)

      assertWithMessage(label)
        .that(cursor.isBeforeFirst)
        .isTrue()
      assertWithMessage(label)
        .that(cursor.isAfterLast)
        .isEqualTo(count == 0)
      assertWithMessage(label)
        .that(cursor.isFirst)
        .isFalse()
      assertWithMessage(label)
        .that(cursor.isLast)
        .isFalse()
      if (count > 0) {
        assertThat(cursor.moveToFirst())
          .isTrue()
        assertThat(cursor.isBeforeFirst)
          .isFalse()
        assertThat(cursor.isAfterLast)
          .isFalse()
        assertThat(cursor.isFirst)
          .isTrue()
        assertThat(cursor.isLast)
          .isFalse()
        assertThat(cursor.moveToLast())
          .isTrue()
        assertThat(cursor.isFirst)
          .isFalse()
        assertThat(cursor.isLast)
          .isTrue()
        assertThat(cursor.moveToNext())
          .isFalse()
        assertThat(cursor.isAfterLast)
          .isTrue()
      }

      verifyNoInteractions(backing, window)
    }
  }

  @Test
  fun `window refills only when crossing cached boundaries in either direction`() {
    val (backing, window, cursor) = fixture()
    whenever(window.startPosition)
      .thenReturn(2, 0)
    whenever(backing.onMove(1, 2))
      .thenReturn(true)
    whenever(backing.onMove(3, 1))
      .thenReturn(true)
    clearInvocations(backing, window)

    assertThat(cursor.moveToPosition(1)).isTrue()
    assertThat(cursor.moveToPosition(2)).isTrue()
    assertThat(cursor.moveToPosition(3)).isTrue()
    assertThat(cursor.moveToPosition(1)).isTrue()
    assertThat(cursor.moveToPosition(0)).isTrue()

    verify(backing).onMove(1, 2)
    verify(backing).onMove(3, 1)
    verify(backing, never()).moveToPosition(any())
    verify(window, times(2)).startPosition
    verify(window, times(2)).numRows
  }

  @Test
  fun `syncWith uses position and refills only outside cached window`() {
    val (backing, window, cursor) = fixture()
    whenever(window.startPosition)
      .thenReturn(4)
    clearInvocations(backing, window)

    cursor.syncWith(cursorPosition = 1)
    assertThat(cursor.position).isEqualTo(1)
    verifyNoInteractions(backing, window)
    cursor.syncWith(cursorPosition = 4)
    assertThat(cursor.position).isEqualTo(4)
    verify(backing).onMove(1, 4)
  }

  @Test
  fun `failed moves clamp position to Android cursor boundaries`() {
    val (_, _, cursor) = fixture()
    val cases = listOf(
      "before first" to -1,
      "after last" to 6,
      "far before first" to Int.MIN_VALUE,
      "far after last" to Int.MAX_VALUE
    )
    for ((label, target) in cases) {
      assertThat(cursor.moveToPosition(1)).isTrue()
      assertWithMessage(label)
        .that(cursor.moveToPosition(target))
        .isFalse()
      assertWithMessage(label)
        .that(cursor.position)
        .isEqualTo(
          when {
            target < 0 -> -1
            else -> 6
          }
        )
    }
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.moveToPrevious()).isFalse()
    assertThat(cursor.position).isEqualTo(-1)
    assertThat(cursor.isBeforeFirst).isTrue()
    assertThat(cursor.moveToNext()).isTrue()
    assertThat(cursor.position).isEqualTo(0)
    assertThat(cursor.moveToLast()).isTrue()
    assertThat(cursor.moveToNext()).isFalse()
    assertThat(cursor.position).isEqualTo(6)
    assertThat(cursor.isAfterLast).isTrue()
    assertThat(cursor.moveToPrevious()).isTrue()
    assertThat(cursor.position).isEqualTo(5)
  }

  @Test
  fun `empty cursor moves use Android boundary positions without refilling`() {
    val (backing, window, cursor) = fixture(count = 0)
    clearInvocations(backing, window)

    assertThat(cursor.moveToLast()).isFalse()
    assertThat(cursor.position).isEqualTo(-1)
    assertThat(cursor.moveToNext()).isFalse()
    assertThat(cursor.position).isEqualTo(0)
    assertThat(cursor.moveToPrevious()).isFalse()
    assertThat(cursor.position).isEqualTo(-1)
    assertThat(cursor.moveToFirst()).isFalse()
    assertThat(cursor.position).isEqualTo(0)
    verifyNoInteractions(backing, window)
  }

  @Test
  fun `rejected window refill returns false and resets before first`() {
    val (backing, window, cursor) = fixture()
    whenever(backing.onMove(1, 2))
      .thenReturn(false)
    clearInvocations(backing, window)

    assertThat(cursor.moveToPosition(1)).isTrue()
    assertThat(cursor.moveToNext()).isFalse()
    assertThat(cursor.position).isEqualTo(-1)
    assertThat(cursor.isBeforeFirst).isTrue()
    verify(backing).onMove(1, 2)
    verifyNoMoreInteractions(backing)
    verifyNoInteractions(window)
  }

  @Test
  fun `closing releases cached window only once and forwards backing close`() {
    val (backing, window, cursor) = fixture()
    clearInvocations(backing, window)

    cursor.close()
    cursor.close()

    verify(backing, times(2)).close()
    verify(window).close()
  }

  private fun fixture(count: Int = 6): Fixture {
    val backing = mock<AbstractWindowedCursor>()
    val window = mock<CursorWindow>()
    whenever(backing.count).thenReturn(count)
    whenever(backing.window).thenReturn(window)
    whenever(backing.onMove(any(), any())).thenReturn(true)
    whenever(window.startPosition).thenReturn(0)
    whenever(window.numRows).thenReturn(count.coerceAtMost(2))
    val cursor = FastCursor.tryCreate(backing) as FastCursor
    inOrder(backing, window) {
      verify(backing).count
      verify(backing).window
      verify(window).startPosition
      verify(window).numRows
    }
    return Fixture(
      backing = backing,
      window = window,
      cursor = cursor
    )
  }

  private data class Fixture(
    val backing: AbstractWindowedCursor,
    val window: CursorWindow,
    val cursor: FastCursor
  )
}
