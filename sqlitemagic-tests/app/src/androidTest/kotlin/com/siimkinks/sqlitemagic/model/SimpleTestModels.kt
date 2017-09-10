package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.Column
import com.siimkinks.sqlitemagic.DataClassWithFieldsTable.DATA_CLASS_WITH_FIELDS
import com.siimkinks.sqlitemagic.DataClassWithMethodsTable.DATA_CLASS_WITH_METHODS
import com.siimkinks.sqlitemagic.DataClassWithNullableFieldsTable.DATA_CLASS_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.DataClassWithNullableMethodsTable.DATA_CLASS_WITH_NULLABLE_METHODS
import com.siimkinks.sqlitemagic.SimpleDataClassWithFieldsAndUniqueTable.SIMPLE_DATA_CLASS_WITH_FIELDS_AND_UNIQUE
import com.siimkinks.sqlitemagic.SimpleDataClassWithMethodsAndUniqueTable.SIMPLE_DATA_CLASS_WITH_METHODS_AND_UNIQUE
import com.siimkinks.sqlitemagic.SimpleImmutableWithBuilderAndUniqueTable.SIMPLE_IMMUTABLE_WITH_BUILDER_AND_UNIQUE
import com.siimkinks.sqlitemagic.SimpleImmutableWithCreatorAndUniqueTable.SIMPLE_IMMUTABLE_WITH_CREATOR_AND_UNIQUE
import com.siimkinks.sqlitemagic.SimpleMutableWithUniqueTable.SIMPLE_MUTABLE_WITH_UNIQUE
import com.siimkinks.sqlitemagic.SimpleValueWithBuilderAndNullableFieldsTable.SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER
import com.siimkinks.sqlitemagic.SimpleValueWithCreatorAndNullableFieldsTable.SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.entity.*
import com.siimkinks.sqlitemagic.model.TestUtil.assertTableCount
import com.siimkinks.sqlitemagic.model.immutable.*

val simpleMutableAutoIdTestModel = TestModelWithNullableColumns<Author>(
    testModel = object : TestModel<Author> {
      override val table: Table<Author>
        get() = AUTHOR
      override val idColumn: Column<Long, Long, Number, *>
        get() = AUTHOR.ID

      override fun deleteTable() {
        Author.deleteTable().execute()
      }

      override fun newRandom(): Author = Author.newRandom()
      override fun setId(v: Author, id: Long): Author {
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
      override fun bulkInsertBuilder(v: Iterable<Author>): EntityBulkInsertBuilder = Author.insert(v)
      override fun updateBuilder(v: Author): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<Author>): EntityBulkUpdateBuilder = Author.update(v)
      override fun persistBuilder(v: Author): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<Author>): EntityBulkPersistBuilder = Author.persist(v)
      override fun deleteBuilder(v: Author): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<Author>): EntityBulkDeleteBuilder = Author.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = Author.deleteTable()
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
  override val idColumn: Column<Long, Long, Number, *>
    get() = SIMPLE_VALUE_WITH_BUILDER.ID

  override fun deleteTable() {
    SimpleValueWithBuilder.deleteTable().execute()
  }

  override fun newRandom(): SimpleValueWithBuilder = SimpleValueWithBuilder.newRandom().build()
  override fun setId(v: SimpleValueWithBuilder, id: Long): SimpleValueWithBuilder =
      SqliteMagic_SimpleValueWithBuilder_Dao.setId(v, id)

  override fun getId(v: SimpleValueWithBuilder): Long? = v.id()
  override fun valsAreEqual(v1: SimpleValueWithBuilder, v2: SimpleValueWithBuilder): Boolean =
      v1.equalsWithoutId(v2)

  override fun updateAllVals(v: SimpleValueWithBuilder, id: Long): SimpleValueWithBuilder =
      SimpleValueWithBuilder.newRandom()
          .id(id)
          .build()

  override fun insertBuilder(v: SimpleValueWithBuilder): EntityInsertBuilder = v.insert()
  override fun bulkInsertBuilder(v: Iterable<SimpleValueWithBuilder>): EntityBulkInsertBuilder = SimpleValueWithBuilder.insert(v)
  override fun updateBuilder(v: SimpleValueWithBuilder): EntityUpdateBuilder = v.update()
  override fun bulkUpdateBuilder(v: Iterable<SimpleValueWithBuilder>): EntityBulkUpdateBuilder = SimpleValueWithBuilder.update(v)
  override fun persistBuilder(v: SimpleValueWithBuilder): EntityPersistBuilder = v.persist()
  override fun bulkPersistBuilder(v: Iterable<SimpleValueWithBuilder>): EntityBulkPersistBuilder = SimpleValueWithBuilder.persist(v)
  override fun deleteBuilder(v: SimpleValueWithBuilder): EntityDeleteBuilder = v.delete()
  override fun bulkDeleteBuilder(v: Collection<SimpleValueWithBuilder>): EntityBulkDeleteBuilder = SimpleValueWithBuilder.delete(v)
  override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_VALUE_WITH_BUILDER)
  override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleValueWithBuilder.deleteTable()
}

