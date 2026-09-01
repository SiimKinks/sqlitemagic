package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldss
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldss
import com.siimkinks.sqlitemagic.ArticleTable.Companion.ARTICLE
import com.siimkinks.sqlitemagic.ScalarStorageEntityTable.Companion.SCALAR_STORAGE_ENTITY
import com.siimkinks.sqlitemagic.ScalarStorageEntitys
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SelectSqlNode
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithNullableFields
import com.siimkinks.sqlitemagic.fixture.model.Account
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import com.siimkinks.sqlitemagic.fixture.model.Article
import com.siimkinks.sqlitemagic.fixture.model.ScalarStorageEntity
import com.siimkinks.sqlitemagic.fixture.model.TransformableObject
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.ScalarColumnCase
import com.siimkinks.sqlitemagic.runtime.model.identity
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted

internal object ScalarColumnCatalog {
  private val nonNullRows = listOf(
    ImmutableValueWithFields(
      id = null,
      stringValue = "non-null-string-1",
      aBoolean = true,
      integer = 101,
      aDouble = 1.25,
      aShort = 11,
      transformableObject = TransformableObject(value = 1001)
    ),
    ImmutableValueWithFields(
      id = null,
      stringValue = "non-null-string-2",
      aBoolean = false,
      integer = 202,
      aDouble = 2.5,
      aShort = 22,
      transformableObject = TransformableObject(value = 1002)
    ),
    ImmutableValueWithFields(
      id = null,
      stringValue = "non-null-string-3",
      aBoolean = true,
      integer = 303,
      aDouble = 3.75,
      aShort = 33,
      transformableObject = TransformableObject(value = 1003)
    )
  )

  private val nullableRows = listOf(
    ImmutableValueWithNullableFields(
      id = null,
      string = null,
      aBoolean = null,
      integer = null
    ),
    ImmutableValueWithNullableFields(
      id = null,
      string = "nullable-string-2",
      aBoolean = false,
      integer = 202
    ),
    ImmutableValueWithNullableFields(
      id = null,
      string = "nullable-string-3",
      aBoolean = true,
      integer = 303
    )
  )

  private val scalarStorageRows = listOf(
    ScalarStorageEntity(
      id = null,
      longValue = 1001L,
      nullableLong = null,
      floatValue = 1.25f,
      nullableFloat = null,
      byteValue = 11.toByte(),
      nullableByte = null,
      byteArray = byteArrayOf(1, 2, 3),
      nullableByteArray = null,
      boxedByteArray = arrayOf(4.toByte(), 5.toByte()),
      nullableBoxedByteArray = null
    ),
    ScalarStorageEntity(
      id = null,
      longValue = 2002L,
      nullableLong = 2003L,
      floatValue = 2.5f,
      nullableFloat = 2.75f,
      byteValue = (-12).toByte(),
      nullableByte = 13.toByte(),
      byteArray = byteArrayOf(6, 7),
      nullableByteArray = byteArrayOf(8, 9),
      boxedByteArray = arrayOf(10.toByte()),
      nullableBoxedByteArray = arrayOf(11.toByte(), 12.toByte())
    ),
    ScalarStorageEntity(
      id = null,
      longValue = 3003L,
      nullableLong = 3004L,
      floatValue = 3.75f,
      nullableFloat = 3.5f,
      byteValue = 14.toByte(),
      nullableByte = (-15).toByte(),
      byteArray = byteArrayOf(),
      nullableByteArray = byteArrayOf(),
      boxedByteArray = emptyArray(),
      nullableBoxedByteArray = emptyArray()
    )
  )

  private val articleRows = listOf(
    Article(
      id = "scalar-article-1",
      account = Account(id = AccountId("scalar-account-1")),
      value = "scalar-article-value-1"
    ),
    Article(
      id = "scalar-article-2",
      account = Account(id = AccountId("scalar-account-2")),
      value = "scalar-article-value-2"
    ),
    Article(
      id = "scalar-article-3",
      account = Account(id = AccountId("scalar-account-3")),
      value = "scalar-article-value-3"
    )
  )

