package com.siimkinks.sqlitemagic.runtime.contract.query

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.Row2
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.StringIdEntityTable.Companion.STRING_ID_ENTITY
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertSqliteRejects
import com.siimkinks.sqlitemagic.runtime.support.sqliteVersion
import com.siimkinks.sqlitemagic.runtime.support.supportsSqlite
import org.junit.Rule
import org.junit.Test

/** RIGHT and FULL joins need SQLite 3.39.0; an older engine rejects them when it prepares the statement. */
internal class RightFullJoinRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun rightAndFullJoinsRunOnSupportingEnginesAndOlderEnginesRejectThem() {
    listOf("a", "b").forEach {
      SimpleMutableEntity(value = it)
        .insert()
        .execute()
    }
    listOf(
      StringIdEntity(
        id = "s1",
        value = "b"
      ),
      StringIdEntity(
        id = "s2",
        value = "c"
      )
    ).forEach {
      it.insert()
        .execute()
    }
    val matching = SIMPLE_MUTABLE_ENTITY.VALUE IS STRING_ID_ENTITY.VALUE
    val right = SELECT
      .COLUMNS(SIMPLE_MUTABLE_ENTITY.VALUE, STRING_ID_ENTITY.VALUE)
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .RIGHT_JOIN(STRING_ID_ENTITY)
      .ON(matching)
      .ORDER_BY(STRING_ID_ENTITY.ID.ASC())
    val full = SELECT
      .COLUMNS(SIMPLE_MUTABLE_ENTITY.VALUE, STRING_ID_ENTITY.VALUE.nullable())
      .FROM(SIMPLE_MUTABLE_ENTITY)
      .FULL_JOIN(STRING_ID_ENTITY)
      .ON(matching)
      .ORDER_BY(SIMPLE_MUTABLE_ENTITY.VALUE.ASC(), STRING_ID_ENTITY.VALUE.ASC())
    val version = sqliteVersion()

    when {
      supportsSqlite(
        version = version,
        minor = 39
      ) -> {
        assertThat(right.execute()).isEqualTo(
          listOf(
            Row2("b", "b"),
            Row2(null, "c")
          )
        )
        assertThat(full.execute()).isEqualTo(
          listOf(
            Row2(null, "c"),
            Row2("a", null),
            Row2("b", "b")
          )
        )
      }
      else -> listOf(right, full).forEach { query ->
        assertSqliteRejects(containing = "RIGHT and FULL OUTER JOINs are not currently supported") { query.execute() }
      }
    }
  }
}
