package com.siimkinks.sqlitemagic.runtime.model

import android.database.Cursor
import com.siimkinks.sqlitemagic.CompiledObservableRawSelect

interface RawCursorModelCase<T> : InsertModelCase<T> {
  fun rawSelect(): CompiledObservableRawSelect

  fun rawSelectWithArgs(value: T): CompiledObservableRawSelect

  fun readCurrentPosition(cursor: Cursor): T
}
