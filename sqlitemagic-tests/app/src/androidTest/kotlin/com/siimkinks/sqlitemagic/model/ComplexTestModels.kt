package com.siimkinks.sqlitemagic.model

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.BuilderMagazineTable.BUILDER_MAGAZINE
import com.siimkinks.sqlitemagic.ComplexDataClassWithFieldsAndUniqueTable.COMPLEX_DATA_CLASS_WITH_FIELDS_AND_UNIQUE
import com.siimkinks.sqlitemagic.ComplexDataClassWithFieldsTable.COMPLEX_DATA_CLASS_WITH_FIELDS
import com.siimkinks.sqlitemagic.ComplexDataClassWithMethodsAndUniqueTable.COMPLEX_DATA_CLASS_WITH_METHODS_AND_UNIQUE
import com.siimkinks.sqlitemagic.ComplexDataClassWithMethodsTable.COMPLEX_DATA_CLASS_WITH_METHODS
import com.siimkinks.sqlitemagic.ComplexImmutableBuilderWithUniqueTable.COMPLEX_IMMUTABLE_BUILDER_WITH_UNIQUE
import com.siimkinks.sqlitemagic.ComplexImmutableCreatorWithUniqueTable.COMPLEX_IMMUTABLE_CREATOR_WITH_UNIQUE
import com.siimkinks.sqlitemagic.ComplexMutableWithUniqueAndNullableIdTable.COMPLEX_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID
import com.siimkinks.sqlitemagic.ComplexMutableWithUniqueTable.COMPLEX_MUTABLE_WITH_UNIQUE
import com.siimkinks.sqlitemagic.CreatorMagazineTable.CREATOR_MAGAZINE
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.SimpleMutableWithUniqueAndNullableIdTable.SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID
import com.siimkinks.sqlitemagic.SimpleMutableWithUniqueTable.SIMPLE_MUTABLE_WITH_UNIQUE
import com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER
import com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR
import com.siimkinks.sqlitemagic.entity.*
import com.siimkinks.sqlitemagic.model.TestUtil.assertTableCount
import com.siimkinks.sqlitemagic.model.immutable.*
import java.util.*

val complexMutableAutoIdTestModel = ComplexTestModelWithNullableColumns(
    testModel = object : TestModel<Magazine> {
      override val table: Table<Magazine>
        get() = MAGAZINE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = MAGAZINE._ID

      override fun deleteTable() {
        Magazine.deleteTable().execute()
        Author.deleteTable().execute()
      }

      override fun newRandom(): Magazine = Magazine.newRandom()
      override fun setId(v: Magazine, id: Long?): Magazine {
        SqliteMagic_Magazine_Dao.setId(v, id!!)
        return v
      }

      override fun getId(v: Magazine): Long? = v.id
      override fun valsAreEqual(v1: Magazine, v2: Magazine): Boolean = v1 == v2
      override fun updateAllVals(v: Magazine, id: Long): Magazine {
        val newRandom = Magazine.newRandom()
        newRandom.id = id
        newRandom.author.id = v.author.id
        return newRandom
      }

      override fun insertBuilder(v: Magazine): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<Magazine>): EntityBulkInsertBuilder = Magazine.insert(v)
      override fun updateBuilder(v: Magazine): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<Magazine>): EntityBulkUpdateBuilder = Magazine.update(v)
      override fun persistBuilder(v: Magazine): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<Magazine>): EntityBulkPersistBuilder = Magazine.persist(v)
      override fun deleteBuilder(v: Magazine): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<Magazine>): EntityBulkDeleteBuilder = Magazine.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = Magazine.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, AUTHOR)
        assertTableCount(0, MAGAZINE)
      }
    },
    nullableColumns = object : ComplexNullableColumns<Magazine> {
      override fun nullSomeColumns(target: Magazine): Magazine {
        target.name = null
        return target
      }

      override fun assertAllExceptNulledColumnsAreUpdated(target: Magazine, nulledVal: Magazine) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.nrOfReleases).isEqualTo(nulledVal.nrOfReleases)
        assertThat(target.author).isEqualTo(nulledVal.author)
        assertThat(target.name).isNotEqualTo(nulledVal.name)
        assertThat(target.name).isNotNull()
      }

      override fun nullSomeComplexColumns(target: Magazine): Magazine {
        target.name = null
        target.author = null
        return target
      }

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: Magazine, nulledVal: Magazine) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.nrOfReleases).isEqualTo(nulledVal.nrOfReleases)
        assertThat(target.author).isNotEqualTo(nulledVal.author)
        assertThat(target.author).isNotNull()
        assertThat(target.name).isNotEqualTo(nulledVal.name)
        assertThat(target.name).isNotNull()
      }
    }
)

val complexImmutableWithBuilderAutoIdTestModel = ComplexTestModelWithNullableColumns(
    testModel = object : TestModel<BuilderMagazine> {
      override val table: Table<BuilderMagazine>
        get() = BUILDER_MAGAZINE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = BUILDER_MAGAZINE.ID

      override fun deleteTable() {
        BuilderMagazine.deleteTable().execute()
        Author.deleteTable().execute()
        SimpleValueWithBuilder.deleteTable().execute()
        SimpleValueWithCreator.deleteTable().execute()
      }

      override fun newRandom(): BuilderMagazine = BuilderMagazine.newRandom().build()
      override fun setId(v: BuilderMagazine, id: Long?): BuilderMagazine = SqliteMagic_BuilderMagazine_Dao.setId(v, id!!)
      override fun getId(v: BuilderMagazine): Long? = v.id()
      override fun valsAreEqual(v1: BuilderMagazine, v2: BuilderMagazine): Boolean = v1.equalsWithoutId(v2)
      override fun updateAllVals(v: BuilderMagazine, id: Long): BuilderMagazine {
        val prevVal = Select.from(BUILDER_MAGAZINE)
            .where(BUILDER_MAGAZINE.ID.`is`(id))
            .queryDeep()
            .takeFirst()
            .execute()!!
        val author = Author.newRandom()
        author.id = prevVal.author()!!.id
        val simpleValWBuilder = SimpleValueWithBuilder
            .newRandom()
            .id(prevVal.simpleValueWithBuilder()!!.id())
            .build()
        val simpleValWCreator = SimpleValueWithCreator
            .newRandom(prevVal.simpleValueWithCreator()!!.id())

        return BuilderMagazine.newRandom()
            .id(id)
            .author(author)
            .simpleValueWithBuilder(simpleValWBuilder)
            .simpleValueWithCreator(simpleValWCreator)
            .build()
      }

      override fun insertBuilder(v: BuilderMagazine): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<BuilderMagazine>): EntityBulkInsertBuilder = BuilderMagazine.insert(v)
      override fun updateBuilder(v: BuilderMagazine): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<BuilderMagazine>): EntityBulkUpdateBuilder = BuilderMagazine.update(v)
      override fun persistBuilder(v: BuilderMagazine): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<BuilderMagazine>): EntityBulkPersistBuilder = BuilderMagazine.persist(v)
      override fun deleteBuilder(v: BuilderMagazine): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<BuilderMagazine>): EntityBulkDeleteBuilder = BuilderMagazine.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = BuilderMagazine.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, AUTHOR)
        assertTableCount(0, BUILDER_MAGAZINE)
        assertTableCount(0, SIMPLE_VALUE_WITH_BUILDER)
        assertTableCount(0, SIMPLE_VALUE_WITH_CREATOR)
      }
    },
    nullableColumns = object : ComplexNullableColumns<BuilderMagazine> {
      override fun nullSomeColumns(target: BuilderMagazine): BuilderMagazine =
          target.copy().name(null).build()

      override fun assertAllExceptNulledColumnsAreUpdated(target: BuilderMagazine, nulledVal: BuilderMagazine) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.simpleValueWithBuilder()).isEqualTo(nulledVal.simpleValueWithBuilder())
        assertThat(target.simpleValueWithCreator()).isEqualTo(nulledVal.simpleValueWithCreator())
        assertThat(target.author()).isEqualTo(nulledVal.author())
        assertThat(target.name()).isNotEqualTo(nulledVal.name())
        assertThat(target.name()).isNotNull()
      }

      override fun nullSomeComplexColumns(target: BuilderMagazine): BuilderMagazine =
          target.copy().name(null).author(null).build()

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: BuilderMagazine, nulledVal: BuilderMagazine) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.simpleValueWithBuilder()).isEqualTo(nulledVal.simpleValueWithBuilder())
        assertThat(target.simpleValueWithCreator()).isEqualTo(nulledVal.simpleValueWithCreator())
        assertThat(target.author()).isNotEqualTo(nulledVal.author())
        assertThat(target.author()).isNotNull()
        assertThat(target.name()).isNotEqualTo(nulledVal.name())
        assertThat(target.name()).isNotNull()
      }
    }
)

