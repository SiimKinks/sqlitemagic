package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.SqlStorageType.BOXED_BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlStorageType.BYTE_ARRAY
import com.siimkinks.sqlitemagic.SqlStorageType.STRING
import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.siimkinks.sqlitemagic.WriterTypes.nullnessType
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.writer.databaseCursorGetter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

internal class ScalarFieldRead(
  deserializedType: TypeName,
  private val sqlStorageType: SqlStorageType,
  transformer: TransformerElement?,
  private val isSqlNullable: Boolean,
  private val checksRequiredNull: Boolean,
  private val columnName: String,
  private val propertyName: String
) {
  val customTransformer = transformer?.takeUnless(TransformerElement::isDefaultTransformer)
  private val isDefaultTransformer = transformer?.isDefaultTransformer == true
  private val hostType = deserializedType.copy(nullable = false)
  private val readerNullable = customTransformer?.decoderResultNullable == true
  val descriptorResultType = hostType.copy(nullable = readerNullable)
  val selectedType = hostType.copy(
    nullable = when {
      !isSqlNullable -> false
      customTransformer?.decoderInputNullable == true -> readerNullable
      else -> true
    }
  )
  val descriptorName = when {
    customTransformer != null -> customTransformer.sqlTypeSelectorName
    isDefaultTransformer -> "BOOLEAN"
    else -> sqlStorageType.name
  }
  val nullness = nullnessType(nullable = isSqlNullable)
  val selectedRead = when {
    isSqlNullable -> nullableReadCode()
    else -> requiredReadCode()
  }

  private fun nullableReadCode(): CodeBlock {
    val raw = rawRead()
    val nullableRaw = when {
      sqlStorageType.hasNullableCursorGetter -> raw
      else -> CodeBlock.of("(if (cursor.isNull(index)) null else %L)", raw)
    }
    return when {
      customTransformer == null -> nullableRaw
      customTransformer.decoderInputNullable -> customTransformer.deserializedValueGetter(nullableRaw)
      else -> CodeBlock.of(
        "%L?.let(%L)",
        nullableRaw,
        customTransformer.dbValueToObjectMethod.callableReference()
      )
    }
  }

  private fun requiredReadCode(): CodeBlock {
    val raw = rawRead()
    val checkedRaw = when {
      checksRequiredNull -> CodeBlock.of(
        "(if (cursor.isNull(index)) throw %T(%S) else %L)",
        SQL_EXCEPTION,
        """Column "$columnName" was NULL""",
        raw
      )
      else -> raw
    }
    return when {
      customTransformer == null -> checkedRaw
      customTransformer.decoderResultNullable -> customTransformer.requiredDeserializedValue(
        value = customTransformer.deserializedValueGetter(checkedRaw),
        propertyName = propertyName
      )
      else -> customTransformer.deserializedValueGetter(checkedRaw)
    }
  }

  private fun rawRead() = when {
    isDefaultTransformer -> CodeBlock.of("cursor.getInt(index) != 0")
    sqlStorageType == BOXED_BYTE_ARRAY && isSqlNullable -> CodeBlock.of("cursor.getBlob(index)?.toTypedArray()")
    else -> databaseCursorGetter(
      storageType = sqlStorageType,
      index = CodeBlock.of("index")
    )
  }
}

private val SqlStorageType.hasNullableCursorGetter
  get() = when (this) {
    STRING, BYTE_ARRAY, BOXED_BYTE_ARRAY -> true
    else -> false
  }
