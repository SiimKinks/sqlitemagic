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

enum class SqlAffinity(
  val defaultValue: String
) {
  BLOB(defaultValue = "0"),
  INTEGER(defaultValue = "0"),
  REAL(defaultValue = "0.0"),
  TEXT(defaultValue = "''")
}

enum class SqlStorageType(
  val affinity: SqlAffinity,
  val isNumeric: Boolean = false
) {
  BYTE_ARRAY(affinity = BLOB),
  BOXED_BYTE_ARRAY(affinity = BLOB),
  BYTE(affinity = BLOB),
  DOUBLE(affinity = REAL, isNumeric = true),
  FLOAT(affinity = REAL, isNumeric = true),
  INT(affinity = INTEGER, isNumeric = true),
  LONG(affinity = INTEGER, isNumeric = true),
  SHORT(affinity = INTEGER, isNumeric = true),
  STRING(affinity = TEXT);

  companion object {
    val supportedTypeNames = SIMPLE_TYPES.keys + "kotlin.Array<kotlin.Byte>"

    fun from(typeName: TypeName): SqlStorageType? {
      val expandedType = typeName.expandedTypeAlias()
      return when {
        expandedType.isBoxedByteArray() -> BOXED_BYTE_ARRAY
        else -> SIMPLE_TYPES[expandedType.classNameWithoutNullability()]
      }
    }
  }
}

private fun TypeName.isBoxedByteArray(): Boolean {
  val parameterizedType = this as? ParameterizedTypeName ?: return false
  if (parameterizedType.rawType != ARRAY) {
    return false
  }
  val elementType = parameterizedType.typeArguments.singleOrNull() ?: return false
  val expandedElementType = elementType
    .wildcardBound()
    .expandedTypeAlias()
  return expandedElementType.classNameWithoutNullability() in BYTE_CLASS_NAMES
}

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