val complexImmutableWithCreatorAutoIdTestModel = ComplexTestModelWithNullableColumns(
    testModel = object : TestModel<CreatorMagazine> {
      override val table: Table<CreatorMagazine>
        get() = CREATOR_MAGAZINE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = CREATOR_MAGAZINE.ID

      override fun deleteTable() {
        CreatorMagazine.deleteTable().execute()
        Author.deleteTable().execute()
        SimpleValueWithBuilder.deleteTable().execute()
        SimpleValueWithCreator.deleteTable().execute()
      }

      override fun newRandom(): CreatorMagazine = CreatorMagazine.newRandom()
      override fun setId(v: CreatorMagazine, id: Long?): CreatorMagazine =
          SqliteMagic_CreatorMagazine_Dao.setId(v, id!!)
      override fun getId(v: CreatorMagazine): Long? = v.id()
      override fun valsAreEqual(v1: CreatorMagazine, v2: CreatorMagazine): Boolean = v1.equalsWithoutId(v2)
      override fun updateAllVals(v: CreatorMagazine, id: Long): CreatorMagazine {
        val prevVal = Select.from(CREATOR_MAGAZINE)
            .where(CREATOR_MAGAZINE.ID.`is`(id))
            .queryDeep()
            .takeFirst()
            .execute()!!
        val author = Author.newRandom()
        author.id = prevVal.author()!!.id
        val simpleValWBuilder = SimpleValueWithBuilder
            .newRandom()
            .id(prevVal.simpleValueWithBuilder()!!.id())
            .build()
        val simpleValWCreator = SimpleValueWithCreator
            .newRandom(prevVal.simpleValueWithCreator()!!.id())

        return CreatorMagazine.create(
            id,
            Utils.randomTableName(),
            author,
            simpleValWBuilder,
            simpleValWCreator)
      }

      override fun insertBuilder(v: CreatorMagazine): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<CreatorMagazine>): EntityBulkInsertBuilder = CreatorMagazine.insert(v)
      override fun updateBuilder(v: CreatorMagazine): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<CreatorMagazine>): EntityBulkUpdateBuilder = CreatorMagazine.update(v)
      override fun persistBuilder(v: CreatorMagazine): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<CreatorMagazine>): EntityBulkPersistBuilder = CreatorMagazine.persist(v)
      override fun deleteBuilder(v: CreatorMagazine): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<CreatorMagazine>): EntityBulkDeleteBuilder = CreatorMagazine.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = CreatorMagazine.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, AUTHOR)
        assertTableCount(0, CREATOR_MAGAZINE)
        assertTableCount(0, SIMPLE_VALUE_WITH_BUILDER)
        assertTableCount(0, SIMPLE_VALUE_WITH_CREATOR)
      }
    },
    nullableColumns = object : ComplexNullableColumns<CreatorMagazine> {
      override fun nullSomeColumns(target: CreatorMagazine): CreatorMagazine =
          CreatorMagazine.create(target.id(), null, target.author(), target.simpleValueWithBuilder(), target.simpleValueWithCreator())

      override fun assertAllExceptNulledColumnsAreUpdated(target: CreatorMagazine, nulledVal: CreatorMagazine) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.simpleValueWithBuilder()).isEqualTo(nulledVal.simpleValueWithBuilder())
        assertThat(target.simpleValueWithCreator()).isEqualTo(nulledVal.simpleValueWithCreator())
        assertThat(target.author()).isEqualTo(nulledVal.author())
        assertThat(target.name()).isNotEqualTo(nulledVal.name())
        assertThat(target.name()).isNotNull()
      }

      override fun nullSomeComplexColumns(target: CreatorMagazine): CreatorMagazine =
          CreatorMagazine.create(target.id(), null, null, target.simpleValueWithBuilder(), target.simpleValueWithCreator())

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: CreatorMagazine, nulledVal: CreatorMagazine) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.simpleValueWithBuilder()).isEqualTo(nulledVal.simpleValueWithBuilder())
        assertThat(target.simpleValueWithCreator()).isEqualTo(nulledVal.simpleValueWithCreator())
        assertThat(target.author()).isNotEqualTo(nulledVal.author())
        assertThat(target.author()).isNotNull()
        assertThat(target.name()).isNotEqualTo(nulledVal.name())
        assertThat(target.name()).isNotNull()
      }
    }
)

val complexDataClassWithFieldsAutoIdTestModel = ComplexTestModelWithNullableColumns(
    testModel = object : TestModel<ComplexDataClassWithFields> {
      override val table: Table<ComplexDataClassWithFields>
        get() = COMPLEX_DATA_CLASS_WITH_FIELDS
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = COMPLEX_DATA_CLASS_WITH_FIELDS.ID

      override fun deleteTable() {
        ComplexDataClassWithFields.deleteTable().execute()
        Author.deleteTable().execute()
        SimpleValueWithBuilder.deleteTable().execute()
        SimpleValueWithCreator.deleteTable().execute()
      }

      override fun newRandom(): ComplexDataClassWithFields = ComplexDataClassWithFields.newRandom()
      override fun setId(v: ComplexDataClassWithFields, id: Long?): ComplexDataClassWithFields =
          SqliteMagic_ComplexDataClassWithFields_Dao.setId(v, id!!)
      override fun getId(v: ComplexDataClassWithFields): Long? = v.id
      override fun valsAreEqual(v1: ComplexDataClassWithFields, v2: ComplexDataClassWithFields): Boolean = v1.equalsWithoutId(v2)
      override fun updateAllVals(v: ComplexDataClassWithFields, id: Long): ComplexDataClassWithFields {
        val prevVal = Select.from(COMPLEX_DATA_CLASS_WITH_FIELDS)
            .where(COMPLEX_DATA_CLASS_WITH_FIELDS.ID.`is`(id))
            .queryDeep()
            .takeFirst()
            .execute()!!
        val author = Author.newRandom()
        author.id = prevVal.author!!.id
        val simpleValWBuilder = SimpleValueWithBuilder
            .newRandom()
            .id(prevVal.simpleValueWithBuilder!!.id())
            .build()
        val simpleValWCreator = SimpleValueWithCreator
            .newRandom(prevVal.simpleValueWithCreator!!.id())

        return ComplexDataClassWithFields(
            id,
            Utils.randomTableName(),
            author,
            simpleValWBuilder,
            simpleValWCreator)
      }

      override fun insertBuilder(v: ComplexDataClassWithFields): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<ComplexDataClassWithFields>): EntityBulkInsertBuilder = ComplexDataClassWithFields.insert(v)
      override fun updateBuilder(v: ComplexDataClassWithFields): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<ComplexDataClassWithFields>): EntityBulkUpdateBuilder = ComplexDataClassWithFields.update(v)
      override fun persistBuilder(v: ComplexDataClassWithFields): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<ComplexDataClassWithFields>): EntityBulkPersistBuilder = ComplexDataClassWithFields.persist(v)
      override fun deleteBuilder(v: ComplexDataClassWithFields): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<ComplexDataClassWithFields>): EntityBulkDeleteBuilder = ComplexDataClassWithFields.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = ComplexDataClassWithFields.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, AUTHOR)
        assertTableCount(0, COMPLEX_DATA_CLASS_WITH_FIELDS)
        assertTableCount(0, SIMPLE_VALUE_WITH_BUILDER)
        assertTableCount(0, SIMPLE_VALUE_WITH_CREATOR)
      }
    },
    nullableColumns = object : ComplexNullableColumns<ComplexDataClassWithFields> {
      override fun nullSomeColumns(target: ComplexDataClassWithFields): ComplexDataClassWithFields =
          ComplexDataClassWithFields(target.id, null, target.author, target.simpleValueWithBuilder, target.simpleValueWithCreator)

      override fun assertAllExceptNulledColumnsAreUpdated(target: ComplexDataClassWithFields, nulledVal: ComplexDataClassWithFields) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.simpleValueWithBuilder).isEqualTo(nulledVal.simpleValueWithBuilder)
        assertThat(target.simpleValueWithCreator).isEqualTo(nulledVal.simpleValueWithCreator)
        assertThat(target.author).isEqualTo(nulledVal.author)
        assertThat(target.name).isNotEqualTo(nulledVal.name)
        assertThat(target.name).isNotNull()
      }

      override fun nullSomeComplexColumns(target: ComplexDataClassWithFields): ComplexDataClassWithFields =
          ComplexDataClassWithFields(target.id, null, null, target.simpleValueWithBuilder, target.simpleValueWithCreator)

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: ComplexDataClassWithFields, nulledVal: ComplexDataClassWithFields) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.simpleValueWithBuilder).isEqualTo(nulledVal.simpleValueWithBuilder)
        assertThat(target.simpleValueWithCreator).isEqualTo(nulledVal.simpleValueWithCreator)
        assertThat(target.author).isNotEqualTo(nulledVal.author)
        assertThat(target.author).isNotNull()
        assertThat(target.name).isNotEqualTo(nulledVal.name)
        assertThat(target.name).isNotNull()
      }
    }
)

