package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.SqlStorageType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlStorageType.BOXED_BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlStorageType.BYTE
import com.siimkinks.sqlitemagic.SqlStorageType.BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlStorageType.DOUBLE
import com.siimkinks.sqlitemagic.SqlStorageType.FLOAT
import com.siimkinks.sqlitemagic.SqlStorageType.INT
import com.siimkinks.sqlitemagic.SqlStorageType.LONG
import com.siimkinks.sqlitemagic.SqlStorageType.SHORT
import com.siimkinks.sqlitemagic.SqlStorageType.STRING
import com.siimkinks.sqlitemagic.WriterTypes.GET_BLOB_OR_NULL
import com.siimkinks.sqlitemagic.WriterTypes.GET_REQUIRED_BLOB
import com.siimkinks.sqlitemagic.WriterTypes.GET_REQUIRED_DOUBLE
import com.siimkinks.sqlitemagic.WriterTypes.GET_REQUIRED_LONG
import com.siimkinks.sqlitemagic.WriterTypes.GET_REQUIRED_TEXT
import com.siimkinks.sqlitemagic.WriterTypes.GET_TEXT_OR_NULL
import com.squareup.kotlinpoet.CodeBlock

internal fun databaseValueGetter(
  storageType: SqlStorageType,
  index: CodeBlock
) = when (storageType) {
  BYTE_ARRAY -> CodeBlock.of("statement.getBlob(%L)", index)
  BOXED_BYTE_ARRAY -> CodeBlock.of("statement.getBlob(%L).toTypedArray()", index)
  BYTE -> CodeBlock.of("statement.getBlob(%L)[0]", index)
  BOOLEAN -> CodeBlock.of("statement.getBoolean(%L)", index)
  DOUBLE -> CodeBlock.of("statement.getDouble(%L)", index)
  FLOAT -> CodeBlock.of("statement.getFloat(%L)", index)
  INT -> CodeBlock.of("statement.getInt(%L)", index)
  LONG -> CodeBlock.of("statement.getLong(%L)", index)
  SHORT -> CodeBlock.of("statement.getLong(%L).toShort()", index)
  STRING -> CodeBlock.of("statement.getText(%L)", index)
}

internal fun nullableDatabaseValueGetter(
  storageType: SqlStorageType,
  index: CodeBlock
) = when (storageType) {
  BYTE_ARRAY -> CodeBlock.of("statement.%M(%L)", GET_BLOB_OR_NULL, index)
  BOXED_BYTE_ARRAY -> CodeBlock.of("statement.%M(%L)?.toTypedArray()", GET_BLOB_OR_NULL, index)
  STRING -> CodeBlock.of("statement.%M(%L)", GET_TEXT_OR_NULL, index)
  else -> null
}

internal fun requiredDatabaseValueGetter(
  storageType: SqlStorageType,
  index: CodeBlock,
  nullMessage: String
) = when (storageType) {
  BYTE_ARRAY -> CodeBlock.of("statement.%M(%L, %S)", GET_REQUIRED_BLOB, index, nullMessage)
  BOXED_BYTE_ARRAY -> CodeBlock.of("statement.%M(%L, %S).toTypedArray()", GET_REQUIRED_BLOB, index, nullMessage)
  BYTE -> CodeBlock.of("statement.%M(%L, %S)[0]", GET_REQUIRED_BLOB, index, nullMessage)
  BOOLEAN -> CodeBlock.of("(statement.%M(%L, %S) != 0L)", GET_REQUIRED_LONG, index, nullMessage)
  DOUBLE -> CodeBlock.of("statement.%M(%L, %S)", GET_REQUIRED_DOUBLE, index, nullMessage)
  FLOAT -> CodeBlock.of("statement.%M(%L, %S).toFloat()", GET_REQUIRED_DOUBLE, index, nullMessage)
  INT -> CodeBlock.of("statement.%M(%L, %S).toInt()", GET_REQUIRED_LONG, index, nullMessage)
  LONG -> CodeBlock.of("statement.%M(%L, %S)", GET_REQUIRED_LONG, index, nullMessage)
  SHORT -> CodeBlock.of("statement.%M(%L, %S).toShort()", GET_REQUIRED_LONG, index, nullMessage)
  STRING -> CodeBlock.of("statement.%M(%L, %S)", GET_REQUIRED_TEXT, index, nullMessage)
}
