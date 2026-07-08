package com.siimkinks.sqlitemagic.utils

import java.util.Locale

fun String.firstCharToUpperCase() = replaceFirstChar {
  when {
    it.isLowerCase() -> it.titlecase(Locale.getDefault())
    else -> it.toString()
  }
}