val complexDataClassWithMethodsAutoIdTestModel = ComplexTestModelWithNullableColumns(
    testModel = object : TestModel<ComplexDataClassWithMethods> {
      override val table: Table<ComplexDataClassWithMethods>
        get() = COMPLEX_DATA_CLASS_WITH_METHODS
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = COMPLEX_DATA_CLASS_WITH_METHODS.ID

      override fun deleteTable() {
        ComplexDataClassWithMethods.deleteTable().execute()
        Author.deleteTable().execute()
        SimpleValueWithBuilder.deleteTable().execute()
        SimpleValueWithCreator.deleteTable().execute()
      }

      override fun newRandom(): ComplexDataClassWithMethods = ComplexDataClassWithMethods.newRandom()
      override fun setId(v: ComplexDataClassWithMethods, id: Long?): ComplexDataClassWithMethods =
          SqliteMagic_ComplexDataClassWithMethods_Dao.setId(v, id!!)
      override fun getId(v: ComplexDataClassWithMethods): Long? = v.id
      override fun valsAreEqual(v1: ComplexDataClassWithMethods, v2: ComplexDataClassWithMethods): Boolean = v1.equalsWithoutId(v2)
      override fun updateAllVals(v: ComplexDataClassWithMethods, id: Long): ComplexDataClassWithMethods {
        val prevVal = Select.from(COMPLEX_DATA_CLASS_WITH_METHODS)
            .where(COMPLEX_DATA_CLASS_WITH_METHODS.ID.`is`(id))
            .queryDeep()
            .takeFirst()
            .execute()!!
        val author = Author.newRandom()
        author.id = prevVal.author!!.id
        val simpleValWBuilder = SimpleValueWithBuilder
            .newRandom()
            .id(prevVal.simpleValueWithBuilder!!.id())
            .build()
        val simpleValWCreator = SimpleValueWithCreator
            .newRandom(prevVal.simpleValueWithCreator!!.id())

        return ComplexDataClassWithMethods(
            id,
            Utils.randomTableName(),
            author,
            simpleValWBuilder,
            simpleValWCreator)
      }

      override fun insertBuilder(v: ComplexDataClassWithMethods): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<ComplexDataClassWithMethods>): EntityBulkInsertBuilder = ComplexDataClassWithMethods.insert(v)
      override fun updateBuilder(v: ComplexDataClassWithMethods): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<ComplexDataClassWithMethods>): EntityBulkUpdateBuilder = ComplexDataClassWithMethods.update(v)
      override fun persistBuilder(v: ComplexDataClassWithMethods): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<ComplexDataClassWithMethods>): EntityBulkPersistBuilder = ComplexDataClassWithMethods.persist(v)
      override fun deleteBuilder(v: ComplexDataClassWithMethods): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<ComplexDataClassWithMethods>): EntityBulkDeleteBuilder = ComplexDataClassWithMethods.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = ComplexDataClassWithMethods.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, AUTHOR)
        assertTableCount(0, COMPLEX_DATA_CLASS_WITH_METHODS)
        assertTableCount(0, SIMPLE_VALUE_WITH_BUILDER)
        assertTableCount(0, SIMPLE_VALUE_WITH_CREATOR)
      }
    },
    nullableColumns = object : ComplexNullableColumns<ComplexDataClassWithMethods> {
      override fun nullSomeColumns(target: ComplexDataClassWithMethods): ComplexDataClassWithMethods =
          ComplexDataClassWithMethods(target.id, null, target.author, target.simpleValueWithBuilder, target.simpleValueWithCreator)

      override fun assertAllExceptNulledColumnsAreUpdated(target: ComplexDataClassWithMethods, nulledVal: ComplexDataClassWithMethods) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.simpleValueWithBuilder).isEqualTo(nulledVal.simpleValueWithBuilder)
        assertThat(target.simpleValueWithCreator).isEqualTo(nulledVal.simpleValueWithCreator)
        assertThat(target.author).isEqualTo(nulledVal.author)
        assertThat(target.name).isNotEqualTo(nulledVal.name)
        assertThat(target.name).isNotNull()
      }

      override fun nullSomeComplexColumns(target: ComplexDataClassWithMethods): ComplexDataClassWithMethods =
          ComplexDataClassWithMethods(target.id, null, null, target.simpleValueWithBuilder, target.simpleValueWithCreator)

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: ComplexDataClassWithMethods, nulledVal: ComplexDataClassWithMethods) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.simpleValueWithBuilder).isEqualTo(nulledVal.simpleValueWithBuilder)
        assertThat(target.simpleValueWithCreator).isEqualTo(nulledVal.simpleValueWithCreator)
        assertThat(target.author).isNotEqualTo(nulledVal.author)
        assertThat(target.author).isNotNull()
        assertThat(target.name).isNotEqualTo(nulledVal.name)
        assertThat(target.name).isNotNull()
      }
    }
)

