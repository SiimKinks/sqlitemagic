package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.NoIdUniqueEntityTable
import com.siimkinks.sqlitemagic.NoIdEntityTable
import com.siimkinks.sqlitemagic.StringIdEntityTable
import com.siimkinks.sqlitemagic.WithoutRowIdEntityTable
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.NoIdUniqueEntity
import com.siimkinks.sqlitemagic.fixture.model.NoIdEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.fixture.model.WithoutRowIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.model.InsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase

internal object IdentityModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    StringIdEntityCase,
    NoIdEntityCase,
    NoIdUniqueEntityCase,
    WithoutRowIdEntityCase,
  )

  private object StringIdEntityCase : InsertModelCase<StringIdEntity> {
    override val name = "StringIdEntity"
    override val table = StringIdEntityTable.STRING_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = StringIdEntity(
      id = "string-id",
      value = "string-value"
    )

    override fun insert(value: StringIdEntity) = value.insert()

    override fun expectedAfterInsert(value: StringIdEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object NoIdEntityCase : InsertModelCase<NoIdEntity> {
    override val name = "NoIdEntity"
    override val table = NoIdEntityTable.NO_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdEntity(value = "no-id")

    override fun insert(value: NoIdEntity) = value.insert()

    override fun expectedAfterInsert(value: NoIdEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object WithoutRowIdEntityCase : InsertModelCase<WithoutRowIdEntity> {
    override val name = "WithoutRowIdEntity"
    override val table = WithoutRowIdEntityTable.WITHOUT_ROW_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.ABSENT

    override fun newValue(sequence: Int) = WithoutRowIdEntity(
      id = "without-rowid",
      value = "without-rowid-value"
    )

    override fun insert(value: WithoutRowIdEntity) = value.insert()

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

    override fun expectedAfterInsert(value: NoIdUniqueEntity, result: EntityInsertResult.Inserted) = value

    override fun conflictingValue(existing: NoIdUniqueEntity, sequence: Int) = existing.copy(
      value = "no-id-unique-conflicting-value-$sequence"
    )

    override fun toString() = name
  }
}
