package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Defines a column as the table ID.
 *
 * An explicit ID may use any otherwise supported persisted
 * type, including a type persisted through a registered transformer.
 * A table does not have to declare an ID.
 *
 * A table without an ID supports insert operations and explicit update or delete statements whose
 * affected rows are selected by the caller. A non-null single-column [Unique] property may be used
 * for identity-dependent entity operations when its generated column property is supplied to
 * `execute` or `observe`. It does not become a model ID.
 *
 * @property autoIncrement Controls whether this ID is auto-incremented. When omitted,
 *     auto-increment is enabled only when the ID's serialized SQLite type is `INTEGER` and the
 *     generated code can convert the SQLite row ID to the declared ID type. Explicit `true`
 *     requires that compatibility and produces a processor diagnostic when it is not available.
 *     Explicit `false` disables auto-increment.
 */
@Target(FIELD)
@Retention(BINARY)
annotation class Id(
  val autoIncrement: Boolean = true
)