val complexMutableFixedIdUniqueNullableTestModel = ComplexTestModelWithUniqueNullableColumns(
    testModel = object : TestModel<ComplexMutableWithUnique> {
      override val table: Table<ComplexMutableWithUnique>
        get() = COMPLEX_MUTABLE_WITH_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = COMPLEX_MUTABLE_WITH_UNIQUE.ID

      override fun deleteTable() {
        ComplexMutableWithUnique.deleteTable().execute()
        SimpleMutableWithUnique.deleteTable().execute()
      }

      override fun newRandom(): ComplexMutableWithUnique = ComplexMutableWithUnique.newRandom()
      override fun setId(v: ComplexMutableWithUnique, id: Long?): ComplexMutableWithUnique {
        SqliteMagic_ComplexMutableWithUnique_Dao.setId(v, id!!)
        return v
      }

      override fun getId(v: ComplexMutableWithUnique): Long? = v.id
      override fun valsAreEqual(v1: ComplexMutableWithUnique, v2: ComplexMutableWithUnique): Boolean = v1 == v2
      override fun updateAllVals(v: ComplexMutableWithUnique, id: Long): ComplexMutableWithUnique {
        val newRandom = ComplexMutableWithUnique.newRandom()
        newRandom.id = id
        newRandom.complexVal.id = v.complexVal.id
        newRandom.complexVal2.id = v.complexVal2.id
        return newRandom
      }

      override fun insertBuilder(v: ComplexMutableWithUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<ComplexMutableWithUnique>): EntityBulkInsertBuilder = ComplexMutableWithUnique.insert(v)
      override fun updateBuilder(v: ComplexMutableWithUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<ComplexMutableWithUnique>): EntityBulkUpdateBuilder = ComplexMutableWithUnique.update(v)
      override fun persistBuilder(v: ComplexMutableWithUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<ComplexMutableWithUnique>): EntityBulkPersistBuilder = ComplexMutableWithUnique.persist(v)
      override fun deleteBuilder(v: ComplexMutableWithUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<ComplexMutableWithUnique>): EntityBulkDeleteBuilder = ComplexMutableWithUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = ComplexMutableWithUnique.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, COMPLEX_MUTABLE_WITH_UNIQUE)
        assertTableCount(0, SIMPLE_MUTABLE_WITH_UNIQUE)
      }
    },
    uniqueValue = object: ComplexUniqueValued<ComplexMutableWithUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = COMPLEX_MUTABLE_WITH_UNIQUE.UNIQUE_VAL
      override val complexUniqueColumn: Unique<NotNullable>
        get() = COMPLEX_MUTABLE_WITH_UNIQUE.COMPLEX_VAL2
      override val complexColumnUniqueColumn: Unique<NotNullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE.UNIQUE_VAL

          override fun getChildren(src: ComplexMutableWithUnique): Map<ChildMetadata, List<Long>> =
          mapOf(
              ChildMetadata(SIMPLE_MUTABLE_WITH_UNIQUE, SIMPLE_MUTABLE_WITH_UNIQUE.ID) to listOf(
                  src.complexVal.id,
                  src.complexVal2.id
              )
          )

      override fun transferUniqueVal(src: ComplexMutableWithUnique, target: ComplexMutableWithUnique): ComplexMutableWithUnique {
        target.uniqueVal = src.uniqueVal
        return target
      }

      override fun transferComplexUniqueVal(src: ComplexMutableWithUnique, target: ComplexMutableWithUnique): ComplexMutableWithUnique {
        target.complexVal2 = src.complexVal2
        return target
      }

      override fun transferComplexColumnUniqueVal(src: ComplexMutableWithUnique, target: ComplexMutableWithUnique): ComplexMutableWithUnique {
        target.complexVal2.uniqueVal = src.complexVal2.uniqueVal
        return target
      }

      override fun transferAllComplexUniqueVals(src: ComplexMutableWithUnique, target: ComplexMutableWithUnique): ComplexMutableWithUnique {
        target.complexVal.uniqueVal = src.complexVal.uniqueVal
        target.complexVal2.uniqueVal = src.complexVal2.uniqueVal
        return target
      }
    },
    nullableColumns = object: ComplexNullableColumns<ComplexMutableWithUnique> {
      override fun nullSomeColumns(target: ComplexMutableWithUnique): ComplexMutableWithUnique {
        target.string = null
        return target
      }

      override fun assertAllExceptNulledColumnsAreUpdated(target: ComplexMutableWithUnique, nulledVal: ComplexMutableWithUnique) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.complexVal).isEqualTo(nulledVal.complexVal)
        assertThat(target.complexVal2).isEqualTo(nulledVal.complexVal2)
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }

      override fun nullSomeComplexColumns(target: ComplexMutableWithUnique): ComplexMutableWithUnique {
        target.string = null
        target.complexVal = null
        return target
      }

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: ComplexMutableWithUnique, nulledVal: ComplexMutableWithUnique) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.complexVal2).isEqualTo(nulledVal.complexVal2)
        assertThat(target.complexVal).isNotEqualTo(nulledVal.complexVal)
        assertThat(target.complexVal).isNotNull()
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }
    }
)

val complexImmutableWithBuilderFixedIdUniqueNullableTestModel = ComplexTestModelWithUniqueNullableColumns(
    testModel = object : TestModel<ComplexImmutableBuilderWithUnique> {
      override val table: Table<ComplexImmutableBuilderWithUnique>
        get() = COMPLEX_IMMUTABLE_BUILDER_WITH_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = COMPLEX_IMMUTABLE_BUILDER_WITH_UNIQUE.ID

      override fun deleteTable() {
        ComplexImmutableBuilderWithUnique.deleteTable().execute()
        SimpleMutableWithUnique.deleteTable().execute()
      }

      override fun newRandom(): ComplexImmutableBuilderWithUnique = ComplexImmutableBuilderWithUnique.newRandom()
      override fun setId(v: ComplexImmutableBuilderWithUnique, id: Long?): ComplexImmutableBuilderWithUnique =
          v.copy()
              .id(id!!)
              .build()

      override fun getId(v: ComplexImmutableBuilderWithUnique): Long? = v.id()
      override fun valsAreEqual(v1: ComplexImmutableBuilderWithUnique, v2: ComplexImmutableBuilderWithUnique): Boolean = v1 == v2
      override fun updateAllVals(v: ComplexImmutableBuilderWithUnique, id: Long): ComplexImmutableBuilderWithUnique {
        val prevVal = Select.from(COMPLEX_IMMUTABLE_BUILDER_WITH_UNIQUE)
            .where(COMPLEX_IMMUTABLE_BUILDER_WITH_UNIQUE.ID.`is`(id))
            .queryDeep()
            .takeFirst()
            .execute()!!
        val complexVal = SimpleMutableWithUnique.newRandom()
        complexVal.id = prevVal.complexVal()!!.id

        val complexVal2 = SimpleMutableWithUnique.newRandom()
        complexVal2.id = prevVal.complexVal2().id

        return ComplexImmutableBuilderWithUnique.builder()
            .id(id)
            .uniqueVal(Random().nextLong())
            .complexVal(complexVal)
            .complexVal2(complexVal2)
            .build()
      }

      override fun insertBuilder(v: ComplexImmutableBuilderWithUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<ComplexImmutableBuilderWithUnique>): EntityBulkInsertBuilder = ComplexImmutableBuilderWithUnique.insert(v)
      override fun updateBuilder(v: ComplexImmutableBuilderWithUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<ComplexImmutableBuilderWithUnique>): EntityBulkUpdateBuilder = ComplexImmutableBuilderWithUnique.update(v)
      override fun persistBuilder(v: ComplexImmutableBuilderWithUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<ComplexImmutableBuilderWithUnique>): EntityBulkPersistBuilder = ComplexImmutableBuilderWithUnique.persist(v)
      override fun deleteBuilder(v: ComplexImmutableBuilderWithUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<ComplexImmutableBuilderWithUnique>): EntityBulkDeleteBuilder = ComplexImmutableBuilderWithUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = ComplexImmutableBuilderWithUnique.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, COMPLEX_IMMUTABLE_BUILDER_WITH_UNIQUE)
        assertTableCount(0, SIMPLE_MUTABLE_WITH_UNIQUE)
      }
    },
    uniqueValue = object: ComplexUniqueValued<ComplexImmutableBuilderWithUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = COMPLEX_IMMUTABLE_BUILDER_WITH_UNIQUE.UNIQUE_VAL
      override val complexUniqueColumn: Unique<NotNullable>
        get() = COMPLEX_IMMUTABLE_BUILDER_WITH_UNIQUE.COMPLEX_VAL2
      override val complexColumnUniqueColumn: Unique<NotNullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE.UNIQUE_VAL

      override fun getChildren(src: ComplexImmutableBuilderWithUnique): Map<ChildMetadata, List<Long>> =
          mapOf(
              ChildMetadata(SIMPLE_MUTABLE_WITH_UNIQUE, SIMPLE_MUTABLE_WITH_UNIQUE.ID) to listOf(
                  src.complexVal()!!.id,
                  src.complexVal2().id
              )
          )

      override fun transferUniqueVal(src: ComplexImmutableBuilderWithUnique, target: ComplexImmutableBuilderWithUnique): ComplexImmutableBuilderWithUnique =
          target.copy()
              .uniqueVal(src.uniqueVal())
              .build()

      override fun transferComplexUniqueVal(src: ComplexImmutableBuilderWithUnique, target: ComplexImmutableBuilderWithUnique): ComplexImmutableBuilderWithUnique =
          target.copy()
              .complexVal2(src.complexVal2())
              .build()

      override fun transferComplexColumnUniqueVal(src: ComplexImmutableBuilderWithUnique, target: ComplexImmutableBuilderWithUnique): ComplexImmutableBuilderWithUnique {
        target.complexVal2().uniqueVal = src.complexVal2().uniqueVal
        return target
      }

      override fun transferAllComplexUniqueVals(src: ComplexImmutableBuilderWithUnique, target: ComplexImmutableBuilderWithUnique): ComplexImmutableBuilderWithUnique {
        target.complexVal()!!.uniqueVal = src.complexVal()!!.uniqueVal
        target.complexVal2().uniqueVal = src.complexVal2().uniqueVal
        return target
      }
    },
    nullableColumns = object: ComplexNullableColumns<ComplexImmutableBuilderWithUnique> {
      override fun nullSomeColumns(target: ComplexImmutableBuilderWithUnique): ComplexImmutableBuilderWithUnique =
          target.copy()
              .string(null)
              .build()

      override fun assertAllExceptNulledColumnsAreUpdated(target: ComplexImmutableBuilderWithUnique, nulledVal: ComplexImmutableBuilderWithUnique) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.uniqueVal()).isEqualTo(nulledVal.uniqueVal())
        assertThat(target.complexVal()).isEqualTo(nulledVal.complexVal())
        assertThat(target.complexVal2()).isEqualTo(nulledVal.complexVal2())
        assertThat(target.string()).isNotEqualTo(nulledVal.string())
        assertThat(target.string()).isNotNull()
      }

      override fun nullSomeComplexColumns(target: ComplexImmutableBuilderWithUnique): ComplexImmutableBuilderWithUnique =
          target.copy()
              .string(null)
              .complexVal(null)
              .build()

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: ComplexImmutableBuilderWithUnique, nulledVal: ComplexImmutableBuilderWithUnique) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.uniqueVal()).isEqualTo(nulledVal.uniqueVal())
        assertThat(target.complexVal2()).isEqualTo(nulledVal.complexVal2())
        assertThat(target.complexVal()).isNotEqualTo(nulledVal.complexVal())
        assertThat(target.complexVal()).isNotNull()
        assertThat(target.string()).isNotEqualTo(nulledVal.string())
        assertThat(target.string()).isNotNull()
      }
    }
)

