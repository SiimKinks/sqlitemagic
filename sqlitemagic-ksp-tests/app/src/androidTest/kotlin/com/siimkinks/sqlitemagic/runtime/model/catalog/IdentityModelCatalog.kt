package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.AccountTable.Companion.ACCOUNT
import com.siimkinks.sqlitemagic.Accounts
import com.siimkinks.sqlitemagic.AutomaticTransformedTable.Companion.AUTOMATIC_TRANSFORMED
import com.siimkinks.sqlitemagic.AutomaticTransformeds
import com.siimkinks.sqlitemagic.NoIdEntityTable.Companion.NO_ID_ENTITY
import com.siimkinks.sqlitemagic.NoIdEntitys
import com.siimkinks.sqlitemagic.NoIdUniqueEntityTable.Companion.NO_ID_UNIQUE_ENTITY
import com.siimkinks.sqlitemagic.NoIdUniqueEntitys
import com.siimkinks.sqlitemagic.StringIdEntityTable.Companion.STRING_ID_ENTITY
import com.siimkinks.sqlitemagic.StringIdEntitys
import com.siimkinks.sqlitemagic.WithoutRowIdEntityTable.Companion.WITHOUT_ROW_ID_ENTITY
import com.siimkinks.sqlitemagic.WithoutRowIdEntitys
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.Account
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import com.siimkinks.sqlitemagic.fixture.model.AutomaticTransformed
import com.siimkinks.sqlitemagic.fixture.model.NoIdEntity
import com.siimkinks.sqlitemagic.fixture.model.NoIdUniqueEntity
import com.siimkinks.sqlitemagic.fixture.model.SequenceId
import com.siimkinks.sqlitemagic.fixture.model.StringIdEntity
import com.siimkinks.sqlitemagic.fixture.model.WithoutRowIdEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.BulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.BulkInsertModelCase
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardBulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardDeleteBuilders
import com.siimkinks.sqlitemagic.runtime.model.StandardOperationBuilders
import com.siimkinks.sqlitemagic.runtime.model.StandardTableDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.UniqueInsertModelCase
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import com.siimkinks.sqlitemagic.update

