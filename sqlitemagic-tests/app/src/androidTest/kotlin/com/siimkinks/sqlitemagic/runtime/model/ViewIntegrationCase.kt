package com.siimkinks.sqlitemagic.runtime.model

import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult

interface ViewIntegrationCase {
  val name: String
}

interface ViewReadCase<T> : ViewIntegrationCase {
  val sourceModelName: String
  val table: Table<T>
  val expected: T
  fun insertSource(connection: DbConnection): EntityInsertResult
}

interface ViewObservationCase<T> : ViewIntegrationCase {
  val table: Table<T>
  val insertedExpected: List<T>
  val updatedExpected: List<T>

  fun insertSource(): EntityInsertResult
  fun updateSource(): Boolean
  fun insertAfterDisposal(): EntityInsertResult
}