val complexImmutableWithCreatorFixedIdUniqueNullableTestModel = ComplexTestModelWithUniqueNullableColumns(
    testModel = object : TestModel<ComplexImmutableCreatorWithUnique> {
      override val table: Table<ComplexImmutableCreatorWithUnique>
        get() = COMPLEX_IMMUTABLE_CREATOR_WITH_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = COMPLEX_IMMUTABLE_CREATOR_WITH_UNIQUE.ID

      override fun deleteTable() {
        ComplexImmutableCreatorWithUnique.deleteTable().execute()
        SimpleMutableWithUnique.deleteTable().execute()
      }

      override fun newRandom(): ComplexImmutableCreatorWithUnique = ComplexImmutableCreatorWithUnique.newRandom()
      override fun setId(v: ComplexImmutableCreatorWithUnique, id: Long?): ComplexImmutableCreatorWithUnique =
          ComplexImmutableCreatorWithUnique.create(
              id!!,
              v.uniqueVal(),
              v.string(),
              v.complexVal(),
              v.complexVal2())

      override fun getId(v: ComplexImmutableCreatorWithUnique): Long? = v.id()
      override fun valsAreEqual(v1: ComplexImmutableCreatorWithUnique, v2: ComplexImmutableCreatorWithUnique): Boolean = v1 == v2
      override fun updateAllVals(v: ComplexImmutableCreatorWithUnique, id: Long): ComplexImmutableCreatorWithUnique {
        val prevVal = Select.from(COMPLEX_IMMUTABLE_CREATOR_WITH_UNIQUE)
            .where(COMPLEX_IMMUTABLE_CREATOR_WITH_UNIQUE.ID.`is`(id))
            .queryDeep()
            .takeFirst()
            .execute()!!
        val complexVal = SimpleMutableWithUnique.newRandom()
        complexVal.id = prevVal.complexVal()!!.id

        val complexVal2 = SimpleMutableWithUnique.newRandom()
        complexVal2.id = prevVal.complexVal2().id

        return ComplexImmutableCreatorWithUnique.create(
            id,
            Random().nextLong(),
            Utils.randomTableName(),
            complexVal,
            complexVal2)
      }

      override fun insertBuilder(v: ComplexImmutableCreatorWithUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<ComplexImmutableCreatorWithUnique>): EntityBulkInsertBuilder = ComplexImmutableCreatorWithUnique.insert(v)
      override fun updateBuilder(v: ComplexImmutableCreatorWithUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<ComplexImmutableCreatorWithUnique>): EntityBulkUpdateBuilder = ComplexImmutableCreatorWithUnique.update(v)
      override fun persistBuilder(v: ComplexImmutableCreatorWithUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<ComplexImmutableCreatorWithUnique>): EntityBulkPersistBuilder = ComplexImmutableCreatorWithUnique.persist(v)
      override fun deleteBuilder(v: ComplexImmutableCreatorWithUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<ComplexImmutableCreatorWithUnique>): EntityBulkDeleteBuilder = ComplexImmutableCreatorWithUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = ComplexImmutableCreatorWithUnique.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, COMPLEX_IMMUTABLE_CREATOR_WITH_UNIQUE)
        assertTableCount(0, SIMPLE_MUTABLE_WITH_UNIQUE)
      }
    },
    uniqueValue = object: ComplexUniqueValued<ComplexImmutableCreatorWithUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = COMPLEX_IMMUTABLE_CREATOR_WITH_UNIQUE.UNIQUE_VAL
      override val complexUniqueColumn: Unique<NotNullable>
        get() = COMPLEX_IMMUTABLE_CREATOR_WITH_UNIQUE.COMPLEX_VAL2
      override val complexColumnUniqueColumn: Unique<NotNullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE.UNIQUE_VAL

      override fun getChildren(src: ComplexImmutableCreatorWithUnique): Map<ChildMetadata, List<Long>> =
          mapOf(
              ChildMetadata(SIMPLE_MUTABLE_WITH_UNIQUE, SIMPLE_MUTABLE_WITH_UNIQUE.ID) to listOf(
                  src.complexVal()!!.id,
                  src.complexVal2().id
              )
          )

      override fun transferUniqueVal(src: ComplexImmutableCreatorWithUnique, target: ComplexImmutableCreatorWithUnique): ComplexImmutableCreatorWithUnique =
          ComplexImmutableCreatorWithUnique.create(
              target.id(),
              src.uniqueVal(),
              target.string(),
              target.complexVal(),
              target.complexVal2())

      override fun transferComplexUniqueVal(src: ComplexImmutableCreatorWithUnique, target: ComplexImmutableCreatorWithUnique): ComplexImmutableCreatorWithUnique =
          ComplexImmutableCreatorWithUnique.create(
              target.id(),
              target.uniqueVal(),
              target.string(),
              target.complexVal(),
              src.complexVal2())

      override fun transferComplexColumnUniqueVal(src: ComplexImmutableCreatorWithUnique, target: ComplexImmutableCreatorWithUnique): ComplexImmutableCreatorWithUnique {
        target.complexVal2().uniqueVal = src.complexVal2().uniqueVal
        return target
      }

      override fun transferAllComplexUniqueVals(src: ComplexImmutableCreatorWithUnique, target: ComplexImmutableCreatorWithUnique): ComplexImmutableCreatorWithUnique {
        target.complexVal()!!.uniqueVal = src.complexVal()!!.uniqueVal
        target.complexVal2().uniqueVal = src.complexVal2().uniqueVal
        return target
      }
    },
    nullableColumns = object: ComplexNullableColumns<ComplexImmutableCreatorWithUnique> {
      override fun nullSomeColumns(target: ComplexImmutableCreatorWithUnique): ComplexImmutableCreatorWithUnique =
          ComplexImmutableCreatorWithUnique.create(target.id(), target.uniqueVal(), null, target.complexVal(), target.complexVal2())

      override fun assertAllExceptNulledColumnsAreUpdated(target: ComplexImmutableCreatorWithUnique, nulledVal: ComplexImmutableCreatorWithUnique) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.uniqueVal()).isEqualTo(nulledVal.uniqueVal())
        assertThat(target.complexVal()).isEqualTo(nulledVal.complexVal())
        assertThat(target.complexVal2()).isEqualTo(nulledVal.complexVal2())
        assertThat(target.string()).isNotEqualTo(nulledVal.string())
        assertThat(target.string()).isNotNull()
      }

      override fun nullSomeComplexColumns(target: ComplexImmutableCreatorWithUnique): ComplexImmutableCreatorWithUnique =
          ComplexImmutableCreatorWithUnique.create(target.id(), target.uniqueVal(), null, null, target.complexVal2())

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: ComplexImmutableCreatorWithUnique, nulledVal: ComplexImmutableCreatorWithUnique) {
        assertThat(target.id()).isEqualTo(nulledVal.id())
        assertThat(target.uniqueVal()).isEqualTo(nulledVal.uniqueVal())
        assertThat(target.complexVal2()).isEqualTo(nulledVal.complexVal2())
        assertThat(target.complexVal()).isNotEqualTo(nulledVal.complexVal())
        assertThat(target.complexVal()).isNotNull()
        assertThat(target.string()).isNotEqualTo(nulledVal.string())
        assertThat(target.string()).isNotNull()
      }
    }
)

