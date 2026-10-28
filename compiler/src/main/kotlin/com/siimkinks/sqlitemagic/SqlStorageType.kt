package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.SqlAffinity.BLOB
import com.siimkinks.sqlitemagic.SqlAffinity.INTEGER
import com.siimkinks.sqlitemagic.SqlAffinity.REAL
import com.siimkinks.sqlitemagic.SqlAffinity.TEXT
import com.siimkinks.sqlitemagic.utils.expandedTypeAlias
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName

internal enum class SqlAffinity {
  BLOB,
  INTEGER,
  REAL,
  TEXT
}

internal enum class SqlStorageType(
  val affinity: SqlAffinity,
  val defaultValue: String
) {
  BYTE_ARRAY(BLOB, "X''"),
  BOXED_BYTE_ARRAY(BLOB, "X''"),
  BYTE(BLOB, "X'00'"),
  DOUBLE(REAL, "0.0"),
  FLOAT(REAL, "0.0"),
  INT(INTEGER, "0"),
  LONG(INTEGER, "0"),
  SHORT(INTEGER, "0"),
  STRING(TEXT, "''");

  companion object {
    fun from(typeName: TypeName): SqlStorageType? {
      val expandedType = typeName.expandedTypeAlias()
      return when {
        expandedType.isBoxedByteArray() -> BOXED_BYTE_ARRAY
        else -> SIMPLE_TYPES[expandedType.classNameWithoutNullability()]
      }
    }
  }
}

private fun TypeName.isBoxedByteArray() = this is ParameterizedTypeName &&
    rawType == ARRAY &&
    typeArguments
      .singleOrNull()
      ?.wildcardBound()
      ?.expandedTypeAlias()
      ?.classNameWithoutNullability() in BYTE_CLASS_NAMES

private fun TypeName.wildcardBound() = when (this) {
  is WildcardTypeName -> inTypes.singleOrNull() ?: outTypes.single()
  else -> this
}

private fun TypeName.classNameWithoutNullability() =
  (this as? ClassName)
    ?.canonicalName

private val BYTE_CLASS_NAMES = setOf(
  BYTE.canonicalName,
  Byte::class.javaObjectType.canonicalName
)

private val SIMPLE_TYPES = mapOf(
  BYTE_ARRAY.canonicalName to SqlStorageType.BYTE_ARRAY,
  BYTE.canonicalName to SqlStorageType.BYTE,
  Byte::class.javaObjectType.canonicalName to SqlStorageType.BYTE,
  DOUBLE.canonicalName to SqlStorageType.DOUBLE,
  Double::class.javaObjectType.canonicalName to SqlStorageType.DOUBLE,
  FLOAT.canonicalName to SqlStorageType.FLOAT,
  Float::class.javaObjectType.canonicalName to SqlStorageType.FLOAT,
  INT.canonicalName to SqlStorageType.INT,
  Int::class.javaObjectType.canonicalName to SqlStorageType.INT,
  LONG.canonicalName to SqlStorageType.LONG,
  Long::class.javaObjectType.canonicalName to SqlStorageType.LONG,
  SHORT.canonicalName to SqlStorageType.SHORT,
  Short::class.javaObjectType.canonicalName to SqlStorageType.SHORT,
  STRING.canonicalName to SqlStorageType.STRING,
  String::class.java.canonicalName to SqlStorageType.STRING
)
