package com.siimkinks.sqlitemagic.runtime.contract.identity

import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.EXCLUDED
import com.siimkinks.sqlitemagic.INSERT
import com.siimkinks.sqlitemagic.MutableRowIdEntitys
import com.siimkinks.sqlitemagic.RowIdChildTable.Companion.ROW_ID_CHILD
import com.siimkinks.sqlitemagic.RowIdEntityTable.Companion.ROW_ID_ENTITY
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.fixture.model.MutableRowIdEntity
import com.siimkinks.sqlitemagic.fixture.model.RowIdChild
import com.siimkinks.sqlitemagic.fixture.model.RowIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.assertSqliteRejects
import com.siimkinks.sqlitemagic.runtime.support.sqliteVersion
import com.siimkinks.sqlitemagic.runtime.support.supportsSqlite
import com.siimkinks.sqlitemagic.update
import org.junit.Rule
import org.junit.Test

internal class RowIdRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun insertsAssignRowIdsThatUpdatesAndPersistsKeep() {
    val results = listOf(
      RowIdEntity(id = "a", value = "a").insert().execute(),
      RowIdEntity(id = "b", value = "b").insert().execute(),
      RowIdEntity(id = "a", value = "a2").update().execute(),
      RowIdEntity(id = "b", rowId = 99, value = "b2").persist().execute(),
      RowIdEntity(id = "c", rowId = 99, value = "c").persist().execute()
    )

    assertThat(results to rows()).isEqualTo(
      listOf(
        EntityInsertResult.Inserted(1),
        EntityInsertResult.Inserted(2),
        true,
        EntityPersistResult.Updated,
        EntityPersistResult.Inserted(3)
      ) to listOf(
        RowIdEntity(id = "a", rowId = 1, value = "a2"),
        RowIdEntity(id = "b", rowId = 2, value = "b2"),
        RowIdEntity(id = "c", rowId = 3, value = "c")
      )
    )
  }

  @Test
  fun aMutableEntityGetsItsRowIdAssigned() {
    val single = MutableRowIdEntity(id = "a", value = "a")
    val bulk = listOf(
      MutableRowIdEntity(id = "b", value = "b"),
      MutableRowIdEntity(id = "c", value = "c")
    )
    single.insert().execute()
    MutableRowIdEntitys.insert(bulk).execute()

    assertThat((listOf(single) + bulk).map(MutableRowIdEntity::rowId)).containsExactly(1L, 2L, 3L).inOrder()
  }

  @Test
  fun replacingARowGivesItANewRowId() {
    RowIdEntity(id = "a", value = "a").insert().execute()
    RowIdEntity(id = "b", value = "b").insert().execute()

    val result = RowIdEntity(id = "a", value = "a2")
      .insert()
      .conflictAlgorithm(CONFLICT_REPLACE)
      .execute()

    assertThat(result to rows()).isEqualTo(
      EntityInsertResult.Inserted(3) to listOf(
        RowIdEntity(id = "a", rowId = 3, value = "a2"),
        RowIdEntity(id = "b", rowId = 2, value = "b")
      )
    )
  }

  @Test
  fun childrenReferenceTheIdAndCascadeWithIt() {
    RowIdChild(
      parent = RowIdEntity(id = "p", value = "parent"),
      label = "child"
    )
      .insert()
      .execute()
    val children = SELECT
      .FROM(ROW_ID_CHILD)
      .execute()
    val parentKeys = SELECT
      .COLUMN(ROW_ID_CHILD.PARENT)
      .FROM(ROW_ID_CHILD)
      .execute()
    RowIdEntity(id = "p", value = "parent").delete().execute()

    assertThat(
      listOf(
        children,
        parentKeys,
        SELECT
          .FROM(ROW_ID_CHILD)
          .execute()
      )
    ).isEqualTo(
      listOf(
        listOf(
          RowIdChild(
            id = 1,
            parent = RowIdEntity(id = "p", rowId = 1, value = "parent"),
            label = "child"
          )
        ),
        listOf("p"),
        emptyList()
      )
    )
  }

  @Test
  fun selectsAndFiltersByTheRowId() {
    RowIdEntity(id = "a", value = "a").insert().execute()
    RowIdEntity(id = "b", value = "b").insert().execute()

    val rowIds = SELECT
      .COLUMN(ROW_ID_ENTITY.ROW_ID)
      .FROM(ROW_ID_ENTITY)
      .ORDER_BY(ROW_ID_ENTITY.ID.DESC())
      .execute()
    val byRowId = SELECT
      .FROM(ROW_ID_ENTITY)
      .WHERE(ROW_ID_ENTITY.ROW_ID IS 2L)
      .execute()

    assertThat(rowIds to byRowId).isEqualTo(
      listOf(2L, 1L) to listOf(RowIdEntity(id = "b", rowId = 2, value = "b"))
    )
  }

  @Test
  fun anUpsertOnTheIdKeepsTheRowId() {
    RowIdEntity(id = "a", value = "a").insert().execute()
    RowIdEntity(id = "b", value = "b").insert().execute()
    val upsert = INSERT
      .INTO(ROW_ID_ENTITY)
      .COLUMNS(ROW_ID_ENTITY.ID, ROW_ID_ENTITY.VALUE)
      .VALUES("a", "a2")
      .ON_CONFLICT(ROW_ID_ENTITY.ID)
      .DO_UPDATE
      .SET(ROW_ID_ENTITY.VALUE, ROW_ID_ENTITY.VALUE.EXCLUDED)

    when {
      supportsSqlite(
        version = sqliteVersion(),
        minor = 24
      ) -> {
        upsert.execute()
        assertThat(rows()).isEqualTo(
          listOf(
            RowIdEntity(id = "a", rowId = 1, value = "a2"),
            RowIdEntity(id = "b", rowId = 2, value = "b")
          )
        )
      }
      else -> assertSqliteRejects(containing = "syntax error") { upsert.execute() }
    }
  }

  private fun rows() = SELECT
    .FROM(ROW_ID_ENTITY)
    .ORDER_BY(ROW_ID_ENTITY.ID.ASC())
    .execute()
}
