package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class DbCallbackTest {
  @Test
  fun `open creates the temporary schema`() {
    val database = RecordingDatabase()
    val generatedDatabase = TestGeneratedDatabase(tableCount = 1)
    val callback = DbCallback(
      context(),
      1,
      generatedDatabase,
      { _, _, _ -> }
    )

    callback.onOpen(database)

    assertThat(generatedDatabase.temporarySchemaDatabases)
      .containsExactly(database)
  }
}

@Suppress("UNCHECKED_CAST")
private fun <T> context() = null as T
