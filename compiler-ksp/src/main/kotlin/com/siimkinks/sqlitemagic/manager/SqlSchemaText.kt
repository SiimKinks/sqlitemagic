package com.siimkinks.sqlitemagic.manager

import java.util.Locale

private val unsupportedAppendConstraintPattern = Regex(
  """(?i)\b(PRIMARY\s+KEY|UNIQUE|REFERENCES)\b"""
)
private val nonConstantDefaultPattern = Regex(
  """(?i)\bDEFAULT\s+(?:CURRENT_TIME|CURRENT_DATE|CURRENT_TIMESTAMP|\()"""
)
private val withoutRowIdPattern = Regex("""(?i)\bWITHOUT\s+ROWID\b""")
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

  if (replacements.isEmpty()) {
    return schema.normalizeSqlWhitespace()
  }
  return when {
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
            0 -> substring(startIndex = 0, endIndex = columnsStart) +
                "(__COLUMNS__)" +
                substring(startIndex = index)
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

internal fun ColumnStructure.requiresValueDuringRebuild(table: TableStructure) =
  id && withoutRowIdPattern.containsMatchIn(table.schema.withoutQuotedSqlSegments())

internal fun ColumnStructure.canBeAddedWithAlterTable(): Boolean {
  if (id || autoIncrement || onDeleteCascade) return false
  val schemaWithoutQuotedSegments = schema.withoutQuotedSqlSegments()
  if (unsupportedAppendConstraintPattern.containsMatchIn(schemaWithoutQuotedSegments)) return false
  if (nonConstantDefaultPattern.containsMatchIn(schemaWithoutQuotedSegments)) return false
  return true
}

internal fun TableStructure.normalizedReferencedTableNames() = schema
  .normalizedReferencedTableNames()

internal fun String.normalizedSqlIdentifier() = lowercase(Locale.ROOT)

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
        val closing = if (opening == '[') ']' else opening
        val segmentEnd = quotedSqlSegmentEnd(
          startIndex = index,
          closing = closing
        )
        if (segmentEnd == null) {
          result.append(substring(startIndex = index))
          index = length
        } else {
          val quotedIdentifier = substring(
            startIndex = index + 1,
            endIndex = segmentEnd - 1
          )
          if (quotedIdentifier.equals(other = identifier, ignoreCase = true)) {
            result.append(replacement)
          } else {
            result.append(substring(startIndex = index, endIndex = segmentEnd))
          }
          index = segmentEnd
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
  if (
    startIndex > 0 && this[startIndex - 1].isSqlIdentifierPart() ||
    endIndex < length && this[endIndex].isSqlIdentifierPart()
  ) {
    return false
  }
  return endIndex <= length &&
      regionMatches(
        thisOffset = startIndex,
        other = identifier,
        otherOffset = 0,
        length = identifier.length,
        ignoreCase = true
      )
}

private fun Char.isSqlIdentifierPart() = isLetterOrDigit() || this == '_' || this == '$'

private fun String.normalizedReferencedTableNames(): Set<String> {
  val result = linkedSetOf<String>()
  var index = 0
  while (index < length) {
    val closing = this[index].sqlClosingQuote()
    if (closing != null) {
      index = quotedSqlSegmentEnd(
        startIndex = index,
        closing = closing
      ) ?: length
      continue
    }
    if (!substringMatchesIdentifier(identifier = "REFERENCES", startIndex = index)) {
      index++
      continue
    }

    val keywordEnd = index + "REFERENCES".length
    if (keywordEnd == length || !this[keywordEnd].isWhitespace()) {
      index = keywordEnd
      continue
    }
    index = keywordEnd
    while (index < length && this[index].isWhitespace()) {
      index++
    }
    if (index == length) break

    val identifier = when (val opening = this[index]) {
      '"', '`', '[' -> {
        val identifierClosing = if (opening == '[') ']' else opening
        val segmentEnd = quotedSqlSegmentEnd(
          startIndex = index,
          closing = identifierClosing
        )
        if (segmentEnd == null) {
          index = length
          null
        } else {
          val value = substring(
            startIndex = index + 1,
            endIndex = segmentEnd - 1
          )
          index = segmentEnd
          value
        }
      }
      else -> {
        val identifierStart = index
        while (index < length && !this[index].isWhitespace() && this[index] != '(') {
          index++
        }
        substring(startIndex = identifierStart, endIndex = index)
      }
    }
    while (index < length && this[index].isWhitespace()) {
      index++
    }
    if (identifier?.isNotEmpty() == true && index < length && this[index] == '(') {
      result += identifier.normalizedSqlIdentifier()
    }
  }
  return result
}

private fun Char.sqlClosingQuote() = when (this) {
  '\'', '"', '`' -> this
  '[' -> ']'
  else -> null
}
