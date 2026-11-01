package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteStatement
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal fun statementOf(vararg values: Any?) = mock<SQLiteStatement>().apply {
  whenever(step()).thenReturn(true, false)
  whenever(isNull(any())).thenAnswer { values[it.getArgument(0)] == null }
  whenever(getText(any())).thenAnswer { values[it.getArgument(0)] as String? ?: "" }
  whenever(getLong(any())).thenAnswer { values[it.getArgument(0)] as Long? ?: 0L }
  whenever(getInt(any())).thenAnswer { (values[it.getArgument(0)] as Long? ?: 0L).toInt() }
}
