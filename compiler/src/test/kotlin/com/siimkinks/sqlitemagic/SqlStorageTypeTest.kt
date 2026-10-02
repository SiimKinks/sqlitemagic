package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertWithMessage
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.WildcardTypeName
import com.squareup.kotlinpoet.tags.TypeAliasTag
import org.junit.jupiter.api.Test

internal class SqlStorageTypeTest {
  @Test
  fun `parses simple nullable and aliased kotlin types`() {
    assertStorageTypes(
      BYTE mapsTo SqlStorageType.BYTE,
      BYTE.copy(nullable = true) mapsTo SqlStorageType.BYTE,
      aliasOf(BYTE) mapsTo SqlStorageType.BYTE,
      BYTE_ARRAY mapsTo SqlStorageType.BYTE_ARRAY,
      BYTE_ARRAY.copy(nullable = true) mapsTo SqlStorageType.BYTE_ARRAY,
      aliasOf(BYTE_ARRAY) mapsTo SqlStorageType.BYTE_ARRAY,
      DOUBLE mapsTo SqlStorageType.DOUBLE,
      DOUBLE.copy(nullable = true) mapsTo SqlStorageType.DOUBLE,
      aliasOf(DOUBLE) mapsTo SqlStorageType.DOUBLE,
      FLOAT mapsTo SqlStorageType.FLOAT,
      FLOAT.copy(nullable = true) mapsTo SqlStorageType.FLOAT,
      aliasOf(FLOAT) mapsTo SqlStorageType.FLOAT,
      INT mapsTo SqlStorageType.INT,
      INT.copy(nullable = true) mapsTo SqlStorageType.INT,
      aliasOf(INT) mapsTo SqlStorageType.INT,
      LONG mapsTo SqlStorageType.LONG,
      LONG.copy(nullable = true) mapsTo SqlStorageType.LONG,
      aliasOf(LONG) mapsTo SqlStorageType.LONG,
      SHORT mapsTo SqlStorageType.SHORT,
      SHORT.copy(nullable = true) mapsTo SqlStorageType.SHORT,
      aliasOf(SHORT) mapsTo SqlStorageType.SHORT,
      STRING mapsTo SqlStorageType.STRING,
      STRING.copy(nullable = true) mapsTo SqlStorageType.STRING,
      aliasOf(STRING) mapsTo SqlStorageType.STRING
    )
  }

  @Test
  fun `parses Java types`() {
    assertStorageTypes(
      ClassName(packageName = "java.lang", "Byte") mapsTo SqlStorageType.BYTE,
      ARRAY.parameterizedBy(
        ClassName(packageName = "java.lang", "Byte")
      ) mapsTo SqlStorageType.BOXED_BYTE_ARRAY,
      ClassName(packageName = "java.lang", "Double") mapsTo SqlStorageType.DOUBLE,
      ClassName(packageName = "java.lang", "Float") mapsTo SqlStorageType.FLOAT,
      ClassName(packageName = "java.lang", "Integer") mapsTo SqlStorageType.INT,
      ClassName(packageName = "java.lang", "Long") mapsTo SqlStorageType.LONG,
      ClassName(packageName = "java.lang", "Short") mapsTo SqlStorageType.SHORT,
      ClassName(packageName = "java.lang", "String") mapsTo SqlStorageType.STRING
    )
  }

  @Test
  fun `parses boxed byte array variants`() {
    assertStorageTypes(
      ARRAY.parameterizedBy(BYTE) mapsTo SqlStorageType.BOXED_BYTE_ARRAY,
      ARRAY
        .parameterizedBy(BYTE)
        .copy(nullable = true) mapsTo SqlStorageType.BOXED_BYTE_ARRAY,
      ARRAY.parameterizedBy(BYTE.copy(nullable = true)) mapsTo SqlStorageType.BOXED_BYTE_ARRAY,
      ARRAY.parameterizedBy(
        WildcardTypeName.producerOf(BYTE)
      ) mapsTo SqlStorageType.BOXED_BYTE_ARRAY,
      ARRAY.parameterizedBy(
        WildcardTypeName.consumerOf(BYTE)
      ) mapsTo SqlStorageType.BOXED_BYTE_ARRAY,
      ARRAY.parameterizedBy(aliasOf(BYTE)) mapsTo SqlStorageType.BOXED_BYTE_ARRAY,
      aliasOf(ARRAY.parameterizedBy(BYTE)) mapsTo SqlStorageType.BOXED_BYTE_ARRAY
    )
  }

  @Test
  fun `does not parse unsupported types`() {
    assertStorageTypes(
      BOOLEAN mapsTo null,
      ClassName(packageName = "kotlin", "Char") mapsTo null,
      ARRAY.parameterizedBy(INT) mapsTo null,
      LIST.parameterizedBy(BYTE) mapsTo null,
      ClassName(packageName = "com.example", "Value") mapsTo null
    )
  }

  private fun assertStorageTypes(vararg cases: SqlStorageTypeCase) =
    cases.forEach(::assertStorageType)

  private fun assertStorageType(case: SqlStorageTypeCase) {
    assertWithMessage("storage type for ${case.typeName}")
      .that(SqlStorageType.from(case.typeName))
      .isEqualTo(case.expected)
  }
}

private data class SqlStorageTypeCase(
  val typeName: TypeName,
  val expected: SqlStorageType?
)

private infix fun TypeName.mapsTo(expected: SqlStorageType?) = SqlStorageTypeCase(
  typeName = this,
  expected = expected
)

private fun aliasOf(typeName: TypeName) = ClassName(
  packageName = "com.example",
  "Alias"
).copy(
  tags = mapOf(TypeAliasTag::class to TypeAliasTag(typeName))
)
