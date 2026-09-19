package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.Utils.ValueParser
import com.siimkinks.sqlitemagic.transformer.BooleanTransformer

@Suppress("UNCHECKED_CAST")
class BooleanColumn<T, N>(
  table: Table<T>,
  name: String,
  valueParser: ValueParser<*>,
  nullable: Boolean,
  alias: String?
) : NumericColumn<Boolean, Boolean, Boolean, T, N>(
  table, name, false, valueParser, nullable, alias
) {
  override fun toSqlArg(value: Boolean) = BooleanTransformer
    .objectToDbValue(value)
    ?.toString()
    ?: throw NullPointerException("SQL argument cannot be null")

  override fun `as`(alias: String) = BooleanColumn<T, N>(
    table = table,
    name = name,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias
  )

  override fun <NewTableType> inTable(table: Table<NewTableType>) = BooleanColumn<NewTableType, N>(
    table = table,
    name = name,
    valueParser = valueParser,
    nullable = nullable,
    alias = alias
  )

  override fun <V> getFromCursor(cursor: Cursor): V? = BooleanTransformer
    .dbValueToObject(
      super.getFromCursor(cursor)
    ) as V?

  override fun <V> getFromStatement(statement: SupportSQLiteStatement): V? = BooleanTransformer
    .dbValueToObject(
      super.getFromStatement(statement)
    ) as V?
}
