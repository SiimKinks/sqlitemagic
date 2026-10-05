package com.siimkinks.sqlitemagic

/**
 * Configures local JVM migration tests, enabled by default in Android application modules.
 * All tested variants use the shared history of one selected release variant.
 */
open class MigrationTestingExtension {
  /**
   * Enables selected-release export and generated JVM test sources/resources. Defaults to true; false opts out.
   * Tests and explicit release publication trigger this work; ordinary application assembly has no such dependencies.
   */
  var enabled = true

  /**
   * Selects the release variant whose compiled schema and shared history the tests validate.
   * Null selects the first registered eligible non-debug variant; an explicit name must match a registered variant.
   */
  var releaseVariant: String? = null

  /**
   * Sets the oldest retained schema version included in test coverage; it must exist in the selected history.
   * Null covers all retained versions. An explicit version excludes older evidence; it does not repair missing inputs.
   */
  var firstVersion: Int? = null
}
