package com.siimkinks.sqlitemagic.runtime.contract.query.connection

import android.os.Build.FINGERPRINT
import android.os.Build.VERSION.RELEASE
import android.os.Build.VERSION.SDK_INT
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnectionImpl
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.readStrings
import com.siimkinks.sqlitemagic.runtime.support.text
import com.siimkinks.sqlitemagic.runtime.support.withDefaultWriter
import com.siimkinks.sqlitemagic.testDriver
import org.junit.Rule
import org.junit.Test

internal class EngineConfigurationRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun reportsConfiguredConnectionEngineAndDeviceConfiguration() {
    val engineConnection = testDriver()
      .open(":memory:")
      .use { it.javaClass }
    val readerConnection = (SqliteMagic.getDefaultConnection() as DbConnectionImpl)
      .read(requiresWriter = false) { it.connection.javaClass }
    withDefaultWriter { connection ->
      val version = connection.text("SELECT sqlite_version()")
      assertThat(version).isNotEmpty()
      val sourceId = runCatching { connection.text("SELECT sqlite_source_id()") }
        .getOrElse { "unavailable: ${it.message}" }
      val journalMode = connection.text("PRAGMA journal_mode")
      val compileOptions = connection
        .readStrings("PRAGMA compile_options")
        .requireNoNulls()
        .sorted()
      assertThat(listOf(connection.javaClass, readerConnection))
        .containsExactly(engineConnection, engineConnection)
      assertThat(journalMode).isEqualTo("wal")

      listOf(
        "sdk=$SDK_INT",
        "release=$RELEASE",
        "fingerprint=$FINGERPRINT",
        "connection_implementation=${connection.javaClass.name}",
        "sqlite_version=$version",
        "sqlite_source_id=$sourceId",
        "journal_mode=$journalMode",
        "compile_option_count=${compileOptions.size}"
      )
        .plus(compileOptions.map { "compile_option=$it" })
        .forEach(::reportConfiguration)
    }
  }

  private fun reportConfiguration(value: String) = println("SQLiteMagicEngineConfiguration $value")
}
