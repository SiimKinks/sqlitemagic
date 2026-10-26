package com.siimkinks.sqlitemagic.runtime.support

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert

internal fun insertSimpleRow(value: SimpleMutableEntity) = insertedResult(
  result = value
    .insert()
    .execute(),
  modelName = "simple-row"
)

internal fun assertSimpleRows(vararg expected: SimpleMutableEntity) = assertThat(
  SELECT.FROM(SIMPLE_MUTABLE_ENTITY)
    .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
    .execute()
).isEqualTo(expected.toList())
