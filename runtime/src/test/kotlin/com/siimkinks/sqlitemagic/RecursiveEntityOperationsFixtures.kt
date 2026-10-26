package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.EntityRelationshipOperations
import com.siimkinks.sqlitemagic.internal.MutableScatterMap

internal const val RECURSIVE_PARENT_TABLE = "recursive_parent"
internal const val RECURSIVE_CHILD_TABLE = "recursive_child"
internal const val RECURSIVE_GRANDCHILD_TABLE = "recursive_grandchild"

internal val RECURSIVE_TABLES = setOf(
  RECURSIVE_PARENT_TABLE,
  RECURSIVE_CHILD_TABLE,
  RECURSIVE_GRANDCHILD_TABLE
)

internal data class RecursiveGrandchild(
  var id: String
)

internal data class RecursiveChild(
  var id: String,
  var grandchild: RecursiveGrandchild?
)

internal data class RecursiveParent(
  var id: String,
  var child: RecursiveChild?
)

internal fun recursiveGraph(
  parentId: String = "parent",
  childId: String = "child",
  grandchildId: String = "grandchild"
) = RecursiveParent(
  id = parentId,
  child = RecursiveChild(
    id = childId,
    grandchild = RecursiveGrandchild(id = grandchildId)
  )
)

internal fun parentWithChild(
  parentId: String = "parent",
  childId: String = "child"
) = RecursiveParent(
  id = parentId,
  child = RecursiveChild(
    id = childId,
    grandchild = null
  )
)

internal fun twoParentBulkGraphs() = listOf(
  parentWithChild(
    parentId = "parent-1",
    childId = "child-1"
  ),
  parentWithChild(
    parentId = "parent-2",
    childId = "child-2"
  )
)

internal enum class RecursiveOperationEvent {
  GRANDCHILD_INSERT,
  GRANDCHILD_UPDATE,
  CHILD_INSERT_RELATIONSHIPS,
  CHILD_INSERT,
  CHILD_UPDATE_RELATIONSHIPS,
  CHILD_UPDATE,
  CHILD_PERSIST_RELATIONSHIPS,
  PARENT_INSERT_RELATIONSHIPS,
  PARENT_INSERT,
  PARENT_UPDATE_RELATIONSHIPS,
  PARENT_UPDATE,
  PARENT_PERSIST_RELATIONSHIPS
}

internal class RecursiveOperationState {
  val events: List<RecursiveOperationEvent>
    field = mutableListOf()
  val childIgnoreNullValues: List<Boolean>
    field = mutableListOf()

  fun record(event: RecursiveOperationEvent) {
    events += event
  }

  fun recordChildIgnoreNullValue(ignoreNullValues: Boolean) {
    childIgnoreNullValues += ignoreNullValues
  }
}

internal class RecursiveGrandchildAdapter(
  private val state: RecursiveOperationState
) : EntityDefaultIdentityAdapter<RecursiveGrandchild> {
  override val moduleName: String? = null
  override val tableName = RECURSIVE_GRANDCHILD_TABLE
  override val insertSql = "INSERT%s INTO $RECURSIVE_GRANDCHILD_TABLE (id) VALUES (?)"
  override val tablePosition = 0
  override val maxColumns = 1
  override val withoutRowId = false
  override val defaultIdentityColumnName = "id"

  override fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveGrandchild,
    generatedRelationshipIds: Map<String, Long>
  ) {
    state.record(RecursiveOperationEvent.GRANDCHILD_INSERT)
    statement.clearBindings()
    statement.bindString(
      index = 1,
      value = entity.id
    )
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveGrandchild,
    columnName: String
  ) {
    state.record(RecursiveOperationEvent.GRANDCHILD_UPDATE)
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
    entity: RecursiveGrandchild,
    values: MutableScatterMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    values.clear()
    values["id"] = entity.id
  }

  override fun bindNotNullForUpdate(
    entity: RecursiveGrandchild,
    values: MutableScatterMap<String, Any>,
    columnName: String
  ) {
    values.clear()
  }

  override fun identity(
    entity: RecursiveGrandchild,
    columnName: String
  ) = entity.id

  override fun hasIdentityValue(
    entity: RecursiveGrandchild,
    columnName: String
  ) = entity.id.isNotEmpty()

  override fun updateStatementSql(columnName: String) =
    "UPDATE%s $RECURSIVE_GRANDCHILD_TABLE SET id=? WHERE id=?"
}

