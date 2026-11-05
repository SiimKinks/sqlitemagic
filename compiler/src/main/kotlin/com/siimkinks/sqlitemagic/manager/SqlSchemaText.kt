package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.SqlAffinity.INTEGER
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.migration.SchemaSqlComparison

private val unsupportedAppendConstraintPattern = Regex(
  """(?i)\b(PRIMARY\s+KEY|UNIQUE|REFERENCES)\b"""
)
private val nonConstantDefaultPattern = Regex(
  """(?i)\bDEFAULT\s+(?:CURRENT_TIME|CURRENT_DATE|CURRENT_TIMESTAMP|\()"""
)
private val withoutRowIdPattern = Regex("""(?i)\bWITHOUT\s+ROWID\b""")
private const val normalizedTableName = "__TABLE__"

internal fun normalizeSql(
  schema: String,
  ownTableName: String?,
  renames: Map<String, String>
) = SchemaSqlComparison.key(
  sql = schema,
  identifiers = buildMap {
    putAll(renames.filterKeys(String::isNotEmpty))
    ownTableName
      ?.takeIf(String::isNotEmpty)
      ?.let { put(it, normalizedTableName) }
  }
)

internal fun DatabaseStructure.sameSchemaAs(other: DatabaseStructure) =
  tables.sameSchemaAs(other = other.tables, same = TableStructure::sameSchemaAs) &&
      indices.sameSchemaAs(other = other.indices, same = IndexStructure::sameSchemaAs) &&
      temporaryTables.sameSchemaAs(other = other.temporaryTables, same = TableStructure::sameSchemaAs) &&
      temporaryIndices.sameSchemaAs(other = other.temporaryIndices, same = IndexStructure::sameSchemaAs) &&
      views == other.views &&
      temporaryViews == other.temporaryViews

internal fun TableStructure.sameSchemaAs(other: TableStructure) = name == other.name &&
    columns.size == other.columns.size &&
    (columns zip other.columns).all { (column, otherColumn) -> column.sameSchemaAs(otherColumn) } &&
    schema.sameSqlAs(other.schema)

internal fun ColumnStructure.sameSchemaAs(other: ColumnStructure) = copy(
  schema = other.schema,
  transformedImplicitDefault = other.transformedImplicitDefault
) == other &&
    schema.sameSqlAs(other.schema)

internal fun IndexStructure.sameSchemaAs(other: IndexStructure) = copy(indexSql = other.indexSql) == other &&
    indexSql.sameSqlAs(other.indexSql)

private fun <T> Map<String, T>.sameSchemaAs(
  other: Map<String, T>,
  same: (T, T) -> Boolean
) = keys == other.keys && all { (name, value) -> same(value, other.getValue(name)) }

private fun String.sameSqlAs(other: String) = this == other ||
    SchemaSqlComparison.key(this) == SchemaSqlComparison.key(other)

internal fun String.withoutTableColumns(): String {
  var index = 0
  while (index < length) {
    when (val closing = this[index].sqlClosingQuote()) {
      null -> when (this[index]) {
        '(' -> {
          val columnsStart = index
          var depth = 1
          index++
          while (index < length && depth > 0) {
            when (val nestedClosing = this[index].sqlClosingQuote()) {
              null -> when (this[index]) {
                '(' -> {
                  depth++
                  index++
                }
                ')' -> {
                  depth--
                  index++
                }
                else -> index++
              }
              else -> index = quotedSqlSegmentEnd(
                startIndex = index,
                closing = nestedClosing
              ) ?: length
            }
          }
          return when (depth) {
            0 -> "${substring(startIndex = 0, endIndex = columnsStart)}(__COLUMNS__)${substring(startIndex = index)}"
            else -> this
          }
        }
        else -> index++
      }
      else -> index = quotedSqlSegmentEnd(
        startIndex = index,
        closing = closing
      ) ?: length
    }
  }
  return this
}

internal fun ColumnStructure.requiresValueDuringRebuild(table: TableStructure) = requiresValue ||
    (id && table.isWithoutRowId)

internal fun ColumnStructure.isNotNullIn(table: TableStructure) = notNull ||
    (id && (sqlType == INTEGER.name || table.isWithoutRowId))

private val TableStructure.isWithoutRowId
  get() = withoutRowIdPattern.containsMatchIn(schema.withoutQuotedSqlSegments())

internal fun ColumnStructure.canBeAddedWithAlterTable(): Boolean {
  if (id || autoIncrement || onDeleteCascade || requiresValue) return false
  val schemaWithoutQuotedSegments = schema.withoutQuotedSqlSegments()
  if (unsupportedAppendConstraintPattern.containsMatchIn(schemaWithoutQuotedSegments)) return false
  if (nonConstantDefaultPattern.containsMatchIn(schemaWithoutQuotedSegments)) return false
  return true
}

