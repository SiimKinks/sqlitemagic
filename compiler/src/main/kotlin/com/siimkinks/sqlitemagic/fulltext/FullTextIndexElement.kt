package com.siimkinks.sqlitemagic.fulltext

import com.siimkinks.sqlitemagic.annotation.FullTextDetail
import com.siimkinks.sqlitemagic.annotation.FullTextDetail.FULL
import com.siimkinks.sqlitemagic.annotation.FullTextRole
import com.siimkinks.sqlitemagic.annotation.FullTextRole.INDEXED
import com.siimkinks.sqlitemagic.annotation.FullTextRole.UNINDEXED
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaProvider
import com.siimkinks.sqlitemagic.schema.SchemaIdentityOwner
import com.siimkinks.sqlitemagic.utils.quoteSqlIdentifier
import java.util.Locale

internal data class FullTextColumnElement(
  val columnName: String,
  val role: FullTextRole
)

internal data class FullTextIndexElement(
  val identity: SqliteSchemaIdentity,
  val tableName: String,
  val rowIdColumnName: String,
  val columns: List<FullTextColumnElement>,
  val tokenize: String,
  val prefix: List<Int>,
  val detail: FullTextDetail,
  val columnSize: Boolean
) : SqliteSchemaProvider by identity {
  val name get() = rawName
  val createSql get() = listOf(virtualTableSql()) + createTriggerSql

  val createTriggerSql
    get() = listOf(
      trigger(
        suffix = INSERT_TRIGGER_SUFFIX,
        event = "AFTER INSERT",
        statements = listOf(insertSql())
      ),
      trigger(
        suffix = DELETE_TRIGGER_SUFFIX,
        event = "AFTER DELETE",
        statements = listOf(deleteSql())
      ),
      trigger(
        suffix = UPDATE_TRIGGER_SUFFIX,
        event = "AFTER UPDATE OF ${(changeColumnNames().map(::quoteSqlIdentifier) + ROW_ID_ALIASES).joinToString()}",
        condition = changedCondition(),
        statements = listOf(deleteSql(), insertSql())
      )
    )

  val dropTriggerSql
    get() = listOf(INSERT_TRIGGER_SUFFIX, DELETE_TRIGGER_SUFFIX, UPDATE_TRIGGER_SUFFIX).map { suffix ->
      "DROP TRIGGER IF EXISTS ${qualified(name + suffix)}"
    }

  val deleteAllSql get() = "INSERT INTO ${qualified(name)}(${quoteSqlIdentifier(name)}) VALUES ('delete-all')"

  private fun virtualTableSql(): String {
    val definitions = columns.map { column ->
      when (column.role) {
        UNINDEXED -> "${quoteSqlIdentifier(column.columnName)} UNINDEXED"
        else -> quoteSqlIdentifier(column.columnName)
      }
    } + listOfNotNull(
      "content=${sqlString(tableName)}",
      "content_rowid=${sqlString(rowIdColumnName)}",
      tokenize
        .takeIf(String::isNotEmpty)
        ?.let { "tokenize=${sqlString(it)}" },
      prefix
        .takeIf(List<Int>::isNotEmpty)
        ?.let { "prefix=${sqlString(it.joinToString(separator = " "))}" },
      "detail=${detail.name.lowercase(Locale.ROOT)}".takeIf { detail != FULL },
      "columnsize=0".takeUnless { columnSize }
    )
    return "CREATE VIRTUAL TABLE IF NOT EXISTS ${qualified(name)} USING fts5(${definitions.joinToString()})"
  }

  private fun qualified(objectName: String) = "${schema.qualifier}.${quoteSqlIdentifier(objectName)}"

  private fun trigger(
    suffix: String,
    event: String,
    condition: String? = null,
    statements: List<String>
  ) = "CREATE TRIGGER IF NOT EXISTS ${qualified(name + suffix)} $event ON ${quoteSqlIdentifier(tableName)} " +
      condition?.let { "WHEN $it " }.orEmpty() +
      "BEGIN ${statements.joinToString(separator = " ") { "$it;" }} END"

  private fun insertSql() = "INSERT INTO ${quoteSqlIdentifier(name)}(rowid, ${columnList()}) " +
      "VALUES (${values(row = "new")})"

  private fun deleteSql() = "INSERT INTO ${quoteSqlIdentifier(name)}(${quoteSqlIdentifier(name)}, rowid, " +
      "${columnList()}) VALUES ('delete', ${values(row = "old")})"

  private fun columnList() = columns.joinToString { quoteSqlIdentifier(it.columnName) }

  private fun values(row: String) = (listOf(rowIdColumnName) + columns.map(FullTextColumnElement::columnName))
    .joinToString { "$row.${quoteSqlIdentifier(it)}" }

  private fun changeColumnNames() = columns
    .filter { it.role == INDEXED }
    .map(FullTextColumnElement::columnName)
    .let { listOf(rowIdColumnName) + it }
    .distinct()

  private fun changedCondition() = changeColumnNames().joinToString(separator = " OR ") {
    "old.${quoteSqlIdentifier(it)} IS NOT new.${quoteSqlIdentifier(it)}"
  }
}

internal fun fullTextIndexName(
  tableName: String,
  value: String
) = value.ifEmpty { "${tableName}_fts" }

internal fun fullTextIndexIdentity(name: String) = SqliteSchemaIdentity(
  schema = MAIN,
  identifier = SqliteIdentifier(name)
)

internal fun fullTextSchemaOwners(
  name: String,
  columnSize: Boolean,
  typeKey: TypeKey
) = listOfNotNull("data", "idx", "docsize".takeIf { columnSize }, "config")
    .map { suffix -> "full-text shadow table" to "${name}_$suffix" }
    .let { shadowTables -> listOf("full-text index" to name) + shadowTables }
    .map { (kind, objectName) ->
      SchemaIdentityOwner(
        kind = kind,
        rawName = objectName,
        identity = fullTextIndexIdentity(objectName),
        typeKey = typeKey
      )
    }

private fun sqlString(value: String) = "'${value.replace(oldValue = "'", newValue = "''")}'"

private const val INSERT_TRIGGER_SUFFIX = "_ai"
private const val DELETE_TRIGGER_SUFFIX = "_ad"
private const val UPDATE_TRIGGER_SUFFIX = "_au"
private val ROW_ID_ALIASES = listOf("rowid", "oid", "_rowid_")
