package com.siimkinks.sqlitemagic.runtime.contract.query

import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.RelationshipQueryModelCase
import com.siimkinks.sqlitemagic.runtime.support.insertedResult

internal data class RelationshipQueryExpectedRows<T>(
  val deep: List<T>,
  val shallow: List<T>
)

internal fun <T> seedExpectedRows(
  modelCase: InsertModelCase<T>,
  count: Int
) = List(size = count) { index ->
  val value = modelCase.newValue(sequence = index + 1)
  val result = insertedResult(
    result = modelCase
      .insert(value = value)
      .execute(),
    modelName = modelCase.name
  )
  modelCase.verifyAfterInsert(
    value = value,
    result = result
  )
  modelCase.expectedAfterInsert(
    value = value,
    result = result
  )
}

internal fun <T> seedRelationshipQueryExpectedRows(
  modelCase: RelationshipQueryModelCase<T>,
  count: Int
): RelationshipQueryExpectedRows<T> {
  val deep = seedExpectedRows(
    modelCase = modelCase,
    count = count
  )
  return RelationshipQueryExpectedRows(
    deep = deep,
    shallow = deep.map(modelCase::expectedAfterShallowQuery)
  )
}
