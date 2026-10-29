package com.siimkinks.sqlitemagic.rxjava2

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.TestAdapter
import com.siimkinks.sqlitemagic.deleteTable
import com.siimkinks.sqlitemagic.newConnection
import org.junit.Test

internal class EntityDeleteOperationsTest {
  @Test
  fun `observed delete table is cold and emits the deleted row count`() {
    val recording = newConnection()
    recording.recordingDatabase.updateResults += 4
    val terminal = TestAdapter()
      .deleteTable()
      .usingConnection(recording.connection)
      .observe()

    assertThat(recording.recordingDatabase.compiledStatements).isEmpty()

    terminal
      .test()
      .assertResult(4)
    assertThat(recording.statementSql).containsExactly("DELETE FROM books WHERE 1")
    assertThat(recording.triggers).containsExactly(setOf("books"))
  }
}
