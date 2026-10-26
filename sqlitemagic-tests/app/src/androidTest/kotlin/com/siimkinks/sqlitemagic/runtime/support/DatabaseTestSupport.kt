package com.siimkinks.sqlitemagic.runtime.support

import android.database.sqlite.SQLiteException
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityOperationBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.expr
import com.siimkinks.sqlitemagic.queryDeep
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import org.junit.Assert.assertThrows

internal data class DatabaseSnapshot<T>(
  val parents: List<T>,
  val related: List<*>? = null
)

internal fun <B : EntityOperationBuilder<B>> B.withConflictAlgorithm(
  conflictAlgorithm: Int?
): B = apply {
  conflictAlgorithm?.let(this::conflictAlgorithm)
}

internal fun insertedResult(
  result: EntityInsertResult,
  modelName: String
) = when (result) {
  is EntityInsertResult.Inserted -> result
  EntityInsertResult.Ignored -> throw AssertionError("Insert was ignored for $modelName")
}

internal fun insertedPersistResult(
  result: EntityPersistResult,
  modelName: String
) = when (result) {
  is EntityPersistResult.Inserted -> result
  EntityPersistResult.Updated -> throw AssertionError("Persist unexpectedly updated for $modelName")
  EntityPersistResult.Ignored -> throw AssertionError("Persist was ignored for $modelName")
}

internal fun insertedRowId(
  result: EntityInsertResult,
  modelName: String
) = checkNotNull(
  insertedResult(
    result = result,
    modelName = modelName
  ).rowId
)

internal fun <T> InsertModelCase<T>.seed() = newValue(sequence = 1).also {
  insertedResult(
    result = insert(value = it)
      .execute(),
    modelName = name
  )
}

internal fun <T> relatedRows(
  modelCase: RecursiveModelCase<T>,
  values: List<T>
) = values.flatMap(modelCase::relatedValues)

internal fun <T> captureRows(
  table: Table<T>,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = SELECT.FROM(table)
  .queryDeep()
  .usingConnection(connection)
  .execute()

internal fun <T> observeRows(table: Table<T>) = SELECT.FROM(table)
  .queryDeep()
  .observe()
  .runQuery()
  .test()

internal fun <T> captureDatabaseSnapshot(
  modelCase: RuntimeModelCase<T>
) = DatabaseSnapshot(
  parents = captureRows(table = modelCase.table),
  related = when (modelCase) {
    is RecursiveModelCase<*> -> captureRows(table = modelCase.relatedTable)
    else -> null
  }
)

internal fun <T> seedRows(
  modelCase: InsertModelCase<T>,
  count: Int,
  firstSequence: Int = 0
): List<T> {
  List(size = count) { modelCase.newValue(sequence = firstSequence + it) }
    .forEach { value ->
      insertedResult(
        result = modelCase
          .insert(value = value)
          .execute(),
        modelName = modelCase.name
      )
    }
  return captureRows(table = modelCase.table)
}

internal fun assertRowsIgnoringOrder(
  table: Table<*>,
  expected: List<Any?>,
  connection: DbConnection = SqliteMagic.getDefaultConnection()
) = assertThat(
  captureRows(
    table = table,
    connection = connection
  )
).containsExactlyElementsIn(expected)

internal fun <T> assertDatabaseSnapshotIgnoringOrder(
  modelCase: RuntimeModelCase<T>,
  expected: DatabaseSnapshot<T>
) {
  assertRowsIgnoringOrder(
    table = modelCase.table,
    expected = expected.parents
  )
  expected.related?.let { expectedRelated ->
    when (modelCase) {
      is RecursiveModelCase<*> -> assertRowsIgnoringOrder(
        table = modelCase.relatedTable,
        expected = expectedRelated
      )
      else -> throw AssertionError(
        "Expected related rows for non-recursive model case ${modelCase.name}"
      )
    }
  }
}

internal fun sqliteVersion() = SELECT
  .COLUMN("sqlite_version()".expr(STRING))
  .execute()
  .single()

internal fun supportsSqlite(version: String, minor: Int) = version
  .split('.')
  .map(String::toInt)
  .let { (major, versionMinor) ->
    major > 3 || major == 3 && versionMinor >= minor
  }

internal fun assertSqliteRejects(containing: String, query: () -> Any?) =
  assertThat(assertThrows(SQLiteException::class.java) { query() })
    .hasMessageThat()
    .contains(containing)
