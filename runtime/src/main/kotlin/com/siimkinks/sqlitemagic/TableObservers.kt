package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterMap
import com.siimkinks.sqlitemagic.internal.ScatterSet
import com.siimkinks.sqlitemagic.internal.TableChangeObserver

internal class TableObservers {
  @Volatile
  private var tableRegistrations: ScatterMap<String, TableRegistrations> = MutableScatterMap(initialCapacity = 0)

  @Volatile
  private var allTableRegistrations = NO_REGISTRATIONS

  @Volatile
  var isClosed = false
    private set

  fun add(
    tables: Array<String>?,
    observer: TableChangeObserver
  ): AutoCloseable {
    val registration = Registration(
      tables = tables,
      observer = observer
    )
    synchronized(this) {
      if (!isClosed) {
        when (tables) {
          null -> allTableRegistrations += registration
          else -> tables.forEach { table -> registrationsOf(table).add(registration) }
        }
        return registration
      }
    }
    observer.onClosed()
    return NO_REGISTRATION
  }

  private fun registrationsOf(table: String) = tableRegistrations[table] ?: TableRegistrations(table).also { created ->
    val current = tableRegistrations
    tableRegistrations = MutableScatterMap<String, TableRegistrations>(initialCapacity = current.size + 1)
      .also { copy ->
        current.forEach(copy::set)
        copy[table] = created
      }
  }

  private fun remove(registration: Registration) = synchronized(this) {
    when (val tables = registration.tables) {
      null -> allTableRegistrations = allTableRegistrations.without(registration)
      else -> tables.forEach { table -> tableRegistrations[table]?.remove(registration) }
    }
  }

  fun publish(table: String) {
    val allTables = allTableRegistrations
    val forTable = tableRegistrations[table]
    val registrations = forTable?.registrations ?: NO_REGISTRATIONS
    if (allTables.isEmpty() && registrations.isEmpty()) return
    val changed = forTable?.changed ?: setOf(table)
    allTables.forEach { it.observer.onTablesChanged(changed) }
    registrations.forEach { it.observer.onTablesChanged(changed) }
  }

  fun publish(tables: ScatterSet<String>) {
    allTableRegistrations.forEach { it.observer.onTablesChanged(tables.asSet()) }
    val tableRegistrations = tableRegistrations
    tables.forEach { table ->
      tableRegistrations[table]?.registrations?.forEach { registration ->
        if (registration.firstObservedIn(tables) == table) {
          registration.observer.onTablesChanged(tables.asSet())
        }
      }
    }
  }

  fun close(): Boolean {
    val (allTables, tables) = synchronized(this) {
      when {
        isClosed -> return false
        else -> (allTableRegistrations to tableRegistrations).also {
          isClosed = true
          allTableRegistrations = NO_REGISTRATIONS
          tableRegistrations = MutableScatterMap(initialCapacity = 0)
        }
      }
    }
    allTables.forEach { it.observer.onClosed() }
    tables.forEach { table, registrations ->
      registrations.registrations.forEach { registration ->
        if (registration.tables?.first() == table) {
          registration.observer.onClosed()
        }
      }
    }
    return true
  }

  private inner class Registration(
    val tables: Array<String>?,
    val observer: TableChangeObserver
  ) : AutoCloseable {
    fun firstObservedIn(changed: ScatterSet<String>) = tables?.first(changed::contains)

    override fun close() = remove(this)
  }

  private class TableRegistrations(table: String) {
    val changed = setOf(table)

    @Volatile
    var registrations = NO_REGISTRATIONS
      private set

    fun add(registration: Registration) {
      registrations += registration
    }

    fun remove(registration: Registration) {
      registrations = registrations.without(registration)
    }
  }

  private companion object {
    val NO_REGISTRATIONS = emptyArray<Registration>()

    fun Array<Registration>.without(registration: Registration) = when (val index = indexOf(registration)) {
      -1 -> this
      else -> Array(size - 1) { position ->
        when {
          position < index -> this[position]
          else -> this[position + 1]
        }
      }
    }
  }
}

internal val NO_REGISTRATION = AutoCloseable {}