val complexDataClassWithFieldsFixedIdUniqueNullableTestModel = ComplexTestModelWithUniqueNullableColumns(
    testModel = object : TestModel<ComplexDataClassWithFieldsAndUnique> {
      override val table: Table<ComplexDataClassWithFieldsAndUnique>
        get() = COMPLEX_DATA_CLASS_WITH_FIELDS_AND_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = COMPLEX_DATA_CLASS_WITH_FIELDS_AND_UNIQUE.ID

      override fun deleteTable() {
        ComplexDataClassWithFieldsAndUnique.deleteTable().execute()
        SimpleMutableWithUnique.deleteTable().execute()
      }

      override fun newRandom(): ComplexDataClassWithFieldsAndUnique = ComplexDataClassWithFieldsAndUnique.newRandom()
      override fun setId(v: ComplexDataClassWithFieldsAndUnique, id: Long?): ComplexDataClassWithFieldsAndUnique =
          ComplexDataClassWithFieldsAndUnique.create(
              id!!,
              v.uniqueVal,
              v.string,
              v.complexVal,
              v.complexVal2)

      override fun getId(v: ComplexDataClassWithFieldsAndUnique): Long? = v.id
      override fun valsAreEqual(v1: ComplexDataClassWithFieldsAndUnique, v2: ComplexDataClassWithFieldsAndUnique): Boolean = v1 == v2
      override fun updateAllVals(v: ComplexDataClassWithFieldsAndUnique, id: Long): ComplexDataClassWithFieldsAndUnique {
        val prevVal = Select.from(COMPLEX_DATA_CLASS_WITH_FIELDS_AND_UNIQUE)
            .where(COMPLEX_DATA_CLASS_WITH_FIELDS_AND_UNIQUE.ID.`is`(id))
            .queryDeep()
            .takeFirst()
            .execute()!!
        val complexVal = SimpleMutableWithUnique.newRandom()
        complexVal.id = prevVal.complexVal.id

        val complexVal2 = SimpleMutableWithUnique.newRandom()
        complexVal2.id = prevVal.complexVal2.id

        return ComplexDataClassWithFieldsAndUnique.create(
            id,
            Random().nextLong(),
            Utils.randomTableName(),
            complexVal,
            complexVal2)
      }

      override fun insertBuilder(v: ComplexDataClassWithFieldsAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<ComplexDataClassWithFieldsAndUnique>): EntityBulkInsertBuilder = ComplexDataClassWithFieldsAndUnique.insert(v)
      override fun updateBuilder(v: ComplexDataClassWithFieldsAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<ComplexDataClassWithFieldsAndUnique>): EntityBulkUpdateBuilder = ComplexDataClassWithFieldsAndUnique.update(v)
      override fun persistBuilder(v: ComplexDataClassWithFieldsAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<ComplexDataClassWithFieldsAndUnique>): EntityBulkPersistBuilder = ComplexDataClassWithFieldsAndUnique.persist(v)
      override fun deleteBuilder(v: ComplexDataClassWithFieldsAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<ComplexDataClassWithFieldsAndUnique>): EntityBulkDeleteBuilder = ComplexDataClassWithFieldsAndUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = ComplexDataClassWithFieldsAndUnique.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, COMPLEX_DATA_CLASS_WITH_FIELDS_AND_UNIQUE)
        assertTableCount(0, SIMPLE_MUTABLE_WITH_UNIQUE)
      }
    },
    uniqueValue = object: ComplexUniqueValued<ComplexDataClassWithFieldsAndUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = COMPLEX_DATA_CLASS_WITH_FIELDS_AND_UNIQUE.UNIQUE_VAL
      override val complexUniqueColumn: Unique<NotNullable>
        get() = COMPLEX_DATA_CLASS_WITH_FIELDS_AND_UNIQUE.COMPLEX_VAL2
      override val complexColumnUniqueColumn: Unique<NotNullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE.UNIQUE_VAL

      override fun getChildren(src: ComplexDataClassWithFieldsAndUnique): Map<ChildMetadata, List<Long>> =
          mapOf(
              ChildMetadata(SIMPLE_MUTABLE_WITH_UNIQUE, SIMPLE_MUTABLE_WITH_UNIQUE.ID) to listOf(
                  src.complexVal.id,
                  src.complexVal2.id
              )
          )

      override fun transferUniqueVal(src: ComplexDataClassWithFieldsAndUnique, target: ComplexDataClassWithFieldsAndUnique): ComplexDataClassWithFieldsAndUnique =
          ComplexDataClassWithFieldsAndUnique.create(
              target.id,
              src.uniqueVal,
              target.string,
              target.complexVal,
              target.complexVal2)

      override fun transferComplexUniqueVal(src: ComplexDataClassWithFieldsAndUnique, target: ComplexDataClassWithFieldsAndUnique): ComplexDataClassWithFieldsAndUnique =
          ComplexDataClassWithFieldsAndUnique.create(
              target.id,
              target.uniqueVal,
              target.string,
              target.complexVal,
              src.complexVal2)

      override fun transferComplexColumnUniqueVal(src: ComplexDataClassWithFieldsAndUnique, target: ComplexDataClassWithFieldsAndUnique): ComplexDataClassWithFieldsAndUnique {
        target.complexVal2.uniqueVal = src.complexVal2.uniqueVal
        return target
      }

      override fun transferAllComplexUniqueVals(src: ComplexDataClassWithFieldsAndUnique, target: ComplexDataClassWithFieldsAndUnique): ComplexDataClassWithFieldsAndUnique {
        target.complexVal.uniqueVal = src.complexVal.uniqueVal
        target.complexVal2.uniqueVal = src.complexVal2.uniqueVal
        return target
      }
    },
    nullableColumns = object: ComplexNullableColumns<ComplexDataClassWithFieldsAndUnique> {
      override fun nullSomeColumns(target: ComplexDataClassWithFieldsAndUnique): ComplexDataClassWithFieldsAndUnique =
          ComplexDataClassWithFieldsAndUnique(target.id, target.uniqueVal, null, target.complexVal, target.complexVal2)

      override fun assertAllExceptNulledColumnsAreUpdated(target: ComplexDataClassWithFieldsAndUnique, nulledVal: ComplexDataClassWithFieldsAndUnique) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.complexVal).isEqualTo(nulledVal.complexVal)
        assertThat(target.complexVal2).isEqualTo(nulledVal.complexVal2)
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }

      override fun nullSomeComplexColumns(target: ComplexDataClassWithFieldsAndUnique): ComplexDataClassWithFieldsAndUnique =
          ComplexDataClassWithFieldsAndUnique(target.id, target.uniqueVal, null, null, target.complexVal2)

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: ComplexDataClassWithFieldsAndUnique, nulledVal: ComplexDataClassWithFieldsAndUnique) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.complexVal2).isEqualTo(nulledVal.complexVal2)
        assertThat(target.complexVal).isNotEqualTo(nulledVal.complexVal)
        assertThat(target.complexVal).isNotNull()
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }
    }
)

