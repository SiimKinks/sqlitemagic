package com.siimkinks.sqlitemagic

import android.database.Cursor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun cursorOf(vararg values: Any?) = mock<Cursor>().apply {
  whenever(isNull(any())).thenAnswer { values[it.getArgument(0)] == null }
  whenever(getString(any())).thenAnswer { values[it.getArgument(0)] as String? }
  whenever(getInt(any())).thenAnswer { values[it.getArgument(0)] as Int? ?: 0 }
  whenever(getLong(any())).thenAnswer { values[it.getArgument(0)] as Long }
}
