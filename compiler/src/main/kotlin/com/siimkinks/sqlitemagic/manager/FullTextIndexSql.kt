package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.annotation.FullTextDetail.FULL
import com.siimkinks.sqlitemagic.annotation.FullTextMatchinfo
import com.siimkinks.sqlitemagic.annotation.FullTextOrder.ASC
import com.siimkinks.sqlitemagic.annotation.FullTextRole.UNINDEXED
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS4
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS5
import com.siimkinks.sqlitemagic.fulltext.FullTextColumnElement
import com.siimkinks.sqlitemagic.fulltext.FullTextIndexElement
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.utils.qualifiedSqlIdentifier
import com.siimkinks.sqlitemagic.utils.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.utils.quoteSqlLiteral
import java.util.Locale

internal fun FullTextIndexElement.createSql() = listOf(createTableSql()) + createTriggerSql()

internal fun FullTextIndexElement.createTriggerSql() = triggerSqlByName().values.toList()

internal fun FullTextIndexElement.dropTriggerSql() = triggerSqlByName().keys.map(::dropFullTextTriggerSql)

internal fun FullTextIndexElement.clearSql() = fullTextCommandSql(
  name = name,
  command = when (version) {
    FTS5 -> "delete-all"
    FTS4 -> "rebuild"
  }
)

internal fun FullTextIndexElement.createTableSql(): String {
  val options = when (version) {
    FTS5 -> fts5Definitions()
    FTS4 -> fts4Definitions()
  }
  return "CREATE VIRTUAL TABLE IF NOT EXISTS ${MAIN.qualifiedSqlIdentifier(name)} USING " +
      "${version.name.lowercase(Locale.ROOT)}(${options.joinToString()})"
}

internal fun FullTextIndexElement.triggerSqlByName() = when (version) {
  FTS5 -> mapOf(
    trigger(
      suffix = INSERT_TRIGGER_SUFFIX,
      event = "AFTER INSERT",
      statements = listOf(insertSql(rowId = "rowid"))
    ),
    trigger(
      suffix = DELETE_TRIGGER_SUFFIX,
      event = "AFTER DELETE",
      statements = listOf(fts5DeleteSql())
    ),
    trigger(
      suffix = UPDATE_TRIGGER_SUFFIX,
      event = "AFTER ${updateEvent()}",
      condition = changedCondition(),
      statements = listOf(fts5DeleteSql(), insertSql(rowId = "rowid"))
    )
  )
  FTS4 -> mapOf(
    trigger(
      suffix = INSERT_TRIGGER_SUFFIX,
      event = "AFTER INSERT",
      statements = listOf(insertSql(rowId = "docid"))
    ),
    trigger(
      suffix = BEFORE_DELETE_TRIGGER_SUFFIX,
      event = "BEFORE DELETE",
      statements = listOf(fts4DeleteSql())
    ),
    trigger(
      suffix = BEFORE_UPDATE_TRIGGER_SUFFIX,
      event = "BEFORE ${updateEvent()}",
      condition = changedCondition(),
      statements = listOf(fts4DeleteSql())
    ),
    trigger(
      suffix = UPDATE_TRIGGER_SUFFIX,
      event = "AFTER ${updateEvent()}",
      condition = changedCondition(),
      statements = listOf(insertSql(rowId = "docid"))
    )
  )
}

internal fun dropFullTextTriggerSql(triggerName: String) =
  "DROP TRIGGER IF EXISTS ${MAIN.qualifiedSqlIdentifier(triggerName)}"

internal fun dropFullTextTableSql(name: String) = "DROP TABLE IF EXISTS ${MAIN.qualifiedSqlIdentifier(name)}"

internal fun fullTextCommandSql(
  name: String,
  command: String
) = "INSERT INTO ${MAIN.qualifiedSqlIdentifier(name)}(${quoteSqlIdentifier(name)}) VALUES (${quoteSqlLiteral(command)})"

