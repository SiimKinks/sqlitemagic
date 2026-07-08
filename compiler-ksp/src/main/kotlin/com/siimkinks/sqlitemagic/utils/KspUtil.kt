package com.siimkinks.sqlitemagic.utils

import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.symbol.KSAnnotated

inline fun <reified T : Annotation> KSAnnotated.findAnnotationWithType(): T? =
  getAnnotationsByType(T::class)
    .firstOrNull()
