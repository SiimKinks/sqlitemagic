package com.siimkinks.sqlitemagic

open class SqliteMagicKspPluginExtension {
  var configureAutomatically = true
  var publicKotlinExtensionFunctions = false
  var generateLogging = true
  var migrateDebugDatabase = true
  var mainModulePath: String? = null
  var debug = false
}
