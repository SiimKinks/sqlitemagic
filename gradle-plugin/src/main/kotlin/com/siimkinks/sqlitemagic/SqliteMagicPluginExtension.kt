package com.siimkinks.sqlitemagic

import org.gradle.api.Action

open class SqliteMagicPluginExtension {
  val migrationTesting = MigrationTestingExtension()
  var configureAutomatically = true
  var publicKotlinExtensionFunctions = false
  var migrateDebugDatabase = true
  var mainModulePath: String? = null
  var debug = false

  fun migrationTesting(configure: Action<MigrationTestingExtension>) = configure.execute(migrationTesting)
}
