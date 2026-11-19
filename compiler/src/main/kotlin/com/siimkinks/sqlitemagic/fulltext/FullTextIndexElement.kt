package com.siimkinks.sqlitemagic.fulltext

import com.siimkinks.sqlitemagic.annotation.FullTextDetail
import com.siimkinks.sqlitemagic.annotation.FullTextMatchinfo
import com.siimkinks.sqlitemagic.annotation.FullTextOrder
import com.siimkinks.sqlitemagic.annotation.FullTextRole
import com.siimkinks.sqlitemagic.annotation.FullTextRole.LANGUAGE_ID
import com.siimkinks.sqlitemagic.annotation.FullTextVersion
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS4
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS5
import com.siimkinks.sqlitemagic.element.TypeKey
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.SqliteSchemaProvider
import com.siimkinks.sqlitemagic.model.TableSeed
import com.siimkinks.sqlitemagic.schema.SchemaIdentityOwner
import com.siimkinks.sqlitemagic.tableStructureClassName
import com.siimkinks.sqlitemagic.utils.firstCharToUpperCase
import com.siimkinks.sqlitemagic.utils.generatedFieldName

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
  val tableColumns get() = columns.filter { it.role != LANGUAGE_ID }
  val languageIdColumn get() = columns.firstOrNull { it.role == LANGUAGE_ID }

  val shadowTableNames
    get() = fullTextShadowTableNames(
      name = name,
      version = version,
      columnSize = columnSize,
      matchinfo = matchinfo
    )
}

internal fun TableSeed.fullTextIndexName(value: String) = value.ifEmpty { "${tableName}_fts" }

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
  FTS4 -> listOfNotNull(
    "content",
    "segments",
    "segdir",
    "docsize".takeIf { matchinfo == FullTextMatchinfo.FTS4 },
    "stat"
  )
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

private val nonIdentifierCharacters = Regex("""[^\p{L}\p{N}]+""")
