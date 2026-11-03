package com.siimkinks.sqlitemagic.manager

import com.siimkinks.sqlitemagic.SqlAffinity.INTEGER
import com.siimkinks.sqlitemagic.internal.SqliteIdentifier
import com.siimkinks.sqlitemagic.utils.quoteSqlIdentifier

private val unsupportedAppendConstraintPattern = Regex(
  """(?i)\b(PRIMARY\s+KEY|UNIQUE|REFERENCES)\b"""
)
private val nonConstantDefaultPattern = Regex(
  """(?i)\bDEFAULT\s+(?:CURRENT_TIME|CURRENT_DATE|CURRENT_TIMESTAMP|\()"""
)
private val withoutRowIdPattern = Regex("""(?i)\bWITHOUT\s+ROWID\b""")
private val bareReferencePattern = Regex("""(?i)\bREFERENCES\s+([\w$]+)\s*\(\s*([\w$]+)\s*\)""")
private val tableSchemaPrefixes = listOf("CREATE TABLE IF NOT EXISTS", "CREATE TEMPORARY TABLE IF NOT EXISTS")
private const val normalizedTableName = "__TABLE__"

private data class SqlIdentifierReplacement(
  val identifier: String,
  val replacement: String
)

internal fun normalizeSql(
  schema: String,
  ownTableName: String?,
  renames: Map<String, String>
): String {
  val replacements = buildList {
    ownTableName
      ?.takeIf(String::isNotEmpty)
      ?.let { tableName ->
        add(
          SqlIdentifierReplacement(
            identifier = tableName,
            replacement = normalizedTableName
          )
        )
      }
    renames.forEach { (identifier, replacement) ->
      if (identifier.isNotEmpty()) {
        add(
          SqlIdentifierReplacement(
            identifier = identifier,
            replacement = replacement
          )
        )
      }
    }
  }

  return when {
    replacements.isEmpty() -> schema.normalizeSqlWhitespace()
    schema.canNormalizeSqlInSinglePass(replacements = replacements) -> normalizeSqlSinglePass(
      schema = schema,
      replacements = replacements
    )
    else -> normalizeSqlSequentially(
      schema = schema,
      replacements = replacements
    )
  }
}

private fun normalizeSqlSequentially(
  schema: String,
  replacements: List<SqlIdentifierReplacement>
): String {
  var normalized = schema
  replacements.forEach { replacement ->
    normalized = normalized.replaceSqlIdentifier(
      identifier = replacement.identifier,
      replacement = replacement.replacement
    )
  }
  return normalized.normalizeSqlWhitespace()
}

private fun normalizeSqlSinglePass(
  schema: String,
  replacements: List<SqlIdentifierReplacement>
): String {
  val result = StringBuilder(schema.length)
  var whitespacePending = false
  var index = 0
  while (index < schema.length) {
    val character = schema[index]
    if (character.isWhitespace()) {
      whitespacePending = true
      index++
      continue
    }
    if (whitespacePending && result.isNotEmpty()) {
      result.append(' ')
    }
    whitespacePending = false
    when {
      character == '\'' -> {
        val segmentEnd = schema.quotedSqlSegmentEnd(
          startIndex = index,
          closing = character
        ) ?: schema.length
        result.append(
          schema.substring(
            startIndex = index,
            endIndex = segmentEnd
          )
        )
        index = segmentEnd
      }
      character.isSqlIdentifierPart() -> {
        val tokenStart = index
        while (index < schema.length && schema[index].isSqlIdentifierPart()) {
          index++
        }
        val token = schema.substring(
          startIndex = tokenStart,
          endIndex = index
        )
        result.append(
          token.applySqlIdentifierReplacements(
            replacements = replacements
          )
        )
      }
      else -> {
        result.append(character)
        index++
      }
    }
  }
  return result.toString().trim()
}

private fun String.canNormalizeSqlInSinglePass(
  replacements: List<SqlIdentifierReplacement>
): Boolean = none { character ->
  character == '"' || character == '`' || character == '['
} && replacements.all { replacement ->
  replacement.identifier.isSimpleSqlIdentifier() &&
      replacement.replacement.isSimpleSqlIdentifier()
}

private fun String.isSimpleSqlIdentifier() = isNotEmpty() && all(Char::isSqlIdentifierPart)

private fun String.applySqlIdentifierReplacements(
  replacements: List<SqlIdentifierReplacement>
): String {
  var normalized = this
  replacements.forEach { replacement ->
    if (normalized.equals(other = replacement.identifier, ignoreCase = true)) {
      normalized = replacement.replacement
    }
  }
  return normalized
}

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

