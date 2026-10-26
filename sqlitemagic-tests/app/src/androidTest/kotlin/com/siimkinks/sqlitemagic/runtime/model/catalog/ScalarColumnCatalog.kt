package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.ArticleTable.Companion.ARTICLE
import com.siimkinks.sqlitemagic.COUNT
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldss
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.ScalarStorageEntityTable.Companion.SCALAR_STORAGE_ENTITY
import com.siimkinks.sqlitemagic.ScalarStorageEntitys
import com.siimkinks.sqlitemagic.SelectQuery
import com.siimkinks.sqlitemagic.fixture.model.Account
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import com.siimkinks.sqlitemagic.fixture.model.Article
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.fixture.model.ImmutableValueWithNullableFields
import com.siimkinks.sqlitemagic.fixture.model.ScalarStorageEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.CountQueryCase
import com.siimkinks.sqlitemagic.runtime.model.ScalarColumnCase
import com.siimkinks.sqlitemagic.runtime.model.identity
import com.siimkinks.sqlitemagic.runtime.support.insertedResult

internal object ScalarColumnCatalog {
  private val nonNullRows = NonNullQueryFixture.nonNullRows

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

  private fun nonNullStringQuery() = SELECT
    .COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
    .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
    .ORDER_BY(IMMUTABLE_VALUE_WITH_FIELDS.ID.ASC())

