package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.NoIdEntityTable
import com.siimkinks.sqlitemagic.NoIdEntitys
import com.siimkinks.sqlitemagic.NoIdUniqueEntityTable
import com.siimkinks.sqlitemagic.NoIdUniqueEntitys
import com.siimkinks.sqlitemagic.StringIdEntityTable
import com.siimkinks.sqlitemagic.StringIdEntitys
import com.siimkinks.sqlitemagic.WithoutRowIdEntityTable
import com.siimkinks.sqlitemagic.WithoutRowIdEntitys
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.NoIdEntity
import com.siimkinks.sqlitemagic.fixture.model.NoIdUniqueEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.fixture.model.WithoutRowIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.BulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase

internal object IdentityModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    StringIdEntityCase,
    NoIdEntityCase,
    NoIdUniqueEntityCase,
    WithoutRowIdEntityCase,
  )

  private object StringIdEntityCase : BulkInsertModelCase<StringIdEntity> {
    override val name = "StringIdEntity"
    override val table = StringIdEntityTable.STRING_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = StringIdEntity(
      id = "string-id-$sequence",
      value = "string-value-$sequence"
    )

    override fun insert(value: StringIdEntity) = value.insert()

    override fun bulkInsert(values: List<StringIdEntity>) = StringIdEntitys.insert(values)

    override fun expectedAfterInsert(value: StringIdEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object NoIdEntityCase : BulkInsertModelCase<NoIdEntity> {
    override val name = "NoIdEntity"
    override val table = NoIdEntityTable.NO_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdEntity(value = "no-id-$sequence")

    override fun insert(value: NoIdEntity) = value.insert()

    override fun bulkInsert(values: List<NoIdEntity>) = NoIdEntitys.insert(values)

    override fun expectedAfterInsert(value: NoIdEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object WithoutRowIdEntityCase : BulkInsertModelCase<WithoutRowIdEntity> {
    override val name = "WithoutRowIdEntity"
    override val table = WithoutRowIdEntityTable.WITHOUT_ROW_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.ABSENT

    override fun newValue(sequence: Int) = WithoutRowIdEntity(
      id = "without-rowid-$sequence",
      value = "without-rowid-value-$sequence"
    )

    override fun insert(value: WithoutRowIdEntity) = value.insert()

    override fun bulkInsert(values: List<WithoutRowIdEntity>) = WithoutRowIdEntitys.insert(values)

    override fun expectedAfterInsert(value: WithoutRowIdEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object NoIdUniqueEntityCase : UniqueInsertModelCase<NoIdUniqueEntity> {
    override val name = "NoIdUniqueEntity"
    override val table = NoIdUniqueEntityTable.NO_ID_UNIQUE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdUniqueEntity(
      uniqueValue = "no-id-unique-$sequence",
      value = "no-id-unique-value-$sequence"
    )

    override fun insert(value: NoIdUniqueEntity) = value.insert()

    override fun bulkInsert(values: List<NoIdUniqueEntity>) = NoIdUniqueEntitys.insert(values)

    override fun expectedAfterInsert(value: NoIdUniqueEntity, result: EntityInsertResult.Inserted) = value

    override fun conflictingValue(existing: NoIdUniqueEntity, sequence: Int) = existing.copy(
      value = "no-id-unique-conflicting-value-$sequence"
    )

    override fun toString() = name
  }
}
