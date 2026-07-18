package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Defines a database configuration.
 *
 * Using this annotation to define a database is optional.
 * It must be used when the database configuration is defined across multiple modules.
 * A module may contain one database configuration, defined with either [Database] or
 * [SubmoduleDatabase].
 *
 * @property name Database name. An empty value preserves the name supplied through processor options.
 * @property version Database version. The default value preserves the version supplied through processor options.
 * @property submodules Submodule database configurations. Each class in the array must be
 *     annotated with [SubmoduleDatabase].
 * @property externalTransformers Classes that contain transformer functions from external modules.
 */
@Target(CLASS)
@Retention(BINARY)
annotation class Database(
  val name: String = "",
  val version: Int = -1,
  val submodules: Array<KClass<*>> = [],
  val externalTransformers: Array<KClass<*>> = []
)
