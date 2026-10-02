package com.siimkinks.sqlitemagic

import android.content.ContentResolver
import android.database.AbstractWindowedCursor
import android.database.Cursor
import android.database.Cursor.FIELD_TYPE_NULL
import android.net.Uri

/**
 * For internal use.
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
      is AbstractWindowedCursor -> FastCursor(cursor)
      else -> cursor
    }
  }

  fun syncWith(cursorPosition: Int) {
    moveWindowIfNeeded(
      oldPosition = this.position,
      newPosition = cursorPosition
    )
    this.position = cursorPosition
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
    if (position >= 0 && position < count) {
      moveWindowIfNeeded(
        oldPosition = this.position,
        newPosition = position
      )
      this.position = position
      return true
    }
    return false
  }

  override fun moveToFirst(): Boolean {
    moveWindowIfNeeded(
      oldPosition = position,
      newPosition = 0
    )
    position = 0
    return count > 0
  }

  override fun moveToLast(): Boolean {
    if (count > 0) {
      val position = count - 1
      moveWindowIfNeeded(
        oldPosition = this.position,
        newPosition = position
      )
      this.position = position
      return true
    }
    return false
  }

  override fun moveToNext(): Boolean {
    var position = this.position
    if (position < count - 1) {
      moveWindowIfNeeded(
        oldPosition = position,
        newPosition = ++position
      )
      this.position = position
      return true
    }
    return false
  }

  override fun moveToPrevious(): Boolean {
    var position = this.position
    if (position > 0) {
      moveWindowIfNeeded(
        oldPosition = position,
        newPosition = --position
      )
      this.position = position
      return true
    }
    return false
  }

  private fun moveWindowIfNeeded(
    oldPosition: Int,
    newPosition: Int
  ) {
    if (newPosition < windowStart || newPosition >= windowEnd) {
      backingCursor.onMove(oldPosition, newPosition)
      windowStart = window.startPosition
      windowEnd = windowStart + window.numRows
    }
  }

  override fun isFirst() = position == 0

  override fun isLast() = position == count - 1

  override fun getBlob(columnIndex: Int): ByteArray? = window.getBlob(position, columnIndex)

  override fun getString(columnIndex: Int): String? = window.getString(position, columnIndex)

  override fun getShort(columnIndex: Int) = window.getLong(position, columnIndex)
    .toShort()

  override fun getInt(columnIndex: Int) = window.getLong(position, columnIndex)
    .toInt()

  override fun getLong(columnIndex: Int) = window.getLong(position, columnIndex)

  override fun getFloat(columnIndex: Int) = window.getDouble(position, columnIndex)
    .toFloat()

  override fun getDouble(columnIndex: Int) = window.getDouble(position, columnIndex)

  override fun isNull(columnIndex: Int) = window.getType(position, columnIndex) == FIELD_TYPE_NULL

  override fun getNotificationUris(): List<Uri?>? = backingCursor.notificationUris

  override fun setNotificationUris(
    cr: ContentResolver,
    uris: List<Uri?>
  ) {
    backingCursor.setNotificationUris(cr, uris)
  }
}
