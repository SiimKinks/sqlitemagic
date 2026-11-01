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
