package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.DataClassWithFieldsTable.DATA_CLASS_WITH_FIELDS
import com.siimkinks.sqlitemagic.DataClassWithMethodsTable.DATA_CLASS_WITH_METHODS
import com.siimkinks.sqlitemagic.DataClassWithNullableFieldsTable.DATA_CLASS_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.DataClassWithNullableMethodsTable.DATA_CLASS_WITH_NULLABLE_METHODS
import com.siimkinks.sqlitemagic.GenericsMutableTable.GENERICS_MUTABLE
import com.siimkinks.sqlitemagic.SimpleDataClassWithFieldsAndUniqueTable.SIMPLE_DATA_CLASS_WITH_FIELDS_AND_UNIQUE
import com.siimkinks.sqlitemagic.SimpleDataClassWithMethodsAndUniqueTable.SIMPLE_DATA_CLASS_WITH_METHODS_AND_UNIQUE
import com.siimkinks.sqlitemagic.SimpleImmutableWithBuilderAndUniqueTable.SIMPLE_IMMUTABLE_WITH_BUILDER_AND_UNIQUE
import com.siimkinks.sqlitemagic.SimpleImmutableWithCreatorAndUniqueTable.SIMPLE_IMMUTABLE_WITH_CREATOR_AND_UNIQUE
import com.siimkinks.sqlitemagic.SimpleMutableWithUniqueAndNullableIdTable.SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID
import com.siimkinks.sqlitemagic.SimpleMutableWithUniqueTable.SIMPLE_MUTABLE_WITH_UNIQUE
import com.siimkinks.sqlitemagic.SimpleValueWithBuilderAndNullableFieldsTable.SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER
import com.siimkinks.sqlitemagic.SimpleValueWithCreatorAndNullableFieldsTable.SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR
import com.siimkinks.sqlitemagic.entity.*
import com.siimkinks.sqlitemagic.model.TestUtil.assertTableCount
import com.siimkinks.sqlitemagic.model.immutable.*

val simpleMutableAutoIdTestModel = TestModelWithNullableColumns<Author>(
    testModel = object : TestModel<Author> {
      override val table: Table<Author>
        get() = AUTHOR
      override val idColumn: Column<Long, Long, Number, *, Nullable>
        get() = AUTHOR.ID

      override fun deleteTable() {
        Authors.deleteTable().execute()
      }

      override fun newRandom(): Author = Author.newRandom()
      override fun setId(v: Author, id: Long?): Author {
        v.id = id
        return v
      }

      override fun getId(v: Author): Long? = v.id
      override fun valsAreEqual(v1: Author, v2: Author): Boolean = v1 == v2
      override fun updateAllVals(v: Author, id: Long): Author {
        val newRandom = Author.newRandom()
        newRandom.id = id
        return newRandom
      }

      override fun insertBuilder(v: Author): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<Author>): EntityBulkInsertBuilder = Authors.insert(v)
      override fun updateBuilder(v: Author): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<Author>): EntityBulkUpdateBuilder = Authors.update(v)
      override fun persistBuilder(v: Author): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<Author>): EntityBulkPersistBuilder = Authors.persist(v)
      override fun deleteBuilder(v: Author): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<Author>): EntityBulkDeleteBuilder = Authors.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = Authors.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, AUTHOR)
    },
    nullableColumns = object : NullableColumns<Author> {
      override fun nullSomeColumns(target: Author): Author {
        target.name = null
        target.boxedBoolean = null
        return target
      }

      override fun assertAllExceptNulledColumnsAreUpdated(target: Author, nulledVal: Author) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.primitiveBoolean).isEqualTo(nulledVal.primitiveBoolean)
        assertThat(target.name).isNotEqualTo(nulledVal.name)
        assertThat(target.name).isNotNull()
        assertThat(target.boxedBoolean).isNotEqualTo(nulledVal.boxedBoolean)
        assertThat(target.boxedBoolean).isNotNull()
      }
    }
)

val simpleImmutableWithBuilderAutoIdTestModel = object : TestModel<SimpleValueWithBuilder> {
  override val table: Table<SimpleValueWithBuilder>
    get() = SIMPLE_VALUE_WITH_BUILDER
  override val idColumn: Column<Long, Long, Number, *, Nullable>
    get() = SIMPLE_VALUE_WITH_BUILDER.ID

  override fun deleteTable() {
    SimpleValueWithBuilders.deleteTable().execute()
  }

  override fun newRandom(): SimpleValueWithBuilder = SimpleValueWithBuilder.newRandom().build()
  override fun setId(v: SimpleValueWithBuilder, id: Long?): SimpleValueWithBuilder =
      SqliteMagic_SimpleValueWithBuilder_Dao.setId(v, id)

  override fun getId(v: SimpleValueWithBuilder): Long? = v.id()
  override fun valsAreEqual(v1: SimpleValueWithBuilder, v2: SimpleValueWithBuilder): Boolean =
      v1.equalsWithoutId(v2)

  override fun updateAllVals(v: SimpleValueWithBuilder, id: Long): SimpleValueWithBuilder =
      SimpleValueWithBuilder.newRandom()
          .id(id)
          .build()

  override fun insertBuilder(v: SimpleValueWithBuilder): EntityInsertBuilder = v.insert()
  override fun bulkInsertBuilder(v: Iterable<SimpleValueWithBuilder>): EntityBulkInsertBuilder = SimpleValueWithBuilders.insert(v)
  override fun updateBuilder(v: SimpleValueWithBuilder): EntityUpdateBuilder = v.update()
  override fun bulkUpdateBuilder(v: Iterable<SimpleValueWithBuilder>): EntityBulkUpdateBuilder = SimpleValueWithBuilders.update(v)
  override fun persistBuilder(v: SimpleValueWithBuilder): EntityPersistBuilder = v.persist()
  override fun bulkPersistBuilder(v: Iterable<SimpleValueWithBuilder>): EntityBulkPersistBuilder = SimpleValueWithBuilders.persist(v)
  override fun deleteBuilder(v: SimpleValueWithBuilder): EntityDeleteBuilder = v.delete()
  override fun bulkDeleteBuilder(v: Collection<SimpleValueWithBuilder>): EntityBulkDeleteBuilder = SimpleValueWithBuilders.delete(v)
  override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_VALUE_WITH_BUILDER)
  override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleValueWithBuilders.deleteTable()
}