internal data class ColumnReference(
  val table: String,
  val column: String
) {
  fun normalized() = ColumnReference(
    table = table.normalizedSqlIdentifier(),
    column = column.normalizedSqlIdentifier()
  )
}

internal fun ColumnStructure.cascadeReference() = when {
  onDeleteCascade -> schema
    .columnReferences()
    .firstOrNull()
  else -> null
}

internal fun TableStructure.normalizedReferencedTableNames() = schema
  .columnReferences()
  .mapTo(destination = linkedSetOf()) { it.table.normalizedSqlIdentifier() }

internal fun String.normalizedSqlIdentifier() = SqliteIdentifier(this).normalizedName

private fun String.withoutQuotedSqlSegments(): String {
  val result = StringBuilder(length)
  var index = 0
  while (index < length) {
    val closing = this[index].sqlClosingQuote()
    if (closing == null) {
      result.append(this[index])
      index++
      continue
    }
    val segmentEnd = quotedSqlSegmentEnd(
      startIndex = index,
      closing = closing
    ) ?: length
    while (index < segmentEnd) {
      result.append(' ')
      index++
    }
  }
  return result.toString()
}

private fun String.quotedSqlSegmentEnd(
  startIndex: Int,
  closing: Char
): Int? {
  var index = startIndex + 1
  while (index < length) {
    when (closing) {
      this[index] -> when {
        closing != ']' && index + 1 < length && this[index + 1] == closing -> index += 2
        else -> return index + 1
      }
      else -> index++
    }
  }
  return null
}

private fun String.substringMatchesIdentifier(
  identifier: String,
  startIndex: Int
): Boolean {
  val endIndex = startIndex + identifier.length
  return !(startIndex > 0 && this[startIndex - 1].isSqlIdentifierPart() ||
      endIndex < length && this[endIndex].isSqlIdentifierPart()) &&
      endIndex <= length &&
      regionMatches(
        thisOffset = startIndex,
        other = identifier,
        otherOffset = 0,
        length = identifier.length,
        ignoreCase = true
      )
}

private fun Char.isSqlIdentifierPart() = isLetterOrDigit() || this == '_' || this == '$'

private fun String.columnReferences(): List<ColumnReference> {
  val result = arrayListOf<ColumnReference>()
  var index = 0
  while (index < length) {
    val closing = this[index].sqlClosingQuote()
    when {
      closing != null -> index = quotedSqlSegmentEnd(
        startIndex = index,
        closing = closing
      ) ?: length
      !substringMatchesIdentifier(identifier = "REFERENCES", startIndex = index) -> index++
      else -> {
        index += "REFERENCES".length
        val table = sqlIdentifierAt(skipSqlWhitespace(index)) ?: continue
        index = skipSqlWhitespace(table.end)
        if (index < length && this[index] == '(') {
          val column = sqlIdentifierAt(skipSqlWhitespace(index + 1)) ?: continue
          result += ColumnReference(
            table = table.name,
            column = column.name
          )
          index = column.end
        }
      }
    }
  }
  return result
}

private class SqlIdentifierToken(
  val name: String,
  val end: Int
)

private fun String.sqlIdentifierAt(startIndex: Int): SqlIdentifierToken? {
  if (startIndex >= length) return null
  return when (val closing = this[startIndex].sqlClosingQuote()) {
    null -> {
      var end = startIndex
      while (end < length && this[end].isSqlIdentifierPart()) {
        end++
      }
      when (end) {
        startIndex -> null
        else -> SqlIdentifierToken(
          name = substring(startIndex = startIndex, endIndex = end),
          end = end
        )
      }
    }
    '\'' -> null
    else -> quotedSqlSegmentEnd(
      startIndex = startIndex,
      closing = closing
    )?.let { end ->
      val quoted = substring(startIndex = startIndex + 1, endIndex = end - 1)
      SqlIdentifierToken(
        name = when (closing) {
          ']' -> quoted
          else -> quoted.replace(oldValue = "$closing$closing", newValue = "$closing")
        },
        end = end
      )
    }
  }
}

private fun String.skipSqlWhitespace(startIndex: Int): Int {
  var index = startIndex
  while (index < length && this[index].isWhitespace()) {
    index++
  }
  return index
}

private fun Char.sqlClosingQuote() = when (this) {
  '\'', '"', '`' -> this
  '[' -> ']'
  else -> null
}
