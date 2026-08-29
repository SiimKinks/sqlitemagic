package com.siimkinks.sqlitemagic.runtime.support

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityOperationBuilder
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.RecursiveModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase

internal data class DatabaseSnapshot<T>(
  val parents: List<T>,
  val related: List<*>? = null
)

internal fun <B : EntityOperationBuilder<B>> B.withConflictAlgorithm(
  conflictAlgorithm: Int?
): B = apply {
  conflictAlgorithm?.let(this::conflictAlgorithm)
}

internal fun <T> captureRows(table: Table<T>) = Select
  .from(table)
  .queryDeep()
  .execute()

internal fun <T> captureDatabaseSnapshot(
  modelCase: RuntimeModelCase<T>
) = DatabaseSnapshot(
  parents = captureRows(table = modelCase.table),
  related = when (modelCase) {
    is RecursiveModelCase<*> -> captureRows(table = modelCase.relatedTable)
    else -> null
  }
)

internal fun assertSeedInserted(
  result: EntityInsertResult,
  modelName: String
) = when (result) {
  is EntityInsertResult.Inserted -> Unit
  EntityInsertResult.Ignored -> throw AssertionError("Seed insert was ignored for $modelName")
}

internal fun <T> seedRows(
  modelCase: InsertModelCase<T>,
  count: Int
): List<T> {
  List(size = count, init = modelCase::newValue).forEach { value ->
    assertSeedInserted(
      result = modelCase
        .insert(value = value)
        .execute(),
      modelName = modelCase.name
    )
  }
  return captureRows(table = modelCase.table)
}

internal fun assertRowsInOrder(
  table: Table<*>,
  expected: List<*>
) = assertThat(captureRows(table = table))
  .containsExactlyElementsIn(expected)
  .inOrder()

internal fun assertRowsIgnoringOrder(
  table: Table<*>,
  expected: List<*>
) = assertThat(captureRows(table = table))
  .containsExactlyElementsIn(expected)

internal fun <T> assertDatabaseSnapshotInOrder(
  modelCase: RuntimeModelCase<T>,
  expected: DatabaseSnapshot<T>
) {
  assertRowsInOrder(
    table = modelCase.table,
    expected = expected.parents
  )
  expected.related?.let { expectedRelated ->
    when (modelCase) {
      is RecursiveModelCase<*> -> assertRowsInOrder(
        table = modelCase.relatedTable,
        expected = expectedRelated
      )
      else -> throw AssertionError(
        "Expected related rows for non-recursive model case ${modelCase.name}"
      )
    }
  }
}

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

internal fun <T> relatedRows(
  modelCase: RecursiveModelCase<T>,
  values: List<T>
) = values.flatMap(modelCase::relatedValues)