val simpleImmutableWithCreatorAutoIdTestModel = object : TestModel<SimpleValueWithCreator> {
  override val table: Table<SimpleValueWithCreator>
    get() = SIMPLE_VALUE_WITH_CREATOR
  override val idColumn: Column<Long, Long, Number, *, Nullable>
    get() = SIMPLE_VALUE_WITH_CREATOR.ID

  override fun deleteTable() {
    SimpleValueWithCreators.deleteTable().execute()
  }

  override fun newRandom(): SimpleValueWithCreator = SimpleValueWithCreator.newRandom()
  override fun setId(v: SimpleValueWithCreator, id: Long?): SimpleValueWithCreator =
      SqliteMagic_SimpleValueWithCreator_Dao.setId(v, id)

  override fun getId(v: SimpleValueWithCreator): Long? = v.id()
  override fun valsAreEqual(v1: SimpleValueWithCreator, v2: SimpleValueWithCreator): Boolean =
      v1.equalsWithoutId(v2)

  override fun updateAllVals(v: SimpleValueWithCreator, id: Long): SimpleValueWithCreator =
      SimpleValueWithCreator.newRandom(id)

  override fun insertBuilder(v: SimpleValueWithCreator): EntityInsertBuilder = v.insert()
  override fun bulkInsertBuilder(v: Iterable<SimpleValueWithCreator>): EntityBulkInsertBuilder = SimpleValueWithCreators.insert(v)
  override fun updateBuilder(v: SimpleValueWithCreator): EntityUpdateBuilder = v.update()
  override fun bulkUpdateBuilder(v: Iterable<SimpleValueWithCreator>): EntityBulkUpdateBuilder = SimpleValueWithCreators.update(v)
  override fun persistBuilder(v: SimpleValueWithCreator): EntityPersistBuilder = v.persist()
  override fun bulkPersistBuilder(v: Iterable<SimpleValueWithCreator>): EntityBulkPersistBuilder = SimpleValueWithCreators.persist(v)
  override fun deleteBuilder(v: SimpleValueWithCreator): EntityDeleteBuilder = v.delete()
  override fun bulkDeleteBuilder(v: Collection<SimpleValueWithCreator>): EntityBulkDeleteBuilder = SimpleValueWithCreators.delete(v)
  override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_VALUE_WITH_CREATOR)
  override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleValueWithCreators.deleteTable()
}

val simpleDataClassWithFieldsAutoIdTestModel = object : TestModel<DataClassWithFields> {
  override val table: Table<DataClassWithFields>
    get() = DATA_CLASS_WITH_FIELDS
  override val idColumn: Column<Long, Long, Number, *, Nullable>
    get() = DATA_CLASS_WITH_FIELDS.ID

  override fun deleteTable() {
    DataClassWithFieldss.deleteTable().execute()
  }

  override fun newRandom(): DataClassWithFields = DataClassWithFields.newRandom()
  override fun setId(v: DataClassWithFields, id: Long?): DataClassWithFields =
      SqliteMagic_DataClassWithFields_Dao.setId(v, id)

  override fun getId(v: DataClassWithFields): Long? = v.id
  override fun valsAreEqual(v1: DataClassWithFields, v2: DataClassWithFields): Boolean =
      v1.equalsWithoutId(v2)

  override fun updateAllVals(v: DataClassWithFields, id: Long): DataClassWithFields =
      DataClassWithFields.newRandom(id)

  override fun insertBuilder(v: DataClassWithFields): EntityInsertBuilder = v.insert()
  override fun bulkInsertBuilder(v: Iterable<DataClassWithFields>): EntityBulkInsertBuilder = DataClassWithFieldss.insert(v)
  override fun updateBuilder(v: DataClassWithFields): EntityUpdateBuilder = v.update()
  override fun bulkUpdateBuilder(v: Iterable<DataClassWithFields>): EntityBulkUpdateBuilder = DataClassWithFieldss.update(v)
  override fun persistBuilder(v: DataClassWithFields): EntityPersistBuilder = v.persist()
  override fun bulkPersistBuilder(v: Iterable<DataClassWithFields>): EntityBulkPersistBuilder = DataClassWithFieldss.persist(v)
  override fun deleteBuilder(v: DataClassWithFields): EntityDeleteBuilder = v.delete()
  override fun bulkDeleteBuilder(v: Collection<DataClassWithFields>): EntityBulkDeleteBuilder = DataClassWithFieldss.delete(v)
  override fun assertNoValsInTables() = assertTableCount(0, DATA_CLASS_WITH_FIELDS)
  override fun deleteTableBuilder(): EntityDeleteTableBuilder = DataClassWithFieldss.deleteTable()
}

val simpleDataClassWithMethodsAutoIdTestModel = object : TestModel<DataClassWithMethods> {
  override val table: Table<DataClassWithMethods>
    get() = DATA_CLASS_WITH_METHODS
  override val idColumn: Column<Long, Long, Number, *, Nullable>
    get() = DATA_CLASS_WITH_METHODS.ID

  override fun deleteTable() {
    DataClassWithMethodss.deleteTable().execute()
  }

  override fun newRandom(): DataClassWithMethods = DataClassWithMethods.newRandom()
  override fun setId(v: DataClassWithMethods, id: Long?): DataClassWithMethods =
      SqliteMagic_DataClassWithMethods_Dao.setId(v, id)

  override fun getId(v: DataClassWithMethods): Long? = v.id
  override fun valsAreEqual(v1: DataClassWithMethods, v2: DataClassWithMethods): Boolean =
      v1.equalsWithoutId(v2)

  override fun updateAllVals(v: DataClassWithMethods, id: Long): DataClassWithMethods =
      DataClassWithMethods.newRandom(id)

  override fun insertBuilder(v: DataClassWithMethods): EntityInsertBuilder = v.insert()
  override fun bulkInsertBuilder(v: Iterable<DataClassWithMethods>): EntityBulkInsertBuilder = DataClassWithMethodss.insert(v)
  override fun updateBuilder(v: DataClassWithMethods): EntityUpdateBuilder = v.update()
  override fun bulkUpdateBuilder(v: Iterable<DataClassWithMethods>): EntityBulkUpdateBuilder = DataClassWithMethodss.update(v)
  override fun persistBuilder(v: DataClassWithMethods): EntityPersistBuilder = v.persist()
  override fun bulkPersistBuilder(v: Iterable<DataClassWithMethods>): EntityBulkPersistBuilder = DataClassWithMethodss.persist(v)
  override fun deleteBuilder(v: DataClassWithMethods): EntityDeleteBuilder = v.delete()
  override fun bulkDeleteBuilder(v: Collection<DataClassWithMethods>): EntityBulkDeleteBuilder = DataClassWithMethodss.delete(v)
  override fun assertNoValsInTables() = assertTableCount(0, DATA_CLASS_WITH_METHODS)
  override fun deleteTableBuilder(): EntityDeleteTableBuilder = DataClassWithMethodss.deleteTable()
}

