package com.siimkinks.sqlitemagic

import android.util.Log
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterMap
import com.siimkinks.sqlitemagic.internal.TableChangeObserver
import org.mockito.Mockito.mockStatic

internal class TriggerRecorder : TableChangeObserver {
  val triggers: List<Set<String>>
    field = mutableListOf()
  var closeCalls = 0
    private set

  override fun onTablesChanged(tables: Set<String>) {
    triggers += tables.toSet()
  }

  override fun onClosed() {
    closeCalls++
  }
}

internal fun DbConnectionImpl.recordTriggers() = TriggerRecorder().also {
  tableObservers.add(
    tables = null,
    observer = it
  )
}

internal fun DbConnectionImpl.failTriggerDelivery(failure: Throwable) = tableObservers.add(
  tables = null,
  observer = object : TableChangeObserver {
    override fun onTablesChanged(tables: Set<String>) = throw failure

    override fun onClosed() = Unit
  }
)

internal fun closedConnection(
  adapterSettings: ScatterMap<AdapterSettingKey<*>, Any> = MutableScatterMap(initialCapacity = 0)
) = testDbConnection(adapterSettings = adapterSettings).also { connection ->
  mockStatic(Log::class.java).use {
    connection.close()
  }
}
