package com.siimkinks.sqlitemagic.rxjava2

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.LONG
import io.reactivex.Observable
import io.reactivex.observers.TestObserver
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

internal class QueryCursorOwnershipTest {
  @Test
  fun `cancellation during driver acquisition closes cursor without delivery or row reads`() {
    for (terminal in Terminal.entries) {
      val cursor = mock<Cursor>(name = "$terminal cursor")
      val database = mock<SupportSQLiteDatabase>()
      val observer = TestObserver<Cursor>()
      whenever(database.query(any<SupportSQLiteQuery>()))
        .thenAnswer {
          observer.dispose()
          cursor
        }
      cursorQuery(database).subscribe(
        terminal = terminal,
        observer = observer
      )
      observer
        .withTag(terminal.name)
        .assertEmpty()
      verify(database).query(any<SupportSQLiteQuery>())
      verify(cursor).close()
      verifyNoMoreInteractions(cursor)
    }
  }

  @Test
  fun `delivered cursor remains open after disposal`() {
    for (terminal in Terminal.entries) {
      val cursor = mock<Cursor>(name = "$terminal cursor")
      val database = mock<SupportSQLiteDatabase>()
      whenever(database.query(any<SupportSQLiteQuery>()))
        .thenReturn(cursor)
      val observer = TestObserver<Cursor>()
      cursorQuery(database).subscribe(
        terminal = terminal,
        observer = observer
      )
      observer
        .withTag(terminal.name)
        .assertValues(cursor)
        .assertNoErrors()
      verifyNoInteractions(cursor)
      observer.dispose()
      verifyNoInteractions(cursor)
    }
  }

  @Test
  fun `cancellation before default delivery keeps external default cursor open`() {
    val defaultCursor = mock<Cursor>()
    val observer = TestObserver<Cursor>()
    val query = object : Query<Cursor?>(mock()) {
      override fun rawQuery(dbConnection: DbConnectionImpl) = mock<Cursor>()

      override fun map(cursor: Cursor, isDisposed: () -> Boolean): Cursor? {
        observer.dispose()
        return null
      }

      override fun discardResult(result: Cursor?) {
        result?.close()
      }
    }
    SingleItemQueryObservable(Observable.just(query))
      .runQueryOrDefault(defaultCursor)
      .subscribe(observer)
    observer.assertEmpty()
    verifyNoInteractions(defaultCursor)
  }

  @Test
  fun `a raw result without the columns readAs reads is closed before the failure`() {
    val cursor = mock<Cursor>()
    whenever(cursor.columnCount)
      .thenReturn(3)
    val database = mock<SupportSQLiteDatabase>()
    whenever(database.query(any<SupportSQLiteQuery>()))
      .thenReturn(cursor)
    val connection = mock<DbConnectionImpl>()
    whenever(connection.readableDatabase)
      .thenReturn(database)
    val failure = assertThrows(IllegalStateException::class.java) {
      SELECT
        .RAW("SELECT * FROM items")
        .readAs(LONG, LONG)
        .usingConnection(connection)
        .toCursor()
        .execute()
    }

    assertThat(failure)
      .hasMessageThat()
      .isEqualTo("raw query returned 3 columns; readAs reads 2")
    verify(cursor).close()
  }

  private fun cursorQuery(database: SupportSQLiteDatabase): Query<Cursor> {
    val connection = mock<DbConnectionImpl>()
    whenever(connection.readableDatabase)
      .thenReturn(database)
    return SELECT
      .FROM(PERSON)
      .usingConnection(connection)
      .toCursor()
      .execution()
  }

  private fun Query<Cursor>.subscribe(
    terminal: Terminal,
    observer: TestObserver<Cursor>
  ) = when (terminal) {
    Terminal.MAYBE -> run().subscribe(observer)
    Terminal.OBSERVABLE -> SingleItemQueryObservable(
      Observable.just(this)
        .concatWith(Observable.never())
    )
      .runQuery()
      .subscribe(observer)
  }

  private enum class Terminal {
    MAYBE,
    OBSERVABLE
  }
}