val simpleGenericsMutableFixedIdUniqueNullableTestModel = TestModelWithUniqueNullableColumns<GenericsMutable>(
    testModel = object : TestModel<GenericsMutable> {
      override val table: Table<GenericsMutable>
        get() = GENERICS_MUTABLE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = GENERICS_MUTABLE.ID

      override fun deleteTable() {
        GenericsMutables.deleteTable().execute()
      }

      override fun newRandom(): GenericsMutable =
          GenericsMutable.newRandom()

      override fun setId(v: GenericsMutable, id: Long?): GenericsMutable {
        SqliteMagic_GenericsMutable_Dao.setId(v, id!!)
        return v
      }

      override fun getId(v: GenericsMutable): Long? = v.id

      override fun valsAreEqual(v1: GenericsMutable, v2: GenericsMutable): Boolean = v1 == v2

      override fun updateAllVals(v: GenericsMutable, id: Long): GenericsMutable {
        val newRandom = GenericsMutable.newRandom()
        newRandom.id = id
        return newRandom
      }

      override fun insertBuilder(v: GenericsMutable): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<GenericsMutable>): EntityBulkInsertBuilder = GenericsMutables.insert(v)
      override fun updateBuilder(v: GenericsMutable): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<GenericsMutable>): EntityBulkUpdateBuilder = GenericsMutables.update(v)
      override fun persistBuilder(v: GenericsMutable): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<GenericsMutable>): EntityBulkPersistBuilder = GenericsMutables.persist(v)
      override fun deleteBuilder(v: GenericsMutable): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<GenericsMutable>): EntityBulkDeleteBuilder = GenericsMutables.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = GenericsMutables.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, GENERICS_MUTABLE)
    },
    uniqueValue = object : UniqueValued<GenericsMutable> {
      override val uniqueColumn: Unique<NotNullable>
        get() = GENERICS_MUTABLE.UNIQUE_VAL

      override fun transferUniqueVal(src: GenericsMutable, target: GenericsMutable): GenericsMutable {
        target.uniqueVal = src.uniqueVal
        return target
      }
    },
    nullableColumns = object: NullableColumns<GenericsMutable> {
      override fun nullSomeColumns(target: GenericsMutable): GenericsMutable {
        target.listOfStrings = null
        return target
      }

      override fun assertAllExceptNulledColumnsAreUpdated(target: GenericsMutable, nulledVal: GenericsMutable) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.listOfInts).isEqualTo(nulledVal.listOfInts)
        assertThat(target.map).isEqualTo(nulledVal.map)
        assertThat(target.listOfStrings).isNotEqualTo(nulledVal.listOfStrings)
        assertThat(target.listOfStrings).isNotNull()
      }
    }
)

val simpleMutableFixedIdUniqueNullableTestModel = TestModelWithUniqueNullableColumns<SimpleMutableWithUnique>(
    testModel = object : TestModel<SimpleMutableWithUnique> {
      override val table: Table<SimpleMutableWithUnique>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE.ID

      override fun deleteTable() {
        SimpleMutableWithUniques.deleteTable().execute()
      }

      override fun newRandom(): SimpleMutableWithUnique =
          SimpleMutableWithUnique.newRandom()

      override fun setId(v: SimpleMutableWithUnique, id: Long?): SimpleMutableWithUnique {
        SqliteMagic_SimpleMutableWithUnique_Dao.setId(v, id!!)
        return v
      }

      override fun getId(v: SimpleMutableWithUnique): Long? = v.id

      override fun valsAreEqual(v1: SimpleMutableWithUnique, v2: SimpleMutableWithUnique): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleMutableWithUnique, id: Long): SimpleMutableWithUnique {
        val newRandom = SimpleMutableWithUnique.newRandom()
        newRandom.id = id
        return newRandom
      }

      override fun insertBuilder(v: SimpleMutableWithUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleMutableWithUnique>): EntityBulkInsertBuilder = SimpleMutableWithUniques.insert(v)
      override fun updateBuilder(v: SimpleMutableWithUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleMutableWithUnique>): EntityBulkUpdateBuilder = SimpleMutableWithUniques.update(v)
      override fun persistBuilder(v: SimpleMutableWithUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleMutableWithUnique>): EntityBulkPersistBuilder = SimpleMutableWithUniques.persist(v)
      override fun deleteBuilder(v: SimpleMutableWithUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleMutableWithUnique>): EntityBulkDeleteBuilder = SimpleMutableWithUniques.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleMutableWithUniques.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_MUTABLE_WITH_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleMutableWithUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE.UNIQUE_VAL

      override fun transferUniqueVal(src: SimpleMutableWithUnique, target: SimpleMutableWithUnique): SimpleMutableWithUnique {
        target.uniqueVal = src.uniqueVal
        return target
      }
    },
    nullableColumns = object: NullableColumns<SimpleMutableWithUnique> {
      override fun nullSomeColumns(target: SimpleMutableWithUnique): SimpleMutableWithUnique {
        target.string = null
        return target
      }

      override fun assertAllExceptNulledColumnsAreUpdated(target: SimpleMutableWithUnique, nulledVal: SimpleMutableWithUnique) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }
    }
)

