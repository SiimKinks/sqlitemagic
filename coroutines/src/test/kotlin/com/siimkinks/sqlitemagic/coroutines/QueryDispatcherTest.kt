package com.siimkinks.sqlitemagic.coroutines

import android.database.Cursor
import android.util.Log
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DELETE
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.PERSON
import com.siimkinks.sqlitemagic.Query
import com.siimkinks.sqlitemagic.RecordingDatabase
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.TestAdapter
import com.siimkinks.sqlitemagic.TestEntity
import com.siimkinks.sqlitemagic.TestGeneratedDatabase
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.TableChangeObserver
import com.siimkinks.sqlitemagic.testDbConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

internal class QueryDispatcherTest {
  @Test
  fun `a connection uses the dispatchCoroutineQueriesOn dispatcher of its builder or else Dispatchers IO`() {
    val helper = mock<SupportSQLiteOpenHelper>()
    val factory = mock<SupportSQLiteOpenHelper.Factory> {
      on { create(any()) } doReturn helper
    }
    fun builder() = SqliteMagic
      .builder(mock())
      .database(TestGeneratedDatabase(tableCount = 0))
      .sqliteFactory(factory)

    val dispatchers = mockStatic(Log::class.java).use {
      listOf(
        builder(),
        builder().dispatchCoroutineQueriesOn(Dispatchers.Unconfined)
      ).map { configured ->
        configured
          .openNewConnection()
          .use(DbConnection::queryDispatcher)
      }
    }

    assertThat(dispatchers)
      .containsExactly(Dispatchers.IO, Dispatchers.Unconfined)
      .inOrder()
  }

  @Test
  fun `database calls run on the connection dispatcher or else on Dispatchers IO`() {
    val executor = Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "configured-dispatcher").apply { isDaemon = true }
    }
    try {
      val threads = listOf(
        dispatcherSettings(executor.asCoroutineDispatcher()),
        MutableScatterMap(initialCapacity = 0)
      ).map { settings ->
        val connection = testDbConnection(
          database = RecordingDatabase(),
          adapterSettings = settings
        )
        val writers = WriterThreads().also {
          connection.tableObservers.add(
            tables = null,
            observer = it
          )
        }
        val reader = threadNameQuery(connection)
        runBlocking {
          val readers = listOf(
            flowOf(reader)
              .runQuery()
              .first(),
            flowOf(reader).runQueryOnce(),
            reader.run()
          ).flatten()
          TestAdapter()
            .insert(TestEntity())
            .usingConnection(connection)
            .await()
          (DELETE FROM PERSON)
            .usingConnection(connection)
            .await()
          (readers + writers.names)
            .map { name -> name.substringBefore(" @coroutine") }
            .map { thread ->
              when {
                thread.startsWith("DefaultDispatcher-worker") -> "io"
                else -> thread
              }
            }
        }
      }

      assertThat(threads).isEqualTo(
        listOf(
          List(5) { "configured-dispatcher" },
          List(5) { "io" }
        )
      )
    } finally {
      executor.shutdownNow()
    }
  }

  private fun threadNameQuery(connection: DbConnectionImpl) = object : Query<List<String>>(connection) {
    override fun rawQuery(dbConnection: DbConnectionImpl) = mock<Cursor>()

    override fun map(cursor: Cursor, isDisposed: () -> Boolean) = listOf(Thread.currentThread().name)
  }

  private class WriterThreads : TableChangeObserver {
    val names: List<String>
      field = CopyOnWriteArrayList()

    override fun onTablesChanged(tables: Set<String>) {
      names += Thread.currentThread().name
    }

    override fun onClosed() = Unit
  }
}
