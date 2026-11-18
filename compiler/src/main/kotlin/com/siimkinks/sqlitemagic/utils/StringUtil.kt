package com.siimkinks.sqlitemagic.utils

import com.siimkinks.sqlitemagic.internal.SqliteSchema
import java.util.Locale

internal fun String.firstCharToUpperCase() = replaceFirstChar {
  when {
    it.isLowerCase() -> it.titlecase(Locale.ROOT)
    else -> it.toString()
  }
}

internal fun String.camelCaseToSnakeCase() = when {
  isEmpty() -> this
  else -> buildString(length + 4) {
    append(this@camelCaseToSnakeCase[0])
    for (character in this@camelCaseToSnakeCase.drop(1)) {
      when {
        character.isUpperCase() -> {
          append('_')
          append(character.lowercaseChar())
        }
        else -> append(character)
      }
    }
  }
}

internal fun String.generatedFieldName() = camelCaseToSnakeCase()
  .uppercase()
  .replace(oldChar = '.', newChar = '_')

internal fun quoteSqlIdentifier(identifier: String) =
    """"${identifier.replace(oldValue = "\"", newValue = "\"\"")}""""

internal fun SqliteSchema.qualifiedSqlIdentifier(identifier: String) = "$qualifier.${quoteSqlIdentifier(identifier)}"

internal fun quoteSqlLiteral(value: String) = "'${value.replace(oldValue = "'", newValue = "''")}'"