val simpleImmutableWithBuilderFixedIdUniqueNullableTestModel = TestModelWithUniqueNullableColumns<SimpleImmutableWithBuilderAndUnique>(
    testModel = object : TestModel<SimpleImmutableWithBuilderAndUnique> {
      override val table: Table<SimpleImmutableWithBuilderAndUnique>
        get() = SIMPLE_IMMUTABLE_WITH_BUILDER_AND_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = SIMPLE_IMMUTABLE_WITH_BUILDER_AND_UNIQUE.ID

      override fun deleteTable() {
        SimpleImmutableWithBuilderAndUniques.deleteTable().execute()
      }

      override fun newRandom(): SimpleImmutableWithBuilderAndUnique =
          SimpleImmutableWithBuilderAndUnique.newRandom()

      override fun setId(v: SimpleImmutableWithBuilderAndUnique, id: Long?): SimpleImmutableWithBuilderAndUnique =
          v.copy()
              .id(id!!)
              .build()

      override fun getId(v: SimpleImmutableWithBuilderAndUnique): Long? = v.id()

      override fun valsAreEqual(v1: SimpleImmutableWithBuilderAndUnique, v2: SimpleImmutableWithBuilderAndUnique): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleImmutableWithBuilderAndUnique, id: Long): SimpleImmutableWithBuilderAndUnique =
          SimpleImmutableWithBuilderAndUnique.newRandomWithId(id)

      override fun insertBuilder(v: SimpleImmutableWithBuilderAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleImmutableWithBuilderAndUnique>): EntityBulkInsertBuilder = SimpleImmutableWithBuilderAndUniques.insert(v)
      override fun updateBuilder(v: SimpleImmutableWithBuilderAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleImmutableWithBuilderAndUnique>): EntityBulkUpdateBuilder = SimpleImmutableWithBuilderAndUniques.update(v)
      override fun persistBuilder(v: SimpleImmutableWithBuilderAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleImmutableWithBuilderAndUnique>): EntityBulkPersistBuilder = SimpleImmutableWithBuilderAndUniques.persist(v)
      override fun deleteBuilder(v: SimpleImmutableWithBuilderAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleImmutableWithBuilderAndUnique>): EntityBulkDeleteBuilder = SimpleImmutableWithBuilderAndUniques.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleImmutableWithBuilderAndUniques.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_IMMUTABLE_WITH_BUILDER_AND_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleImmutableWithBuilderAndUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = SIMPLE_IMMUTABLE_WITH_BUILDER_AND_UNIQUE.UNIQUE_VAL

      override fun transferUniqueVal(src: SimpleImmutableWithBuilderAndUnique, target: SimpleImmutableWithBuilderAndUnique): SimpleImmutableWithBuilderAndUnique =
          target.copy()
              .uniqueVal(src.uniqueVal())
              .build()
    },
    nullableColumns = object: NullableColumns<SimpleImmutableWithBuilderAndUnique> {
      override fun nullSomeColumns(target: SimpleImmutableWithBuilderAndUnique): SimpleImmutableWithBuilderAndUnique =
          target.copy().string(null).build()

      override fun assertAllExceptNulledColumnsAreUpdated(target: SimpleImmutableWithBuilderAndUnique, nulledVal: SimpleImmutableWithBuilderAndUnique) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.uniqueVal()).isEqualTo(nulledVal.uniqueVal())
        assertThat(target.string()).isNotEqualTo(nulledVal.string())
        assertThat(target.string()).isNotNull()
      }
    }
)

val simpleImmutableWithCreatorFixedIdUniqueNullableTestModel = TestModelWithUniqueNullableColumns<SimpleImmutableWithCreatorAndUnique>(
    testModel = object : TestModel<SimpleImmutableWithCreatorAndUnique> {
      override val table: Table<SimpleImmutableWithCreatorAndUnique>
        get() = SIMPLE_IMMUTABLE_WITH_CREATOR_AND_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = SIMPLE_IMMUTABLE_WITH_CREATOR_AND_UNIQUE.ID

      override fun deleteTable() {
        SimpleImmutableWithCreatorAndUniques.deleteTable().execute()
      }

      override fun newRandom(): SimpleImmutableWithCreatorAndUnique =
          SimpleImmutableWithCreatorAndUnique.newRandom()

      override fun setId(v: SimpleImmutableWithCreatorAndUnique, id: Long?): SimpleImmutableWithCreatorAndUnique =
          v.setId(id!!)

      override fun getId(v: SimpleImmutableWithCreatorAndUnique): Long? = v.id()

      override fun valsAreEqual(v1: SimpleImmutableWithCreatorAndUnique, v2: SimpleImmutableWithCreatorAndUnique): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleImmutableWithCreatorAndUnique, id: Long): SimpleImmutableWithCreatorAndUnique =
          SimpleImmutableWithCreatorAndUnique.newRandomWithId(id)

      override fun insertBuilder(v: SimpleImmutableWithCreatorAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleImmutableWithCreatorAndUnique>): EntityBulkInsertBuilder = SimpleImmutableWithCreatorAndUniques.insert(v)
      override fun updateBuilder(v: SimpleImmutableWithCreatorAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleImmutableWithCreatorAndUnique>): EntityBulkUpdateBuilder = SimpleImmutableWithCreatorAndUniques.update(v)
      override fun persistBuilder(v: SimpleImmutableWithCreatorAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleImmutableWithCreatorAndUnique>): EntityBulkPersistBuilder = SimpleImmutableWithCreatorAndUniques.persist(v)
      override fun deleteBuilder(v: SimpleImmutableWithCreatorAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleImmutableWithCreatorAndUnique>): EntityBulkDeleteBuilder = SimpleImmutableWithCreatorAndUniques.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleImmutableWithCreatorAndUniques.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_IMMUTABLE_WITH_CREATOR_AND_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleImmutableWithCreatorAndUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = SIMPLE_IMMUTABLE_WITH_CREATOR_AND_UNIQUE.UNIQUE_VAL

      override fun transferUniqueVal(src: SimpleImmutableWithCreatorAndUnique, target: SimpleImmutableWithCreatorAndUnique): SimpleImmutableWithCreatorAndUnique =
          target.setUniqueVal(src.uniqueVal())
    },
    nullableColumns = object: NullableColumns<SimpleImmutableWithCreatorAndUnique> {
      override fun nullSomeColumns(target: SimpleImmutableWithCreatorAndUnique): SimpleImmutableWithCreatorAndUnique =
          SimpleImmutableWithCreatorAndUnique.create(target.id(), target.uniqueVal(), null)

      override fun assertAllExceptNulledColumnsAreUpdated(target: SimpleImmutableWithCreatorAndUnique, nulledVal: SimpleImmutableWithCreatorAndUnique) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.uniqueVal()).isEqualTo(nulledVal.uniqueVal())
        assertThat(target.string()).isNotEqualTo(nulledVal.string())
        assertThat(target.string()).isNotNull()
      }
    }
)