internal fun DatabaseStructure.withQuotedIdentifiers() = copy(
  tables = tables.mapValues { (_, table) -> table.withQuotedIdentifiers() },
  temporaryTables = temporaryTables.mapValues { (_, table) -> table.withQuotedIdentifiers() }
)

private fun TableStructure.withQuotedIdentifiers(): TableStructure {
  val bareDefinition = " $name (${columns.joinToString(separator = ", ", transform = ColumnStructure::schema)})"
  val prefix = tableSchemaPrefixes.firstOrNull { schema.startsWith(prefix = "$it$bareDefinition") } ?: return this
  val quotedColumns = columns.map(ColumnStructure::withQuotedIdentifiers)
  return copy(
    schema = buildString {
      append(prefix)
      append(' ')
      append(quoteSqlIdentifier(name))
      append(" (")
      quotedColumns.joinTo(buffer = this, separator = ", ", transform = ColumnStructure::schema)
      append(')')
      append(schema, prefix.length + bareDefinition.length, schema.length)
    },
    columns = quotedColumns
  )
}

private fun ColumnStructure.withQuotedIdentifiers() = when {
  schema.startsWith(prefix = "$name ") -> copy(
    schema = quoteSqlIdentifier(name) + schema
      .substring(startIndex = name.length)
      .withQuotedBareReferences()
  )
  else -> this
}

private fun String.withQuotedBareReferences(): String {
  val result = StringBuilder(length)
  var copiedUntil = 0
  bareReferencePattern
    .findAll(withoutQuotedSqlSegments())
    .forEach { match ->
      match.groups
        .drop(1)
        .filterNotNull()
        .forEach { identifier ->
          result.append(this, copiedUntil, identifier.range.first)
          result.append(quoteSqlIdentifier(identifier.value))
          copiedUntil = identifier.range.last + 1
        }
    }
  return result
    .append(this, copiedUntil, length)
    .toString()
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

private fun String.normalizeSqlWhitespace(): String {
  val result = StringBuilder(length)
  var whitespacePending = false
  var index = 0
  while (index < length) {
    val character = this[index]
    if (character.isWhitespace()) {
      whitespacePending = true
      index++
      continue
    }
    if (whitespacePending && result.isNotEmpty()) {
      result.append(' ')
    }
    whitespacePending = false
    when (val closing = character.sqlClosingQuote()) {
      null -> {
        result.append(character)
        index++
      }
      else -> index = appendQuotedSqlSegment(
        result = result,
        startIndex = index,
        closing = closing
      )
    }
  }
  return result.toString().trim()
}

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

private fun String.appendQuotedSqlSegment(
  result: StringBuilder,
  startIndex: Int,
  closing: Char
): Int {
  val segmentEnd = quotedSqlSegmentEnd(
    startIndex = startIndex,
    closing = closing
  ) ?: length
  result.append(
    substring(
      startIndex = startIndex,
      endIndex = segmentEnd
    )
  )
  return segmentEnd
}

private fun String.replaceSqlIdentifier(
  identifier: String,
  replacement: String
): String {
  if (identifier.isEmpty()) return this
  val result = StringBuilder(length)
  var index = 0
  while (index < length) {
    when {
      this[index] == '\'' -> {
        val literalEnd = quotedSqlSegmentEnd(
          startIndex = index,
          closing = '\''
        ) ?: length
        result.append(substring(startIndex = index, endIndex = literalEnd))
        index = literalEnd
      }
      this[index] == '`' || this[index] == '"' || this[index] == '[' -> {
        val opening = this[index]
        val closing = when (opening) {
          '[' -> ']'
          else -> opening
        }
        val segmentEnd = quotedSqlSegmentEnd(
          startIndex = index,
          closing = closing
        )
        when (segmentEnd) {
          null -> {
            result.append(substring(startIndex = index))
            index = length
          }
          else -> {
            val quotedIdentifier = substring(
              startIndex = index + 1,
              endIndex = segmentEnd - 1
            )
            result.append(
              when {
                quotedIdentifier.equals(other = identifier, ignoreCase = true) -> replacement
                else -> substring(startIndex = index, endIndex = segmentEnd)
              }
            )
            index = segmentEnd
          }
        }
      }
      substringMatchesIdentifier(
        identifier = identifier,
        startIndex = index
      ) -> {
        result.append(replacement)
        index += identifier.length
      }
      else -> {
        result.append(this[index])
        index++
      }
    }
  }
  return result.toString()
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