internal object IdentityModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    AutomaticTransformedCase,
    AccountCase,
    StringIdEntityCase,
    NoIdEntityCase,
    NoIdUniqueEntityCase,
    WithoutRowIdEntityCase,
  )

  internal val representativeEmptyBulkCase: BulkPersistModelCase<NoIdUniqueEntity> = NoIdUniqueEntityCase

  private object AutomaticTransformedCase :
    StandardBulkPersistModelCase<AutomaticTransformed>,
    StandardBulkDeleteModelCase<AutomaticTransformed> {
    override val name = "AutomaticTransformed"
    override val table = AUTOMATIC_TRANSFORMED
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = AutomaticTransformed(
      id = SequenceId(0),
      value = "automatic-transformed-value-$sequence"
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = AutomaticTransformed::insert,
      bulkInsert = AutomaticTransformeds::insert,
      update = AutomaticTransformed::update,
      bulkUpdate = AutomaticTransformeds::update,
      persist = AutomaticTransformed::persist,
      bulkPersist = AutomaticTransformeds::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = AutomaticTransformed::delete,
      bulkDelete = AutomaticTransformeds::delete,
      deleteTable = AutomaticTransformeds::deleteTable
    )

    override fun expectedAfterInsert(
      value: AutomaticTransformed,
      result: EntityInsertResult.Inserted
    ) = value.copy(id = SequenceId(checkNotNull(result.rowId)))

    override fun expectedAfterBulkInsert(
      values: List<AutomaticTransformed>,
      actual: List<AutomaticTransformed>
    ) = actual.map { persisted ->
      values
        .single { it.value == persisted.value }
        .copy(id = persisted.id)
    }

    override fun updatedValue(value: AutomaticTransformed, sequence: Int) = value.copy(
      id = value.id,
      value = "automatic-transformed-updated-value-$sequence"
    )

    override fun toString() = name
  }

  private object AccountCase :
    StandardBulkPersistModelCase<Account>,
    StandardBulkDeleteModelCase<Account> {
    override val name = "Account"
    override val table = ACCOUNT
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = Account(
      id = AccountId("account-id-$sequence"),
      label = "account-label-$sequence"
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = Account::insert,
      bulkInsert = Accounts::insert,
      update = Account::update,
      bulkUpdate = Accounts::update,
      persist = Account::persist,
      bulkPersist = Accounts::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = Account::delete,
      bulkDelete = Accounts::delete,
      deleteTable = Accounts::deleteTable
    )

    override fun expectedAfterInsert(value: Account, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: Account, sequence: Int) = value.copy(
      id = value.id,
      label = "account-updated-label-$sequence"
    )

    override fun toString() = name
  }

  private object StringIdEntityCase :
    StandardBulkPersistModelCase<StringIdEntity>,
    StandardBulkDeleteModelCase<StringIdEntity> {
    override val name = "StringIdEntity"
    override val table = STRING_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = StringIdEntity(
      id = "string-id-$sequence",
      value = "string-value-$sequence"
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = StringIdEntity::insert,
      bulkInsert = StringIdEntitys::insert,
      update = StringIdEntity::update,
      bulkUpdate = StringIdEntitys::update,
      persist = StringIdEntity::persist,
      bulkPersist = StringIdEntitys::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = StringIdEntity::delete,
      bulkDelete = StringIdEntitys::delete,
      deleteTable = StringIdEntitys::deleteTable
    )

    override fun expectedAfterInsert(value: StringIdEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: StringIdEntity, sequence: Int) = value.copy(
      id = value.id,
      value = "string-value-updated-$sequence"
    )

    override fun toString() = name
  }

  private object NoIdEntityCase :
    BulkInsertModelCase<NoIdEntity>,
    StandardTableDeleteModelCase<NoIdEntity> {
    override val name = "NoIdEntity"
    override val table = NO_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdEntity(value = "no-id-$sequence")

    override fun insert(value: NoIdEntity) = value.insert()

    override fun bulkInsert(values: List<NoIdEntity>) = NoIdEntitys.insert(values)

    override fun deleteTable() = NoIdEntitys.deleteTable()

    override fun expectedAfterInsert(value: NoIdEntity, result: EntityInsertResult.Inserted) = value

    override fun toString() = name
  }

  private object WithoutRowIdEntityCase :
    StandardBulkPersistModelCase<WithoutRowIdEntity>,
    StandardBulkDeleteModelCase<WithoutRowIdEntity> {
    override val name = "WithoutRowIdEntity"
    override val table = WITHOUT_ROW_ID_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.ABSENT

    override fun newValue(sequence: Int) = WithoutRowIdEntity(
      id = "without-rowid-$sequence",
      value = "without-rowid-value-$sequence"
    )

    override val operationBuilders = StandardOperationBuilders(
      insert = WithoutRowIdEntity::insert,
      bulkInsert = WithoutRowIdEntitys::insert,
      update = WithoutRowIdEntity::update,
      bulkUpdate = WithoutRowIdEntitys::update,
      persist = WithoutRowIdEntity::persist,
      bulkPersist = WithoutRowIdEntitys::persist
    )

    override val deleteBuilders = StandardDeleteBuilders(
      delete = WithoutRowIdEntity::delete,
      bulkDelete = WithoutRowIdEntitys::delete,
      deleteTable = WithoutRowIdEntitys::deleteTable
    )

    override fun expectedAfterInsert(value: WithoutRowIdEntity, result: EntityInsertResult.Inserted) = value

    override fun updatedValue(value: WithoutRowIdEntity, sequence: Int) = value.copy(
      id = value.id,
      value = "without-rowid-value-updated-$sequence"
    )

    override fun toString() = name
  }

  private object NoIdUniqueEntityCase :
    UniqueInsertModelCase<NoIdUniqueEntity>,
    BulkPersistModelCase<NoIdUniqueEntity>,
    BulkDeleteModelCase<NoIdUniqueEntity>,
    StandardTableDeleteModelCase<NoIdUniqueEntity> {
    override val name = "NoIdUniqueEntity"
    override val table = NO_ID_UNIQUE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdUniqueEntity(
      uniqueValue = "no-id-unique-$sequence",
      value = "no-id-unique-value-$sequence"
    )

    override fun insert(value: NoIdUniqueEntity) = value.insert()

    override fun bulkInsert(values: List<NoIdUniqueEntity>) = NoIdUniqueEntitys.insert(values)

    override fun executeDelete(value: NoIdUniqueEntity) = value
      .delete()
      .execute(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun observeDelete(value: NoIdUniqueEntity) = value
      .delete()
      .observe(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun executeBulkDelete(values: Collection<NoIdUniqueEntity>) = NoIdUniqueEntitys
      .delete(o = values)
      .execute(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun observeBulkDelete(values: Collection<NoIdUniqueEntity>) = NoIdUniqueEntitys
      .delete(o = values)
      .observe(byColumn = NO_ID_UNIQUE_ENTITY.UNIQUE_VALUE)

    override fun deleteTable() = NoIdUniqueEntitys.deleteTable()

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
