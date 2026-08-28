package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.NoIdEntityTable.Companion.NO_ID_ENTITY
import com.siimkinks.sqlitemagic.NoIdEntitys
import com.siimkinks.sqlitemagic.NoIdUniqueEntityTable.Companion.NO_ID_UNIQUE_ENTITY
import com.siimkinks.sqlitemagic.NoIdUniqueEntitys
import com.siimkinks.sqlitemagic.StringIdEntityTable.Companion.STRING_ID_ENTITY
import com.siimkinks.sqlitemagic.StringIdEntitys
import com.siimkinks.sqlitemagic.WithoutRowIdEntityTable.Companion.WITHOUT_ROW_ID_ENTITY
import com.siimkinks.sqlitemagic.WithoutRowIdEntitys
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.NoIdEntity
import com.siimkinks.sqlitemagic.fixture.model.NoIdUniqueEntity
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.fixture.model.WithoutRowIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.BulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.withConflictAlgorithm
import com.siimkinks.sqlitemagic.update

internal object IdentityModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    StringIdEntityCase,
    NoIdEntityCase,
    NoIdUniqueEntityCase,
    WithoutRowIdEntityCase,
  )

  internal val emptyBulkUpdateCase: BulkPersistModelCase<NoIdUniqueEntity> = NoIdUniqueEntityCase

  private object StringIdEntityCase :
    BulkPersistModelCase<StringIdEntity> {
    override val name = "StringIdEntity"
    override val table = STRING_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = StringIdEntity(
      id = "string-id-$sequence",
      value = "string-value-$sequence"
    )

    override fun insert(value: StringIdEntity) = value.insert()

    override fun bulkInsert(values: List<StringIdEntity>) = StringIdEntitys.insert(values)

    override fun expectedAfterInsert(value: StringIdEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: StringIdEntity, sequence: Int) = value.copy(
      id = value.id,
      value = "string-value-updated-$sequence"
    )

    override fun executeUpdate(
      value: StringIdEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: StringIdEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(
      values: Iterable<StringIdEntity>,
      conflictAlgorithm: Int?
    ) = StringIdEntitys
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeBulkUpdate(
      values: Iterable<StringIdEntity>,
      conflictAlgorithm: Int?
    ) = StringIdEntitys
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkPersist(
      values: Iterable<StringIdEntity>,
      conflictAlgorithm: Int?
    ) = StringIdEntitys
      .persist(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeBulkPersist(
      values: Iterable<StringIdEntity>,
      conflictAlgorithm: Int?
    ) = StringIdEntitys
      .persist(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executePersist(value: StringIdEntity) = value
      .persist()
      .execute()

    override fun observePersist(value: StringIdEntity) = value
      .persist()
      .observe()

    override fun toString() = name
  }

  private object NoIdEntityCase : BulkInsertModelCase<NoIdEntity> {
    override val name = "NoIdEntity"
    override val table = NO_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdEntity(value = "no-id-$sequence")

    override fun insert(value: NoIdEntity) = value.insert()

    override fun bulkInsert(values: List<NoIdEntity>) = NoIdEntitys.insert(values)

    override fun expectedAfterInsert(value: NoIdEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object WithoutRowIdEntityCase :
    BulkPersistModelCase<WithoutRowIdEntity> {
    override val name = "WithoutRowIdEntity"
    override val table = WITHOUT_ROW_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.ABSENT

    override fun newValue(sequence: Int) = WithoutRowIdEntity(
      id = "without-rowid-$sequence",
      value = "without-rowid-value-$sequence"
    )

    override fun insert(value: WithoutRowIdEntity) = value.insert()

    override fun bulkInsert(values: List<WithoutRowIdEntity>) = WithoutRowIdEntitys.insert(values)

    override fun expectedAfterInsert(value: WithoutRowIdEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: WithoutRowIdEntity, sequence: Int) = value.copy(
      id = value.id,
      value = "without-rowid-value-updated-$sequence"
    )

    override fun executeUpdate(
      value: WithoutRowIdEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeUpdate(
      value: WithoutRowIdEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkUpdate(
      values: Iterable<WithoutRowIdEntity>,
      conflictAlgorithm: Int?
    ) = WithoutRowIdEntitys
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeBulkUpdate(
      values: Iterable<WithoutRowIdEntity>,
      conflictAlgorithm: Int?
    ) = WithoutRowIdEntitys
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executeBulkPersist(
      values: Iterable<WithoutRowIdEntity>,
      conflictAlgorithm: Int?
    ) = WithoutRowIdEntitys
      .persist(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute()

    override fun observeBulkPersist(
      values: Iterable<WithoutRowIdEntity>,
      conflictAlgorithm: Int?
    ) = WithoutRowIdEntitys
      .persist(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe()

    override fun executePersist(value: WithoutRowIdEntity) = value
      .persist()
      .execute()

    override fun observePersist(value: WithoutRowIdEntity) = value
      .persist()
      .observe()

    override fun toString() = name
  }

  private object NoIdUniqueEntityCase :
    UniqueInsertModelCase<NoIdUniqueEntity>,
    BulkPersistModelCase<NoIdUniqueEntity> {
    override val name = "NoIdUniqueEntity"
    override val table = NO_ID_UNIQUE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdUniqueEntity(
      uniqueValue = "no-id-unique-$sequence",
      value = "no-id-unique-value-$sequence"
    )

    override fun insert(value: NoIdUniqueEntity) = value.insert()

    override fun bulkInsert(values: List<NoIdUniqueEntity>) = NoIdUniqueEntitys.insert(values)

    override fun expectedAfterInsert(value: NoIdUniqueEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: NoIdUniqueEntity, sequence: Int) = value.copy(
      uniqueValue = value.uniqueValue,
      value = "no-id-unique-value-updated-$sequence"
    )

    override fun executeUpdate(
      value: NoIdUniqueEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun observeUpdate(
      value: NoIdUniqueEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .observe(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun executeBulkUpdate(
      values: Iterable<NoIdUniqueEntity>,
      conflictAlgorithm: Int?
    ) = NoIdUniqueEntitys
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun observeBulkUpdate(
      values: Iterable<NoIdUniqueEntity>,
      conflictAlgorithm: Int?
    ) = NoIdUniqueEntitys
      .update(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun executeBulkPersist(
      values: Iterable<NoIdUniqueEntity>,
      conflictAlgorithm: Int?
    ) = NoIdUniqueEntitys
      .persist(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun observeBulkPersist(
      values: Iterable<NoIdUniqueEntity>,
      conflictAlgorithm: Int?
    ) = NoIdUniqueEntitys
      .persist(values)
      .withConflictAlgorithm(conflictAlgorithm)
      .observe(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun executePersist(value: NoIdUniqueEntity) = value
      .persist()
      .execute(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun observePersist(value: NoIdUniqueEntity) = value
      .persist()
      .observe(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun conflictingValue(existing: NoIdUniqueEntity, sequence: Int) = existing.copy(
      value = "no-id-unique-conflicting-value-$sequence"
    )

    override fun toString() = name
  }
}
