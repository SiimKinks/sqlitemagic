package com.siimkinks.sqlitemagic.rxjava3

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.asValue
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class ScalarSelectTest {
  @Test
  fun `source-free observation is cold and emits one mapped result per subscription`() {
    val connection = mock<DbConnectionImpl>()
    val database = mock<SupportSQLiteDatabase>()
    val cursor = mock<Cursor>()
    whenever(connection.readableDatabase).thenReturn(database)
    whenever(connection.adapterSetting(QUERY_SCHEDULER)).thenReturn(Schedulers.trampoline())
    whenever(database.query(any<SupportSQLiteQuery>())).thenReturn(cursor)
    whenever(cursor.count).thenReturn(1)
    whenever(cursor.moveToNext()).thenReturn(true, false, true, false)
    whenever(cursor.getLong(0)).thenReturn(8L)
    val observable = SELECT.COLUMN(8L.asValue())
      .usingConnection(connection)
      .observe()
      .runQuery()
    verifyNoInteractions(connection)

    observable
      .test()
      .assertResult(listOf(8L))
    observable
      .test()
      .assertResult(listOf(8L))
    verify(cursor, times(2)).close()
  }
}
