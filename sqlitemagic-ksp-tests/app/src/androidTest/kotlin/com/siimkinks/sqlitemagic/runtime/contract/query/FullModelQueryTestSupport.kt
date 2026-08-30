package com.siimkinks.sqlitemagic.runtime.contract.query

import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase

internal fun <T> seedExpectedRows(
  modelCase: InsertModelCase<T>,
  count: Int
) = List(size = count) { index ->
  val value = modelCase.newValue(sequence = index + 1)
  when (val result = modelCase.insert(value = value).execute()) {
    is EntityInsertResult.Inserted -> {
      modelCase.verifyAfterInsert(
        value = value,
        result = result
      )
      modelCase.expectedAfterInsert(
        value = value,
        result = result
      )
    }
    EntityInsertResult.Ignored -> throw AssertionError(
      "Seed insert was ignored for ${modelCase.name}"
    )
  }
}
