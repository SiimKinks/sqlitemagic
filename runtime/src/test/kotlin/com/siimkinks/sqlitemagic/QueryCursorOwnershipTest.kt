package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.common.truth.Truth.assertWithMessage
import io.reactivex.Observable
import io.reactivex.observers.TestObserver
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
    for (kind in listOf("raw", "compiled")) {
      for (terminal in listOf("Maybe", "Observable")) {
        val label = "$kind $terminal"
        val cursor = mock<Cursor>(name = "$label cursor")
        val database = mock<SupportSQLiteDatabase>()
        val observer = TestObserver<Cursor>()
        whenever(database.query(any<String>()))
          .thenAnswer {
            observer.dispose()
            cursor
          }
        cursorQuery(
          kind = kind,
          database = database
        ).subscribe(
          terminal = terminal,
          observer = observer
        )
        assertWithMessage(label)
          .that(observer.values())
          .isEmpty()
        assertWithMessage(label)
          .that(observer.errors())
          .isEmpty()
        observer.assertNotComplete()
        verify(database).query(any<String>())
        verify(cursor).close()
        verifyNoMoreInteractions(cursor)
      }
    }
  }

  @Test
  fun `delivered cursor remains open after disposal until caller closes it`() {
    for (kind in listOf("raw", "compiled")) {
      for (terminal in listOf("Maybe", "Observable")) {
        val label = "$kind $terminal"
        val cursor = mock<Cursor>(name = "$label cursor")
        val database = mock<SupportSQLiteDatabase>()
        whenever(database.query(any<String>()))
          .thenReturn(cursor)
        val observer = TestObserver<Cursor>()
        cursorQuery(
          kind = kind,
          database = database
        ).subscribe(
          terminal = terminal,
          observer = observer
        )
        assertWithMessage(label)
          .that(observer.values())
          .containsExactly(cursor)
        assertWithMessage(label)
          .that(observer.errors())
          .isEmpty()
        verifyNoInteractions(cursor)
        observer.dispose()
        verifyNoInteractions(cursor)
        cursor.close()
        verify(cursor).close()
        verifyNoMoreInteractions(cursor)
      }
    }
  }

  @Test
  fun `cancellation before default delivery keeps external default cursor open`() {
    val defaultCursor = mock<Cursor>()
    val observer = TestObserver<Cursor>()
    val query = object : Query<Cursor>(mock()) {
      override fun rawQuery(inStream: Boolean, dbConnection: DbConnectionImpl): Cursor? = null

      override fun map(cursor: Cursor?, dbConnection: DbConnectionImpl): Cursor? {
        observer.dispose()
        return null
      }

      override fun discardResult(result: Cursor) = result.close()
    }
    SingleItemQueryObservable(Observable.just(query))
      .runQueryOrDefault(defaultCursor)
      .subscribe(observer)
    observer.assertEmpty()
    verifyNoInteractions(defaultCursor)
    defaultCursor.close()
    verify(defaultCursor).close()
    verifyNoMoreInteractions(defaultCursor)
  }

  @Suppress("UNCHECKED_CAST")
  private fun cursorQuery(
    kind: String,
    database: SupportSQLiteDatabase
  ): Query<Cursor> {
    val connection = mock<DbConnectionImpl>()
    whenever(connection.readableDatabase)
      .thenReturn(database)
    return when (kind) {
      "raw" -> Select.raw("SELECT * FROM items")
        .usingConnection(connection)
        .compile() as RawSelect.CompiledRawSelectImpl
      else -> {
        val table = testTable(
          name = "items",
          mapper = { _, _, _ -> Query.Mapper { error("Cursor query must not decode rows") } }
        )
        Select.all()
          .from(table)
          .usingConnection(connection)
          .compile()
          .toCursor() as Query<Cursor>
      }
    }
  }

  private fun Query<Cursor>.subscribe(
    terminal: String,
    observer: TestObserver<Cursor>
  ) = when (terminal) {
    "Maybe" -> run().subscribe(observer)
    else -> SingleItemQueryObservable(
      Observable.just(this)
        .concatWith(Observable.never())
    )
      .runQuery()
      .subscribe(observer)
  }
}
