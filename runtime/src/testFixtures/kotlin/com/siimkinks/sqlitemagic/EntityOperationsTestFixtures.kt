package com.siimkinks.sqlitemagic

import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityGeneratedIdAdapter
import com.siimkinks.sqlitemagic.internal.MutableScatterMap

internal data class TestEntity(
  var id: String = "id-1",
  var key: String = "key-1",
  var name: String? = "name",
  var generatedRowId: Long? = null
)

internal object TestSchema {
  val table = testTable<TestEntity>(name = "books")
  val id = testStringKey(
    table = table,
    name = "id"
  )
  val key = testStringKey(
    table = table,
    name = "key"
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
  override val defaultIdentityColumnName = TestSchema.id.name

  val bindMaps: List<MutableScatterMap<String, Any>>
    field = mutableListOf()

  override fun bindToInsertStatement(
    statement: SupportSQLiteStatement,
    entity: TestEntity,
    generatedRelationshipIds: Map<String, Long>
  ) {
    statement.clearBindings()
    statement.bindString(
      index = 1,
      value = entity.id
    )
    statement.bindString(
      index = 2,
      value = entity.key
    )
    when (val name = entity.name) {
      null -> statement.bindNull(3)
      else -> statement.bindString(
        index = 3,
        value = name
      )
    }
  }

  override fun bindToUpdateStatement(
    statement: SupportSQLiteStatement,
    entity: TestEntity,
    columnName: String
  ) {
    statement.clearBindings()
    statement.bindString(
      index = 1,
      value = entity.name ?: ""
    )
    statement.bindString(
      index = 2,
      value = entity.identityValue(columnName)
    )
  }

  override fun bindNotNullForInsert(
    entity: TestEntity,
    values: MutableScatterMap<String, Any>,
    generatedRelationshipIds: Map<String, Long>
  ) {
    values.clear()
    if (!omitNullInsertValues) {
      values["id"] = entity.id
      values["key"] = entity.key
      entity.name?.let {
        values["name"] = it
      }
    }
    bindMaps += values
  }

  override fun bindNotNullForUpdate(
    entity: TestEntity,
    values: MutableScatterMap<String, Any>,
    columnName: String
  ) {
    values.clear()
    entity.name?.let {
      values["name"] = it
    }
  }

  override fun identity(
    entity: TestEntity,
    columnName: String
  ) = entity.identityValue(columnName)

  override fun hasIdentityValue(
    entity: TestEntity,
    columnName: String
  ) = entity
    .identityValue(columnName)
    .isNotEmpty()

  override fun updateStatementSql(columnName: String) = when (columnName) {
    "id" -> "UPDATE%s books SET name=? WHERE id=?"
    "key" -> "UPDATE%s books SET name=? WHERE key=?"
    else -> error("Unknown test identity column")
  }

  override fun assignGeneratedId(
    entity: TestEntity,
    rowId: Long
  ) {
    entity.generatedRowId = rowId
  }
}

private fun TestEntity.identityValue(columnName: String) = when (columnName) {
  "id" -> id
  "key" -> key
  else -> error("Unknown test identity column")
}

internal enum class LogLevel {
  DEBUG,
  ERROR
}

internal data class LoggedMessage(
  val level: LogLevel,
  val message: String,
  val throwable: Throwable? = null
)

internal class RecordingLogger : Logger {
  val events: List<LoggedMessage>
    field = mutableListOf()

  override fun logDebug(message: String) {
    events += LoggedMessage(
      level = LogLevel.DEBUG,
      message = message
    )
  }

  override fun logError(message: String, throwable: Throwable) {
    events += LoggedMessage(
      level = LogLevel.ERROR,
      message = message,
      throwable = throwable
    )
  }
}

internal fun withLogger(loggingEnabled: Boolean, block: (RecordingLogger) -> Unit) {
  val previousLogger = SqliteMagic.logger
  val previousLoggingEnabled = SqliteMagic.loggingEnabled
  val logger = RecordingLogger()
  SqliteMagic.logger = logger
  SqliteMagic.loggingEnabled = loggingEnabled
  try {
    block(logger)
  } finally {
    SqliteMagic.logger = previousLogger
    SqliteMagic.loggingEnabled = previousLoggingEnabled
  }
}
