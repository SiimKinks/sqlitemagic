package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.SqlStorageType.BOXED_BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlStorageType.BYTE
import com.siimkinks.sqlitemagic.SqlStorageType.BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlStorageType.DOUBLE
import com.siimkinks.sqlitemagic.SqlStorageType.FLOAT
import com.siimkinks.sqlitemagic.SqlStorageType.INT
import com.siimkinks.sqlitemagic.SqlStorageType.LONG
import com.siimkinks.sqlitemagic.SqlStorageType.SHORT
import com.siimkinks.sqlitemagic.SqlStorageType.STRING
import com.squareup.kotlinpoet.CodeBlock

internal fun databaseCursorGetter(
  storageType: SqlStorageType,
  index: CodeBlock
): CodeBlock = when (storageType) {
  BYTE_ARRAY -> CodeBlock.of("cursor.getBlob(%L)", index)
  BOXED_BYTE_ARRAY -> CodeBlock.of("cursor.getBlob(%L).toTypedArray()", index)
  BYTE -> CodeBlock.of("cursor.getBlob(%L)[0]", index)
  DOUBLE -> CodeBlock.of("cursor.getDouble(%L)", index)
  FLOAT -> CodeBlock.of("cursor.getFloat(%L)", index)
  INT -> CodeBlock.of("cursor.getInt(%L)", index)
  LONG -> CodeBlock.of("cursor.getLong(%L)", index)
  SHORT -> CodeBlock.of("cursor.getShort(%L)", index)
  STRING -> CodeBlock.of("cursor.getString(%L)", index)
}
