package com.siimkinks.sqlitemagic.internal

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.CONSTRUCTOR
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY

/** Marks the runtime API that is used only by SqliteMagic generated code. It may change in any release. */
@RequiresOptIn(
  message = "This is SqliteMagic API for generated code; it may change in any release.",
  level = RequiresOptIn.Level.ERROR
)
@Retention(BINARY)
@Target(CLASS, CONSTRUCTOR, FUNCTION, PROPERTY)
annotation class InternalSqliteMagicApi
