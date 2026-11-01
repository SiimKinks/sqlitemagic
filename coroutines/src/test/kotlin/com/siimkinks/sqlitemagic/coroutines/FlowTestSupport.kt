package com.siimkinks.sqlitemagic.coroutines

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.QueryingDriver
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterMap
import com.siimkinks.sqlitemagic.testDbConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

internal fun dispatcherSettings(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined) =
  MutableScatterMap<AdapterSettingKey<*>, Any>().apply {
    this[QUERY_DISPATCHER] = dispatcher
  }

internal fun testConnection(dispatcher: CoroutineDispatcher = Dispatchers.Unconfined) =
  testDbConnection(adapterSettings = dispatcherSettings(dispatcher))

internal fun readsAuthor(sql: String) = """FROM "main"."author"""" in sql

internal fun authorNames(connection: DbConnectionImpl) = SELECT
  .COLUMN(AUTHOR.NAME)
  .FROM(AUTHOR)
  .ORDER_BY(AUTHOR.ID.ASC())
  .usingConnection(connection)

internal fun queryingConnection(
  answers: (String) -> Boolean,
  adapterSettings: ScatterMap<AdapterSettingKey<*>, Any> = dispatcherSettings(),
  onQuery: () -> SQLiteStatement
) = testDbConnection(
  driver = QueryingDriver(
    answers = answers,
    onQuery = onQuery
  ),
  adapterSettings = adapterSettings
)

internal data class FlowSignals<T>(
  val values: List<T>,
  val failure: Throwable? = null,
  val completed: Boolean = false
)

internal class FlowCollection<T>(
  scope: CoroutineScope,
  flow: Flow<T>
) {
  private val values = CopyOnWriteArrayList<T>()

  @Volatile
  private var failure: Throwable? = null

  @Volatile
  private var completed = false

  private val job = scope.launch(Dispatchers.Unconfined) {
    try {
      flow.collect(values::add)
      completed = true
    } catch (collectionFailure: Throwable) {
      if (isActive) {
        failure = collectionFailure
      }
    }
  }

  val signals
    get() = FlowSignals(
      values = values.toList(),
      failure = failure,
      completed = completed
    )

  fun cancel() = job.cancel()
}

internal fun <T> CoroutineScope.collectUnconfined(flow: Flow<T>) = FlowCollection(
  scope = this,
  flow = flow
)
