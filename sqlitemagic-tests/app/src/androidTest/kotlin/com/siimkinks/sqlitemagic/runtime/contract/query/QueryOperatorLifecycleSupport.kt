package com.siimkinks.sqlitemagic.runtime.contract.query

import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.insertedResult

internal fun newQueryOperatorLifecycleEntity(sequence: Int) = SimpleMutableEntity(
  id = null,
  value = "query-operator-lifecycle-$sequence",
  boxedBoolean = null,
  primitiveBoolean = true
)

internal fun insertQueryOperatorLifecycleEntity(sequence: Int) = newQueryOperatorLifecycleEntity(sequence)
  .also {
    insertedResult(
      result = it
        .insert()
        .execute(),
      modelName = "SimpleMutableEntity"
    )
  }
