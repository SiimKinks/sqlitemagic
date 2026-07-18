package com.siimkinks.sqlitemagic.utils

import java.util.Locale

fun String.firstCharToUpperCase() = replaceFirstChar {
  when {
    it.isLowerCase() -> it.titlecase(Locale.getDefault())
    else -> it.toString()
  }
}

fun String.camelCaseToSnakeCase() = when {
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
