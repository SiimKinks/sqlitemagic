package com.siimkinks.sqlitemagic.runtime.model

import android.database.Cursor
import com.siimkinks.sqlitemagic.CompiledRawCursor
import com.siimkinks.sqlitemagic.DeclaredTables

interface RawCursorModelCase<T> : InsertModelCase<T> {
  fun rawSelect(): CompiledRawCursor<DeclaredTables>

  fun rawSelectWithArgs(value: T): CompiledRawCursor<DeclaredTables>

  fun readCurrentPosition(cursor: Cursor): T
}
