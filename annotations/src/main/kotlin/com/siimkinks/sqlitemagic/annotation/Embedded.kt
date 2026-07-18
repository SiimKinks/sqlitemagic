package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.FIELD

/**
 * Flattens the annotated value object's persisted properties into its enclosing [Table].
 *
 * For example:
 *
 * ```kotlin
 * data class Coordinates(
 *   val latitude: Double,
 *   val longitude: Double
 * )
 *
 * @Table
 * data class Address(
 *   val street: String,
 *   @Embedded
 *   val coordinates: Coordinates
 * )
 * ```
 *
 * `Address` has the columns `street`, `latitude`, and `longitude`. No column is created
 * for `coordinates` itself, and `Coordinates` does not become a separate table or relationship.
 *
 * All eligible field-backed instance properties of the embedded value are persisted. An inner
 * [IgnoreColumn] excludes a property, while an inner [Column] overrides its column defaults.
 * Embedded values may contain other embedded values; prefixes from every nesting level are
 * prepended from outermost to innermost.
 *
 * A nullable embedded value is stored as `NULL` in all of its descendant columns. It is restored
 * as `null` when all of those values are `NULL`; otherwise, the embedded object is reconstructed.
 *
 * This annotation cannot be combined on the same property with [Column], [Id], [Unique], [Index],
 * or [IgnoreColumn]. An [Id] must also not be declared inside an embedded value because table
 * identity must be a direct property of the enclosing table.
 *
 * @property prefix Text prepended exactly as declared to every descendant column name. For example:
 *
 * ```kotlin
 * @Embedded(prefix = "coordinate_")
 * val coordinates: Coordinates
 * ```
 *
 * The `latitude` and `longitude` properties are stored in columns named
 * `coordinate_latitude` and `coordinate_longitude`. Separators are not added automatically.
 * Defaults to an empty string.
 */
@Target(FIELD)
@Retention(BINARY)
annotation class Embedded(
  val prefix: String = ""
)