val simpleDataClassWithFieldsFixedIdUniqueNullableTestModel = TestModelWithUniqueNullableColumns<SimpleDataClassWithFieldsAndUnique>(
    testModel = object : TestModel<SimpleDataClassWithFieldsAndUnique> {
      override val table: Table<SimpleDataClassWithFieldsAndUnique>
        get() = SIMPLE_DATA_CLASS_WITH_FIELDS_AND_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = SIMPLE_DATA_CLASS_WITH_FIELDS_AND_UNIQUE.ID

      override fun deleteTable() {
        SimpleDataClassWithFieldsAndUniques.deleteTable().execute()
      }

      override fun newRandom(): SimpleDataClassWithFieldsAndUnique =
          SimpleDataClassWithFieldsAndUnique.newRandom()

      override fun setId(v: SimpleDataClassWithFieldsAndUnique, id: Long?): SimpleDataClassWithFieldsAndUnique =
          v.setId(id!!)

      override fun getId(v: SimpleDataClassWithFieldsAndUnique): Long? = v.id

      override fun valsAreEqual(v1: SimpleDataClassWithFieldsAndUnique, v2: SimpleDataClassWithFieldsAndUnique): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleDataClassWithFieldsAndUnique, id: Long): SimpleDataClassWithFieldsAndUnique =
          SimpleDataClassWithFieldsAndUnique.newRandomWithId(id)

      override fun insertBuilder(v: SimpleDataClassWithFieldsAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleDataClassWithFieldsAndUnique>): EntityBulkInsertBuilder = SimpleDataClassWithFieldsAndUniques.insert(v)
      override fun updateBuilder(v: SimpleDataClassWithFieldsAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleDataClassWithFieldsAndUnique>): EntityBulkUpdateBuilder = SimpleDataClassWithFieldsAndUniques.update(v)
      override fun persistBuilder(v: SimpleDataClassWithFieldsAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleDataClassWithFieldsAndUnique>): EntityBulkPersistBuilder = SimpleDataClassWithFieldsAndUniques.persist(v)
      override fun deleteBuilder(v: SimpleDataClassWithFieldsAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleDataClassWithFieldsAndUnique>): EntityBulkDeleteBuilder = SimpleDataClassWithFieldsAndUniques.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleDataClassWithFieldsAndUniques.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_DATA_CLASS_WITH_FIELDS_AND_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleDataClassWithFieldsAndUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = SIMPLE_DATA_CLASS_WITH_FIELDS_AND_UNIQUE.UNIQUE_VAL

      override fun transferUniqueVal(src: SimpleDataClassWithFieldsAndUnique, target: SimpleDataClassWithFieldsAndUnique): SimpleDataClassWithFieldsAndUnique =
          target.setUniqueVal(src.uniqueVal)
    },
    nullableColumns = object: NullableColumns<SimpleDataClassWithFieldsAndUnique> {
      override fun nullSomeColumns(target: SimpleDataClassWithFieldsAndUnique): SimpleDataClassWithFieldsAndUnique =
          SimpleDataClassWithFieldsAndUnique(target.id, target.uniqueVal, null)

      override fun assertAllExceptNulledColumnsAreUpdated(target: SimpleDataClassWithFieldsAndUnique, nulledVal: SimpleDataClassWithFieldsAndUnique) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }
    }
)

val simpleDataClassWithMethodsFixedIdUniqueNullableTestModel = TestModelWithUniqueNullableColumns<SimpleDataClassWithMethodsAndUnique>(
    testModel = object : TestModel<SimpleDataClassWithMethodsAndUnique> {
      override val table: Table<SimpleDataClassWithMethodsAndUnique>
        get() = SIMPLE_DATA_CLASS_WITH_METHODS_AND_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = SIMPLE_DATA_CLASS_WITH_METHODS_AND_UNIQUE.ID

      override fun deleteTable() {
        SimpleDataClassWithMethodsAndUniques.deleteTable().execute()
      }

      override fun newRandom(): SimpleDataClassWithMethodsAndUnique =
          SimpleDataClassWithMethodsAndUnique.newRandom()

      override fun setId(v: SimpleDataClassWithMethodsAndUnique, id: Long?): SimpleDataClassWithMethodsAndUnique =
          v.setId(id!!)

      override fun getId(v: SimpleDataClassWithMethodsAndUnique): Long? = v.id

      override fun valsAreEqual(v1: SimpleDataClassWithMethodsAndUnique, v2: SimpleDataClassWithMethodsAndUnique): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleDataClassWithMethodsAndUnique, id: Long): SimpleDataClassWithMethodsAndUnique =
          SimpleDataClassWithMethodsAndUnique.newRandomWithId(id)

      override fun insertBuilder(v: SimpleDataClassWithMethodsAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleDataClassWithMethodsAndUnique>): EntityBulkInsertBuilder = SimpleDataClassWithMethodsAndUniques.insert(v)
      override fun updateBuilder(v: SimpleDataClassWithMethodsAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleDataClassWithMethodsAndUnique>): EntityBulkUpdateBuilder = SimpleDataClassWithMethodsAndUniques.update(v)
      override fun persistBuilder(v: SimpleDataClassWithMethodsAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleDataClassWithMethodsAndUnique>): EntityBulkPersistBuilder = SimpleDataClassWithMethodsAndUniques.persist(v)
      override fun deleteBuilder(v: SimpleDataClassWithMethodsAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleDataClassWithMethodsAndUnique>): EntityBulkDeleteBuilder = SimpleDataClassWithMethodsAndUniques.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleDataClassWithMethodsAndUniques.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_DATA_CLASS_WITH_METHODS_AND_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleDataClassWithMethodsAndUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = SIMPLE_DATA_CLASS_WITH_METHODS_AND_UNIQUE.UNIQUE_VAL

      override fun transferUniqueVal(src: SimpleDataClassWithMethodsAndUnique, target: SimpleDataClassWithMethodsAndUnique): SimpleDataClassWithMethodsAndUnique =
          target.setUniqueVal(src.uniqueVal)
    },
    nullableColumns = object: NullableColumns<SimpleDataClassWithMethodsAndUnique> {
      override fun nullSomeColumns(target: SimpleDataClassWithMethodsAndUnique): SimpleDataClassWithMethodsAndUnique =
          SimpleDataClassWithMethodsAndUnique(target.id, target.uniqueVal, null)

      override fun assertAllExceptNulledColumnsAreUpdated(target: SimpleDataClassWithMethodsAndUnique, nulledVal: SimpleDataClassWithMethodsAndUnique) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }
    }
)

