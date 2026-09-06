package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.ArticleTable.Companion.ARTICLE
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.Companion.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.Companion.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldss
import com.siimkinks.sqlitemagic.ScalarStorageEntityTable.Companion.SCALAR_STORAGE_ENTITY
import com.siimkinks.sqlitemagic.ScalarStorageEntitys
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SelectSqlNode
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
import com.siimkinks.sqlitemagic.runtime.support.assertSeedInserted

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

  private fun nonNullStringQuery() = Select
    .column(IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE)
    .from(IMMUTABLE_VALUE_WITH_FIELDS)
    .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())

  private fun nullableStringQuery() = Select
    .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING)
    .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
    .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())

  val cases: List<ScalarColumnCase<*>> = listOf(
    listOf<ScalarColumnCase<*>>(
      scalarColumnCase(
        name = "non-null String",
        expectedValues = nonNullRows.map(ImmutableValueWithFields::stringValue),
        seed = NonNullQueryFixture::seed,
        query = ::nonNullStringQuery
      )
    ),
    scalarColumnCases(
      name = "non-null Boolean",
      emptyName = "empty non-null Boolean table",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::aBoolean),
      seed = NonNullQueryFixture::seed,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "non-null Int",
      emptyName = "empty non-null Int table",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::integer),
      seed = NonNullQueryFixture::seed,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "non-null Double",
      emptyName = "empty non-null Double table",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::aDouble),
      seed = NonNullQueryFixture::seed,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "non-null Short",
      emptyName = "empty non-null Short table",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::aShort),
      seed = NonNullQueryFixture::seed,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "non-null TransformableObject",
      emptyName = "empty non-null TransformableObject table",
      expectedValues = nonNullRows.map(ImmutableValueWithFields::transformableObject),
      seed = NonNullQueryFixture::seed,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_FIELDS.TRANSFORMABLE_OBJECT)
          .from(IMMUTABLE_VALUE_WITH_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_FIELDS.ID.asc())
      }
    ),
    listOf<ScalarColumnCase<*>>(
      scalarColumnCase(
        name = "nullable String with null row",
        expectedValues = nullableRows.map(ImmutableValueWithNullableFields::string),
        seed = ::seedNullableRows,
        query = ::nullableStringQuery
      )
    ),
    scalarColumnCases(
      name = "nullable Boolean with null row",
      emptyName = "empty nullable Boolean table",
      expectedValues = nullableRows.map(ImmutableValueWithNullableFields::aBoolean),
      seed = ::seedNullableRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN)
          .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "nullable Int with null row",
      emptyName = "empty nullable Int table",
      expectedValues = nullableRows.map(ImmutableValueWithNullableFields::integer),
      seed = ::seedNullableRows,
      query = {
        Select
          .column(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER)
          .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)
          .orderBy(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "non-null Long",
      emptyName = "empty non-null Long",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::longValue),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.LONG_VALUE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "nullable Long with null row",
      emptyName = "empty nullable Long",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableLong),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_LONG)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "non-null Float",
      emptyName = "empty non-null Float",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::floatValue),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.FLOAT_VALUE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "nullable Float with null row",
      emptyName = "empty nullable Float",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableFloat),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_FLOAT)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "non-null Byte",
      emptyName = "empty non-null Byte",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::byteValue),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.BYTE_VALUE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "nullable Byte with null row",
      emptyName = "empty nullable Byte",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableByte),
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_BYTE)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "non-null ByteArray",
      emptyName = "empty non-null ByteArray",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::byteArray),
      normalize = ::byteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "nullable ByteArray with null row",
      emptyName = "empty nullable ByteArray",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableByteArray),
      normalize = ::nullableByteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "non-null Array<Byte>",
      emptyName = "empty non-null Array<Byte>",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::boxedByteArray),
      normalize = ::boxedByteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.BOXED_BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    scalarColumnCases(
      name = "nullable Array<Byte> with null row",
      emptyName = "empty nullable Array<Byte>",
      expectedValues = scalarStorageRows.map(ScalarStorageEntity::nullableBoxedByteArray),
      normalize = ::nullableBoxedByteArrayComparable,
      query = {
        Select
          .column(SCALAR_STORAGE_ENTITY.NULLABLE_BOXED_BYTE_ARRAY)
          .from(SCALAR_STORAGE_ENTITY)
          .orderBy(SCALAR_STORAGE_ENTITY.ID.asc())
      }
    ),
    listOf<ScalarColumnCase<*>>(
      emptyScalarColumnCase(
        name = "empty non-null String table",
        query = ::nonNullStringQuery
      ),
      emptyScalarColumnCase(
        name = "empty nullable String table",
        query = ::nullableStringQuery
      )
    ),
    scalarColumnCases(
      name = "relationship Article.account declared AccountId",
      emptyName = "empty relationship Article.account declared AccountId",
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
  ).flatten()

  private fun nonNullRowsQuery() = Select.from(IMMUTABLE_VALUE_WITH_FIELDS)

  private fun nullableStringCountQuery() =
    Select
      .column(Select.count(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING))
      .from(IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS)

  private fun selectedColumnCountQuery() =
    Select
      .column(Select.count())
      .from(IMMUTABLE_VALUE_WITH_FIELDS)

  private fun articleAccountCountQuery() =
    Select
      .column(Select.count(ARTICLE.ACCOUNT))
      .from(ARTICLE)

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
        .map(ImmutableValueWithNullableFields::string)
        .filterNotNull()
        .size
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
      expectedCount = articleRows
        .map(Article::account)
        .map(Account::id)
        .size
        .toLong(),
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

  private fun <T> scalarColumnCases(
    name: String,
    emptyName: String,
    expectedValues: List<T>,
    query: () -> SelectSqlNode.SelectNode<T, Select.Select1, *>,
    seed: () -> Unit = ::seedScalarStorageRows,
    normalize: (T) -> Any? = ::identity
  ): List<ScalarColumnCase<*>> = listOf(
    scalarColumnCase(
      name = name,
      expectedValues = expectedValues,
      query = query,
      seed = seed,
      normalize = normalize
    ),
    emptyScalarColumnCase(
      name = emptyName,
      query = query,
      normalize = normalize
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

  private fun <T> emptyScalarColumnCase(
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
