package com.siimkinks.sqlitemagic.runtime.contract.query

import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert

internal fun newQueryOperatorLifecycleEntity(sequence: Int) = SimpleMutableEntity(
  id = null,
  value = "query-operator-lifecycle-$sequence",
  boxedBoolean = null,
  primitiveBoolean = true
)

internal fun insertQueryOperatorLifecycleEntity(sequence: Int) =
  newQueryOperatorLifecycleEntity(sequence)
    .also(::assertSuccessfulQueryOperatorLifecycleInsert)

private fun assertSuccessfulQueryOperatorLifecycleInsert(value: SimpleMutableEntity) = when (
  value
    .insert()
    .execute()
) {
  is EntityInsertResult.Inserted -> Unit
  EntityInsertResult.Ignored -> error("Deterministic insert was ignored")
}
