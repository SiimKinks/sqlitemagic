package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_NAME
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DB_VERSION
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_DEBUG
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_MAIN_MODULE_PATH
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_MIGRATE_DEBUG
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_PROJECT_DIR
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_PUBLIC_EXTENSIONS
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_DEBUG
import com.siimkinks.sqlitemagic.SqliteMagicSymbolProcessor.Companion.OPTION_VARIANT_NAME
import org.junit.jupiter.api.Test

internal class CompilerOptionsTest {
  @Test
  fun `parses the complete manager option contract`() {
    val actual = CompilerOptions.from(
      mapOf(
        OPTION_DEBUG to "true",
        OPTION_VARIANT_DEBUG to "true",
        OPTION_DB_NAME to "library.db",
        OPTION_DB_VERSION to "12",
        OPTION_MIGRATE_DEBUG to "false",
        OPTION_PROJECT_DIR to "/project",
        OPTION_VARIANT_NAME to "demoDebug",
        OPTION_MAIN_MODULE_PATH to "/main",
        OPTION_PUBLIC_EXTENSIONS to "true"
      )
    )

    assertThat(actual).isEqualTo(
      CompilerOptions(
        debug = true,
        isDebugVariant = true,
        dbName = "library.db",
        dbVersion = 12,
        migrateDebug = false,
        projectDir = "/project",
        variantName = "demoDebug",
        mainModulePath = "/main",
        publicExtensions = true
      )
    )
  }

  @Test
  fun `uses safe defaults for absent manager options`() {
    assertThat(CompilerOptions.from(emptyMap())).isEqualTo(
      CompilerOptions(
        debug = false,
        isDebugVariant = false,
        dbName = null,
        dbVersion = null,
        migrateDebug = true,
        projectDir = null,
        variantName = null,
        mainModulePath = null,
        publicExtensions = false
      )
    )
  }
}
