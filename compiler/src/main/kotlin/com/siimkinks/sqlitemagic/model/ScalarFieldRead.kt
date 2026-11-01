package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.siimkinks.sqlitemagic.WriterTypes.nullnessType
import com.siimkinks.sqlitemagic.transformer.TransformerElement
import com.siimkinks.sqlitemagic.writer.databaseValueGetter
import com.siimkinks.sqlitemagic.writer.nullableDatabaseValueGetter
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.TypeName

internal class ScalarFieldRead(
  deserializedType: TypeName,
  private val sqlStorageType: SqlStorageType,
  private val transformer: TransformerElement?,
  isSqlNullable: Boolean,
  private val checksRequiredNull: Boolean,
  private val columnName: String,
  private val propertyName: String
) {
  private val hostType = deserializedType.copy(nullable = false)
  private val readerNullable = transformer?.decoderResultNullable == true
  val descriptorResultType = hostType.copy(nullable = readerNullable)
  val selectedType = hostType.copy(
    nullable = when {
      !isSqlNullable -> false
      transformer?.decoderInputNullable == true -> readerNullable
      else -> true
    }
  )
  val descriptorName = when {
    transformer != null -> transformer.sqlTypeSelectorName
    else -> sqlStorageType.name
  }
  val nullness = nullnessType(nullable = isSqlNullable)
  val selectedRead = when {
    isSqlNullable -> nullableReadCode()
    else -> requiredReadCode()
  }

  private fun nullableReadCode(): CodeBlock {
    val raw = rawRead()
    val rawOrNull = nullableDatabaseValueGetter(
      storageType = sqlStorageType,
      index = CodeBlock.of("index")
    )
    return when {
      transformer == null -> rawOrNull ?: nullOr(raw)
      transformer.decoderInputNullable -> transformer.deserializedValueGetter(rawOrNull ?: nullOr(raw))
      rawOrNull != null -> transformer.nullableDeserializedValueGetter(rawOrNull)
      else -> nullOr(transformer.deserializedValueGetter(raw))
    }
  }

  private fun nullOr(read: CodeBlock) = CodeBlock.of("(if (statement.isNull(index)) null else %L)", read)

  private fun requiredReadCode(): CodeBlock {
    val raw = rawRead()
    val checkedRaw = when {
      checksRequiredNull -> CodeBlock.of(
        "(if (statement.isNull(index)) throw %T(%S) else %L)",
        SQL_EXCEPTION,
        """Column "$columnName" was NULL""",
        raw
      )
      else -> raw
    }
    return when {
      transformer == null -> checkedRaw
      transformer.decoderResultNullable -> transformer.requiredDeserializedValue(
        value = transformer.deserializedValueGetter(checkedRaw),
        propertyName = propertyName
      )
      else -> transformer.deserializedValueGetter(checkedRaw)
    }
  }

  private fun rawRead() = databaseValueGetter(
    storageType = sqlStorageType,
    index = CodeBlock.of("index")
  )
}
