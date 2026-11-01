package com.siimkinks.sqlitemagic.runtime.contract.transaction

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.insertedResult
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.lang.Thread.State.TIMED_WAITING
import java.lang.Thread.State.WAITING
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicReference

internal class ConcurrentTransactionRuntimeTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  private val workers = Executors.newFixedThreadPool(2)

  @After
  fun stopWorkers() {
    workers.shutdownNow()
  }

  @Test
  fun typedReadOnAnotherThreadDuringATransactionSeesTheLastCommittedRows() {
    val committed = insert(value = "committed")
    val uncommitted = SimpleMutableEntity(value = "uncommitted")
    val transaction = SqliteMagic.newTransaction()
    val duringTransaction = try {
      insert(entity = uncommitted)
      val otherThread = workers
        .submit(::readEntities)
        .get(5, SECONDS)
      val transactionThread = readEntities()
      transaction.markSuccessful()
      listOf(otherThread, transactionThread)
    } finally {
      transaction.end()
    }
    val afterCommit = workers
      .submit(::readEntities)
      .get(5, SECONDS)

    assertThat(duringTransaction + listOf(afterCommit)).isEqualTo(
      listOf(
        listOf(committed),
        listOf(committed, uncommitted),
        listOf(committed, uncommitted)
      )
    )
  }

  @Test
  fun insertOfTheSameEntityTypeOnAnotherThreadCompletesAfterTheTransaction() {
    insert(value = "before transaction")
    val concurrentThread = AtomicReference<Thread>()

    val transactional = workers.submit<Future<*>> {
      val transaction = SqliteMagic.newTransaction()
      try {
        insert(value = "first in transaction")
        val concurrent = workers.submit {
          concurrentThread.set(Thread.currentThread())
          insert(value = "concurrent")
        }
        while (!concurrent.isDone && concurrentThread.get()?.state !in BLOCKED_STATES) {
          Thread.yield()
        }
        insert(value = "second in transaction")
        transaction.markSuccessful()
        concurrent
      } finally {
        transaction.end()
      }
    }
    transactional
      .get(10, SECONDS)
      .get(10, SECONDS)

    assertThat(
      SELECT.COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
        .execute()
    ).isEqualTo(listOf("before transaction", "first in transaction", "second in transaction", "concurrent"))
  }

  private fun insert(value: String) = insert(entity = SimpleMutableEntity(value = value))

  private fun insert(entity: SimpleMutableEntity) = entity.also {
    insertedResult(
      result = it
        .insert()
        .execute(),
      modelName = "SimpleMutableEntity"
    )
  }

  private fun readEntities() = SELECT.FROM(SIMPLE_MUTABLE_ENTITY)
    .ORDER_BY(SIMPLE_MUTABLE_ENTITY.ID.ASC())
    .execute()
}

private val BLOCKED_STATES = setOf(WAITING, TIMED_WAITING)
