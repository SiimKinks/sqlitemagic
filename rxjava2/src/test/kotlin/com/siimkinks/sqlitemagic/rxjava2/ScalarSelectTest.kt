package com.siimkinks.sqlitemagic.rxjava2

import androidx.sqlite.SQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.asValue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.atomic.AtomicInteger

internal class ScalarSelectTest {
  @Test
  fun `source-free observation is cold and emits one mapped result per subscription`() {
    val queries = AtomicInteger()
    val connection = queryingConnection(answers = "SELECT ?"::equals) {
      queries.incrementAndGet()
      mock<SQLiteStatement> {
        on { step() }.doReturn(true, false)
        on { getLong(0) } doReturn 8L
      }
    }
    val observable = SELECT.COLUMN(8L.asValue())
      .usingConnection(connection)
      .observe()
      .runQuery()
    val queriesBeforeSubscription = queries.get()

    observable
      .test()
      .assertResult(listOf(8L))
    observable
      .test()
      .assertResult(listOf(8L))
    assertThat(queriesBeforeSubscription to queries.get()).isEqualTo(0 to 2)
  }
}
