package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.TableChangeObserver

internal class TableObservers {
  @Volatile
  private var registrations = emptyArray<Registration>()

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
        registrations += registration
        return registration
      }
    }
    observer.onClosed()
    return NO_REGISTRATION
  }

  private fun remove(registration: Registration) = synchronized(this) {
    val current = registrations
    val index = current.indexOf(registration)
    if (index >= 0) {
      registrations = Array(current.size - 1) { position ->
        when {
          position < index -> current[position]
          else -> current[position + 1]
        }
      }
    }
  }

  fun publish(tables: Set<String>) {
    for (registration in registrations) {
      if (registration.matches(tables)) {
        registration.observer.onTablesChanged(tables)
      }
    }
  }

  fun close(): Boolean {
    val closing = synchronized(this) {
      when {
        isClosed -> return false
        else -> registrations.also {
          isClosed = true
          registrations = emptyArray()
        }
      }
    }
    for (registration in closing) {
      registration.observer.onClosed()
    }
    return true
  }

  private inner class Registration(
    private val tables: Array<String>?,
    val observer: TableChangeObserver
  ) : AutoCloseable {
    fun matches(changed: Set<String>) = when {
      tables == null -> true
      tables.size == 1 -> tables[0] in changed
      else -> tables.any(changed::contains)
    }

    override fun close() = remove(this)
  }
}

internal val NO_REGISTRATION = AutoCloseable {}