val simpleImmutableWithCreatorAutoIdTestModel = object : TestModel<SimpleValueWithCreator> {
  override val table: Table<SimpleValueWithCreator>
    get() = SIMPLE_VALUE_WITH_CREATOR
  override val idColumn: Column<Long, Long, Number, *>
    get() = SIMPLE_VALUE_WITH_CREATOR.ID

  override fun deleteTable() {
    SimpleValueWithCreator.deleteTable().execute()
  }

  override fun newRandom(): SimpleValueWithCreator = SimpleValueWithCreator.newRandom()
  override fun setId(v: SimpleValueWithCreator, id: Long): SimpleValueWithCreator =
      SqliteMagic_SimpleValueWithCreator_Dao.setId(v, id)

  override fun getId(v: SimpleValueWithCreator): Long? = v.id()
  override fun valsAreEqual(v1: SimpleValueWithCreator, v2: SimpleValueWithCreator): Boolean =
      v1.equalsWithoutId(v2)

  override fun updateAllVals(v: SimpleValueWithCreator, id: Long): SimpleValueWithCreator =
      SimpleValueWithCreator.newRandom(id)

  override fun insertBuilder(v: SimpleValueWithCreator): EntityInsertBuilder = v.insert()
  override fun bulkInsertBuilder(v: Iterable<SimpleValueWithCreator>): EntityBulkInsertBuilder = SimpleValueWithCreator.insert(v)
  override fun updateBuilder(v: SimpleValueWithCreator): EntityUpdateBuilder = v.update()
  override fun bulkUpdateBuilder(v: Iterable<SimpleValueWithCreator>): EntityBulkUpdateBuilder = SimpleValueWithCreator.update(v)
  override fun persistBuilder(v: SimpleValueWithCreator): EntityPersistBuilder = v.persist()
  override fun bulkPersistBuilder(v: Iterable<SimpleValueWithCreator>): EntityBulkPersistBuilder = SimpleValueWithCreator.persist(v)
  override fun deleteBuilder(v: SimpleValueWithCreator): EntityDeleteBuilder = v.delete()
  override fun bulkDeleteBuilder(v: Collection<SimpleValueWithCreator>): EntityBulkDeleteBuilder = SimpleValueWithCreator.delete(v)
  override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_VALUE_WITH_CREATOR)
  override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleValueWithCreator.deleteTable()
}

val simpleDataClassWithFieldsAutoIdTestModel = object : TestModel<DataClassWithFields> {
  override val table: Table<DataClassWithFields>
    get() = DATA_CLASS_WITH_FIELDS
  override val idColumn: Column<Long, Long, Number, *>
    get() = DATA_CLASS_WITH_FIELDS.ID

  override fun deleteTable() {
    DataClassWithFields.deleteTable().execute()
  }

  override fun newRandom(): DataClassWithFields = DataClassWithFields.newRandom()
  override fun setId(v: DataClassWithFields, id: Long): DataClassWithFields =
      SqliteMagic_DataClassWithFields_Dao.setId(v, id)

  override fun getId(v: DataClassWithFields): Long? = v.id
  override fun valsAreEqual(v1: DataClassWithFields, v2: DataClassWithFields): Boolean =
      v1.equalsWithoutId(v2)

  override fun updateAllVals(v: DataClassWithFields, id: Long): DataClassWithFields =
      DataClassWithFields.newRandom(id)

  override fun insertBuilder(v: DataClassWithFields): EntityInsertBuilder = v.insert()
  override fun bulkInsertBuilder(v: Iterable<DataClassWithFields>): EntityBulkInsertBuilder = DataClassWithFields.insert(v)
  override fun updateBuilder(v: DataClassWithFields): EntityUpdateBuilder = v.update()
  override fun bulkUpdateBuilder(v: Iterable<DataClassWithFields>): EntityBulkUpdateBuilder = DataClassWithFields.update(v)
  override fun persistBuilder(v: DataClassWithFields): EntityPersistBuilder = v.persist()
  override fun bulkPersistBuilder(v: Iterable<DataClassWithFields>): EntityBulkPersistBuilder = DataClassWithFields.persist(v)
  override fun deleteBuilder(v: DataClassWithFields): EntityDeleteBuilder = v.delete()
  override fun bulkDeleteBuilder(v: Collection<DataClassWithFields>): EntityBulkDeleteBuilder = DataClassWithFields.delete(v)
  override fun assertNoValsInTables() = assertTableCount(0, DATA_CLASS_WITH_FIELDS)
  override fun deleteTableBuilder(): EntityDeleteTableBuilder = DataClassWithFields.deleteTable()
}

