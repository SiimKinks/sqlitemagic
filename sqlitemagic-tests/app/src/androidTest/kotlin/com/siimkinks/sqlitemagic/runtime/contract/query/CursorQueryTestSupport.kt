package com.siimkinks.sqlitemagic.runtime.contract.query

import android.database.Cursor
import com.siimkinks.sqlitemagic.CompiledCursor
import com.siimkinks.sqlitemagic.runtime.model.RawCursorModelCase

internal fun <T> readTypedCursor(
  cursorSelect: CompiledCursor<T>,
  cursor: Cursor = cursorSelect.execute()
) = cursor.use {
  buildList {
    while (cursor.moveToNext()) {
      add(checkNotNull(cursorSelect.getFromCurrentPosition(cursor)))
    }
  }
}

internal fun <T> readRawCursor(
  modelCase: RawCursorModelCase<T>,
  cursor: Cursor
) = cursor.use {
  buildList {
    while (cursor.moveToNext()) {
      add(modelCase.readCurrentPosition(cursor = cursor))
    }
  }
}
