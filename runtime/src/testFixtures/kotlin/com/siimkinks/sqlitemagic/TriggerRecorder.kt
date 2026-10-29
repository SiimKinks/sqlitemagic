package com.siimkinks.sqlitemagic

import android.util.Log
import androidx.sqlite.db.SupportSQLiteDatabase
import com.siimkinks.sqlitemagic.internal.AdapterSettingKey
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterMap
import com.siimkinks.sqlitemagic.internal.TableChangeObserver
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock

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

internal fun DbConnectionImpl.recordTriggers() = TriggerRecorder().also { recorder ->
  tableObservers.add(
    tables = null,
    observer = recorder
  )
}

internal fun closedConnection(
  adapterSettings: ScatterMap<AdapterSettingKey<*>, Any> = MutableScatterMap(initialCapacity = 0)
) = testDbConnection(
  database = mock<SupportSQLiteDatabase>(),
  adapterSettings = adapterSettings
).also { connection ->
  mockStatic(Log::class.java).use {
    connection.close()
  }
}
