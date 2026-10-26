package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.SqlStorageType.BOXED_BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlStorageType.BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlStorageType.STRING
import com.siimkinks.sqlitemagic.WriterTypes.nullnessType
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.writer.databaseCursorGetter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

internal class ScalarFieldRead(
  deserializedType: TypeName,
  sqlStorageType: SqlStorageType,
  transformer: TransformerElement?,
  isSchemaNullable: Boolean,
  sqlRequired: Boolean
) {
  val customTransformer = transformer?.takeUnless(TransformerElement::isDefaultTransformer)
  private val isDefaultTransformer = transformer?.isDefaultTransformer == true
  private val hostType = deserializedType.copy(nullable = false)
  private val getterNullable = sqlStorageType.hasNullableCursorGetter
  private val readerNullable = customTransformer?.decoderResultNullable == true
  val descriptorResultType = hostType.copy(nullable = readerNullable)
  val selectedType = hostType.copy(
    nullable = when {
      isDefaultTransformer -> isSchemaNullable
      customTransformer != null -> readerNullable ||
          (!customTransformer.decoderInputNullable && (isSchemaNullable || getterNullable))
      else -> isSchemaNullable || getterNullable
    }
  )
  val descriptorName = when {
    customTransformer != null -> customTransformer.sqlTypeSelectorName
    isDefaultTransformer -> "BOOLEAN"
    else -> sqlStorageType.name
  }
  val nullness = nullnessType(nullable = !sqlRequired)
  val selectedRead = selectedReadCode(
    sqlStorageType = sqlStorageType,
    isSchemaNullable = isSchemaNullable,
    customTransformer = customTransformer,
    isDefaultTransformer = isDefaultTransformer,
    sqlRequired = sqlRequired
  )
}

private fun selectedReadCode(
  sqlStorageType: SqlStorageType,
  isSchemaNullable: Boolean,
  customTransformer: TransformerElement?,
  isDefaultTransformer: Boolean,
  sqlRequired: Boolean
): CodeBlock {
  val nullableRead = isSchemaNullable && !sqlRequired
  val raw = when {
    isDefaultTransformer -> CodeBlock.of("cursor.getInt(index) != 0")
    else -> sqlStorageType.cursorRead()
  }
  val nullableRaw = when {
    nullableRead && !sqlStorageType.hasNullableCursorGetter -> CodeBlock.of(
      "(if (cursor.isNull(index)) null else %L)",
      raw
    )
    else -> raw
  }
  return when {
    customTransformer == null -> nullableRaw
    customTransformer.decoderInputNullable -> customTransformer
      .dbValueToObjectMethod
      .callWithArgument(nullableRaw)
    nullableRead || sqlStorageType.hasNullableCursorGetter -> CodeBlock.of(
      "%L?.let(%L)",
      nullableRaw,
      customTransformer.dbValueToObjectMethod.callableReference()
    )
    else -> customTransformer
      .dbValueToObjectMethod
      .callWithArgument(raw)
  }
}

private val SqlStorageType.hasNullableCursorGetter
  get() = when (this) {
    STRING, BYTE_ARRAY, BOXED_BYTE_ARRAY -> true
    else -> false
  }

private fun SqlStorageType.cursorRead() = when (this) {
  BOXED_BYTE_ARRAY -> CodeBlock.of("cursor.getBlob(index)?.toTypedArray()")
  else -> databaseCursorGetter(
    storageType = this,
    index = CodeBlock.of("index")
  )
}
