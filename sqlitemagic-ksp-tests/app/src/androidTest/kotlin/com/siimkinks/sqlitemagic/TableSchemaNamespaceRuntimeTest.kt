package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import org.junit.Test

private const val TABLE_NAME = "table_schema_namespace_contract"

class TableSchemaNamespaceRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun aliasedTablesReadTheirDeclaredSchemasWhenBothContainTheSameName() {
    val database = (SqliteMagic.getDefaultConnection() as DbConnectionImpl).writableDatabase
    database.execSQL("CREATE TABLE main.$TABLE_NAME (value TEXT NOT NULL)")
    try {
      database.execSQL("CREATE TEMP TABLE $TABLE_NAME (value TEXT NOT NULL)")
      try {
        database.execSQL("INSERT INTO main.$TABLE_NAME (value) VALUES ('main value')")
        database.execSQL("INSERT INTO temp.$TABLE_NAME (value) VALUES ('temp value')")

        val main = NamespaceTable(isTemporary = false).`as`("main_row")
        val temporary = NamespaceTable(isTemporary = true).`as`("temp_row")

        assertThat(
          Select
            .column(valueColumn(main))
            .from(main)
            .compile()
            .execute()
        ).containsExactly("main value")
        assertThat(
          Select
            .column(valueColumn(temporary))
            .from(temporary)
            .compile()
            .execute()
        ).containsExactly("temp value")
      } finally {
        database.execSQL("DROP TABLE IF EXISTS temp.$TABLE_NAME")
      }
    } finally {
      database.execSQL("DROP TABLE IF EXISTS main.$TABLE_NAME")
    }
  }
}

private class NamespaceTable(
  isTemporary: Boolean
) : Table<String>(
  name = TABLE_NAME,
  alias = null,
  nrOfColumns = 1,
  temporary = isTemporary
)

private fun valueColumn(table: Table<String>) = Column<String, String, CharSequence, String, NotNullable>(
  table = table,
  name = "value",
  valueParser = Utils.STRING_PARSER,
  nullable = false,
  alias = null
)