val simpleImmutableWithBuilderNullableTestModel = TestModelWithNullableColumns<SimpleValueWithBuilderAndNullableFields>(
    testModel = object: TestModel<SimpleValueWithBuilderAndNullableFields> {
      override val table: Table<SimpleValueWithBuilderAndNullableFields>
        get() = SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS
      override val idColumn: Column<Long, Long, Number, *, Nullable>
        get() = SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS.ID

      override fun deleteTable() {
        SimpleValueWithBuilderAndNullableFieldss.deleteTable().execute()
      }

      override fun newRandom(): SimpleValueWithBuilderAndNullableFields = SimpleValueWithBuilderAndNullableFields.newRandom().build()
      override fun setId(v: SimpleValueWithBuilderAndNullableFields, id: Long?): SimpleValueWithBuilderAndNullableFields =
          SqliteMagic_SimpleValueWithBuilderAndNullableFields_Dao.setId(v, id)

      override fun getId(v: SimpleValueWithBuilderAndNullableFields): Long? = v.id()
      override fun valsAreEqual(v1: SimpleValueWithBuilderAndNullableFields, v2: SimpleValueWithBuilderAndNullableFields): Boolean =
          v1.equalsWithoutId(v2)

      override fun updateAllVals(v: SimpleValueWithBuilderAndNullableFields, id: Long): SimpleValueWithBuilderAndNullableFields =
          SimpleValueWithBuilderAndNullableFields.newRandom()
              .id(id)
              .build()

      override fun insertBuilder(v: SimpleValueWithBuilderAndNullableFields): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleValueWithBuilderAndNullableFields>): EntityBulkInsertBuilder = SimpleValueWithBuilderAndNullableFieldss.insert(v)
      override fun updateBuilder(v: SimpleValueWithBuilderAndNullableFields): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleValueWithBuilderAndNullableFields>): EntityBulkUpdateBuilder = SimpleValueWithBuilderAndNullableFieldss.update(v)
      override fun persistBuilder(v: SimpleValueWithBuilderAndNullableFields): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleValueWithBuilderAndNullableFields>): EntityBulkPersistBuilder = SimpleValueWithBuilderAndNullableFieldss.persist(v)
      override fun deleteBuilder(v: SimpleValueWithBuilderAndNullableFields): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleValueWithBuilderAndNullableFields>): EntityBulkDeleteBuilder = SimpleValueWithBuilderAndNullableFieldss.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleValueWithBuilderAndNullableFieldss.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS)
    },
    nullableColumns = object: NullableColumns<SimpleValueWithBuilderAndNullableFields> {
      override fun nullSomeColumns(target: SimpleValueWithBuilderAndNullableFields): SimpleValueWithBuilderAndNullableFields =
          target.copy().string(null).boxedBoolean(null).build()

      override fun assertAllExceptNulledColumnsAreUpdated(target: SimpleValueWithBuilderAndNullableFields, nulledVal: SimpleValueWithBuilderAndNullableFields) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.boxedInteger()).isEqualTo(nulledVal.boxedInteger())
        assertThat(target.boxedBoolean()).isNotEqualTo(nulledVal.boxedBoolean())
        assertThat(target.boxedBoolean()).isNotNull()
        assertThat(target.string()).isNotEqualTo(nulledVal.string())
        assertThat(target.string()).isNotNull()
      }
    }
)

val simpleImmutableWithCreatorNullableTestModel = TestModelWithNullableColumns<SimpleValueWithCreatorAndNullableFields>(
    testModel = object: TestModel<SimpleValueWithCreatorAndNullableFields> {
      override val table: Table<SimpleValueWithCreatorAndNullableFields>
        get() = SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS
      override val idColumn: Column<Long, Long, Number, *, Nullable>
        get() = SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS.ID

      override fun deleteTable() {
        SimpleValueWithCreatorAndNullableFieldss.deleteTable().execute()
      }

      override fun newRandom(): SimpleValueWithCreatorAndNullableFields = SimpleValueWithCreatorAndNullableFields.newRandom()
      override fun setId(v: SimpleValueWithCreatorAndNullableFields, id: Long?): SimpleValueWithCreatorAndNullableFields =
          SqliteMagic_SimpleValueWithCreatorAndNullableFields_Dao.setId(v, id)

      override fun getId(v: SimpleValueWithCreatorAndNullableFields): Long? = v.id()
      override fun valsAreEqual(v1: SimpleValueWithCreatorAndNullableFields, v2: SimpleValueWithCreatorAndNullableFields): Boolean =
          v1.equalsWithoutId(v2)

      override fun updateAllVals(v: SimpleValueWithCreatorAndNullableFields, id: Long): SimpleValueWithCreatorAndNullableFields =
          SimpleValueWithCreatorAndNullableFields.newRandomWithId(id)

      override fun insertBuilder(v: SimpleValueWithCreatorAndNullableFields): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleValueWithCreatorAndNullableFields>): EntityBulkInsertBuilder = SimpleValueWithCreatorAndNullableFieldss.insert(v)
      override fun updateBuilder(v: SimpleValueWithCreatorAndNullableFields): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleValueWithCreatorAndNullableFields>): EntityBulkUpdateBuilder = SimpleValueWithCreatorAndNullableFieldss.update(v)
      override fun persistBuilder(v: SimpleValueWithCreatorAndNullableFields): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleValueWithCreatorAndNullableFields>): EntityBulkPersistBuilder = SimpleValueWithCreatorAndNullableFieldss.persist(v)
      override fun deleteBuilder(v: SimpleValueWithCreatorAndNullableFields): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleValueWithCreatorAndNullableFields>): EntityBulkDeleteBuilder = SimpleValueWithCreatorAndNullableFieldss.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleValueWithCreatorAndNullableFieldss.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS)
    },
    nullableColumns = object: NullableColumns<SimpleValueWithCreatorAndNullableFields> {
      override fun nullSomeColumns(target: SimpleValueWithCreatorAndNullableFields): SimpleValueWithCreatorAndNullableFields =
          SimpleValueWithCreatorAndNullableFields.nullSomeColumns(target.id(), target.boxedInteger())

      override fun assertAllExceptNulledColumnsAreUpdated(target: SimpleValueWithCreatorAndNullableFields, nulledVal: SimpleValueWithCreatorAndNullableFields) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.boxedInteger()).isEqualTo(nulledVal.boxedInteger())
        assertThat(target.boxedBoolean()).isNotEqualTo(nulledVal.boxedBoolean())
        assertThat(target.boxedBoolean()).isNotNull()
        assertThat(target.string()).isNotEqualTo(nulledVal.string())
        assertThat(target.string()).isNotNull()
      }
    }
)

