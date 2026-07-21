package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.NameConst.PACKAGE_ROOT
import com.siimkinks.sqlitemagic.SqlStorageType
import com.siimkinks.sqlitemagic.annotation.TableOption
import com.siimkinks.sqlitemagic.element.mockParsedType
import com.siimkinks.sqlitemagic.transformer.mockTransformerElement
import com.siimkinks.sqlitemagic.utils.SqliteMagicSources.PACKAGE
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.STRING
import org.junit.jupiter.api.Test

internal class ModelElementTest {
  @Test
  fun `column retains complete flattened schema and relationship boundary`() {
    val transformer = mockTransformerElement()
    val relationship = mockRelationshipElement(
      referencedIdType = transformer.deserializedType,
      referencedIdSerializedType = transformer.serializedType,
      referencedIdTransformer = transformer,
      isHandledRecursively = true,
      onDeleteCascade = true
    )
    val access = mockPropertyAccess(
      path = mockPropertyPath("metadata", "owner")
    )
    val deserializedType = relationship.referencedTableType

    val actual = mockColumnElement(
      access = access,
      columnName = "meta_owner",
      deserializedType = deserializedType,
      isNullable = false,
      isSchemaNullable = true,
      defaultValue = "NULL",
      relationship = relationship,
      isUnique = true,
      index = mockIndexElement(
        name = "index_project_meta_owner",
        isUnique = true,
        belongsToIndex = "project_owner"
      ),
      embeddedPrefixes = listOf("meta_")
    )

    assertThat(actual)
      .isEqualTo(
        ColumnElement(
          access = access,
          columnName = "meta_owner",
          deserializedType = deserializedType,
          isNullable = false,
          isSchemaNullable = true,
          defaultValue = "NULL",
          transformer = null,
          relationship = relationship,
          id = null,
          isUnique = true,
          index = IndexElement(
            name = "index_project_meta_owner",
            isUnique = true,
            belongsToIndex = "project_owner"
          ),
          embeddedPrefixes = listOf("meta_")
        )
      )
    assertThat(actual.sqlStorageType)
      .isEqualTo(SqlStorageType.LONG)
  }

  @Test
  fun `column and logical property derive their type metadata from canonical values`() {
    val transformer = mockTransformerElement()
    val access = mockPropertyAccess(path = mockPropertyPath("amount"))
    val column = mockColumnElement(
      access = access,
      deserializedType = transformer.deserializedType,
      transformer = transformer,
      isNullable = true
    )
    val property = mockColumnPropertyElement(column = column)

    assertThat(column.serializedType)
      .isEqualTo(transformer.serializedType)
    assertThat(column.sqlStorageType)
      .isEqualTo(SqlStorageType.LONG)
    assertThat(property)
      .isEqualTo(ColumnPropertyElement(column = column))
    assertThat(property.access)
      .isEqualTo(access)
    assertThat(property.deserializedType)
      .isEqualTo(transformer.deserializedType)
    assertThat(property.isNullable)
      .isTrue()
  }

