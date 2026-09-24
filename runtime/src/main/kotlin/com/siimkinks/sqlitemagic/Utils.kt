package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.sqlite.db.SupportSQLiteStatement
import com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_BYTES
import com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_PRIMITIVE_BYTES
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import java.util.LinkedList
import java.util.Random

/** Internal utility functions. */
object Utils {
  private val random = Random()
  private val charSet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()

  @VisibleForTesting
  internal const val TABLE_NAME_LEN = 6

  @CheckResult
  fun randomTableName() = buildString(TABLE_NAME_LEN) {
    val random = random
    val charSet = charSet
    val charSetSize = charSet.size
    repeat(TABLE_NAME_LEN) {
      append(charSet[random.nextInt(charSetSize)])
    }
  }

  @CheckResult
  internal fun addTableAlias(
    table: Table<*>,
    systemRenamedTables: SimpleArrayMap<String, LinkedList<String>>
  ): String {
    val nameInQuery = table.nameInQuery
    (systemRenamedTables[table.name]
      ?: LinkedList<String>().also { systemRenamedTables.put(table.name, it) })
      .add(nameInQuery)
    return nameInQuery
  }

  @CheckResult
  fun toByteArray(array: Array<Byte>?) = when {
    array == null -> null
    array.isEmpty() -> EMPTY_PRIMITIVE_BYTES
    else -> ByteArray(size = array.size) { array[it] }
  }

  @CheckResult
  fun toByteArray(array: ByteArray?): Array<Byte>? = when {
    array == null -> null
    array.isEmpty() -> EMPTY_BYTES
    else -> array.toTypedArray()
  }

  @CheckResult
  internal fun <V : Number> numericConstantToSqlString(value: V): String {
    val stringValue = value.toString()
    return when {
      value.toInt() < 0 -> "($stringValue)"
      else -> stringValue
    }
  }

  internal fun <V : Number> parserForNumberType(value: V) = when (value) {
    is Long -> LONG_PARSER
    is Int -> INTEGER_PARSER
    is Short -> SHORT_PARSER
    is Double -> DOUBLE_PARSER
    is Float -> FLOAT_PARSER
    is Byte -> BYTE_PARSER
    else -> LONG_PARSER
  }

  /** Parser for string values. */
  val STRING_PARSER: ValueParser<String> = object : ValueParser<String> {
    override fun parseFromCursor(fastCursor: Cursor): String? =
      fastCursor.getString(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): String? =
      statement.simpleQueryForString()
  }

  /** Parser for non-null long values. */
  val LONG_PARSER: ValueParser<Long> = object : ValueParser<Long> {
    override fun parseFromCursor(fastCursor: Cursor): Long =
      fastCursor.getLong(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): Long =
      statement.simpleQueryForLong()
  }

  /** Parser for nullable long values. */
  val NULLABLE_LONG_PARSER: ValueParser<Long> = object : ValueParser<Long> {
    override fun parseFromCursor(fastCursor: Cursor): Long =
      fastCursor.getLong(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): Long? =
      statement.simpleQueryForString()?.toLong()
  }

  /** Parser for non-null integer values. */
  val INTEGER_PARSER: ValueParser<Int> = object : ValueParser<Int> {
    override fun parseFromCursor(fastCursor: Cursor): Int =
      fastCursor.getInt(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): Int =
      statement.simpleQueryForLong().toInt()
  }

  /** Parser for nullable integer values. */
  val NULLABLE_INTEGER_PARSER: ValueParser<Int> = object : ValueParser<Int> {
    override fun parseFromCursor(fastCursor: Cursor): Int =
      fastCursor.getInt(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): Int? =
      statement.simpleQueryForString()?.toInt()
  }

  /** Parser for non-null short values. */
  val SHORT_PARSER: ValueParser<Short> = object : ValueParser<Short> {
    override fun parseFromCursor(fastCursor: Cursor): Short =
      fastCursor.getShort(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): Short =
      statement.simpleQueryForLong().toShort()
  }

  /** Parser for nullable short values. */
  val NULLABLE_SHORT_PARSER: ValueParser<Short> = object : ValueParser<Short> {
    override fun parseFromCursor(fastCursor: Cursor): Short =
      fastCursor.getShort(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): Short? =
      statement.simpleQueryForString()?.toShort()
  }

  /** Parser for double values. */
  val DOUBLE_PARSER: ValueParser<Double> = object : ValueParser<Double> {
    override fun parseFromCursor(fastCursor: Cursor): Double =
      fastCursor.getDouble(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): Double? =
      statement.simpleQueryForString()?.toDouble()
  }

  /** Parser for float values. */
  val FLOAT_PARSER: ValueParser<Float> = object : ValueParser<Float> {
    override fun parseFromCursor(fastCursor: Cursor): Float =
      fastCursor.getFloat(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): Float? =
      statement.simpleQueryForString()?.toFloat()
  }

  /** Parser for byte values. */
  val BYTE_PARSER: ValueParser<Byte> = object : ValueParser<Byte> {
    override fun parseFromCursor(fastCursor: Cursor): Byte =
      fastCursor.getBlob(0)[0]

    override fun parseFromStatement(statement: SupportSQLiteStatement): Byte =
      statement.simpleQueryForLong().toByte()

    override fun supportsStatementParsing() = false
  }

  /** Parser for nullable byte values. */
  val NULLABLE_BYTE_PARSER: ValueParser<Byte> = object : ValueParser<Byte> {
    override fun parseFromCursor(fastCursor: Cursor): Byte =
      fastCursor.getBlob(0)[0]

    override fun parseFromStatement(statement: SupportSQLiteStatement): Byte? =
      statement.simpleQueryForString()?.toByte()

    override fun supportsStatementParsing() = false
  }

  /** Parser for primitive byte-array values. */
  val UNBOXED_BYTE_ARRAY_PARSER: ValueParser<ByteArray> = object : ValueParser<ByteArray> {
    override fun parseFromCursor(fastCursor: Cursor): ByteArray? =
      fastCursor.getBlob(0)

    override fun parseFromStatement(statement: SupportSQLiteStatement): ByteArray =
      throw UnsupportedOperationException("Querying byte array as column is not supported")

    override fun supportsStatementParsing() = false
  }

  /** Parser for boxed byte-array values. */
  val BOXED_BYTE_ARRAY_PARSER: ValueParser<Array<Byte>> = object : ValueParser<Array<Byte>> {
    override fun parseFromCursor(fastCursor: Cursor): Array<Byte>? =
      toByteArray(fastCursor.getBlob(0))

    override fun parseFromStatement(statement: SupportSQLiteStatement): Array<Byte> =
      throw UnsupportedOperationException("Querying byte array as column is not supported")

    override fun supportsStatementParsing() = false
  }

  /** Parser for a table wildcard. */
  internal val TABLE_ALL_PARSER: ValueParser<Any> = object : ValueParser<Any> {
    override fun parseFromCursor(fastCursor: Cursor): Any =
      throw UnsupportedOperationException("A table wildcard cannot be parsed as a single column")

    override fun parseFromStatement(statement: SupportSQLiteStatement): Any =
      throw UnsupportedOperationException("A table wildcard cannot be parsed as a single column")

    override fun supportsStatementParsing() = false
  }

  interface ValueParser<T> {
    fun parseFromCursor(fastCursor: Cursor): T?

    fun parseFromStatement(statement: SupportSQLiteStatement): T?

    fun supportsStatementParsing(): Boolean = true
  }
}