private fun FullTextIndexElement.fts5Definitions() = columns.map { column ->
  when (column.role) {
    UNINDEXED -> "${quoteSqlIdentifier(column.columnName)} UNINDEXED"
    else -> quoteSqlIdentifier(column.columnName)
  }
} + listOfNotNull(
  "content=${quoteSqlLiteral(tableName)}",
  "content_rowid=${quoteSqlLiteral(rowIdColumnName)}",
  tokenize
    .takeIf(String::isNotEmpty)
    ?.let { "tokenize=${quoteSqlLiteral(it)}" },
  prefix
    .takeIf(List<Int>::isNotEmpty)
    ?.let { "prefix=${quoteSqlLiteral(it.joinToString(separator = " "))}" },
  "detail=${detail.name.lowercase(Locale.ROOT)}".takeIf { detail != FULL },
  "columnsize=0".takeUnless { columnSize }
)

private fun FullTextIndexElement.fts4Definitions() = tableColumns.map { quoteSqlIdentifier(it.columnName) } +
    "content=${quoteSqlLiteral(tableName)}" +
    listOfNotNull(languageIdColumn?.let { "languageid=${quoteSqlIdentifier(it.columnName)}" }) +
    tableColumns
      .filter { it.role == UNINDEXED }
      .map { "notindexed=${quoteSqlIdentifier(it.columnName)}" } +
    listOfNotNull(
      "matchinfo=fts3".takeIf { matchinfo == FullTextMatchinfo.FTS3 },
      "order=DESC".takeIf { order != ASC },
      tokenize
        .takeIf(String::isNotEmpty)
        ?.let { "tokenize=$it" },
      prefix
        .takeIf(List<Int>::isNotEmpty)
        ?.let { """prefix="${it.joinToString(separator = ",")}"""" }
    )

private fun FullTextIndexElement.trigger(
  suffix: String,
  event: String,
  condition: String? = null,
  statements: List<String>
) = name + suffix to "CREATE TRIGGER IF NOT EXISTS ${MAIN.qualifiedSqlIdentifier(name + suffix)} $event ON " +
    "${quoteSqlIdentifier(tableName)} " +
    condition?.let { "WHEN $it " }.orEmpty() +
    "BEGIN ${statements.joinToString(separator = " ") { "$it;" }} END"

private fun FullTextIndexElement.updateEvent() =
  "UPDATE OF ${(changeColumnNames().map(::quoteSqlIdentifier) + ROW_ID_PSEUDO_COLUMNS).joinToString()}"

private fun FullTextIndexElement.insertSql(rowId: String) =
  "INSERT INTO ${quoteSqlIdentifier(name)}($rowId, ${columnList()}) VALUES (${values(row = "new")})"

private fun FullTextIndexElement.fts5DeleteSql() = "INSERT INTO ${quoteSqlIdentifier(name)}" +
    "(${quoteSqlIdentifier(name)}, rowid, ${columnList()}) VALUES ('delete', ${values(row = "old")})"

private fun FullTextIndexElement.fts4DeleteSql() =
  "DELETE FROM ${quoteSqlIdentifier(name)} WHERE docid = old.${quoteSqlIdentifier(rowIdColumnName)}"

private fun FullTextIndexElement.columnList() = columns.joinToString { quoteSqlIdentifier(it.columnName) }

private fun FullTextIndexElement.values(row: String) = listOf(rowIdColumnName)
  .plus(columns.map(FullTextColumnElement::columnName))
  .joinToString { "$row.${quoteSqlIdentifier(it)}" }

private fun FullTextIndexElement.changeColumnNames() = columns
  .filter { it.role != UNINDEXED }
  .map(FullTextColumnElement::columnName)
  .let { listOf(rowIdColumnName) + it }
  .distinct()

private fun FullTextIndexElement.changedCondition() = changeColumnNames().joinToString(separator = " OR ") {
  "old.${quoteSqlIdentifier(it)} IS NOT new.${quoteSqlIdentifier(it)}"
}

private const val INSERT_TRIGGER_SUFFIX = "_ai"
private const val DELETE_TRIGGER_SUFFIX = "_ad"
private const val UPDATE_TRIGGER_SUFFIX = "_au"
private const val BEFORE_DELETE_TRIGGER_SUFFIX = "_bd"
private const val BEFORE_UPDATE_TRIGGER_SUFFIX = "_bu"
