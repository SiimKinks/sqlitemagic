package com.siimkinks.sqlitemagic.rxjava2

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AUTHOR
import com.siimkinks.sqlitemagic.BOOK
import com.siimkinks.sqlitemagic.DELETE
import com.siimkinks.sqlitemagic.EXCEPT
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.libraryDatabase
import org.junit.Test

internal class NestedQueryDependencyTest {
  @Test
  fun `an observed compound reruns after changes to a table only a later arm's nested query reads`() {
    val idle = SELECT
      .COLUMN(AUTHOR.NAME)
      .FROM(AUTHOR)
      .EXCEPT(
        SELECT
          .COLUMN(AUTHOR.NAME)
          .FROM(AUTHOR)
          .WHERE(AUTHOR.ID IN (SELECT COLUMN BOOK.AUTHOR FROM BOOK))
      )

    libraryDatabase().use { database ->
      val connection = database.dbConnection(adapterSettings = querySchedulerSettings())
      val observed = idle
        .usingConnection(connection)
        .observe()
        .runQuery()
        .test()
      DELETE
        .FROM(BOOK)
        .WHERE(BOOK.TITLE IS "Emma")
        .usingConnection(connection)
        .execute()

      assertThat(observed.values()).isEqualTo(listOf(emptyList(), listOf("Grace")))
      observed.dispose()
    }
  }
}
