package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS

/**
 * Marks a class as a table in the SQLite database.
 *
 * Supported table models are Kotlin data classes and mutable classes whose persisted properties
 * can be read and written by generated code and which can be constructed through a supported
 * model construction path.
 *
 * An [Id] column is optional. When present, it may use any otherwise supported persisted type.
 * When absent, no implicit ID column is generated.
 *
 * A table without an ID supports insert operations and explicit update or delete statements whose
 * affected rows are selected by the caller. When it has a non-null single-column [Unique]
 * property, identity-dependent entity operations are also available but require the generated
 * unique-column property as an argument to `execute` or `observe`. A nullable unique column or
 * composite unique index cannot serve as that operation key. Single-entity persist reports
 * whether it inserted a row, including SQLite's generated row ID, or updated an existing row.
 *
 * @property value Table name. Defaults to the lower-cased class name with camel case replaced by `_`.
 *
 * @property persistAll Whether all possible properties should be persisted as columns using
 *     [Column] defaults. A property's [Column] annotation overrides those defaults, while
 *     [IgnoreColumn] excludes the property.
 *
 * @property options SQLite table definition options. See [TableOption].
 */
@Target(CLASS)
@Retention(BINARY)
annotation class Table(
  val value: String = "",
  val persistAll: Boolean = true,
  val options: Array<TableOption> = []
)