val simpleDataClassWithMethodsAutoIdTestModel = object : TestModel<DataClassWithMethods> {
  override val table: Table<DataClassWithMethods>
    get() = DATA_CLASS_WITH_METHODS
  override val idColumn: Column<Long, Long, Number, *>
    get() = DATA_CLASS_WITH_METHODS.ID

  override fun deleteTable() {
    DataClassWithMethods.deleteTable().execute()
  }

  override fun newRandom(): DataClassWithMethods = DataClassWithMethods.newRandom()
  override fun setId(v: DataClassWithMethods, id: Long): DataClassWithMethods =
      SqliteMagic_DataClassWithMethods_Dao.setId(v, id)

  override fun getId(v: DataClassWithMethods): Long? = v.id
  override fun valsAreEqual(v1: DataClassWithMethods, v2: DataClassWithMethods): Boolean =
      v1.equalsWithoutId(v2)

  override fun updateAllVals(v: DataClassWithMethods, id: Long): DataClassWithMethods =
      DataClassWithMethods.newRandom(id)

  override fun insertBuilder(v: DataClassWithMethods): EntityInsertBuilder = v.insert()
  override fun bulkInsertBuilder(v: Iterable<DataClassWithMethods>): EntityBulkInsertBuilder = DataClassWithMethods.insert(v)
  override fun updateBuilder(v: DataClassWithMethods): EntityUpdateBuilder = v.update()
  override fun bulkUpdateBuilder(v: Iterable<DataClassWithMethods>): EntityBulkUpdateBuilder = DataClassWithMethods.update(v)
  override fun persistBuilder(v: DataClassWithMethods): EntityPersistBuilder = v.persist()
  override fun bulkPersistBuilder(v: Iterable<DataClassWithMethods>): EntityBulkPersistBuilder = DataClassWithMethods.persist(v)
  override fun deleteBuilder(v: DataClassWithMethods): EntityDeleteBuilder = v.delete()
  override fun bulkDeleteBuilder(v: Collection<DataClassWithMethods>): EntityBulkDeleteBuilder = DataClassWithMethods.delete(v)
  override fun assertNoValsInTables() = assertTableCount(0, DATA_CLASS_WITH_METHODS)
  override fun deleteTableBuilder(): EntityDeleteTableBuilder = DataClassWithMethods.deleteTable()
}

