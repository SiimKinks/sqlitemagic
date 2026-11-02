package com.siimkinks.sqlitemagic.framework

import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.internal.MutationResults

internal class FrameworkSQLiteConnection(private val db: SQLiteDatabase) : SQLiteConnection, MutationResults {
  private val idleWindow = IdleCursorWindow()
  private val transaction = TransactionState(db)
  private val readSnapshot = ReadSnapshot(
    db = db,
    transaction = transaction
  )

  override fun prepare(sql: String): SQLiteStatement = FrameworkSQLiteStatement(
    db = db,
    sql = sql,
    idleWindow = idleWindow,
    readSnapshot = readSnapshot,
    transaction = transaction
  )

  override fun inTransaction() = transaction.isOpen

  override fun close() {
    idleWindow.close()
    db.close()
  }

  override fun executeInsert(statement: SQLiteStatement) = (statement as FrameworkSQLiteStatement).executeInsert()

  override fun executeUpdateDelete(statement: SQLiteStatement) =
    (statement as FrameworkSQLiteStatement).executeUpdateDelete()
}
