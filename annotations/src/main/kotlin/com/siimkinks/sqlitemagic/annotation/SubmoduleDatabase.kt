package com.siimkinks.sqlitemagic.annotation

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.reflect.KClass

/**
 * Defines a submodule database configuration.
 *
 * A module may contain one database configuration, defined with either [Database] or
 * [SubmoduleDatabase].
 *
 * @property value Non-empty unique name that identifies the submodule database configuration.
 * @property externalTransformers Classes that contain transformer functions from external modules.
 */
@Target(CLASS)
@Retention(BINARY)
annotation class SubmoduleDatabase(
  val value: String,
  val externalTransformers: Array<KClass<*>> = []
)