val simpleMutableFixedIdUniqueNullableTestModel = TestModelWithUniqueNullableColumns<SimpleMutableWithUnique>(
    testModel = object : TestModel<SimpleMutableWithUnique> {
      override val table: Table<SimpleMutableWithUnique>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE
      override val idColumn: Column<Long, Long, Number, *>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE.ID

      override fun deleteTable() {
        SimpleMutableWithUnique.deleteTable().execute()
      }

      override fun newRandom(): SimpleMutableWithUnique =
          SimpleMutableWithUnique.newRandom()

      override fun setId(v: SimpleMutableWithUnique, id: Long): SimpleMutableWithUnique {
        SqliteMagic_SimpleMutableWithUnique_Dao.setId(v, id)
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
      override fun bulkInsertBuilder(v: Iterable<SimpleMutableWithUnique>): EntityBulkInsertBuilder = SimpleMutableWithUnique.insert(v)
      override fun updateBuilder(v: SimpleMutableWithUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleMutableWithUnique>): EntityBulkUpdateBuilder = SimpleMutableWithUnique.update(v)
      override fun persistBuilder(v: SimpleMutableWithUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleMutableWithUnique>): EntityBulkPersistBuilder = SimpleMutableWithUnique.persist(v)
      override fun deleteBuilder(v: SimpleMutableWithUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleMutableWithUnique>): EntityBulkDeleteBuilder = SimpleMutableWithUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleMutableWithUnique.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_MUTABLE_WITH_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleMutableWithUnique> {
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
      override val idColumn: Column<Long, Long, Number, *>
        get() = SIMPLE_IMMUTABLE_WITH_BUILDER_AND_UNIQUE.ID

      override fun deleteTable() {
        SimpleImmutableWithBuilderAndUnique.deleteTable().execute()
      }

      override fun newRandom(): SimpleImmutableWithBuilderAndUnique =
          SimpleImmutableWithBuilderAndUnique.newRandom()

      override fun setId(v: SimpleImmutableWithBuilderAndUnique, id: Long): SimpleImmutableWithBuilderAndUnique =
          v.copy()
              .id(id)
              .build()

      override fun getId(v: SimpleImmutableWithBuilderAndUnique): Long? = v.id()

      override fun valsAreEqual(v1: SimpleImmutableWithBuilderAndUnique, v2: SimpleImmutableWithBuilderAndUnique): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleImmutableWithBuilderAndUnique, id: Long): SimpleImmutableWithBuilderAndUnique =
          SimpleImmutableWithBuilderAndUnique.newRandomWithId(id)

      override fun insertBuilder(v: SimpleImmutableWithBuilderAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleImmutableWithBuilderAndUnique>): EntityBulkInsertBuilder = SimpleImmutableWithBuilderAndUnique.insert(v)
      override fun updateBuilder(v: SimpleImmutableWithBuilderAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleImmutableWithBuilderAndUnique>): EntityBulkUpdateBuilder = SimpleImmutableWithBuilderAndUnique.update(v)
      override fun persistBuilder(v: SimpleImmutableWithBuilderAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleImmutableWithBuilderAndUnique>): EntityBulkPersistBuilder = SimpleImmutableWithBuilderAndUnique.persist(v)
      override fun deleteBuilder(v: SimpleImmutableWithBuilderAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleImmutableWithBuilderAndUnique>): EntityBulkDeleteBuilder = SimpleImmutableWithBuilderAndUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleImmutableWithBuilderAndUnique.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_IMMUTABLE_WITH_BUILDER_AND_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleImmutableWithBuilderAndUnique> {
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
      override val idColumn: Column<Long, Long, Number, *>
        get() = SIMPLE_IMMUTABLE_WITH_CREATOR_AND_UNIQUE.ID

      override fun deleteTable() {
        SimpleImmutableWithCreatorAndUnique.deleteTable().execute()
      }

      override fun newRandom(): SimpleImmutableWithCreatorAndUnique =
          SimpleImmutableWithCreatorAndUnique.newRandom()

      override fun setId(v: SimpleImmutableWithCreatorAndUnique, id: Long): SimpleImmutableWithCreatorAndUnique =
          v.setId(id)

      override fun getId(v: SimpleImmutableWithCreatorAndUnique): Long? = v.id()

      override fun valsAreEqual(v1: SimpleImmutableWithCreatorAndUnique, v2: SimpleImmutableWithCreatorAndUnique): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleImmutableWithCreatorAndUnique, id: Long): SimpleImmutableWithCreatorAndUnique =
          SimpleImmutableWithCreatorAndUnique.newRandomWithId(id)

      override fun insertBuilder(v: SimpleImmutableWithCreatorAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleImmutableWithCreatorAndUnique>): EntityBulkInsertBuilder = SimpleImmutableWithCreatorAndUnique.insert(v)
      override fun updateBuilder(v: SimpleImmutableWithCreatorAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleImmutableWithCreatorAndUnique>): EntityBulkUpdateBuilder = SimpleImmutableWithCreatorAndUnique.update(v)
      override fun persistBuilder(v: SimpleImmutableWithCreatorAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleImmutableWithCreatorAndUnique>): EntityBulkPersistBuilder = SimpleImmutableWithCreatorAndUnique.persist(v)
      override fun deleteBuilder(v: SimpleImmutableWithCreatorAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleImmutableWithCreatorAndUnique>): EntityBulkDeleteBuilder = SimpleImmutableWithCreatorAndUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleImmutableWithCreatorAndUnique.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_IMMUTABLE_WITH_CREATOR_AND_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleImmutableWithCreatorAndUnique> {
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
      override val idColumn: Column<Long, Long, Number, *>
        get() = SIMPLE_DATA_CLASS_WITH_FIELDS_AND_UNIQUE.ID

      override fun deleteTable() {
        SimpleDataClassWithFieldsAndUnique.deleteTable().execute()
      }

      override fun newRandom(): SimpleDataClassWithFieldsAndUnique =
          SimpleDataClassWithFieldsAndUnique.newRandom()

      override fun setId(v: SimpleDataClassWithFieldsAndUnique, id: Long): SimpleDataClassWithFieldsAndUnique =
          v.setId(id)

      override fun getId(v: SimpleDataClassWithFieldsAndUnique): Long? = v.id

      override fun valsAreEqual(v1: SimpleDataClassWithFieldsAndUnique, v2: SimpleDataClassWithFieldsAndUnique): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleDataClassWithFieldsAndUnique, id: Long): SimpleDataClassWithFieldsAndUnique =
          SimpleDataClassWithFieldsAndUnique.newRandomWithId(id)

      override fun insertBuilder(v: SimpleDataClassWithFieldsAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleDataClassWithFieldsAndUnique>): EntityBulkInsertBuilder = SimpleDataClassWithFieldsAndUnique.insert(v)
      override fun updateBuilder(v: SimpleDataClassWithFieldsAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleDataClassWithFieldsAndUnique>): EntityBulkUpdateBuilder = SimpleDataClassWithFieldsAndUnique.update(v)
      override fun persistBuilder(v: SimpleDataClassWithFieldsAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleDataClassWithFieldsAndUnique>): EntityBulkPersistBuilder = SimpleDataClassWithFieldsAndUnique.persist(v)
      override fun deleteBuilder(v: SimpleDataClassWithFieldsAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleDataClassWithFieldsAndUnique>): EntityBulkDeleteBuilder = SimpleDataClassWithFieldsAndUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleDataClassWithFieldsAndUnique.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_DATA_CLASS_WITH_FIELDS_AND_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleDataClassWithFieldsAndUnique> {
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
      override val idColumn: Column<Long, Long, Number, *>
        get() = SIMPLE_DATA_CLASS_WITH_METHODS_AND_UNIQUE.ID

      override fun deleteTable() {
        SimpleDataClassWithMethodsAndUnique.deleteTable().execute()
      }

      override fun newRandom(): SimpleDataClassWithMethodsAndUnique =
          SimpleDataClassWithMethodsAndUnique.newRandom()

      override fun setId(v: SimpleDataClassWithMethodsAndUnique, id: Long): SimpleDataClassWithMethodsAndUnique =
          v.setId(id)

      override fun getId(v: SimpleDataClassWithMethodsAndUnique): Long? = v.id

      override fun valsAreEqual(v1: SimpleDataClassWithMethodsAndUnique, v2: SimpleDataClassWithMethodsAndUnique): Boolean = v1 == v2

      override fun updateAllVals(v: SimpleDataClassWithMethodsAndUnique, id: Long): SimpleDataClassWithMethodsAndUnique =
          SimpleDataClassWithMethodsAndUnique.newRandomWithId(id)

      override fun insertBuilder(v: SimpleDataClassWithMethodsAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleDataClassWithMethodsAndUnique>): EntityBulkInsertBuilder = SimpleDataClassWithMethodsAndUnique.insert(v)
      override fun updateBuilder(v: SimpleDataClassWithMethodsAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleDataClassWithMethodsAndUnique>): EntityBulkUpdateBuilder = SimpleDataClassWithMethodsAndUnique.update(v)
      override fun persistBuilder(v: SimpleDataClassWithMethodsAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleDataClassWithMethodsAndUnique>): EntityBulkPersistBuilder = SimpleDataClassWithMethodsAndUnique.persist(v)
      override fun deleteBuilder(v: SimpleDataClassWithMethodsAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleDataClassWithMethodsAndUnique>): EntityBulkDeleteBuilder = SimpleDataClassWithMethodsAndUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleDataClassWithMethodsAndUnique.deleteTable()
      override fun assertNoValsInTables() = assertTableCount(0, SIMPLE_DATA_CLASS_WITH_METHODS_AND_UNIQUE)
    },
    uniqueValue = object : UniqueValued<SimpleDataClassWithMethodsAndUnique> {
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
      override val idColumn: Column<Long, Long, Number, *>
        get() = SIMPLE_VALUE_WITH_BUILDER_AND_NULLABLE_FIELDS.ID

      override fun deleteTable() {
        SimpleValueWithBuilderAndNullableFields.deleteTable().execute()
      }

      override fun newRandom(): SimpleValueWithBuilderAndNullableFields = SimpleValueWithBuilderAndNullableFields.newRandom().build()
      override fun setId(v: SimpleValueWithBuilderAndNullableFields, id: Long): SimpleValueWithBuilderAndNullableFields =
          SqliteMagic_SimpleValueWithBuilderAndNullableFields_Dao.setId(v, id)

      override fun getId(v: SimpleValueWithBuilderAndNullableFields): Long? = v.id()
      override fun valsAreEqual(v1: SimpleValueWithBuilderAndNullableFields, v2: SimpleValueWithBuilderAndNullableFields): Boolean =
          v1.equalsWithoutId(v2)

      override fun updateAllVals(v: SimpleValueWithBuilderAndNullableFields, id: Long): SimpleValueWithBuilderAndNullableFields =
          SimpleValueWithBuilderAndNullableFields.newRandom()
              .id(id)
              .build()

      override fun insertBuilder(v: SimpleValueWithBuilderAndNullableFields): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleValueWithBuilderAndNullableFields>): EntityBulkInsertBuilder = SimpleValueWithBuilderAndNullableFields.insert(v)
      override fun updateBuilder(v: SimpleValueWithBuilderAndNullableFields): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleValueWithBuilderAndNullableFields>): EntityBulkUpdateBuilder = SimpleValueWithBuilderAndNullableFields.update(v)
      override fun persistBuilder(v: SimpleValueWithBuilderAndNullableFields): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleValueWithBuilderAndNullableFields>): EntityBulkPersistBuilder = SimpleValueWithBuilderAndNullableFields.persist(v)
      override fun deleteBuilder(v: SimpleValueWithBuilderAndNullableFields): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleValueWithBuilderAndNullableFields>): EntityBulkDeleteBuilder = SimpleValueWithBuilderAndNullableFields.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleValueWithBuilderAndNullableFields.deleteTable()
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
      override val idColumn: Column<Long, Long, Number, *>
        get() = SIMPLE_VALUE_WITH_CREATOR_AND_NULLABLE_FIELDS.ID

      override fun deleteTable() {
        SimpleValueWithCreatorAndNullableFields.deleteTable().execute()
      }

      override fun newRandom(): SimpleValueWithCreatorAndNullableFields = SimpleValueWithCreatorAndNullableFields.newRandom()
      override fun setId(v: SimpleValueWithCreatorAndNullableFields, id: Long): SimpleValueWithCreatorAndNullableFields =
          SqliteMagic_SimpleValueWithCreatorAndNullableFields_Dao.setId(v, id)

      override fun getId(v: SimpleValueWithCreatorAndNullableFields): Long? = v.id()
      override fun valsAreEqual(v1: SimpleValueWithCreatorAndNullableFields, v2: SimpleValueWithCreatorAndNullableFields): Boolean =
          v1.equalsWithoutId(v2)

      override fun updateAllVals(v: SimpleValueWithCreatorAndNullableFields, id: Long): SimpleValueWithCreatorAndNullableFields =
          SimpleValueWithCreatorAndNullableFields.newRandomWithId(id)

      override fun insertBuilder(v: SimpleValueWithCreatorAndNullableFields): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<SimpleValueWithCreatorAndNullableFields>): EntityBulkInsertBuilder = SimpleValueWithCreatorAndNullableFields.insert(v)
      override fun updateBuilder(v: SimpleValueWithCreatorAndNullableFields): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<SimpleValueWithCreatorAndNullableFields>): EntityBulkUpdateBuilder = SimpleValueWithCreatorAndNullableFields.update(v)
      override fun persistBuilder(v: SimpleValueWithCreatorAndNullableFields): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<SimpleValueWithCreatorAndNullableFields>): EntityBulkPersistBuilder = SimpleValueWithCreatorAndNullableFields.persist(v)
      override fun deleteBuilder(v: SimpleValueWithCreatorAndNullableFields): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<SimpleValueWithCreatorAndNullableFields>): EntityBulkDeleteBuilder = SimpleValueWithCreatorAndNullableFields.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = SimpleValueWithCreatorAndNullableFields.deleteTable()
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
      override val idColumn: Column<Long, Long, Number, *>
        get() = DATA_CLASS_WITH_NULLABLE_FIELDS.ID

      override fun deleteTable() {
        DataClassWithNullableFields.deleteTable().execute()
      }

      override fun newRandom(): DataClassWithNullableFields = DataClassWithNullableFields.newRandom()
      override fun setId(v: DataClassWithNullableFields, id: Long): DataClassWithNullableFields =
          SqliteMagic_DataClassWithNullableFields_Dao.setId(v, id)

      override fun getId(v: DataClassWithNullableFields): Long? = v.id
      override fun valsAreEqual(v1: DataClassWithNullableFields, v2: DataClassWithNullableFields): Boolean =
          v1.equalsWithoutId(v2)

      override fun updateAllVals(v: DataClassWithNullableFields, id: Long): DataClassWithNullableFields =
          DataClassWithNullableFields.newRandom(id)

      override fun insertBuilder(v: DataClassWithNullableFields): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<DataClassWithNullableFields>): EntityBulkInsertBuilder = DataClassWithNullableFields.insert(v)
      override fun updateBuilder(v: DataClassWithNullableFields): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<DataClassWithNullableFields>): EntityBulkUpdateBuilder = DataClassWithNullableFields.update(v)
      override fun persistBuilder(v: DataClassWithNullableFields): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<DataClassWithNullableFields>): EntityBulkPersistBuilder = DataClassWithNullableFields.persist(v)
      override fun deleteBuilder(v: DataClassWithNullableFields): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<DataClassWithNullableFields>): EntityBulkDeleteBuilder = DataClassWithNullableFields.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = DataClassWithNullableFields.deleteTable()
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
      override val idColumn: Column<Long, Long, Number, *>
        get() = DATA_CLASS_WITH_NULLABLE_METHODS.ID

      override fun deleteTable() {
        DataClassWithNullableMethods.deleteTable().execute()
      }

      override fun newRandom(): DataClassWithNullableMethods = DataClassWithNullableMethods.newRandom()
      override fun setId(v: DataClassWithNullableMethods, id: Long): DataClassWithNullableMethods =
          SqliteMagic_DataClassWithNullableMethods_Dao.setId(v, id)

      override fun getId(v: DataClassWithNullableMethods): Long? = v.id
      override fun valsAreEqual(v1: DataClassWithNullableMethods, v2: DataClassWithNullableMethods): Boolean =
          v1.equalsWithoutId(v2)

      override fun updateAllVals(v: DataClassWithNullableMethods, id: Long): DataClassWithNullableMethods =
          DataClassWithNullableMethods.newRandom(id)

      override fun insertBuilder(v: DataClassWithNullableMethods): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<DataClassWithNullableMethods>): EntityBulkInsertBuilder = DataClassWithNullableMethods.insert(v)
      override fun updateBuilder(v: DataClassWithNullableMethods): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<DataClassWithNullableMethods>): EntityBulkUpdateBuilder = DataClassWithNullableMethods.update(v)
      override fun persistBuilder(v: DataClassWithNullableMethods): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<DataClassWithNullableMethods>): EntityBulkPersistBuilder = DataClassWithNullableMethods.persist(v)
      override fun deleteBuilder(v: DataClassWithNullableMethods): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<DataClassWithNullableMethods>): EntityBulkDeleteBuilder = DataClassWithNullableMethods.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = DataClassWithNullableMethods.deleteTable()
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

val SIMPLE_FIXED_ID_MODELS = arrayOf(
    simpleMutableFixedIdUniqueNullableTestModel,
    simpleImmutableWithBuilderFixedIdUniqueNullableTestModel,
    simpleImmutableWithCreatorFixedIdUniqueNullableTestModel,
    simpleDataClassWithFieldsFixedIdUniqueNullableTestModel,
    simpleDataClassWithMethodsFixedIdUniqueNullableTestModel)

val SIMPLE_NULLABLE_FIXED_ID_MODELS = arrayOf(
    simpleMutableFixedIdUniqueNullableTestModel,
    simpleImmutableWithBuilderFixedIdUniqueNullableTestModel,
    simpleImmutableWithCreatorFixedIdUniqueNullableTestModel,
    simpleDataClassWithFieldsFixedIdUniqueNullableTestModel,
    simpleDataClassWithMethodsFixedIdUniqueNullableTestModel)

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