  val cases = listOf(
    scalarColumnCase(
      name = "non-null String",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::stringValue),
      seed = ::seedNonNullRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null Boolean",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::aBoolean),
      seed = ::seedNonNullRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null Int",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::integer),
      seed = ::seedNonNullRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null Double",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::aDouble),
      seed = ::seedNonNullRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null Short",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::aShort),
      seed = ::seedNonNullRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null TransformableObject",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::transformableObject),
      seed = ::seedNonNullRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.TRANSFORMABLE_OBJECT)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "nullable String with null row",
      expectedValues = nullableRows.map(ImmutableValueWithNullableFields::string),
      seed = ::seedNullableRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
          .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "nullable Boolean with null row",
      expectedValues = nullableRows.map(ImmutableValueWithNullableFields::aBoolean),
      seed = ::seedNullableRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN)
          .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "nullable Int with null row",
      expectedValues = nullableRows.map(ImmutableValueWithNullableFields::integer),
      seed = ::seedNullableRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER)
          .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null Long",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::longValue),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.LONG_VALUE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<Long>(
      name = "empty non-null Long",
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.LONG_VALUE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "nullable Long with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableLong),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_LONG)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<Long?>(
      name = "empty nullable Long",
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_LONG)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null Float",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::floatValue),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.FLOAT_VALUE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<Float>(
      name = "empty non-null Float",
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.FLOAT_VALUE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "nullable Float with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableFloat),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_FLOAT)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<Float?>(
      name = "empty nullable Float",
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_FLOAT)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null Byte",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::byteValue),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.BYTE_VALUE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<Byte>(
      name = "empty non-null Byte",
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.BYTE_VALUE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "nullable Byte with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableByte),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_BYTE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<Byte?>(
      name = "empty nullable Byte",
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_BYTE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null ByteArray",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::byteArray),
      normalize = ::byteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<ByteArray>(
      name = "empty non-null ByteArray",
      normalize = ::byteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "nullable ByteArray with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableByteArray),
      normalize = ::nullableByteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<ByteArray?>(
      name = "empty nullable ByteArray",
      normalize = ::nullableByteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "non-null Array<Byte>",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::boxedByteArray),
      normalize = ::boxedByteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.BOXED_BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<Array<Byte>>(
      name = "empty non-null Array<Byte>",
      normalize = ::boxedByteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.BOXED_BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "nullable Array<Byte> with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableBoxedByteArray),
      normalize = ::nullableBoxedByteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_BOXED_BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    emptyScalarStorageCase<Array<Byte>?>(
      name = "empty nullable Array<Byte>",
      normalize = ::nullableBoxedByteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_BOXED_BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "empty non-null String table",
      expectedValues = emptyList(),
      seed = {},
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "empty nullable String table",
      expectedValues = emptyList(),
      seed = {},
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
          .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
      }
    ),
    scalarColumnCase(
      name = "relationship Article.account declared AccountId",
      expectedValues = articleRows
        .map(Article::account)
        .map(Account::id),
      seed = ::seedArticleRows,
      query = {
        Select
          .column(ARTICLE.ACCOUNT)
          .from(ARTICLE)
          .orderBy(ARTICLE.ID.asc())
      }
    )
  )

  private fun <T> scalarColumnCase(
    name: String,
    expectedValues: List<T>,
    query: () -> SelectSqlNode.SelectNode<T, Select.Select1, *>,
    seed: () -> Unit = ::seedScalarStorageRows,
    normalize: (T) -> Any? = ::identity
  ) = object : ScalarColumnCase<T>(
    name = name,
    expectedValues = expectedValues,
    seed = seed,
    query = query,
    normalize = normalize
  ) {}

  private fun <T> emptyScalarStorageCase(
    name: String,
    query: () -> SelectSqlNode.SelectNode<T, Select.Select1, *>,
    normalize: (T) -> Any? = ::identity
  ) = scalarColumnCase(
    name = name,
    expectedValues = emptyList(),
    query = query,
    seed = {},
    normalize = normalize
  )

  private fun byteArrayComparable(value: ByteArray) = value.toList()

  private fun nullableByteArrayComparable(value: ByteArray?) = value?.toList()

  private fun boxedByteArrayComparable(value: Array<Byte>) = value.toList()

  private fun nullableBoxedByteArrayComparable(value: Array<Byte>?) = value?.toList()

  private fun seedNonNullRows() {
    check(
      ImmutableValueWithFieldss
        .insert(nonNullRows)
        .execute()
    )
  }

  private fun seedNullableRows() {
    check(
      ImmutableValueWithNullableFieldss
        .insert(nullableRows)
        .execute()
    )
  }

  private fun seedScalarStorageRows() {
    check(
      ScalarStorageEntitys
        .insert(scalarStorageRows)
        .execute()
    )
  }

  private fun seedArticleRows() {
    articleRows
      .map(Article::account)
      .forEach { account ->
        assertSeedInserted(
          result = account.insert().execute(),
          modelName = "Account scalar column seed"
        )
      }
    articleRows.forEach { article ->
      assertSeedInserted(
        result = article.insert().execute(),
        modelName = "Article scalar column seed"
      )
    }
  }
}
