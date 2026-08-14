package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.EntityRelationshipOperations
import com.siimkinks.sqlitemagic.internal.GeneratedEntityIdentity
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

internal typealias GeneratedIdChild = TestEntity

internal data class GeneratedIdParent(
  var id: String,
  var child: GeneratedIdChild
)

private typealias GeneratedIdParentColumn = Column<*, *, *, GeneratedIdParent, NotNullable>

private object GeneratedIdTestSchema {
  val parentTable = Table<GeneratedIdParent>("generated_id_parent", null, 1)
  val parentId = UniqueColumn<String, String, String, GeneratedIdParent, NotNullable>(
    parentTable,
    "id",
    false,
    Utils.STRING_PARSER,
    false,
    null
  )
}

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
) : EntityDefaultIdentityAdapter<GeneratedIdChild> by delegate

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
  override val defaultIdentityColumn = GeneratedIdTestSchema.parentId
  override val triggerTableNames = arrayOf("generated_id_parent", "books")

  override fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: GeneratedIdParent,
    generatedRelationshipIds: Map<String, Long>
  ) {
    val childId = generatedRelationshipIds["child"] ?: 0L
    state.recordFixedChildId(childId)
    statement.clearBindings()
    statement.bindString(1, entity.id)
    statement.bindLong(2, childId)
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: GeneratedIdParent,
    byColumn: GeneratedIdParentColumn
  ) {
    statement.clearBindings()
    statement.bindString(1, entity.id)
    statement.bindString(2, entity.id)
  }

  override fun bindNotNullForInsert(
    entity: GeneratedIdParent,
    values: SimpleArrayMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    val childId = generatedRelationshipIds["child"] ?: 0L
    state.recordNullOmittingChildId(childId)
    values.clear()
    values.put("id", entity.id)
    values.put("child", childId)
  }

  override fun bindNotNullForUpdate(
    entity: GeneratedIdParent,
    values: SimpleArrayMap<String, Any>,
    byColumn: GeneratedIdParentColumn
  ) {
    values.clear()
  }

  override fun identity(
    entity: GeneratedIdParent,
    byColumn: GeneratedIdParentColumn
  ) = GeneratedEntityIdentity(
    columnName = "id",
    serializedValue = entity.id
  )

  override fun hasIdentityValue(
    entity: GeneratedIdParent,
    byColumn: GeneratedIdParentColumn
  ) = entity.id.isNotEmpty()

  override fun updateStatementSql(byColumn: GeneratedIdParentColumn) =
    "UPDATE%s generated_id_parent SET child=? WHERE id=?"

  override fun insertRelationships(
    entity: GeneratedIdParent,
    operations: EntityRelationshipOperations
  ): Boolean {
    val result = operations.insert(
      adapter = childAdapter,
      entity = entity.child
    )
    return when (result) {
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
  ): Boolean {
    val result = operations.persist(
      adapter = childAdapter,
      entity = entity.child
    )
    return when (result) {
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
}

internal data class GeneratedIdAdapters(
  val state: GeneratedIdOperationState,
  val parent: EntityDefaultIdentityAdapter<GeneratedIdParent>
)

internal fun generatedIdAdapters(): GeneratedIdAdapters {
  val state = GeneratedIdOperationState()
  return GeneratedIdAdapters(
    state = state,
    parent = GeneratedIdParentAdapter(
      state = state,
      childAdapter = GeneratedIdChildAdapter()
    )
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
