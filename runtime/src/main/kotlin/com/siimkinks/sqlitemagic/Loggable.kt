package com.siimkinks.sqlitemagic

internal interface Loggable {
  val tableName: String

  fun logInsert(entity: Any?) = logDebug("INSERT\n  table: %s\n  object: %s", tableName, entity)
  fun logInsertId(rowId: Long) = logDebug("INSERT id: %s", rowId)
  fun logUpdate(entity: Any?) = logDebug("UPDATE\n  table: %s\n  object: %s", tableName, entity)
  fun logUpdateRowsAffected(rowsAffected: Int) = logDebug("UPDATE rows affected: %s", rowsAffected)
  fun logPersist(entity: Any?) = logDebug("PERSIST\n  table: %s\n  object: %s", tableName, entity)
  fun logPersistUpdateFailed() = logDebug("PERSIST update failed; trying insertion")
  fun logPersistInsertId(rowId: Long?) = logDebug("PERSIST insert id: %s", rowId)

  fun logError(exception: Exception, message: String) {
    if (SqliteMagic.LOGGING_ENABLED) {
      LogUtil.logError(exception, message)
    }
  }

  private fun logDebug(message: String, vararg args: Any?) {
    if (SqliteMagic.LOGGING_ENABLED) {
      LogUtil.logDebug(message, *args)
    }
  }
}