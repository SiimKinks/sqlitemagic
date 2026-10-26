package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.DOUBLE
import com.siimkinks.sqlitemagic.SqlType.LONG
import org.junit.Test

internal class BundledSqliteDatabaseTest {
  private data class StorageRow(
    val integer: Long,
    val real: Double,
    val text: String?,
    val blob: List<Byte>,
    val nullCell: Long?
  )

  @Test
  fun `scalar SELECT reads each native storage class and binds native arguments in order`() {
    personDatabase().use { database ->
      val connection = database.dbConnection()
      val row = StorageRow(
        integer = SELECT
          .COLUMN("9007199254740993".expr(LONG))
          .usingConnection(connection)
          .execute()
          .single(),
        real = SELECT
          .COLUMN("? * 2".expr(DOUBLE, 1.25))
          .usingConnection(connection)
          .execute()
          .single(),
        text = SELECT
          .COLUMN(PERSON.NAME)
          .FROM(PERSON)
          .WHERE("person.age < ? AND person.id > ?".expr(BOOLEAN, 18, 3L))
          .usingConnection(connection)
          .execute()
          .single(),
        blob = SELECT
          .COLUMN(byteArrayOf(1, 2).asValue())
          .usingConnection(connection)
          .execute()
          .single()
          .toList(),
        nullCell = SELECT
          .COLUMN(PERSON.PARENT_ID)
          .FROM(PERSON)
          .LIMIT(1)
          .usingConnection(connection)
          .execute()
          .single()
      )

      assertThat(row).isEqualTo(
        StorageRow(
          integer = 9007199254740993L,
          real = 2.5,
          text = "Linus",
          blob = listOf(1, 2),
          nullCell = null
        )
      )
    }
  }
}