val complexDataClassWithMethodsFixedIdUniqueNullableTestModel = ComplexTestModelWithUniqueNullableColumns(
    testModel = object : TestModel<ComplexDataClassWithMethodsAndUnique> {
      override val table: Table<ComplexDataClassWithMethodsAndUnique>
        get() = COMPLEX_DATA_CLASS_WITH_METHODS_AND_UNIQUE
      override val idColumn: Column<Long, Long, Number, *, NotNullable>
        get() = COMPLEX_DATA_CLASS_WITH_METHODS_AND_UNIQUE.ID

      override fun deleteTable() {
        ComplexDataClassWithMethodsAndUnique.deleteTable().execute()
        SimpleMutableWithUnique.deleteTable().execute()
      }

      override fun newRandom(): ComplexDataClassWithMethodsAndUnique = ComplexDataClassWithMethodsAndUnique.newRandom()
      override fun setId(v: ComplexDataClassWithMethodsAndUnique, id: Long?): ComplexDataClassWithMethodsAndUnique =
          ComplexDataClassWithMethodsAndUnique.create(
              id!!,
              v.uniqueVal,
              v.string,
              v.complexVal,
              v.complexVal2)

      override fun getId(v: ComplexDataClassWithMethodsAndUnique): Long? = v.id
      override fun valsAreEqual(v1: ComplexDataClassWithMethodsAndUnique, v2: ComplexDataClassWithMethodsAndUnique): Boolean = v1 == v2
      override fun updateAllVals(v: ComplexDataClassWithMethodsAndUnique, id: Long): ComplexDataClassWithMethodsAndUnique {
        val prevVal = Select.from(COMPLEX_DATA_CLASS_WITH_METHODS_AND_UNIQUE)
            .where(COMPLEX_DATA_CLASS_WITH_METHODS_AND_UNIQUE.ID.`is`(id))
            .queryDeep()
            .takeFirst()
            .execute()!!
        val complexVal = SimpleMutableWithUnique.newRandom()
        complexVal.id = prevVal.complexVal.id

        val complexVal2 = SimpleMutableWithUnique.newRandom()
        complexVal2.id = prevVal.complexVal2.id

        return ComplexDataClassWithMethodsAndUnique.create(
            id,
            Random().nextLong(),
            Utils.randomTableName(),
            complexVal,
            complexVal2)
      }

      override fun insertBuilder(v: ComplexDataClassWithMethodsAndUnique): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<ComplexDataClassWithMethodsAndUnique>): EntityBulkInsertBuilder = ComplexDataClassWithMethodsAndUnique.insert(v)
      override fun updateBuilder(v: ComplexDataClassWithMethodsAndUnique): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<ComplexDataClassWithMethodsAndUnique>): EntityBulkUpdateBuilder = ComplexDataClassWithMethodsAndUnique.update(v)
      override fun persistBuilder(v: ComplexDataClassWithMethodsAndUnique): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<ComplexDataClassWithMethodsAndUnique>): EntityBulkPersistBuilder = ComplexDataClassWithMethodsAndUnique.persist(v)
      override fun deleteBuilder(v: ComplexDataClassWithMethodsAndUnique): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<ComplexDataClassWithMethodsAndUnique>): EntityBulkDeleteBuilder = ComplexDataClassWithMethodsAndUnique.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = ComplexDataClassWithMethodsAndUnique.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, COMPLEX_DATA_CLASS_WITH_METHODS_AND_UNIQUE)
        assertTableCount(0, SIMPLE_MUTABLE_WITH_UNIQUE)
      }
    },
    uniqueValue = object: ComplexUniqueValued<ComplexDataClassWithMethodsAndUnique> {
      override val uniqueColumn: Unique<NotNullable>
        get() = COMPLEX_DATA_CLASS_WITH_METHODS_AND_UNIQUE.UNIQUE_VAL
      override val complexUniqueColumn: Unique<NotNullable>
        get() = COMPLEX_DATA_CLASS_WITH_METHODS_AND_UNIQUE.COMPLEX_VAL2
      override val complexColumnUniqueColumn: Unique<NotNullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE.UNIQUE_VAL

      override fun getChildren(src: ComplexDataClassWithMethodsAndUnique): Map<ChildMetadata, List<Long>> =
          mapOf(
              ChildMetadata(SIMPLE_MUTABLE_WITH_UNIQUE, SIMPLE_MUTABLE_WITH_UNIQUE.ID) to listOf(
                  src.complexVal.id,
                  src.complexVal2.id
              )
          )

      override fun transferUniqueVal(src: ComplexDataClassWithMethodsAndUnique, target: ComplexDataClassWithMethodsAndUnique): ComplexDataClassWithMethodsAndUnique =
          ComplexDataClassWithMethodsAndUnique.create(
              target.id,
              src.uniqueVal,
              target.string,
              target.complexVal,
              target.complexVal2)

      override fun transferComplexUniqueVal(src: ComplexDataClassWithMethodsAndUnique, target: ComplexDataClassWithMethodsAndUnique): ComplexDataClassWithMethodsAndUnique =
          ComplexDataClassWithMethodsAndUnique.create(
              target.id,
              target.uniqueVal,
              target.string,
              target.complexVal,
              src.complexVal2)

      override fun transferComplexColumnUniqueVal(src: ComplexDataClassWithMethodsAndUnique, target: ComplexDataClassWithMethodsAndUnique): ComplexDataClassWithMethodsAndUnique {
        target.complexVal2.uniqueVal = src.complexVal2.uniqueVal
        return target
      }

      override fun transferAllComplexUniqueVals(src: ComplexDataClassWithMethodsAndUnique, target: ComplexDataClassWithMethodsAndUnique): ComplexDataClassWithMethodsAndUnique {
        target.complexVal.uniqueVal = src.complexVal.uniqueVal
        target.complexVal2.uniqueVal = src.complexVal2.uniqueVal
        return target
      }
    },
    nullableColumns = object: ComplexNullableColumns<ComplexDataClassWithMethodsAndUnique> {
      override fun nullSomeColumns(target: ComplexDataClassWithMethodsAndUnique): ComplexDataClassWithMethodsAndUnique =
          ComplexDataClassWithMethodsAndUnique(target.id, target.uniqueVal, null, target.complexVal, target.complexVal2)

      override fun assertAllExceptNulledColumnsAreUpdated(target: ComplexDataClassWithMethodsAndUnique, nulledVal: ComplexDataClassWithMethodsAndUnique) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.complexVal).isEqualTo(nulledVal.complexVal)
        assertThat(target.complexVal2).isEqualTo(nulledVal.complexVal2)
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }

      override fun nullSomeComplexColumns(target: ComplexDataClassWithMethodsAndUnique): ComplexDataClassWithMethodsAndUnique =
          ComplexDataClassWithMethodsAndUnique(target.id, target.uniqueVal, null, null, target.complexVal2)

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: ComplexDataClassWithMethodsAndUnique, nulledVal: ComplexDataClassWithMethodsAndUnique) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.complexVal2).isEqualTo(nulledVal.complexVal2)
        assertThat(target.complexVal).isNotEqualTo(nulledVal.complexVal)
        assertThat(target.complexVal).isNotNull()
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }
    }
)

