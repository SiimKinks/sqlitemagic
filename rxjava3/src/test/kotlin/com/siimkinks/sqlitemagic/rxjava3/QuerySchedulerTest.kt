package com.siimkinks.sqlitemagic.rxjava3

import android.app.Application
import android.util.Log
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.TestGeneratedDatabase
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport.adapterSetting
import io.reactivex.rxjava3.schedulers.Schedulers
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.File

internal class QuerySchedulerTest {
  @Test
  fun `scheduleRxQueriesOn sets the query scheduler of the connections that the builder opens`() {
    val context = mock<Application> {
      on { getDatabasePath(any()) } doReturn File("unopened.db")
    }
    val scheduler = Schedulers.trampoline()
    fun builder() = SqliteMagic
      .builder(context)
      .database(TestGeneratedDatabase(tableCount = 0))
      .driver(BundledSQLiteDriver())

    val schedulers = mockStatic(Log::class.java).use {
      listOf(
        builder(),
        builder().scheduleRxQueriesOn(scheduler)
      ).map { configured ->
        configured
          .openNewConnection()
          .use { connection ->
            adapterSetting(
              connection = connection,
              key = QUERY_SCHEDULER
            )
          }
      }
    }

    assertThat(schedulers)
      .containsExactly(null, scheduler)
      .inOrder()
  }
}
