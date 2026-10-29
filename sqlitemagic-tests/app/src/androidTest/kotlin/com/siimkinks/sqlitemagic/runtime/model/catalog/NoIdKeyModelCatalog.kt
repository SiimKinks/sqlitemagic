package com.siimkinks.sqlitemagic.runtime.model.catalog

import com.siimkinks.sqlitemagic.NoIdMultiUniqueEntityTable.Companion.NO_ID_MULTI_UNIQUE_ENTITY
import com.siimkinks.sqlitemagic.NoIdMultiUniqueEntitys
import com.siimkinks.sqlitemagic.NoIdUniqueAccountTable.Companion.NO_ID_UNIQUE_ACCOUNT
import com.siimkinks.sqlitemagic.NoIdUniqueAccounts
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.fixture.model.Account
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import com.siimkinks.sqlitemagic.fixture.model.NoIdMultiUniqueEntity
import com.siimkinks.sqlitemagic.fixture.model.NoIdUniqueAccount
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.model.BulkDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.model.BulkPersistModelCase
import com.siimkinks.sqlitemagic.runtime.model.InsertRowIdExpectation
import com.siimkinks.sqlitemagic.runtime.model.RuntimeModelCase
import com.siimkinks.sqlitemagic.runtime.model.StandardTableDeleteModelCase
import com.siimkinks.sqlitemagic.runtime.support.ReactiveAdapter
import com.siimkinks.sqlitemagic.runtime.support.withConflictAlgorithm
import com.siimkinks.sqlitemagic.update

internal object NoIdKeyModelCatalog {
  val cases: List<RuntimeModelCase<*>> = listOf(
    NoIdMultiUniqueEntityBySlug,
    NoIdMultiUniqueEntityByExternalKey,
    NoIdUniqueAccountByAccount,
  )

  private object NoIdMultiUniqueEntityBySlug :
    BulkPersistModelCase<NoIdMultiUniqueEntity>,
    BulkDeleteModelCase<NoIdMultiUniqueEntity>,
    StandardTableDeleteModelCase<NoIdMultiUniqueEntity> {
    override val name = "NoIdMultiUniqueEntityBySlug"
    override val table = NO_ID_MULTI_UNIQUE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdMultiUniqueEntity(
      slug = "no-id-multi-unique-slug-$sequence",
      externalKey = "no-id-multi-unique-external-key-$sequence",
      value = "no-id-multi-unique-value-$sequence"
    )

    override fun insert(value: NoIdMultiUniqueEntity) = value.insert()

    override fun bulkInsert(values: List<NoIdMultiUniqueEntity>) = NoIdMultiUniqueEntitys
      .insert(o = values)

    override fun expectedAfterInsert(
      value: NoIdMultiUniqueEntity,
      result: EntityInsertResult.Inserted
    ) = value

    override fun updatedValue(value: NoIdMultiUniqueEntity, sequence: Int) = value.copy(
      value = "no-id-multi-unique-updated-value-$sequence"
    )

    override fun executeUpdate(
      value: NoIdMultiUniqueEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)

    override fun observeUpdate(
      adapter: ReactiveAdapter,
      value: NoIdMultiUniqueEntity,
      conflictAlgorithm: Int?
    ) = adapter.observe(
      builder = value
        .update()
        .withConflictAlgorithm(conflictAlgorithm),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG
    )

    override fun executeBulkUpdate(
      values: Iterable<NoIdMultiUniqueEntity>,
      conflictAlgorithm: Int?
    ) = NoIdMultiUniqueEntitys
      .update(o = values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)

    override fun observeBulkUpdate(
      adapter: ReactiveAdapter,
      values: Iterable<NoIdMultiUniqueEntity>,
      conflictAlgorithm: Int?
    ) = adapter.observe(
      builder = NoIdMultiUniqueEntitys
        .update(o = values)
        .withConflictAlgorithm(conflictAlgorithm),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG
    )

    override fun executePersist(value: NoIdMultiUniqueEntity) = value
      .persist()
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)

    override fun observePersist(
      adapter: ReactiveAdapter,
      value: NoIdMultiUniqueEntity
    ) = adapter.observe(
      builder = value.persist(),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG
    )

    override fun executeBulkPersist(
      values: Iterable<NoIdMultiUniqueEntity>,
      conflictAlgorithm: Int?
    ) = NoIdMultiUniqueEntitys
      .persist(o = values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)

