package com.siimkinks.sqlitemagic.runtime.support

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert

internal fun simpleRow(
  id: Long,
  value: String?
) = SimpleMutableEntity(
  id = id,
  value = value,
  boxedBoolean = null,
  primitiveBoolean = false
)

internal fun insertSimpleRow(value: SimpleMutableEntity) = assertSeedInserted(
  result = value
    .insert()
    .execute(),
  modelName = "simple-row"
)

internal fun assertSimpleRows(vararg expected: SimpleMutableEntity) = assertThat(
  Select
    .from(SIMPLE_MUTABLE_ENTITY)
    .orderBy(SIMPLE_MUTABLE_ENTITY.ID.asc())
    .execute()
).isEqualTo(expected.toList())