val simpleDataClassWithFieldsNullableTestModel = TestModelWithNullableColumns<DataClassWithNullableFields>(
    testModel = object: TestModel<DataClassWithNullableFields> {
      override val table: Table<DataClassWithNullableFields>
        get() = DATA_CLASS_WITH_NULLABLE_FIELDS
      override val idColumn: Column<Long, Long, Number, *, Nullable>
        get() = DATA_CLASS_WITH_NULLABLE_FIELDS.ID

      override fun deleteTable() {
        DataClassWithNullableFieldss.deleteTable().execute()
      }

      override fun newRandom(): DataClassWithNullableFields = DataClassWithNullableFields.newRandom()
      override fun setId(v: DataClassWithNullableFields, id: Long?): DataClassWithNullableFields =
          SqliteMagic_DataClassWithNullableFields_Dao.setId(v, id)

      override fun getId(v: DataClassWithNullableFields): Long? = v.id
      override fun valsAreEqual(v1: DataClassWithNullableFields, v2: DataClassWithNullableFields): Boolean =
          v1.equalsWithoutId(v2)

      override fun updateAllVals(v: DataClassWithNullableFields, id: Long): DataClassWithNullableFields =
          DataClassWithNullableFields.newRandom(id)

      override fun insertBuilder(v: DataClassWithNullableFields): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<DataClassWithNullableFields>): EntityBulkInsertBuilder = DataClassWithNullableFieldss.insert(v)
      override fun updateBuilder(v: DataClassWithNullableFields): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<DataClassWithNullableFields>): EntityBulkUpdateBuilder = DataClassWithNullableFieldss.update(v)
      override fun persistBuilder(v: DataClassWithNullableFields): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<DataClassWithNullableFields>): EntityBulkPersistBuilder = DataClassWithNullableFieldss.persist(v)
      override fun deleteBuilder(v: DataClassWithNullableFields): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<DataClassWithNullableFields>): EntityBulkDeleteBuilder = DataClassWithNullableFieldss.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = DataClassWithNullableFieldss.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, DATA_CLASS_WITH_NULLABLE_FIELDS)
    },
    nullableColumns = object: NullableColumns<DataClassWithNullableFields> {
      override fun nullSomeColumns(target: DataClassWithNullableFields): DataClassWithNullableFields =
          DataClassWithNullableFields(target.id, null, null, target.integer, target.transformableObject)

      override fun assertAllExceptNulledColumnsAreUpdated(target: DataClassWithNullableFields, nulledVal: DataClassWithNullableFields) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.integer).isEqualTo(nulledVal.integer)
        assertThat(target.transformableObject).isEqualTo(nulledVal.transformableObject)
        assertThat(target.stringValue).isNotEqualTo(nulledVal.stringValue)
        assertThat(target.stringValue).isNotNull()
        assertThat(target.aBoolean).isNotEqualTo(nulledVal.aBoolean)
        assertThat(target.aBoolean).isNotNull()
      }
    }
)

val simpleDataClassWithMethodsNullableTestModel = TestModelWithNullableColumns<DataClassWithNullableMethods>(
    testModel = object: TestModel<DataClassWithNullableMethods> {
      override val table: Table<DataClassWithNullableMethods>
        get() = DATA_CLASS_WITH_NULLABLE_METHODS
      override val idColumn: Column<Long, Long, Number, *, Nullable>
        get() = DATA_CLASS_WITH_NULLABLE_METHODS.ID

      override fun deleteTable() {
        DataClassWithNullableMethodss.deleteTable().execute()
      }

      override fun newRandom(): DataClassWithNullableMethods = DataClassWithNullableMethods.newRandom()
      override fun setId(v: DataClassWithNullableMethods, id: Long?): DataClassWithNullableMethods =
          SqliteMagic_DataClassWithNullableMethods_Dao.setId(v, id)

      override fun getId(v: DataClassWithNullableMethods): Long? = v.id
      override fun valsAreEqual(v1: DataClassWithNullableMethods, v2: DataClassWithNullableMethods): Boolean =
          v1.equalsWithoutId(v2)

      override fun updateAllVals(v: DataClassWithNullableMethods, id: Long): DataClassWithNullableMethods =
          DataClassWithNullableMethods.newRandom(id)

      override fun insertBuilder(v: DataClassWithNullableMethods): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<DataClassWithNullableMethods>): EntityBulkInsertBuilder = DataClassWithNullableMethodss.insert(v)
      override fun updateBuilder(v: DataClassWithNullableMethods): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<DataClassWithNullableMethods>): EntityBulkUpdateBuilder = DataClassWithNullableMethodss.update(v)
      override fun persistBuilder(v: DataClassWithNullableMethods): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<DataClassWithNullableMethods>): EntityBulkPersistBuilder = DataClassWithNullableMethodss.persist(v)
      override fun deleteBuilder(v: DataClassWithNullableMethods): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<DataClassWithNullableMethods>): EntityBulkDeleteBuilder = DataClassWithNullableMethodss.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = DataClassWithNullableMethodss.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, DATA_CLASS_WITH_NULLABLE_METHODS)
    },
    nullableColumns = object: NullableColumns<DataClassWithNullableMethods> {
      override fun nullSomeColumns(target: DataClassWithNullableMethods): DataClassWithNullableMethods =
          DataClassWithNullableMethods(target.id, null, null, target.integer, target.transformableObject)

      override fun assertAllExceptNulledColumnsAreUpdated(target: DataClassWithNullableMethods, nulledVal: DataClassWithNullableMethods) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.integer).isEqualTo(nulledVal.integer)
        assertThat(target.transformableObject).isEqualTo(nulledVal.transformableObject)
        assertThat(target.stringValue).isNotEqualTo(nulledVal.stringValue)
        assertThat(target.stringValue).isNotNull()
        assertThat(target.aBoolean).isNotEqualTo(nulledVal.aBoolean)
        assertThat(target.aBoolean).isNotNull()
      }
    }
)