  private fun nullableStringQuery() = SELECT
    .COLUMN(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
    .FROM(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
    .ORDER_BY(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.ASC())

  val cases = listOf<ScalarColumnCase<*>>(
    scalarColumnCase(
      name = "non-null String",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::stringValue),
      seed = NonNullQueryFixture::seed,
      query = ::nonNullStringQuery
    ),
    scalarColumnCase(
      name = "non-null Boolean",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::aBoolean),
      seed = NonNullQueryFixture::seed,
      query = {
        SELECT
          .COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN)
          .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
          .ORDER_BY(IMMUTABLE_VALUE_WITH_FIELDS.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "non-null Int",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::integer),
      seed = NonNullQueryFixture::seed,
      query = {
        SELECT
          .COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER)
          .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
          .ORDER_BY(IMMUTABLE_VALUE_WITH_FIELDS.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "non-null Double",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::aDouble),
      seed = NonNullQueryFixture::seed,
      query = {
        SELECT
          .COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE)
          .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
          .ORDER_BY(IMMUTABLE_VALUE_WITH_FIELDS.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "non-null Short",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::aShort),
      seed = NonNullQueryFixture::seed,
      query = {
        SELECT
          .COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT)
          .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
          .ORDER_BY(IMMUTABLE_VALUE_WITH_FIELDS.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "non-null TransformableObject",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::transformableObject),
      seed = NonNullQueryFixture::seed,
      query = {
        SELECT
          .COLUMN(IMMUTABLE_VALUE_WITH_FIELDS.TRANSFORMABLE_OBJECT)
          .FROM(IMMUTABLE_VALUE_WITH_FIELDS)
          .ORDER_BY(IMMUTABLE_VALUE_WITH_FIELDS.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "nullable String with null row",
      expectedValues = nullableRows.map(ImmutableValueWithNullableFields::string),
      seed = ::seedNullableRows,
      query = ::nullableStringQuery
    ),
    scalarColumnCase(
      name = "nullable Boolean with null row",
      expectedValues = nullableRows.map(ImmutableValueWithNullableFields::aBoolean),
      seed = ::seedNullableRows,
      query = {
        SELECT
          .COLUMN(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN)
          .FROM(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
          .ORDER_BY(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "nullable Int with null row",
      expectedValues = nullableRows.map(ImmutableValueWithNullableFields::integer),
      seed = ::seedNullableRows,
      query = {
        SELECT
          .COLUMN(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER)
          .FROM(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
          .ORDER_BY(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "non-null Long",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::longValue),
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.LONG_VALUE)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "nullable Long with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableLong),
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.NULLABLE_LONG)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "non-null Float",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::floatValue),
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.FLOAT_VALUE)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "nullable Float with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableFloat),
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.NULLABLE_FLOAT)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "non-null Byte",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::byteValue),
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.BYTE_VALUE)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "nullable Byte with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableByte),
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.NULLABLE_BYTE)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "non-null ByteArray",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::byteArray),
      normalize = ::nullableByteArrayComparable,
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.BYTE_ARRAY)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "nullable ByteArray with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableByteArray),
      normalize = ::nullableByteArrayComparable,
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.NULLABLE_BYTE_ARRAY)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "non-null Array<Byte>",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::boxedByteArray),
      normalize = ::nullableBoxedByteArrayComparable,
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.BOXED_BYTE_ARRAY)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    scalarColumnCase(
      name = "nullable Array<Byte> with null row",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableBoxedByteArray),
      normalize = ::nullableBoxedByteArrayComparable,
      query = {
        SELECT
          .COLUMN(SCALAR_STORAGE_ENTITY.NULLABLE_BOXED_BYTE_ARRAY)
          .FROM(SCALAR_STORAGE_ENTITY)
          .ORDER_BY(SCALAR_STORAGE_ENTITY.ID.ASC())
      }
    ),
    emptyScalarColumnCase(
      name = "empty non-null String table",
      query = ::nonNullStringQuery
    ),
    emptyScalarColumnCase(
      name = "empty nullable String table",
      query = ::nullableStringQuery
    ),
    scalarColumnCase(
      name = "relationship Article.account declared AccountId",
      expectedValues = articleRows
        .map(Article::account)
        .map(Account::id),
      seed = ::seedArticleRows,
      query = {
        SELECT
          .COLUMN(ARTICLE.ACCOUNT)
          .FROM(ARTICLE)
          .ORDER_BY(ARTICLE.ID.ASC())
      }
    )
  )

  private fun <T> scalarColumnCase(
    name: String,
    expectedValues: List<T>,
    query: () -> SelectQuery<out T, *>,
    seed: () -> Unit = ::seedScalarStorageRows,
    normalize: (T) -> Any? = ::identity
  ) = ScalarColumnCase(
    name = name,
    expectedValues = expectedValues,
    seed = seed,
    query = query,
    normalize = normalize
  )

  private fun <T> emptyScalarColumnCase(
    name: String,
    query: () -> SelectQuery<out T, *>
  ) = scalarColumnCase(
    name = name,
    expectedValues = emptyList(),
    query = query,
    seed = {}
  )

  private fun nonNullRowsQuery() = SELECT FROM IMMUTABLE_VALUE_WITH_FIELDS

  private fun nullableStringCountQuery() = SELECT
    .COLUMN(COUNT(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING))
    .FROM(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)

  private fun selectedColumnCountQuery() = SELECT
    .COLUMN(COUNT())
    .FROM(IMMUTABLE_VALUE_WITH_FIELDS)

  private fun articleAccountCountQuery() = SELECT
    .COLUMN(COUNT(ARTICLE.ACCOUNT))
    .FROM(ARTICLE)

  val countCases = listOf(
    CountQueryCase(
      name = "count all non-null rows",
      seed = NonNullQueryFixture::seed,
      expectedCount = nonNullRows.size.toLong(),
      execute = {
        nonNullRowsQuery()
          .count()
          .execute()
      },
      observeOnce = {
        nonNullRowsQuery()
          .count()
          .observe()
          .runQueryOnce()
      }
    ),
    CountQueryCase(
      name = "count non-null nullable String values",
      seed = ::seedNullableRows,
      expectedCount = nullableRows
        .count { it.string != null }
        .toLong(),
      execute = {
        checkNotNull(
          nullableStringCountQuery()
            .takeFirst()
            .execute()
        )
      },
      observeOnce = {
        nullableStringCountQuery()
          .takeFirst()
          .observe()
          .runQueryOnce()
      }
    ),
    CountQueryCase(
      name = "count selected column",
      seed = NonNullQueryFixture::seed,
      expectedCount = nonNullRows.size.toLong(),
      execute = {
        checkNotNull(
          selectedColumnCountQuery()
            .takeFirst()
            .execute()
        )
      },
      observeOnce = {
        selectedColumnCountQuery()
          .takeFirst()
          .observe()
          .runQueryOnce()
      }
    ),
    CountQueryCase(
      name = "count relationship Article.account",
      seed = ::seedArticleRows,
      expectedCount = articleRows.size.toLong(),
      execute = {
        checkNotNull(
          articleAccountCountQuery()
            .takeFirst()
            .execute()
        )
      },
      observeOnce = {
        articleAccountCountQuery()
          .takeFirst()
          .observe()
          .runQueryOnce()
      }
    )
  )

  private fun nullableByteArrayComparable(value: ByteArray?) = value?.toList()

  private fun nullableBoxedByteArrayComparable(value: Array<Byte>?) = value?.toList()

  private fun seedNullableRows() = check(
    ImmutableValueWithNullableFieldss
      .insert(nullableRows)
      .execute()
  )

  private fun seedScalarStorageRows() = check(
    ScalarStorageEntitys
      .insert(scalarStorageRows)
      .execute()
  )

  private fun seedArticleRows() {
    articleRows
      .map(Article::account)
      .forEach {
        insertedResult(
          result = it
            .insert()
            .execute(),
          modelName = "Account scalar column seed"
        )
      }
    articleRows.forEach {
      insertedResult(
        result = it
          .insert()
          .execute(),
        modelName = "Article scalar column seed"
      )
    }
  }
}
