package com.siimkinks.sqlitemagic.runtime.contract.trigger

import android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
import com.siimkinks.sqlitemagic.CascadingNoteTable.Companion.CASCADING_NOTE
import com.siimkinks.sqlitemagic.DELETE
import com.siimkinks.sqlitemagic.EntityWithRelationshipTable.Companion.ENTITY_WITH_RELATIONSHIP
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SimpleMutableEntitys
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.fixture.model.CascadingNote
import com.siimkinks.sqlitemagic.fixture.model.EntityWithRelationship
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.RxJava2Adapter
import com.siimkinks.sqlitemagic.runtime.support.record
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
internal class CascadeDeleteTriggerTest(
  private val write: CascadingWrite
) {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun refreshesQueriesOverTablesTheDeleteCascadesInto() {
    val parent = SimpleMutableEntity(value = "parent")
    val child = EntityWithRelationship().apply {
      value = "child"
      relatedEntity = parent
    }
    child
      .insert()
      .execute()
    CascadingNote(
      text = "note",
      entity = child
    )
      .insert()
      .execute()
    val other = SimpleMutableEntity(value = "other")
    other
      .insert()
      .execute()
    val childValues = RxJava2Adapter
      .runQuery(SELECT.COLUMN(ENTITY_WITH_RELATIONSHIP.VALUE).FROM(ENTITY_WITH_RELATIONSHIP))
      .record()
    val noteTexts = RxJava2Adapter
      .runQuery(SELECT.COLUMN(CASCADING_NOTE.TEXT).FROM(CASCADING_NOTE))
      .record()
    try {
      write.execute(
        parent = parent,
        other = other
      )

      childValues
        .assertNoErrors()
        .assertValuesOnly(listOf("child"), emptyList())
      noteTexts
        .assertNoErrors()
        .assertValuesOnly(listOf("note"), emptyList())
    } finally {
      childValues.dispose()
      noteTexts.dispose()
    }
  }

  enum class CascadingWrite {
    ENTITY_DELETE {
      override fun execute(
        parent: SimpleMutableEntity,
        other: SimpleMutableEntity
      ) {
        parent
          .delete()
          .execute()
      }
    },
    BULK_DELETE {
      override fun execute(
        parent: SimpleMutableEntity,
        other: SimpleMutableEntity
      ) {
        SimpleMutableEntitys
          .delete(listOf(parent, other))
          .execute()
      }
    },
    DELETE_TABLE {
      override fun execute(
        parent: SimpleMutableEntity,
        other: SimpleMutableEntity
      ) {
        SimpleMutableEntitys
          .deleteTable()
          .execute()
      }
    },
    DELETE_FROM {
      override fun execute(
        parent: SimpleMutableEntity,
        other: SimpleMutableEntity
      ) {
        DELETE
          .FROM(SIMPLE_MUTABLE_ENTITY)
          .WHERE(SIMPLE_MUTABLE_ENTITY.ID IS checkNotNull(parent.id))
          .execute()
      }
    },
    UPDATE_OR_REPLACE {
      override fun execute(
        parent: SimpleMutableEntity,
        other: SimpleMutableEntity
      ) {
        UPDATE
          .OR(CONFLICT_REPLACE)
          .TABLE(SIMPLE_MUTABLE_ENTITY)
          .SET(SIMPLE_MUTABLE_ENTITY.ID, parent.id)
          .WHERE(SIMPLE_MUTABLE_ENTITY.ID IS checkNotNull(other.id))
          .execute()
      }
    };

    abstract fun execute(
      parent: SimpleMutableEntity,
      other: SimpleMutableEntity
    )
  }

  companion object {
    @JvmStatic
    @Parameters(name = "{0}")
    fun writes() = CascadingWrite.entries.toList()
  }
}