val simpleMutableAutoIdUniqueNullableTestModel = TestModelWithUniqueNullableColumns<SimpleMutableWithUniqueAndNullableId>(
    testModel = object : TestModel<SimpleMutableWithUniqueAndNullableId> {
      override val table: Table<SimpleMutableWithUniqueAndNullableId>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID
      override val idColumn: Column<Long, Long, Number, *, Nullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID.ID

      override fun deleteTable() {
        SimpleMutableWithUniqueAndNullableIds.deleteTable().execute()
      }

      override fun newRandom(): SimpleMutableWithUniqueAndNullableId =
          SimpleMutableWithUniqueAndNullableId.newRandom()

      override fun setId(v: SimpleMutableWithUniqueAndNullableId, id: Long?): SimpleMutableWithUniqueAndNullableId {
        SqliteMagic_SimpleMutableWithUniqueAndNullableId_Dao.setId(v, id)
        return v
      }

      override fun getId(v: SimpleMutableWithUniqueAndNullableId): Long? = v.id

      override fun valsAreEqual(v1: SimpleMutableWithUniqueAndNullableId, v2: SimpleMutableWithUniqueAndNullableId): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleMutableWithUniqueAndNullableId, id: Long): SimpleMutableWithUniqueAndNullableId {
        val newRandom = SimpleMutableWithUniqueAndNullableId.newRandom()
        newRandom.id = id
        return newRandom
      }

      override fun insertBuilder(v: SimpleMutableWithUniqueAndNullableId): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleMutableWithUniqueAndNullableId>): EntityBulkInsertBuilder = SimpleMutableWithUniqueAndNullableIds.insert(v)
      override fun updateBuilder(v: SimpleMutableWithUniqueAndNullableId): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleMutableWithUniqueAndNullableId>): EntityBulkUpdateBuilder = SimpleMutableWithUniqueAndNullableIds.update(v)
      override fun persistBuilder(v: SimpleMutableWithUniqueAndNullableId): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleMutableWithUniqueAndNullableId>): EntityBulkPersistBuilder = SimpleMutableWithUniqueAndNullableIds.persist(v)
      override fun deleteBuilder(v: SimpleMutableWithUniqueAndNullableId): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleMutableWithUniqueAndNullableId>): EntityBulkDeleteBuilder = SimpleMutableWithUniqueAndNullableIds.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleMutableWithUniqueAndNullableIds.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID)
    },
    uniqueValue = object : UniqueValued<SimpleMutableWithUniqueAndNullableId> {
      override val uniqueColumn: Unique<NotNullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID.UNIQUE_VAL

      override fun transferUniqueVal(src: SimpleMutableWithUniqueAndNullableId, target: SimpleMutableWithUniqueAndNullableId): SimpleMutableWithUniqueAndNullableId {
        target.uniqueVal = src.uniqueVal
        return target
      }
    },
    nullableColumns = object: NullableColumns<SimpleMutableWithUniqueAndNullableId> {
      override fun nullSomeColumns(target: SimpleMutableWithUniqueAndNullableId): SimpleMutableWithUniqueAndNullableId {
        target.string = null
        return target
      }

      override fun assertAllExceptNulledColumnsAreUpdated(target: SimpleMutableWithUniqueAndNullableId, nulledVal: SimpleMutableWithUniqueAndNullableId) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }
    }
)

val SIMPLE_FIXED_ID_MODELS = arrayOf(
    simpleMutableFixedIdUniqueNullableTestModel,
    simpleImmutableWithBuilderFixedIdUniqueNullableTestModel,
    simpleImmutableWithCreatorFixedIdUniqueNullableTestModel,
    simpleDataClassWithFieldsFixedIdUniqueNullableTestModel,
    simpleDataClassWithMethodsFixedIdUniqueNullableTestModel,
    simpleGenericsMutableFixedIdUniqueNullableTestModel)

val SIMPLE_NULLABLE_FIXED_ID_MODELS = arrayOf(
    simpleMutableFixedIdUniqueNullableTestModel,
    simpleImmutableWithBuilderFixedIdUniqueNullableTestModel,
    simpleImmutableWithCreatorFixedIdUniqueNullableTestModel,
    simpleDataClassWithFieldsFixedIdUniqueNullableTestModel,
    simpleDataClassWithMethodsFixedIdUniqueNullableTestModel,
    simpleGenericsMutableFixedIdUniqueNullableTestModel)

val SIMPLE_AUTO_ID_MODELS = arrayOf(
    simpleMutableAutoIdTestModel,
    simpleImmutableWithBuilderAutoIdTestModel,
    simpleImmutableWithCreatorAutoIdTestModel,
    simpleDataClassWithFieldsAutoIdTestModel,
    simpleDataClassWithMethodsAutoIdTestModel)

val SIMPLE_NULLABLE_AUTO_ID_MODELS = arrayOf(
    simpleMutableAutoIdTestModel,
    simpleImmutableWithBuilderNullableTestModel,
    simpleImmutableWithCreatorNullableTestModel,
    simpleDataClassWithFieldsNullableTestModel,
    simpleDataClassWithMethodsNullableTestModel)

val SIMPLE_NULLABLE_UNIQUE_AUTO_ID_MODELS = arrayOf(
    simpleMutableAutoIdUniqueNullableTestModel)
