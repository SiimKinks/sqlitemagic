package com.siimkinks.sqlitemagic.runtime.contract.identity

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AccountTable.Companion.ACCOUNT
import com.siimkinks.sqlitemagic.NoIdUniqueAccountTable.Companion.NO_ID_UNIQUE_ACCOUNT
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.fixture.model.Account
import com.siimkinks.sqlitemagic.fixture.model.AccountId
import com.siimkinks.sqlitemagic.fixture.model.NoIdUniqueAccount
import com.siimkinks.sqlitemagic.NoIdUniqueAccounts
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.update
import org.junit.Test

class NoIdUniqueRelationshipRuntimeTest : RuntimeDatabaseTest() {
  @Test
  fun singleUpdateByAccountExecutesAndObservesOnlySelectedRows() {
    val seeded = seedRows(count = 3)
    val executeValue = seeded.owners[0].copy(value = "single-update-execute")
    val observeValue = seeded.owners[1].copy(value = "single-update-observe")

    assertThat(
      executeValue
        .update()
        .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
    ).isTrue()
    observeValue
      .update()
      .observe(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
      .test()
      .assertComplete()

    assertState(
      seeded = seeded,
      expectedOwners = listOf(
        executeValue,
        observeValue,
        seeded.owners[2]
      )
    )
  }

  @Test
  fun bulkUpdateByAccountExecutesAndObservesOnlySelectedGroups() {
    val seeded = seedRows(count = 5)
    val executeValues = seeded.owners
      .take(2)
      .mapIndexed { index, owner -> owner.copy(value = "bulk-update-execute-$index") }
    val observeValues = seeded.owners
      .subList(
        fromIndex = 2,
        toIndex = 4
      )
      .mapIndexed { index, owner -> owner.copy(value = "bulk-update-observe-$index") }

    assertThat(
      NoIdUniqueAccounts
        .update(executeValues)
        .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
    ).isTrue()
    NoIdUniqueAccounts
      .update(observeValues)
      .observe(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
      .test()
      .assertComplete()

    assertState(
      seeded = seeded,
      expectedOwners = executeValues + observeValues + seeded.owners[4]
    )
  }

  @Test
  fun singlePersistByAccountExecutesAndObservesOnlySelectedRows() {
    val seeded = seedRows(count = 3)
    val executeValue = seeded.owners[0].copy(value = "single-persist-execute")
    val observeValue = seeded.owners[1].copy(value = "single-persist-observe")

    assertThat(
      executeValue
        .persist()
        .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
    ).isEqualTo(EntityPersistResult.Updated)
    assertThat(
      observeValue
        .persist()
        .observe(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
        .blockingGet()
    ).isEqualTo(EntityPersistResult.Updated)

    assertState(
      seeded = seeded,
      expectedOwners = listOf(
        executeValue,
        observeValue,
        seeded.owners[2]
      )
    )
  }

  @Test
  fun bulkPersistByAccountExecutesAndObservesOnlySelectedGroups() {
    val seeded = seedRows(count = 5)
    val executeValues = seeded.owners
      .take(2)
      .mapIndexed { index, owner -> owner.copy(value = "bulk-persist-execute-$index") }
    val observeValues = seeded.owners
      .subList(
        fromIndex = 2,
        toIndex = 4
      )
      .mapIndexed { index, owner -> owner.copy(value = "bulk-persist-observe-$index") }

    assertThat(
      NoIdUniqueAccounts
        .persist(executeValues)
        .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
    ).isTrue()
    NoIdUniqueAccounts
      .persist(observeValues)
      .observe(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
      .test()
      .assertComplete()

    assertState(
      seeded = seeded,
      expectedOwners = executeValues + observeValues + seeded.owners[4]
    )
  }

  @Test
  fun singleDeleteByAccountExecutesAndObservesOnlySelectedRows() {
    val seeded = seedRows(count = 3)

    assertThat(
      seeded.owners[0]
        .delete()
        .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
    ).isEqualTo(1)
    assertThat(
      seeded.owners[1]
        .delete()
        .observe(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
        .blockingGet()
    ).isEqualTo(1)

    assertState(
      seeded = seeded,
      expectedOwners = listOf(seeded.owners[2])
    )
  }

  @Test
  fun bulkDeleteByAccountExecutesAndObservesOnlySelectedGroups() {
    val seeded = seedRows(count = 5)
    val executeValues = seeded.owners.take(2)
    val observeValues = seeded.owners.subList(
      fromIndex = 2,
      toIndex = 4
    )

    assertThat(
      NoIdUniqueAccounts
        .delete(o = executeValues)
        .execute(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
    ).isEqualTo(2)
    assertThat(
      NoIdUniqueAccounts
        .delete(o = observeValues)
        .observe(byColumn = NO_ID_UNIQUE_ACCOUNT.ACCOUNT)
        .blockingGet()
    ).isEqualTo(2)

    assertState(
      seeded = seeded,
      expectedOwners = listOf(seeded.owners[4])
    )
  }

  private fun seedRows(count: Int): SeededRows {
    val accounts = List(size = count) { index ->
      Account(
        id = AccountId("no-id-unique-account-$index"),
        label = "seeded-account-label-$index"
      )
    }
    accounts.forEach { account ->
      when (account.insert().execute()) {
        is EntityInsertResult.Inserted -> Unit
        EntityInsertResult.Ignored -> throw AssertionError("Account seed insert was ignored")
      }
    }

    val owners = accounts.mapIndexed { index, account ->
      NoIdUniqueAccount(
        account = account,
        value = "no-id-unique-owner-$index"
      )
    }
    owners.forEach { owner ->
      when (owner.insert().execute()) {
        is EntityInsertResult.Inserted -> Unit
        EntityInsertResult.Ignored -> throw AssertionError("Owner seed insert was ignored")
      }
    }
    return SeededRows(
      accounts = accounts,
      owners = owners
    )
  }

  private fun assertState(
    seeded: SeededRows,
    expectedOwners: List<NoIdUniqueAccount>
  ) {
    assertThat(
      Select
        .from(NO_ID_UNIQUE_ACCOUNT)
        .execute()
    ).containsExactlyElementsIn(expectedOwners.map(::shallow))
    assertThat(
      Select
        .from(ACCOUNT)
        .execute()
    ).containsExactlyElementsIn(seeded.accounts)
  }

  private fun shallow(owner: NoIdUniqueAccount) = owner.copy(
    account = Account(id = owner.account.id)
  )

  private data class SeededRows(
    val accounts: List<Account>,
    val owners: List<NoIdUniqueAccount>
  )
}