    override fun observeBulkPersist(
      adapter: ReactiveAdapter,
      values: Iterable<NoIdMultiUniqueEntity>,
      conflictAlgorithm: Int?
    ) = adapter.observe(
      builder = NoIdMultiUniqueEntitys
        .persist(o = values)
        .withConflictAlgorithm(conflictAlgorithm),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG
    )

    override fun executeDelete(value: NoIdMultiUniqueEntity) = value
      .delete()
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)

    override fun observeDelete(
      adapter: ReactiveAdapter,
      value: NoIdMultiUniqueEntity
    ) = adapter.observe(
      builder = value.delete(),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG
    )

    override fun executeBulkDelete(values: Collection<NoIdMultiUniqueEntity>) = NoIdMultiUniqueEntitys
      .delete(o = values)
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG)

    override fun observeBulkDelete(
      adapter: ReactiveAdapter,
      values: Collection<NoIdMultiUniqueEntity>
    ) = adapter.observe(
      builder = NoIdMultiUniqueEntitys.delete(o = values),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.SLUG
    )

    override fun deleteTable() = NoIdMultiUniqueEntitys.deleteTable()

    override fun toString() = name
  }

  private object NoIdMultiUniqueEntityByExternalKey :
    BulkPersistModelCase<NoIdMultiUniqueEntity>,
    BulkDeleteModelCase<NoIdMultiUniqueEntity>,
    StandardTableDeleteModelCase<NoIdMultiUniqueEntity> {
    override val name = "NoIdMultiUniqueEntityByExternalKey"
    override val table = NO_ID_MULTI_UNIQUE_ENTITY
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdMultiUniqueEntity(
      slug = "no-id-multi-unique-slug-$sequence",
      externalKey = "no-id-multi-unique-external-key-$sequence",
      value = "no-id-multi-unique-value-$sequence"
    )

    override fun insert(value: NoIdMultiUniqueEntity) = value.insert()

    override fun bulkInsert(values: List<NoIdMultiUniqueEntity>) = NoIdMultiUniqueEntitys
      .insert(o = values)

    override fun expectedAfterInsert(
      value: NoIdMultiUniqueEntity,
      result: EntityInsertResult.Inserted
    ) = value

    override fun updatedValue(value: NoIdMultiUniqueEntity, sequence: Int) = value.copy(
      value = "no-id-multi-unique-updated-value-$sequence"
    )

    override fun executeUpdate(
      value: NoIdMultiUniqueEntity,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)

    override fun observeUpdate(
      adapter: ReactiveAdapter,
      value: NoIdMultiUniqueEntity,
      conflictAlgorithm: Int?
    ) = adapter.observe(
      builder = value
        .update()
        .withConflictAlgorithm(conflictAlgorithm),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY
    )

    override fun executeBulkUpdate(
      values: Iterable<NoIdMultiUniqueEntity>,
      conflictAlgorithm: Int?
    ) = NoIdMultiUniqueEntitys
      .update(o = values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)

    override fun observeBulkUpdate(
      adapter: ReactiveAdapter,
      values: Iterable<NoIdMultiUniqueEntity>,
      conflictAlgorithm: Int?
    ) = adapter.observe(
      builder = NoIdMultiUniqueEntitys
        .update(o = values)
        .withConflictAlgorithm(conflictAlgorithm),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY
    )

    override fun executePersist(value: NoIdMultiUniqueEntity) = value
      .persist()
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)

    override fun observePersist(
      adapter: ReactiveAdapter,
      value: NoIdMultiUniqueEntity
    ) = adapter.observe(
      builder = value.persist(),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY
    )

    override fun executeBulkPersist(
      values: Iterable<NoIdMultiUniqueEntity>,
      conflictAlgorithm: Int?
    ) = NoIdMultiUniqueEntitys
      .persist(o = values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)

    override fun observeBulkPersist(
      adapter: ReactiveAdapter,
      values: Iterable<NoIdMultiUniqueEntity>,
      conflictAlgorithm: Int?
    ) = adapter.observe(
      builder = NoIdMultiUniqueEntitys
        .persist(o = values)
        .withConflictAlgorithm(conflictAlgorithm),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY
    )

    override fun executeDelete(value: NoIdMultiUniqueEntity) = value
      .delete()
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)

    override fun observeDelete(
      adapter: ReactiveAdapter,
      value: NoIdMultiUniqueEntity
    ) = adapter.observe(
      builder = value.delete(),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY
    )

    override fun executeBulkDelete(values: Collection<NoIdMultiUniqueEntity>) = NoIdMultiUniqueEntitys
      .delete(o = values)
      .execute(byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY)

    override fun observeBulkDelete(
      adapter: ReactiveAdapter,
      values: Collection<NoIdMultiUniqueEntity>
    ) = adapter.observe(
      builder = NoIdMultiUniqueEntitys.delete(o = values),
      byColumn = NO_ID_MULTI_UNIQUE_ENTITY.EXTERNAL_KEY
    )

    override fun deleteTable() = NoIdMultiUniqueEntitys.deleteTable()

    override fun toString() = name
  }

  private object NoIdUniqueAccountByAccount :
    BulkPersistModelCase<NoIdUniqueAccount>,
    BulkDeleteModelCase<NoIdUniqueAccount>,
    StandardTableDeleteModelCase<NoIdUniqueAccount> {
    override val name = "NoIdUniqueAccountByAccount"
    override val table = NO_ID_UNIQUE_ACCOUNT
    override val rowIdExpectation = InsertRowIdExpectation.PRESENT

    override fun newValue(sequence: Int) = NoIdUniqueAccount(
      account = Account(id = AccountId("no-id-unique-account-$sequence")),
      value = "no-id-unique-value-$sequence"
    )

    override fun insert(value: NoIdUniqueAccount) = value.insert()

    override fun bulkInsert(values: List<NoIdUniqueAccount>) = NoIdUniqueAccounts
      .insert(o = values)

    override fun expectedAfterInsert(
      value: NoIdUniqueAccount,
      result: EntityInsertResult.Inserted
    ) = value

    override fun updatedValue(value: NoIdUniqueAccount, sequence: Int) = value.copy(
      value = "no-id-unique-updated-value-$sequence"
    )

    override fun executeUpdate(
      value: NoIdUniqueAccount,
      conflictAlgorithm: Int?
    ) = value
      .update()
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)

    override fun observeUpdate(
      adapter: ReactiveAdapter,
      value: NoIdUniqueAccount,
      conflictAlgorithm: Int?
    ) = adapter.observe(
      builder = value
        .update()
        .withConflictAlgorithm(conflictAlgorithm),
      byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT
    )

    override fun executeBulkUpdate(
      values: Iterable<NoIdUniqueAccount>,
      conflictAlgorithm: Int?
    ) = NoIdUniqueAccounts
      .update(o = values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)

    override fun observeBulkUpdate(
      adapter: ReactiveAdapter,
      values: Iterable<NoIdUniqueAccount>,
      conflictAlgorithm: Int?
    ) = adapter.observe(
      builder = NoIdUniqueAccounts
        .update(o = values)
        .withConflictAlgorithm(conflictAlgorithm),
      byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT
    )

    override fun executePersist(value: NoIdUniqueAccount) = value
      .persist()
      .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)

    override fun observePersist(
      adapter: ReactiveAdapter,
      value: NoIdUniqueAccount
    ) = adapter.observe(
      builder = value.persist(),
      byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT
    )

    override fun executeBulkPersist(
      values: Iterable<NoIdUniqueAccount>,
      conflictAlgorithm: Int?
    ) = NoIdUniqueAccounts
      .persist(o = values)
      .withConflictAlgorithm(conflictAlgorithm)
      .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)

    override fun observeBulkPersist(
      adapter: ReactiveAdapter,
      values: Iterable<NoIdUniqueAccount>,
      conflictAlgorithm: Int?
    ) = adapter.observe(
      builder = NoIdUniqueAccounts
        .persist(o = values)
        .withConflictAlgorithm(conflictAlgorithm),
      byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT
    )

    override fun executeDelete(value: NoIdUniqueAccount) = value
      .delete()
      .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)

    override fun observeDelete(
      adapter: ReactiveAdapter,
      value: NoIdUniqueAccount
    ) = adapter.observe(
      builder = value.delete(),
      byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT
    )

    override fun executeBulkDelete(values: Collection<NoIdUniqueAccount>) = NoIdUniqueAccounts
      .delete(o = values)
      .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)

    override fun observeBulkDelete(
      adapter: ReactiveAdapter,
      values: Collection<NoIdUniqueAccount>
    ) = adapter.observe(
      builder = NoIdUniqueAccounts.delete(o = values),
      byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT
    )

    override fun deleteTable() = NoIdUniqueAccounts.deleteTable()

    override fun toString() = name
  }
}
