package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.EntityRelationshipOperations
import com.siimkinks.sqlitemagic.internal.MutableScatterMap

internal data class GeneratedIdParent(
  var id: String,
  var child: TestEntity
)

internal class GeneratedIdOperationState {
  val fixedChildIds: List<Long>
    field = mutableListOf()
  val nullOmittingChildIds: List<Long>
    field = mutableListOf()

  fun recordFixedChildId(childId: Long) {
    fixedChildIds += childId
  }

  fun recordNullOmittingChildId(childId: Long) {
    nullOmittingChildIds += childId
  }
}

private class GeneratedIdChildAdapter(
  delegate: TestAdapter = TestAdapter()
) : EntityDefaultIdentityAdapter<TestEntity> by delegate

private class GeneratedIdParentAdapter(
  private val state: GeneratedIdOperationState,
  private val childAdapter: GeneratedIdChildAdapter
) : EntityDefaultIdentityAdapter<GeneratedIdParent>,
  EntityRecursiveAdapter<GeneratedIdParent> {
  override val moduleName: String? = null
  override val tableName = "generated_id_parent"
  override val insertSql = "INSERT%s INTO generated_id_parent (id, child) VALUES (?, ?)"
  override val tablePosition = 1
  override val maxColumns = 2
  override val withoutRowId = false
  override val defaultIdentityColumnName = "id"
  override val triggerTableNames = arrayOf("generated_id_parent", "books")

  override fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: GeneratedIdParent,
    generatedRelationshipIds: Map<String, Long>
  ) {
    val childId = generatedRelationshipIds["child"] ?: 0L
    state.recordFixedChildId(childId)
    statement.clearBindings()
    statement.bindString(
      index = 1,
      value = entity.id
    )
    statement.bindLong(
      index = 2,
      value = childId
    )
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: GeneratedIdParent,
    columnName: String
  ) {
    statement.clearBindings()
    statement.bindString(
      index = 1,
      value = entity.id
    )
    statement.bindString(
      index = 2,
      value = entity.id
    )
  }

  override fun bindNotNullForInsert(
    entity: GeneratedIdParent,
    values: MutableScatterMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    val childId = generatedRelationshipIds["child"] ?: 0L
    state.recordNullOmittingChildId(childId)
    values.clear()
    values["id"] = entity.id
    values["child"] = childId
  }

  override fun bindNotNullForUpdate(
    entity: GeneratedIdParent,
    values: MutableScatterMap<String, Any>,
    columnName: String
  ) {
    values.clear()
  }

  override fun identity(
    entity: GeneratedIdParent,
    columnName: String
  ) = entity.id

  override fun hasIdentityValue(
    entity: GeneratedIdParent,
    columnName: String
  ) = entity.id.isNotEmpty()

  override fun updateStatementSql(columnName: String) =
    "UPDATE%s generated_id_parent SET child=? WHERE id=?"

  override fun insertRelationships(
    entity: GeneratedIdParent,
    operations: EntityRelationshipOperations
  ) = when (
    val result = operations.insert(
      adapter = childAdapter,
      entity = entity.child
    )
  ) {
    EntityInsertResult.Ignored -> false
    is EntityInsertResult.Inserted -> {
      result.rowId?.let {
        operations.rememberGeneratedId(
          columnName = "child",
          rowId = it
        )
      }
      true
    }
  }

  override fun updateRelationships(
    entity: GeneratedIdParent,
    operations: EntityRelationshipOperations
  ) = operations.update(
    adapter = childAdapter,
    entity = entity.child
  )

  override fun persistRelationships(
    entity: GeneratedIdParent,
    operations: EntityRelationshipOperations
  ) = when (
    val result = operations.persist(
      adapter = childAdapter,
      entity = entity.child
    )
  ) {
    EntityPersistResult.Ignored -> false
    EntityPersistResult.Updated -> true
    is EntityPersistResult.Inserted -> {
      result.rowId?.let {
        operations.rememberGeneratedId(
          columnName = "child",
          rowId = it
        )
      }
      true
    }
  }
}

internal class GeneratedIdAdapters {
  val state = GeneratedIdOperationState()
  val parent: EntityDefaultIdentityAdapter<GeneratedIdParent> = GeneratedIdParentAdapter(
    state = state,
    childAdapter = GeneratedIdChildAdapter()
  )
}

internal fun generatedIdParent(parentId: String = "parent") = GeneratedIdParent(
  id = parentId,
  child = TestEntity(
    id = "",
    key = "child",
    name = null
  )
)
