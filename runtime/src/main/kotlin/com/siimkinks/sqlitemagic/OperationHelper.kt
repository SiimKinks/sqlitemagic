package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase.CONFLICT_ABORT
import android.database.sqlite.SQLiteDatabase.CONFLICT_NONE
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.EntityOperation.INSERT
import com.siimkinks.sqlitemagic.EntityOperation.UPDATE
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap.BASE_SIZE
import java.io.Closeable

internal enum class EntityOperation {
  INSERT,
  UPDATE
}

internal class OperationHelper(
  private val conflictAlgorithm: Int,
  operation: EntityOperation,
  private val operationByColumns: List<Column<*, *, *, *, *>> = emptyList()
) : Closeable {
  private val customConflictSql = conflictAlgorithm != CONFLICT_NONE && conflictAlgorithm != CONFLICT_ABORT
  private val customUpdateSql = operation == UPDATE && operationByColumns.isNotEmpty()
  private var inserts = when {
    customConflictSql && operation == INSERT -> SimpleArrayMap<String, SupportSQLiteStatement>(BASE_SIZE)
    else -> null
  }
  private var updates = when {
    operation == UPDATE && (customConflictSql || customUpdateSql) -> SimpleArrayMap<String, SupportSQLiteStatement>(
      BASE_SIZE
    )
    else -> null
  }

  fun insertStatement(
    tableName: String,
    sql: String,
    manager: EntityDbManager
  ): SupportSQLiteStatement = when {
    customConflictSql -> checkNotNull(inserts)
      .let { inserts ->
        inserts.get(tableName)
          ?: manager
            .compileStatement(sql, conflictAlgorithm)
            .also { inserts.put(tableName, it) }
      }
    else -> manager.getInsertStatement(sql)
  }

  fun updateStatement(
    tableName: String,
    sql: String,
    manager: EntityDbManager
  ): SupportSQLiteStatement = when {
    customConflictSql || customUpdateSql -> checkNotNull(updates)
      .let { updates ->
        updates.get(tableName)
          ?: manager
            .compileStatement(
              SqlUtil.opByColumnSql(sql, tableName, operationByColumns),
              conflictAlgorithm
            )
            .also { updates.put(tableName, it) }
      }
    else -> manager.getUpdateStatement(sql)
  }

  override fun close() {
    val insertStatements = inserts
    inserts = null
    insertStatements?.closeStatements()
    val updateStatements = updates
    updates = null
    updateStatements?.closeStatements()
  }

  private fun SimpleArrayMap<String, SupportSQLiteStatement>.closeStatements() = try {
    for (index in 0 until size()) {
      valueAt(index).close()
    }
  } catch (_: Exception) {
    // Statement cleanup is best effort, as with the connection-owned statement caches.
  }
}