internal class RecursiveChildAdapter(
  private val state: RecursiveOperationState,
  private val grandchildAdapter: RecursiveGrandchildAdapter
) : EntityDefaultIdentityAdapter<RecursiveChild>,
  EntityRecursiveAdapter<RecursiveChild> {
  override val moduleName: String? = null
  override val tableName = RECURSIVE_CHILD_TABLE
  override val insertSql = "INSERT%s INTO $RECURSIVE_CHILD_TABLE (id, grandchild) VALUES (?, ?)"
  override val tablePosition = 1
  override val maxColumns = 2
  override val withoutRowId = false
  override val defaultIdentityColumnName = "id"
  override val triggerTableNames = arrayOf(
    RECURSIVE_CHILD_TABLE,
    RECURSIVE_GRANDCHILD_TABLE
  )

  override fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveChild,
    generatedRelationshipIds: Map<String, Long>
  ) {
    state.record(RecursiveOperationEvent.CHILD_INSERT)
    statement.clearBindings()
    statement.bindString(
      index = 1,
      value = entity.id
    )
    when (val grandchild = entity.grandchild) {
      null -> statement.bindNull(2)
      else -> statement.bindString(
        index = 2,
        value = grandchild.id
      )
    }
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveChild,
    columnName: String
  ) {
    state.record(RecursiveOperationEvent.CHILD_UPDATE)
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
    entity: RecursiveChild,
    values: MutableScatterMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    values.clear()
    values["id"] = entity.id
    entity.grandchild?.let {
      values["grandchild"] = it.id
    }
  }

  override fun bindNotNullForUpdate(
    entity: RecursiveChild,
    values: MutableScatterMap<String, Any>,
    columnName: String
  ) {
    values.clear()
  }

  override fun identity(
    entity: RecursiveChild,
    columnName: String
  ) = entity.id

  override fun hasIdentityValue(
    entity: RecursiveChild,
    columnName: String
  ) = entity.id.isNotEmpty()

  override fun updateStatementSql(columnName: String) =
    "UPDATE%s $RECURSIVE_CHILD_TABLE SET id=? WHERE id=?"

  override fun insertRelationships(
    entity: RecursiveChild,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.CHILD_INSERT_RELATIONSHIPS)
    return entity.grandchild
      ?.let {
        operations.insert(
          adapter = grandchildAdapter,
          entity = it
        ) != EntityInsertResult.Ignored
      }
      ?: true
  }

  override fun updateRelationships(
    entity: RecursiveChild,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.CHILD_UPDATE_RELATIONSHIPS)
    return entity.grandchild
      ?.let {
        operations.update(
          adapter = grandchildAdapter,
          entity = it
        )
      }
      ?: true
  }

  override fun persistRelationships(
    entity: RecursiveChild,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.CHILD_PERSIST_RELATIONSHIPS)
    state.recordChildIgnoreNullValue((operations as RelationshipOperations).ignoreNullValues)
    return entity.grandchild
      ?.let {
        operations.persist(
          adapter = grandchildAdapter,
          entity = it
        ) != EntityPersistResult.Ignored
      }
      ?: true
  }
}

internal class RecursiveParentAdapter(
  private val state: RecursiveOperationState,
  private val childAdapter: RecursiveChildAdapter
) : EntityDefaultIdentityAdapter<RecursiveParent>,
  EntityRecursiveAdapter<RecursiveParent> {
  override val moduleName: String? = null
  override val tableName = RECURSIVE_PARENT_TABLE
  override val insertSql = "INSERT%s INTO $RECURSIVE_PARENT_TABLE (id, child) VALUES (?, ?)"
  override val tablePosition = 2
  override val maxColumns = 2
  override val withoutRowId = false
  override val defaultIdentityColumnName = "id"
  override val triggerTableNames = arrayOf(
    RECURSIVE_PARENT_TABLE,
    RECURSIVE_CHILD_TABLE,
    RECURSIVE_GRANDCHILD_TABLE
  )

  override fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveParent,
    generatedRelationshipIds: Map<String, Long>
  ) {
    state.record(RecursiveOperationEvent.PARENT_INSERT)
    statement.clearBindings()
    statement.bindString(
      index = 1,
      value = entity.id
    )
    when (val child = entity.child) {
      null -> statement.bindNull(2)
      else -> statement.bindString(
        index = 2,
        value = child.id
      )
    }
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveParent,
    columnName: String
  ) {
    state.record(RecursiveOperationEvent.PARENT_UPDATE)
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
    entity: RecursiveParent,
    values: MutableScatterMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    values.clear()
    values["id"] = entity.id
    entity.child?.let {
      values["child"] = it.id
    }
  }

  override fun bindNotNullForUpdate(
    entity: RecursiveParent,
    values: MutableScatterMap<String, Any>,
    columnName: String
  ) {
    values.clear()
  }

  override fun identity(
    entity: RecursiveParent,
    columnName: String
  ) = entity.id

  override fun hasIdentityValue(
    entity: RecursiveParent,
    columnName: String
  ) = entity.id.isNotEmpty()

  override fun updateStatementSql(columnName: String) =
    "UPDATE%s $RECURSIVE_PARENT_TABLE SET id=? WHERE id=?"

  override fun insertRelationships(
    entity: RecursiveParent,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.PARENT_INSERT_RELATIONSHIPS)
    return entity.child
      ?.let {
        operations.insert(
          adapter = childAdapter,
          entity = it
        ) != EntityInsertResult.Ignored
      }
      ?: true
  }

  override fun updateRelationships(
    entity: RecursiveParent,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.PARENT_UPDATE_RELATIONSHIPS)
    return entity.child
      ?.let {
        operations.update(
          adapter = childAdapter,
          entity = it
        )
      }
      ?: true
  }

  override fun persistRelationships(
    entity: RecursiveParent,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.PARENT_PERSIST_RELATIONSHIPS)
    return entity.child
      ?.let {
        operations.persist(
          adapter = childAdapter,
          entity = it
        ) != EntityPersistResult.Ignored
      }
      ?: true
  }
}

internal class RecursiveScenario {
  val state = RecursiveOperationState()
  val parent = RecursiveParentAdapter(
    state = state,
    childAdapter = RecursiveChildAdapter(
      state = state,
      grandchildAdapter = RecursiveGrandchildAdapter(state)
    )
  )
  val recordingConnection = newRecursiveConnection()
  val database = recordingConnection.recordingDatabase
  val connection = recordingConnection.connection
  val triggers get() = recordingConnection.triggers
}
