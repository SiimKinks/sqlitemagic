package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Defines a view corresponding to a [SQLite view](https://www.sqlite.org/lang_createview.html).
 *
 * Supported view models are Kotlin data classes and mutable classes that can be constructed and
 * populated by generated code.
 *
 * Each view must declare exactly one query of type `CompiledSelect` annotated with [ViewQuery].
 * In Kotlin, declare the query as a non-private companion-object property. `@JvmField` is not
 * required.
 *
 * ```kotlin
 * @View
 * data class AuthorView(
 *   @ViewColumn("author.name")
 *   val name: String
 * ) {
 *   companion object {
 *     @ViewQuery
 *     val QUERY = (SELECT COLUMN AUTHOR.NAME FROM AUTHOR).compile()
 *   }
 * }
 * ```
 *
 * Each view must declare [ViewColumn] mappings from the view query to its columns.
 *
 * @property value View name. Defaults to the lower-cased class name with camel case replaced by `_`.
 */
@Target(CLASS)
@Retention(BINARY)
annotation class View(
  val value: String = ""
)
