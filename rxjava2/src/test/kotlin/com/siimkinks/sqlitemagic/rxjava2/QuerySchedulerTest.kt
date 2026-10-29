package com.siimkinks.sqlitemagic.rxjava2

import android.util.Log
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.TestGeneratedDatabase
import com.siimkinks.sqlitemagic.internal.ReactiveAdapterSupport
import io.reactivex.schedulers.Schedulers
import org.junit.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class QuerySchedulerTest {
  @Test
  fun `scheduleRxQueriesOn sets the query scheduler of the connections that the builder opens`() {
    val helper = mock<SupportSQLiteOpenHelper>()
    val factory = mock<SupportSQLiteOpenHelper.Factory> {
      on { create(any()) } doReturn helper
    }
    val scheduler = Schedulers.trampoline()
    fun builder() = SqliteMagic
      .builder(mock())
      .database(TestGeneratedDatabase(tableCount = 0))
      .sqliteFactory(factory)

    val schedulers = mockStatic(Log::class.java).use {
      listOf(
        builder(),
        builder().scheduleRxQueriesOn(scheduler)
      ).map { configured ->
        configured
          .openNewConnection()
          .use { connection ->
            ReactiveAdapterSupport.adapterSetting(
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