val complexMutableAutoIdUniqueNullableTestModel = ComplexTestModelWithUniqueNullableColumns(
    testModel = object : TestModel<ComplexMutableWithUniqueAndNullableId> {
      override val table: Table<ComplexMutableWithUniqueAndNullableId>
        get() = COMPLEX_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID
      override val idColumn: Column<Long, Long, Number, *, Nullable>
        get() = COMPLEX_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID.ID

      override fun deleteTable() {
        ComplexMutableWithUniqueAndNullableId.deleteTable().execute()
        SimpleMutableWithUnique.deleteTable().execute()
      }

      override fun newRandom(): ComplexMutableWithUniqueAndNullableId = ComplexMutableWithUniqueAndNullableId.newRandom()
      override fun setId(v: ComplexMutableWithUniqueAndNullableId, id: Long?): ComplexMutableWithUniqueAndNullableId {
        SqliteMagic_ComplexMutableWithUniqueAndNullableId_Dao.setId(v, id)
        return v
      }

      override fun getId(v: ComplexMutableWithUniqueAndNullableId): Long? = v.id
      override fun valsAreEqual(v1: ComplexMutableWithUniqueAndNullableId, v2: ComplexMutableWithUniqueAndNullableId): Boolean = v1 == v2
      override fun updateAllVals(v: ComplexMutableWithUniqueAndNullableId, id: Long): ComplexMutableWithUniqueAndNullableId {
        val newRandom = ComplexMutableWithUniqueAndNullableId.newRandom()
        newRandom.id = id
        newRandom.complexVal.id = v.complexVal.id
        newRandom.complexVal2.id = v.complexVal2.id
        return newRandom
      }

      override fun insertBuilder(v: ComplexMutableWithUniqueAndNullableId): EntityInsertBuilder = v.insert()
      override fun bulkInsertBuilder(v: Iterable<ComplexMutableWithUniqueAndNullableId>): EntityBulkInsertBuilder = ComplexMutableWithUniqueAndNullableId.insert(v)
      override fun updateBuilder(v: ComplexMutableWithUniqueAndNullableId): EntityUpdateBuilder = v.update()
      override fun bulkUpdateBuilder(v: Iterable<ComplexMutableWithUniqueAndNullableId>): EntityBulkUpdateBuilder = ComplexMutableWithUniqueAndNullableId.update(v)
      override fun persistBuilder(v: ComplexMutableWithUniqueAndNullableId): EntityPersistBuilder = v.persist()
      override fun bulkPersistBuilder(v: Iterable<ComplexMutableWithUniqueAndNullableId>): EntityBulkPersistBuilder = ComplexMutableWithUniqueAndNullableId.persist(v)
      override fun deleteBuilder(v: ComplexMutableWithUniqueAndNullableId): EntityDeleteBuilder = v.delete()
      override fun bulkDeleteBuilder(v: Collection<ComplexMutableWithUniqueAndNullableId>): EntityBulkDeleteBuilder = ComplexMutableWithUniqueAndNullableId.delete(v)
      override fun deleteTableBuilder(): EntityDeleteTableBuilder = ComplexMutableWithUniqueAndNullableId.deleteTable()
      override fun assertNoValsInTables() {
        assertTableCount(0, COMPLEX_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID)
        assertTableCount(0, SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID)
      }
    },
    uniqueValue = object: ComplexUniqueValued<ComplexMutableWithUniqueAndNullableId> {
      override val uniqueColumn: Unique<NotNullable>
        get() = COMPLEX_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID.UNIQUE_VAL
      override val complexUniqueColumn: Unique<NotNullable>
        get() = COMPLEX_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID.COMPLEX_VAL2
      override val complexColumnUniqueColumn: Unique<NotNullable>
        get() = SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID.UNIQUE_VAL

      override fun getChildren(src: ComplexMutableWithUniqueAndNullableId): Map<ChildMetadata, List<Long>> =
          mapOf(
              ChildMetadata(SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID, SIMPLE_MUTABLE_WITH_UNIQUE_AND_NULLABLE_ID.ID as Column<Long, Long, Number, *, NotNullable>) to listOf(
                  src.complexVal.id!!,
                  src.complexVal2.id!!
              )
          )

      override fun transferUniqueVal(src: ComplexMutableWithUniqueAndNullableId, target: ComplexMutableWithUniqueAndNullableId): ComplexMutableWithUniqueAndNullableId {
        target.uniqueVal = src.uniqueVal
        return target
      }

      override fun transferComplexUniqueVal(src: ComplexMutableWithUniqueAndNullableId, target: ComplexMutableWithUniqueAndNullableId): ComplexMutableWithUniqueAndNullableId {
        target.complexVal2 = src.complexVal2
        return target
      }

      override fun transferComplexColumnUniqueVal(src: ComplexMutableWithUniqueAndNullableId, target: ComplexMutableWithUniqueAndNullableId): ComplexMutableWithUniqueAndNullableId {
        target.complexVal2.uniqueVal = src.complexVal2.uniqueVal
        return target
      }

      override fun transferAllComplexUniqueVals(src: ComplexMutableWithUniqueAndNullableId, target: ComplexMutableWithUniqueAndNullableId): ComplexMutableWithUniqueAndNullableId {
        target.complexVal.uniqueVal = src.complexVal.uniqueVal
        target.complexVal2.uniqueVal = src.complexVal2.uniqueVal
        return target
      }
    },
    nullableColumns = object: ComplexNullableColumns<ComplexMutableWithUniqueAndNullableId> {
      override fun nullSomeColumns(target: ComplexMutableWithUniqueAndNullableId): ComplexMutableWithUniqueAndNullableId {
        target.string = null
        return target
      }

      override fun assertAllExceptNulledColumnsAreUpdated(target: ComplexMutableWithUniqueAndNullableId, nulledVal: ComplexMutableWithUniqueAndNullableId) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.complexVal).isEqualTo(nulledVal.complexVal)
        assertThat(target.complexVal2).isEqualTo(nulledVal.complexVal2)
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }

      override fun nullSomeComplexColumns(target: ComplexMutableWithUniqueAndNullableId): ComplexMutableWithUniqueAndNullableId {
        target.string = null
        target.complexVal = null
        return target
      }

      override fun assertAllExceptNulledComplexColumnsAreUpdated(target: ComplexMutableWithUniqueAndNullableId, nulledVal: ComplexMutableWithUniqueAndNullableId) {
        assertThat(target.id).isEqualTo(nulledVal.id)
        assertThat(target.uniqueVal).isEqualTo(nulledVal.uniqueVal)
        assertThat(target.complexVal2).isEqualTo(nulledVal.complexVal2)
        assertThat(target.complexVal).isNotEqualTo(nulledVal.complexVal)
        assertThat(target.complexVal).isNotNull()
        assertThat(target.string).isNotEqualTo(nulledVal.string)
        assertThat(target.string).isNotNull()
      }
    }
)

val COMPLEX_FIXED_ID_MODELS = arrayOf(
    complexMutableFixedIdUniqueNullableTestModel,
    complexImmutableWithBuilderFixedIdUniqueNullableTestModel,
    complexImmutableWithCreatorFixedIdUniqueNullableTestModel,
    complexDataClassWithFieldsFixedIdUniqueNullableTestModel,
    complexDataClassWithMethodsFixedIdUniqueNullableTestModel)

val COMPLEX_NULLABLE_FIXED_ID_MODELS = arrayOf(
    complexMutableFixedIdUniqueNullableTestModel,
    complexImmutableWithBuilderFixedIdUniqueNullableTestModel,
    complexImmutableWithCreatorFixedIdUniqueNullableTestModel,
    complexDataClassWithFieldsFixedIdUniqueNullableTestModel,
    complexDataClassWithMethodsFixedIdUniqueNullableTestModel)

val COMPLEX_AUTO_ID_MODELS = arrayOf(
    complexMutableAutoIdTestModel,
    complexImmutableWithBuilderAutoIdTestModel,
    complexImmutableWithCreatorAutoIdTestModel,
    complexDataClassWithFieldsAutoIdTestModel,
    complexDataClassWithMethodsAutoIdTestModel)

val COMPLEX_NULLABLE_AUTO_ID_MODELS = arrayOf(
    complexMutableAutoIdTestModel,
    complexImmutableWithBuilderAutoIdTestModel,
    complexImmutableWithCreatorAutoIdTestModel,
    complexDataClassWithFieldsAutoIdTestModel,
    complexDataClassWithMethodsAutoIdTestModel)

val COMPLEX_NULLABLE_UNIQUE_AUTO_ID_MODELS = arrayOf(
    complexMutableAutoIdUniqueNullableTestModel)