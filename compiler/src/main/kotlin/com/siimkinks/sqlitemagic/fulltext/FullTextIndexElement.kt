package com.siimkinks.sqlitemagic.fulltext

import com.siimkinks.sqlitemagic.annotation.FullTextDetail
import com.siimkinks.sqlitemagic.annotation.FullTextDetail.FULL
import com.siimkinks.sqlitemagic.annotation.FullTextMatchinfo
import com.siimkinks.sqlitemagic.annotation.FullTextOrder
import com.siimkinks.sqlitemagic.annotation.FullTextOrder.ASC
import com.siimkinks.sqlitemagic.annotation.FullTextRole
import com.siimkinks.sqlitemagic.annotation.FullTextRole.LANGUAGE_ID
import com.siimkinks.sqlitemagic.annotation.FullTextRole.UNINDEXED
import com.siimkinks.sqlitemagic.annotation.FullTextVersion
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS4
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS5
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaProvider
import com.siimkinks.sqlitemagic.schema.SchemaIdentityOwner
import com.siimkinks.sqlitemagic.tableStructureClassName
import com.siimkinks.sqlitemagic.utils.firstCharToUpperCase
import com.siimkinks.sqlitemagic.utils.generatedFieldName
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
  val version: FullTextVersion,
  val columns: List<FullTextColumnElement>,
  val tokenize: String,
  val prefix: List<Int>,
  val detail: FullTextDetail,
  val columnSize: Boolean,
  val matchinfo: FullTextMatchinfo,
  val order: FullTextOrder
) : SqliteSchemaProvider by identity {
  val name get() = rawName
  val tableClassName get() = tableStructureClassName(fullTextIndexArtifactStem(name))
  val fieldName get() = name.replace(regex = nonIdentifierCharacters, replacement = "_").generatedFieldName()
  val createSql get() = listOf(createTableSql) + createTriggerSql
  val createTriggerSql get() = triggerSqlByName.values.toList()
  val dropTriggerSql get() = triggerSqlByName.keys.map(::dropFullTextTriggerSql)
  val tableColumns get() = columns.filter { it.role != LANGUAGE_ID }
  val languageIdColumn get() = columns.firstOrNull { it.role == LANGUAGE_ID }

  val clearSql
    get() = fullTextCommandSql(
      name = name,
      command = when (version) {
        FTS5 -> "delete-all"
        FTS4 -> "rebuild"
      }
    )

  val shadowTableNames
    get() = fullTextShadowTableNames(
      name = name,
      version = version,
      columnSize = columnSize,
      matchinfo = matchinfo
    )

  val triggerSqlByName
    get() = when (version) {
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

  val createTableSql: String
    get() {
      val options = when (version) {
        FTS5 -> fts5Definitions()
        FTS4 -> fts4Definitions()
      }
      return "CREATE VIRTUAL TABLE IF NOT EXISTS ${mainQualified(name)} USING " +
          "${version.name.lowercase(Locale.ROOT)}(${options.joinToString()})"
    }

  private fun fts5Definitions() = columns.map { column ->
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

  private fun fts4Definitions() = tableColumns.map { quoteSqlIdentifier(it.columnName) } +
      "content=${sqlString(tableName)}" +
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

  private fun trigger(
    suffix: String,
    event: String,
    condition: String? = null,
    statements: List<String>
  ) = name + suffix to "CREATE TRIGGER IF NOT EXISTS ${mainQualified(name + suffix)} $event ON " +
      "${quoteSqlIdentifier(tableName)} " +
      condition?.let { "WHEN $it " }.orEmpty() +
      "BEGIN ${statements.joinToString(separator = " ") { "$it;" }} END"

  private fun updateEvent() =
    "UPDATE OF ${(changeColumnNames().map(::quoteSqlIdentifier) + ROW_ID_ALIASES).joinToString()}"

  private fun insertSql(rowId: String) = "INSERT INTO ${quoteSqlIdentifier(name)}($rowId, ${columnList()}) " +
      "VALUES (${values(row = "new")})"

  private fun fts5DeleteSql() = "INSERT INTO ${quoteSqlIdentifier(name)}(${quoteSqlIdentifier(name)}, rowid, " +
      "${columnList()}) VALUES ('delete', ${values(row = "old")})"

  private fun fts4DeleteSql() = "DELETE FROM ${quoteSqlIdentifier(name)} WHERE docid = " +
      "old.${quoteSqlIdentifier(rowIdColumnName)}"

  private fun columnList() = columns.joinToString { quoteSqlIdentifier(it.columnName) }

  private fun values(row: String) = (listOf(rowIdColumnName) + columns.map(FullTextColumnElement::columnName))
    .joinToString { "$row.${quoteSqlIdentifier(it)}" }

  private fun changeColumnNames() = columns
    .filter { it.role != UNINDEXED }
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

internal fun fullTextIndexArtifactStem(name: String) = name
    .split(nonIdentifierCharacters)
    .joinToString(separator = "", transform = String::firstCharToUpperCase)

internal fun fullTextIndexIdentity(name: String) = SqliteSchemaIdentity(
  schema = MAIN,
  identifier = SqliteIdentifier(name)
)

internal fun fullTextShadowTableNames(
  name: String,
  version: FullTextVersion,
  columnSize: Boolean,
  matchinfo: FullTextMatchinfo
) = when (version) {
  FTS5 -> listOfNotNull("data", "idx", "docsize".takeIf { columnSize }, "config")
  FTS4 -> listOfNotNull("segments", "segdir", "docsize".takeIf { matchinfo == FullTextMatchinfo.FTS4 }, "stat")
}.map { suffix -> "${name}_$suffix" }

internal fun fullTextSchemaOwners(
  name: String,
  shadowTableNames: List<String>,
  typeKey: TypeKey
) = shadowTableNames
    .map { "full-text shadow table" to it }
    .let { shadowTables -> listOf("full-text index" to name) + shadowTables }
    .map { (kind, objectName) ->
      SchemaIdentityOwner(
        kind = kind,
        rawName = objectName,
        identity = fullTextIndexIdentity(objectName),
        typeKey = typeKey
      )
    }

internal fun dropFullTextTriggerSql(triggerName: String) = "DROP TRIGGER IF EXISTS ${mainQualified(triggerName)}"

internal fun dropFullTextTableSql(name: String) = "DROP TABLE IF EXISTS ${mainQualified(name)}"

internal fun fullTextCommandSql(
  name: String,
  command: String
) = "INSERT INTO ${mainQualified(name)}(${quoteSqlIdentifier(name)}) VALUES (${sqlString(command)})"

private fun mainQualified(objectName: String) = "${MAIN.qualifier}.${quoteSqlIdentifier(objectName)}"

private fun sqlString(value: String) = "'${value.replace(oldValue = "'", newValue = "''")}'"

private const val INSERT_TRIGGER_SUFFIX = "_ai"
private const val DELETE_TRIGGER_SUFFIX = "_ad"
private const val UPDATE_TRIGGER_SUFFIX = "_au"
private const val BEFORE_DELETE_TRIGGER_SUFFIX = "_bd"
private const val BEFORE_UPDATE_TRIGGER_SUFFIX = "_bu"
private val ROW_ID_ALIASES = listOf("rowid", "oid", "_rowid_")
private val nonIdentifierCharacters = Regex("""[^\p{L}\p{N}]+""")
