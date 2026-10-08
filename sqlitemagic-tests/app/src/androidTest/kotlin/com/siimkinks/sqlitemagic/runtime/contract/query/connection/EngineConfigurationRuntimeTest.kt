package com.siimkinks.sqlitemagic.runtime.contract.query.connection

import android.database.Cursor
import android.os.Build
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.namespaceContractWritableDatabase
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import org.junit.Test

class EngineConfigurationRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun reportsFrameworkConnectionEngineAndDeviceConfiguration() {
    val database = namespaceContractWritableDatabase()
    val version = database.query("SELECT sqlite_version()")
      .use(Cursor::readStrings)
      .single()
    assertThat(version).isNotEmpty()
    val sourceId = runCatching {
      database.query("SELECT sqlite_source_id()")
        .use(Cursor::readStrings)
        .single()
    }.getOrElse { error -> "unavailable: ${error.message}" }
    val journalMode = database.query("PRAGMA journal_mode")
      .use(Cursor::readStrings)
      .single()
    val compileOptions = database.query("PRAGMA compile_options")
      .use(Cursor::readStrings)
      .sorted()
    assertThat(journalMode).isNotEmpty()

    listOf(
      "sdk=${Build.VERSION.SDK_INT}",
      "release=${Build.VERSION.RELEASE}",
      "fingerprint=${Build.FINGERPRINT}",
      "configured_factory=${FrameworkSQLiteOpenHelperFactory::class.java.name}",
      "database_implementation=${database.javaClass.name}",
      "sqlite_version=$version",
      "sqlite_source_id=$sourceId",
      "journal_mode=$journalMode",
      "compile_option_count=${compileOptions.size}"
    ).forEach(::reportConfiguration)
    compileOptions.forEach(::reportCompileOption)
  }

  private fun reportCompileOption(option: String) = reportConfiguration("compile_option=$option")

  private fun reportConfiguration(value: String) = println("SQLiteMagicEngineConfiguration $value")
}
