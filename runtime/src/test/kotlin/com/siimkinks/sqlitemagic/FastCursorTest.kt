package com.siimkinks.sqlitemagic

import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.CursorWindow
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class FastCursorTest {
  @Test
  fun ordinaryCursorIsReturnedUnchangedWithoutInspection() {
    val cursor = mock<Cursor>()

    assertThat(FastCursor.tryCreate(cursor)).isSameInstanceAs(cursor)
    verifyNoInteractions(cursor)
  }

  @Test
  fun countIsEvaluatedOnceAndMovementWithinCachedWindowDoesNotTouchBackingCursor() {
    val fixture = fixture()
    val cursor = fixture.cursor
    clearInvocations(fixture.backing, fixture.window)

    repeat(3) {
      assertThat(cursor.count).isEqualTo(6)
    }
    assertThat(cursor.moveToFirst()).isTrue()
    assertThat(cursor.moveToNext()).isTrue()
    assertThat(cursor.moveToPrevious()).isTrue()
    assertThat(cursor.move(1)).isTrue()
    assertThat(cursor.moveToPosition(0)).isTrue()

    verifyNoInteractions(fixture.backing, fixture.window)
  }

  @Test
  fun cachedReadsUseWindowAtWrapperPositionIncludingNullAndNarrowingConversions() {
    val fixture = fixture()
    val cursor = fixture.cursor
    val bytes = byteArrayOf(1, 2, 3)
    whenever(fixture.window.getLong(1, 0))
      .thenReturn(65537L)
    whenever(fixture.window.getDouble(1, 1))
      .thenReturn(1.25)
    whenever(fixture.window.getString(1, 2))
      .thenReturn("value")
    whenever(fixture.window.getBlob(1, 3))
      .thenReturn(bytes)
    whenever(fixture.window.getType(1, 4))
      .thenReturn(Cursor.FIELD_TYPE_NULL)
    whenever(fixture.window.getType(1, 5))
      .thenReturn(Cursor.FIELD_TYPE_STRING)
    clearInvocations(fixture.backing, fixture.window)

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

    verifyNoInteractions(fixture.backing)
    verify(fixture.window, times(3)).getLong(1, 0)
    verify(fixture.window, times(2)).getDouble(1, 1)
    verify(fixture.window).getString(1, 2)
    verify(fixture.window).getBlob(1, 3)
    verify(fixture.window).getType(1, 4)
    verify(fixture.window).getType(1, 5)
    verify(fixture.window).getString(1, 4)
    verify(fixture.window).getBlob(1, 4)
  }

  @Test
  fun windowRefillsOnlyWhenCrossingCachedBoundariesInEitherDirection() {
    val fixture = fixture()
    whenever(fixture.window.startPosition)
      .thenReturn(2, 0)
    whenever(fixture.backing.onMove(1, 2))
      .thenReturn(true)
    whenever(fixture.backing.onMove(3, 1))
      .thenReturn(true)
    clearInvocations(fixture.backing, fixture.window)

    assertThat(fixture.cursor.moveToPosition(1)).isTrue()
    assertThat(fixture.cursor.moveToPosition(2)).isTrue()
    assertThat(fixture.cursor.moveToPosition(3)).isTrue()
    assertThat(fixture.cursor.moveToPosition(1)).isTrue()
    assertThat(fixture.cursor.moveToPosition(0)).isTrue()

    verify(fixture.backing).onMove(1, 2)
    verify(fixture.backing).onMove(3, 1)
    verify(fixture.backing, never()).moveToPosition(org.mockito.kotlin.any())
    verify(fixture.window, times(2)).startPosition
    verify(fixture.window, times(2)).numRows
  }

  @Test
  fun syncWithUsesPositionAndRefillsOnlyOutsideCachedWindow() {
    val fixture = fixture()
    whenever(fixture.window.startPosition)
      .thenReturn(4)
    clearInvocations(fixture.backing, fixture.window)

    fixture.cursor.syncWith(cursorPosition = 1)
    assertThat(fixture.cursor.position).isEqualTo(1)
    verifyNoInteractions(fixture.backing, fixture.window)
    fixture.cursor.syncWith(cursorPosition = 4)
    assertThat(fixture.cursor.position).isEqualTo(4)
    verify(fixture.backing).onMove(1, 4)
  }

  @Test
  fun failedMovesPreserveCurrentPositionAtBothBoundaries() {
    val fixture = fixture()
    val cases = listOf(
      "before first" to -1,
      "after last" to 6,
      "far before first" to Int.MIN_VALUE,
      "far after last" to Int.MAX_VALUE
    )
    for ((label, target) in cases) {
      assertThat(fixture.cursor.moveToPosition(1)).isTrue()
      assertWithMessage(label)
        .that(fixture.cursor.moveToPosition(target))
        .isFalse()
      assertWithMessage(label)
        .that(fixture.cursor.position)
        .isEqualTo(1)
    }
    assertThat(fixture.cursor.moveToFirst()).isTrue()
    assertThat(fixture.cursor.moveToPrevious()).isFalse()
    assertThat(fixture.cursor.position).isEqualTo(0)
    assertThat(fixture.cursor.isFirst).isTrue()
    assertThat(fixture.cursor.moveToLast()).isTrue()
    assertThat(fixture.cursor.moveToNext()).isFalse()
    assertThat(fixture.cursor.position).isEqualTo(5)
    assertThat(fixture.cursor.isLast).isTrue()
  }

  @Test
  fun emptyCursorRejectsLastNextPreviousAndAbsoluteMovesWithoutMoving() {
    val fixture = fixture(count = 0)
    clearInvocations(fixture.backing, fixture.window)

    assertThat(fixture.cursor.moveToLast()).isFalse()
    assertThat(fixture.cursor.moveToNext()).isFalse()
    assertThat(fixture.cursor.moveToPrevious()).isFalse()
    assertThat(fixture.cursor.moveToPosition(0)).isFalse()
    assertThat(fixture.cursor.position).isEqualTo(-1)
    verifyNoInteractions(fixture.backing, fixture.window)
  }

  @Test
  fun emptyFirstMoveRetainsOriginalPositionAndRefillBehavior() {
    val fixture = fixture(count = 0)
    clearInvocations(fixture.backing, fixture.window)

    // The original FastCursor has different failed-move positioning from AbstractCursor.
    // Preserve the migration baseline; changing empty positioning belongs in a separate fix.
    assertThat(fixture.cursor.moveToFirst()).isFalse()
    assertThat(fixture.cursor.position).isEqualTo(0)
    verify(fixture.backing).onMove(-1, 0)
    verify(fixture.window).startPosition
    verify(fixture.window).numRows
  }

  @Test
  fun closingReleasesCachedWindowOnlyOnceAndForwardsBackingClose() {
    val fixture = fixture()
    clearInvocations(fixture.backing, fixture.window)

    fixture.cursor.close()
    fixture.cursor.close()

    verify(fixture.backing, times(2)).close()
    verify(fixture.window).close()
  }

  private fun fixture(count: Int = 6): Fixture {
    val backing = mock<AbstractWindowedCursor>()
    val window = mock<CursorWindow>()
    whenever(backing.count).thenReturn(count)
    whenever(backing.window).thenReturn(window)
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
