package com.siimkinks.sqlitemagic

import android.content.ContentResolver
import android.database.AbstractWindowedCursor
import android.database.CharArrayBuffer
import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_NULL
import android.net.Uri

/**
 * Cached movement updates only this cursor's position. All position-dependent reads must use the cached window.
 * Modified implementation from greenDAO:
 * <https://github.com/greenrobot/greenDAO/blob/master/DaoCore/src/de/greenrobot/dao/internal/FastCursor.java>
 */
internal class FastCursor private constructor(
  private val backingCursor: AbstractWindowedCursor
) : Cursor by backingCursor {
  private val count = backingCursor.count // fills cursor window
  private var window = backingCursor.window
  private var windowStart = window.startPosition
  private var windowEnd = windowStart + window.numRows
  private var position = -1

  companion object {
    fun tryCreate(cursor: Cursor) = when (cursor) {
      is AbstractWindowedCursor -> try {
        FastCursor(cursor)
      } catch (failure: Throwable) {
        cursor.close()
        throw failure
      }
      else -> cursor
    }
  }

  fun syncWith(cursorPosition: Int) {
    moveToPosition(cursorPosition)
  }

  override fun close() {
    backingCursor.close()
    window?.close()
    window = null
  }

  override fun getCount() = count

  override fun getPosition() = position

  override fun move(offset: Int) = moveToPosition(position + offset)

  override fun moveToPosition(position: Int): Boolean {
    when {
      position >= count -> {
        this.position = count
        return false
      }
      position < 0 -> {
        this.position = -1
        return false
      }
      position == this.position -> return true
    }
    val moved = moveWindowIfNeeded(
      oldPosition = this.position,
      newPosition = position
    )
    this.position = when {
      moved -> position
      else -> -1
    }
    return moved
  }

  override fun moveToFirst() = moveToPosition(0)

  override fun moveToLast() = moveToPosition(count - 1)

  override fun moveToNext() = moveToPosition(position + 1)

  override fun moveToPrevious() = moveToPosition(position - 1)

  private fun moveWindowIfNeeded(
    oldPosition: Int,
    newPosition: Int
  ): Boolean {
    if (newPosition < windowStart || newPosition >= windowEnd) {
      if (!backingCursor.onMove(oldPosition, newPosition)) {
        return false
      }
      window = backingCursor.window
      windowStart = window.startPosition
      windowEnd = windowStart + window.numRows
    }
    return true
  }

  override fun isFirst() = position == 0 && count > 0

  override fun isLast() = position == count - 1 && count > 0

  override fun isBeforeFirst() = position == -1 || count == 0

  override fun isAfterLast() = position == count || count == 0

  override fun getBlob(columnIndex: Int): ByteArray? = window.getBlob(position, columnIndex)

  override fun getString(columnIndex: Int): String? = window.getString(position, columnIndex)

  override fun copyStringToBuffer(columnIndex: Int, buffer: CharArrayBuffer) =
    window.copyStringToBuffer(position, columnIndex, buffer)

  override fun getShort(columnIndex: Int) = window.getLong(position, columnIndex)
    .toShort()

  override fun getInt(columnIndex: Int) = window.getLong(position, columnIndex)
    .toInt()

  override fun getLong(columnIndex: Int) = window.getLong(position, columnIndex)

  override fun getFloat(columnIndex: Int) = window.getDouble(position, columnIndex)
    .toFloat()

  override fun getDouble(columnIndex: Int) = window.getDouble(position, columnIndex)

  override fun getType(columnIndex: Int) = window.getType(position, columnIndex)

  override fun isNull(columnIndex: Int) = getType(columnIndex) == FIELD_TYPE_NULL

  override fun getNotificationUris(): List<Uri?>? = backingCursor.notificationUris

  override fun setNotificationUris(
    cr: ContentResolver,
    uris: List<Uri?>
  ) = backingCursor.setNotificationUris(cr, uris)
}