  @Test
  fun `table retains the complete logical and flattened generation boundary`() {
    val idPath = mockPropertyPath("id")
    val emailPath = mockPropertyPath("email")
    val detailsPath = mockPropertyPath("details")
    val latitudePath = mockPropertyPath("details", "geo", "latitude")
    val idAccess = mockPropertyAccess(path = idPath)
    val emailAccess = mockPropertyAccess(path = emailPath)
    val latitudeAccess = mockPropertyAccess(path = latitudePath)
    val stringType = parsedType(
      className = STRING,
      storageType = SqlStorageType.STRING
    )
    val doubleType = parsedType(
      className = DOUBLE,
      storageType = SqlStorageType.DOUBLE
    )
    val detailsType = parsedType(className = ClassName(PACKAGE, "Details"))
    val idColumn = mockColumnElement(
      access = idAccess,
      columnName = "account_id",
      deserializedType = stringType,
      id = mockIdElement(
        autoIncrementMode = AutoIncrementMode.DISABLED,
        isAutoIncrement = false,
        canAssignGeneratedId = false
      )
    )
    val emailColumn = mockColumnElement(
      access = emailAccess,
      columnName = "email",
      deserializedType = stringType,
      isUnique = true
    )
    val latitudeColumn = mockColumnElement(
      access = latitudeAccess,
      columnName = "details_geo_lat",
      deserializedType = doubleType,
      isSchemaNullable = true,
      defaultValue = "NULL",
      embeddedPrefixes = listOf("details_", "geo_")
    )
    val idProperty = mockColumnPropertyElement(column = idColumn)
    val emailProperty = mockColumnPropertyElement(column = emailColumn)
    val latitudeProperty = mockColumnPropertyElement(column = latitudeColumn)
    val geoProperty = mockEmbeddedPropertyElement(
      access = mockPropertyAccess(
        path = mockPropertyPath("details", "geo")
      ),
      deserializedType = parsedType(className = ClassName(PACKAGE, "Geo")),
      isNullable = true,
      prefix = "geo_",
      cumulativePrefix = "details_geo_",
      construction = mockModelConstruction(
        constructorParameters = listOf(latitudePath)
      ),
      properties = listOf(latitudeProperty)
    )
    val detailsProperty = mockEmbeddedPropertyElement(
      access = mockPropertyAccess(path = detailsPath),
      deserializedType = detailsType,
      isNullable = true,
      prefix = "details_",
      cumulativePrefix = "details_",
      construction = mockModelConstruction(
        constructorParameters = listOf(geoProperty.access.path)
      ),
      properties = listOf(geoProperty)
    )
    val tableType = parsedType(className = ClassName(PACKAGE, "Container", "Account"))
    val construction = mockModelConstruction(
      constructorParameters = listOf(idPath, detailsPath, emailPath)
    )
    val actual = mockTableElement(
      parsedType = tableType,
      tableName = "accounts",
      artifactStem = "Container_Account",
      declarationOrder = 3,
      options = setOf(TableOption.TEMPORARY, TableOption.WITHOUT_ROWID),
      construction = construction,
      properties = listOf(idProperty, detailsProperty, emailProperty)
    )

    assertThat(actual)
      .isEqualTo(
        TableElement(
          parsedType = tableType,
          tableName = "accounts",
          artifactStem = "Container_Account",
          declarationOrder = 3,
          options = setOf(TableOption.TEMPORARY, TableOption.WITHOUT_ROWID),
          construction = construction,
          properties = listOf(idProperty, detailsProperty, emailProperty)
        )
      )
    assertThat(actual.allColumns)
      .containsExactly(idColumn, latitudeColumn, emailColumn)
      .inOrder()
    assertThat(actual.modelName)
      .isEqualTo("Account")
    assertThat(actual.packageName)
      .isEqualTo(PACKAGE)
    assertThat(
      listOf(
        actual.generationNames.daoClassName,
        actual.generationNames.handlerClassName,
        actual.generationNames.tableClassName,
        actual.generationNames.extensionsFileName,
        actual.generationNames.operationsObjectName
      )
    )
      .containsExactly(
        ClassName(PACKAGE, "SqliteMagic_Container_Account_Dao"),
        ClassName(PACKAGE_ROOT, "SqliteMagic_Container_Account_Handler"),
        ClassName(PACKAGE_ROOT, "Container_AccountTable"),
        "_Container_Account",
        "Container_Accounts"
      )
      .inOrder()
  }

  @Test
  fun `table identity projections preserve optional IDs and eligible unique keys`() {
    val idColumn = mockColumnElement(id = mockIdElement())
    val eligibleUniqueColumn = mockColumnElement(
      columnName = "email",
      isUnique = true
    )
    val nullableUniqueColumn = mockColumnElement(
      columnName = "nickname",
      isNullable = true,
      isUnique = true
    )
    val table = mockTableElement(
      properties = listOf(idColumn, eligibleUniqueColumn, nullableUniqueColumn)
        .map(::mockColumnPropertyElement)
    )
    val tableWithoutId = mockTableElement(
      properties = listOf(eligibleUniqueColumn, nullableUniqueColumn)
        .map(::mockColumnPropertyElement)
    )

    assertThat(table.idColumn)
      .isEqualTo(idColumn)
    assertThat(table.eligibleUniqueColumns)
      .containsExactly(eligibleUniqueColumn)
    assertThat(tableWithoutId.idColumn)
      .isNull()
  }
}

private fun parsedType(
  className: ClassName,
  storageType: SqlStorageType? = null
) = mockParsedType(
  typeName = className,
  sqlStorageType = storageType
)
