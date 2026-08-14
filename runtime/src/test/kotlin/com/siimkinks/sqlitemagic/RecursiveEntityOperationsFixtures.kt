package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.EntityRelationshipOperations
import com.siimkinks.sqlitemagic.internal.GeneratedEntityIdentity
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

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

private typealias RecursiveGrandchildColumn = Column<*, *, *, RecursiveGrandchild, NotNullable>
private typealias RecursiveChildColumn = Column<*, *, *, RecursiveChild, NotNullable>
private typealias RecursiveParentColumn = Column<*, *, *, RecursiveParent, NotNullable>

private object RecursiveTestSchema {
  val grandchildTable = Table<RecursiveGrandchild>(RECURSIVE_GRANDCHILD_TABLE, null, 1)
  val grandchildId = UniqueColumn<String, String, String, RecursiveGrandchild, NotNullable>(
    grandchildTable,
    "id",
    false,
    Utils.STRING_PARSER,
    false,
    null
  )
  val childTable = Table<RecursiveChild>(RECURSIVE_CHILD_TABLE, null, 1)
  val childId = UniqueColumn<String, String, String, RecursiveChild, NotNullable>(
    childTable,
    "id",
    false,
    Utils.STRING_PARSER,
    false,
    null
  )
  val parentTable = Table<RecursiveParent>(RECURSIVE_PARENT_TABLE, null, 1)
  val parentId = UniqueColumn<String, String, String, RecursiveParent, NotNullable>(
    parentTable,
    "id",
    false,
    Utils.STRING_PARSER,
    false,
    null
  )
}

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
  override val defaultIdentityColumn = RecursiveTestSchema.grandchildId

  override fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveGrandchild,
    generatedRelationshipIds: Map<String, Long>
  ) {
    state.record(RecursiveOperationEvent.GRANDCHILD_INSERT)
    statement.clearBindings()
    statement.bindString(1, entity.id)
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveGrandchild,
    byColumn: RecursiveGrandchildColumn
  ) {
    state.record(RecursiveOperationEvent.GRANDCHILD_UPDATE)
    statement.clearBindings()
    statement.bindString(1, entity.id)
    statement.bindString(2, entity.id)
  }

  override fun bindNotNullForInsert(
    entity: RecursiveGrandchild,
    values: SimpleArrayMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    values.clear()
    values.put("id", entity.id)
  }

  override fun bindNotNullForUpdate(
    entity: RecursiveGrandchild,
    values: SimpleArrayMap<String, Any>,
    byColumn: RecursiveGrandchildColumn
  ) {
    values.clear()
  }

  override fun identity(
    entity: RecursiveGrandchild,
    byColumn: RecursiveGrandchildColumn
  ) = GeneratedEntityIdentity(
    columnName = "id",
    serializedValue = entity.id
  )

  override fun hasIdentityValue(
    entity: RecursiveGrandchild,
    byColumn: RecursiveGrandchildColumn
  ) = entity.id.isNotEmpty()

  override fun updateStatementSql(byColumn: RecursiveGrandchildColumn) =
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
  override val defaultIdentityColumn = RecursiveTestSchema.childId
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
    statement.bindString(1, entity.id)
    entity.grandchild
      ?.let { statement.bindString(2, it.id) }
      ?: statement.bindNull(2)
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveChild,
    byColumn: RecursiveChildColumn
  ) {
    state.record(RecursiveOperationEvent.CHILD_UPDATE)
    statement.clearBindings()
    statement.bindString(1, entity.id)
    statement.bindString(2, entity.id)
  }

  override fun bindNotNullForInsert(
    entity: RecursiveChild,
    values: SimpleArrayMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    values.clear()
    values.put("id", entity.id)
    entity.grandchild?.let {
      values.put("grandchild", it.id)
    }
  }

  override fun bindNotNullForUpdate(
    entity: RecursiveChild,
    values: SimpleArrayMap<String, Any>,
    byColumn: RecursiveChildColumn
  ) {
    values.clear()
  }

  override fun identity(
    entity: RecursiveChild,
    byColumn: RecursiveChildColumn
  ) = GeneratedEntityIdentity(
    columnName = "id",
    serializedValue = entity.id
  )

  override fun hasIdentityValue(
    entity: RecursiveChild,
    byColumn: RecursiveChildColumn
  ) = entity.id.isNotEmpty()

  override fun updateStatementSql(byColumn: RecursiveChildColumn) =
    "UPDATE%s $RECURSIVE_CHILD_TABLE SET id=? WHERE id=?"

  override fun insertRelationships(
    entity: RecursiveChild,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.CHILD_INSERT_RELATIONSHIPS)
    entity.grandchild?.let {
      when (operations.insert(adapter = grandchildAdapter, entity = it)) {
        EntityInsertResult.Ignored -> return false
        is EntityInsertResult.Inserted -> Unit
      }
    }
    return true
  }

  override fun updateRelationships(
    entity: RecursiveChild,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.CHILD_UPDATE_RELATIONSHIPS)
    return entity.grandchild
      ?.let {
        operations.update(adapter = grandchildAdapter, entity = it)
      }
      ?: true
  }

  override fun persistRelationships(
    entity: RecursiveChild,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.CHILD_PERSIST_RELATIONSHIPS)
    state.recordChildIgnoreNullValue(operations.ignoreNullValues)
    return entity.grandchild
      ?.let {
        when (operations.persist(adapter = grandchildAdapter, entity = it)) {
          EntityPersistResult.Ignored -> false
          EntityPersistResult.Updated,
          is EntityPersistResult.Inserted -> true
        }
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
  override val defaultIdentityColumn = RecursiveTestSchema.parentId
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
    statement.bindString(1, entity.id)
    entity.child
      ?.let { statement.bindString(2, it.id) }
      ?: statement.bindNull(2)
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: RecursiveParent,
    byColumn: RecursiveParentColumn
  ) {
    state.record(RecursiveOperationEvent.PARENT_UPDATE)
    statement.clearBindings()
    statement.bindString(1, entity.id)
    statement.bindString(2, entity.id)
  }

  override fun bindNotNullForInsert(
    entity: RecursiveParent,
    values: SimpleArrayMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    values.clear()
    values.put("id", entity.id)
    entity.child?.let {
      values.put("child", it.id)
    }
  }

  override fun bindNotNullForUpdate(
    entity: RecursiveParent,
    values: SimpleArrayMap<String, Any>,
    byColumn: RecursiveParentColumn
  ) {
    values.clear()
  }

  override fun identity(
    entity: RecursiveParent,
    byColumn: RecursiveParentColumn
  ) = GeneratedEntityIdentity(
    columnName = "id",
    serializedValue = entity.id
  )

  override fun hasIdentityValue(
    entity: RecursiveParent,
    byColumn: RecursiveParentColumn
  ) = entity.id.isNotEmpty()

  override fun updateStatementSql(byColumn: RecursiveParentColumn) =
    "UPDATE%s $RECURSIVE_PARENT_TABLE SET id=? WHERE id=?"

  override fun insertRelationships(
    entity: RecursiveParent,
    operations: EntityRelationshipOperations
  ): Boolean {
    state.record(RecursiveOperationEvent.PARENT_INSERT_RELATIONSHIPS)
    return entity.child
      ?.let {
        when (operations.insert(adapter = childAdapter, entity = it)) {
          EntityInsertResult.Ignored -> false
          is EntityInsertResult.Inserted -> true
        }
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
        operations.update(adapter = childAdapter, entity = it)
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
        when (operations.persist(adapter = childAdapter, entity = it)) {
          EntityPersistResult.Ignored -> false
          EntityPersistResult.Updated,
          is EntityPersistResult.Inserted -> true
        }
      }
      ?: true
  }
}

internal data class RecursiveAdapters(
  val state: RecursiveOperationState,
  val grandchild: RecursiveGrandchildAdapter,
  val child: RecursiveChildAdapter,
  val parent: RecursiveParentAdapter
)

internal fun recursiveAdapters(): RecursiveAdapters {
  val state = RecursiveOperationState()
  val grandchild = RecursiveGrandchildAdapter(state)
  val child = RecursiveChildAdapter(
    state = state,
    grandchildAdapter = grandchild
  )
  return RecursiveAdapters(
    state = state,
    grandchild = grandchild,
    child = child,
    parent = RecursiveParentAdapter(
      state = state,
      childAdapter = child
    )
  )
}

internal data class RecursiveScenario(
  val adapters: RecursiveAdapters,
  val recordingConnection: RecordingConnection
) {
  val parent get() = adapters.parent
  val state get() = adapters.state
  val database get() = recordingConnection.recordingDatabase
  val connection get() = recordingConnection.connection
  val triggers get() = recordingConnection.triggers
}

internal fun recursiveScenario() = RecursiveScenario(
  adapters = recursiveAdapters(),
  recordingConnection = newRecursiveConnection()
)
