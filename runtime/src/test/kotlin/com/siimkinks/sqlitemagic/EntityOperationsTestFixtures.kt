package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityGeneratedIdAdapter
import com.siimkinks.sqlitemagic.internal.GeneratedEntityIdentity
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap

internal typealias TestColumn = Column<*, *, *, TestEntity, NotNullable>

internal data class TestEntity(
  var id: String,
  var key: String,
  var name: String?,
  var generatedRowId: Long? = null
)

internal object TestSchema {
  val table = Table<TestEntity>("books", null, 3)
  val id = UniqueColumn<String, String, String, TestEntity, NotNullable>(
    table,
    "id",
    false,
    Utils.STRING_PARSER,
    false,
    null
  )
  val key = UniqueColumn<String, String, String, TestEntity, NotNullable>(
    table,
    "key",
    false,
    Utils.STRING_PARSER,
    false,
    null
  )
}

internal class TestAdapter(
  override val withoutRowId: Boolean = false,
  private val omitNullInsertValues: Boolean = false
) : EntityDefaultIdentityAdapter<TestEntity>, EntityGeneratedIdAdapter<TestEntity> {
  override val moduleName: String? = null
  override val tableName = "books"
  override val insertSql = "INSERT%s INTO books (id, key, name) VALUES (?, ?, ?)"
  override val tablePosition = 0
  override val maxColumns = 3
  override val defaultIdentityColumn = TestSchema.id

  val bindMaps = mutableListOf<SimpleArrayMap<String, Any>>()

  override fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: TestEntity,
    generatedRelationshipIds: Map<String, Long>
  ) {
    statement.clearBindings()
    statement.bindString(1, entity.id)
    statement.bindString(2, entity.key)
    when (entity.name) {
      null -> statement.bindNull(3)
      else -> statement.bindString(3, entity.name)
    }
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: TestEntity,
    byColumn: TestColumn
  ) {
    statement.clearBindings()
    statement.bindString(1, entity.name ?: "")
    statement.bindString(
      2,
      when (byColumn) {
        TestSchema.id -> entity.id
        TestSchema.key -> entity.key
        else -> error("Unknown test identity column")
      }
    )
  }

  override fun bindNotNullForInsert(
    entity: TestEntity,
    values: SimpleArrayMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    values.clear()
    if (!omitNullInsertValues) {
      values.put("id", entity.id)
      values.put("key", entity.key)
      entity.name?.let {
        values.put("name", it)
      }
    }
    bindMaps += values
  }

  override fun bindNotNullForUpdate(
    entity: TestEntity,
    values: SimpleArrayMap<String, Any>,
    byColumn: TestColumn
  ) {
    values.clear()
    entity.name?.let {
      values.put("name", it)
    }
  }

  override fun identity(
    entity: TestEntity,
    byColumn: TestColumn
  ) = GeneratedEntityIdentity(
    columnName = when (byColumn) {
      TestSchema.id -> TestSchema.id.nameForTest()
      TestSchema.key -> TestSchema.key.nameForTest()
      else -> error("Unknown test identity column")
    },
    serializedValue = when (byColumn) {
      TestSchema.id -> entity.id
      TestSchema.key -> entity.key
      else -> error("Unknown test identity column")
    }
  )

  override fun hasIdentityValue(
    entity: TestEntity,
    byColumn: TestColumn
  ) = when (byColumn) {
    TestSchema.id -> entity.id.isNotEmpty()
    TestSchema.key -> entity.key.isNotEmpty()
    else -> error("Unknown test identity column")
  }

  override fun updateStatementSql(byColumn: TestColumn) = when (byColumn) {
    TestSchema.id -> "UPDATE%s books SET name=? WHERE id=?"
    TestSchema.key -> "UPDATE%s books SET name=? WHERE key=?"
    else -> error("Unknown test identity column")
  }

  override fun assignGeneratedId(
    entity: TestEntity,
    rowId: Long
  ) {
    entity.generatedRowId = rowId
  }
}

internal class OperationLoggingLogger : Logger {
  val debugMessages = mutableListOf<String>()

  override fun logDebug(message: String) {
    debugMessages += message
  }

  override fun logWarning(message: String) = Unit

  override fun logError(message: String) = Unit

  override fun logError(message: String, throwable: Throwable) = Unit

  override fun logQueryTime(
    queryTimeInMillis: Long,
    observedTables: Array<String>,
    sql: String,
    args: Array<String>?
  ) = Unit
}

internal fun TestColumn.nameForTest() = when {
  this == TestSchema.id -> "id"
  this == TestSchema.key -> "key"
  else -> error("Unknown test identity column")
}
