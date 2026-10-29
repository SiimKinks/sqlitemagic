package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DELETE
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.MainSessionValueTable.Companion.MAIN_SESSION_VALUE
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SubmodulePersistentValueTable.Companion.SUBMODULE_PERSISTENT_VALUE
import com.siimkinks.sqlitemagic.SubmoduleSessionValueTable.Companion.SUBMODULE_SESSION_VALUE
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.fixture.model.MainSessionValue
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmodulePersistentValue
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionValue
import com.siimkinks.sqlitemagic.runtime.support.ClearDatabaseRule
import com.siimkinks.sqlitemagic.runtime.support.SQLITE_TEMP_MASTER
import com.siimkinks.sqlitemagic.runtime.support.assertRowsIgnoringOrder
import com.siimkinks.sqlitemagic.runtime.support.captureRows
import com.siimkinks.sqlitemagic.runtime.support.openNamedConnection
import com.siimkinks.sqlitemagic.runtime.support.readableDatabase
import com.siimkinks.sqlitemagic.runtime.support.schemaObjectNames
import com.siimkinks.sqlitemagic.runtime.support.withNamedDatabase
import com.siimkinks.sqlitemagic.rxjava2.observe
import com.siimkinks.sqlitemagic.update
import org.junit.Rule
import org.junit.Test

private const val ALTERNATE_DATABASE_NAME = "cov03.db"

internal class AlternateConnectionManagerIntegrationTest {
  @get:Rule
  val clearDatabase = ClearDatabaseRule()

  @Test
  fun generatedOperationsRouteMainAndSubmoduleManagers() = withAlternateConnection { connection ->
    val mainInserted = newMainValue(value = "main-inserted")
    mainInserted
      .insert()
      .usingConnection(connection)
      .execute()
    assertRowsIgnoringOrder(
      table = SIMPLE_MUTABLE_ENTITY,
      connection = connection,
      expected = listOf(mainInserted)
    )

    val mainUpdated = mainInserted.copy(value = "main-updated")
    mainUpdated
      .update()
      .usingConnection(connection)
      .execute()
    val mainPersisted = mainUpdated.copy(value = "main-persisted")
    mainPersisted
      .persist()
      .usingConnection(connection)
      .execute()
    assertRowsIgnoringOrder(
      table = SIMPLE_MUTABLE_ENTITY,
      connection = connection,
      expected = listOf(mainPersisted)
    )
    mainPersisted
      .delete()
      .usingConnection(connection)
      .execute()

    val submoduleInserted = SubmodulePersistentValue(
      id = "submodule-inserted",
      value = "submodule-inserted"
    )
    submoduleInserted
      .insert()
      .usingConnection(connection)
      .execute()
    val submoduleUpdated = submoduleInserted.copy(value = "submodule-updated")
    submoduleUpdated
      .update()
      .usingConnection(connection)
      .execute()
    val submodulePersisted = submoduleUpdated.copy(value = "submodule-persisted")
    submodulePersisted
      .persist()
      .usingConnection(connection)
      .execute()
    assertRowsIgnoringOrder(
      table = SUBMODULE_PERSISTENT_VALUE,
      connection = connection,
      expected = listOf(submodulePersisted)
    )
    submodulePersisted
      .delete()
      .usingConnection(connection)
      .execute()

    assertRowsIgnoringOrder(
      table = SIMPLE_MUTABLE_ENTITY,
      connection = connection,
      expected = emptyList()
    )
    assertRowsIgnoringOrder(
      table = SUBMODULE_PERSISTENT_VALUE,
      connection = connection,
      expected = emptyList()
    )
    assertDefaultPersistentTablesEmpty()
  }

  @Test
  fun querySurfacesAndBuildersUseAlternateConnection() = withAlternateConnection { connection ->
    val inserted = newMainValue(value = "query-inserted")
    inserted
      .insert()
      .usingConnection(connection)
      .execute()
    val insertedId = checkNotNull(inserted.id)

    assertThat(
      captureRows(
        table = SIMPLE_MUTABLE_ENTITY,
        connection = connection
      )
    ).containsExactly(inserted)
    assertThat(
      SELECT
        .COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .usingConnection(connection)
        .execute()
    ).containsExactly("query-inserted")
    assertThat(rawValues(connection = connection))
      .containsExactly("query-inserted")

    assertThat(
      UPDATE
        .TABLE(SIMPLE_MUTABLE_ENTITY)
        .SET(SIMPLE_MUTABLE_ENTITY.VALUE, "builder-updated")
        .WHERE(SIMPLE_MUTABLE_ENTITY.ID IS insertedId)
        .usingConnection(connection)
        .execute()
    ).isEqualTo(1)
    assertThat(
      SELECT
        .COLUMN(SIMPLE_MUTABLE_ENTITY.VALUE)
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .usingConnection(connection)
        .execute()
    ).containsExactly("builder-updated")

    assertThat(
      DELETE
        .FROM(SIMPLE_MUTABLE_ENTITY)
        .WHERE(SIMPLE_MUTABLE_ENTITY.ID IS insertedId)
        .usingConnection(connection)
        .execute()
    ).isEqualTo(1)
    assertRowsIgnoringOrder(
      table = SIMPLE_MUTABLE_ENTITY,
      connection = connection,
      expected = emptyList()
    )
    assertDefaultPersistentTablesEmpty()
  }

  @Test
  fun observersAreIsolatedAndAlternateCloseCompletesOnlyItsObservers() = withAlternateConnection { connection ->
    val defaultObserver = observeMainCount(connection = SqliteMagic.getDefaultConnection())
      .test()
      .assertValuesOnly(0L)
    val alternateObserver = observeMainCount(connection = connection)
      .test()
      .assertValuesOnly(0L)
    var connectionClosed = false
    try {
      newMainValue(value = "alternate-observed")
        .insert()
        .usingConnection(connection)
        .execute()
      defaultObserver.assertValuesOnly(0L)
      alternateObserver.assertValuesOnly(0L, 1L)

      newMainValue(value = "default-observed")
        .insert()
        .execute()
      defaultObserver.assertValuesOnly(0L, 1L)
      alternateObserver.assertValuesOnly(0L, 1L)

      connection.close()
      connectionClosed = true
      alternateObserver.assertComplete()
      defaultObserver.assertNotComplete()

      newMainValue(value = "default-after-close")
        .insert()
        .execute()
      defaultObserver.assertValuesOnly(0L, 1L, 2L)
      alternateObserver.assertResult(0L, 1L)
    } finally {
      defaultObserver.dispose()
      alternateObserver.dispose()
      if (!connectionClosed) {
        connection.close()
      }
    }
  }

  @Test
  fun reopenRetainsPersistentRowsAndRecreatesIsolatedTemporaryTables() = withNamedDatabase(
    databaseName = ALTERNATE_DATABASE_NAME
  ) { application ->
    var connection = openAlternateConnection(application = application)
    try {
      val mainPersistent = newMainValue(value = "main-persistent")
      val mainTemporary = MainSessionValue(
        id = "main-temporary",
        value = "main-temporary"
      )
      val submodulePersistent = SubmodulePersistentValue(
        id = "submodule-persistent",
        value = "submodule-persistent"
      )
      val submoduleTemporary = SubmoduleSessionValue(
        id = "submodule-temporary",
        value = "submodule-temporary"
      )
      mainPersistent
        .insert()
        .usingConnection(connection)
        .execute()
      mainTemporary
        .insert()
        .usingConnection(connection)
        .execute()
      submodulePersistent
        .insert()
        .usingConnection(connection)
        .execute()
      submoduleTemporary
        .insert()
        .usingConnection(connection)
        .execute()

      assertThat(temporaryTableNames(connection = connection))
        .containsExactly("main_session_value", "submodule_session_value")
      assertDefaultTablesEmpty()

      connection.close()
      connection = openAlternateConnection(application = application)

      assertThat(temporaryTableNames(connection = connection))
        .containsExactly("main_session_value", "submodule_session_value")
      assertRowsIgnoringOrder(
        table = SIMPLE_MUTABLE_ENTITY,
        connection = connection,
        expected = listOf(mainPersistent)
      )
      assertRowsIgnoringOrder(
        table = SUBMODULE_PERSISTENT_VALUE,
        connection = connection,
        expected = listOf(submodulePersistent)
      )
      assertRowsIgnoringOrder(
        table = MAIN_SESSION_VALUE,
        connection = connection,
        expected = emptyList()
      )
      assertRowsIgnoringOrder(
        table = SUBMODULE_SESSION_VALUE,
        connection = connection,
        expected = emptyList()
      )

      val afterReopen = SubmodulePersistentValue(
        id = "submodule-after-reopen",
        value = "submodule-after-reopen"
      )
      afterReopen
        .insert()
        .usingConnection(connection)
        .execute()
      assertRowsIgnoringOrder(
        table = SUBMODULE_PERSISTENT_VALUE,
        connection = connection,
        expected = listOf(submodulePersistent, afterReopen)
      )
    } finally {
      connection.close()
    }
  }

  private fun withAlternateConnection(test: (DbConnection) -> Unit) =
    withNamedDatabase(databaseName = ALTERNATE_DATABASE_NAME) {
      openAlternateConnection(application = it)
        .use(test)
    }

  private fun openAlternateConnection(application: Application) = openNamedConnection(
    application = application,
    databaseName = ALTERNATE_DATABASE_NAME
  )

  private fun observeMainCount(connection: DbConnection) = SELECT
    .FROM(SIMPLE_MUTABLE_ENTITY)
    .usingConnection(connection)
    .count()
    .observe()
    .runQuery()

  private fun rawValues(connection: DbConnection) = SELECT
    .RAW("SELECT value FROM simple_mutable_entity ORDER BY id")
    .readAs(STRING)
    .usingConnection(connection)
    .execute()

  private fun temporaryTableNames(connection: DbConnection) = setOf("main_session_value", "submodule_session_value")
    .intersect(
      schemaObjectNames(
        master = SQLITE_TEMP_MASTER,
        type = "table",
        database = connection.readableDatabase
      )
    )

  private fun assertDefaultPersistentTablesEmpty() {
    assertRowsIgnoringOrder(
      table = SIMPLE_MUTABLE_ENTITY,
      expected = emptyList()
    )
    assertRowsIgnoringOrder(
      table = SUBMODULE_PERSISTENT_VALUE,
      expected = emptyList()
    )
  }

  private fun assertDefaultTablesEmpty() {
    assertDefaultPersistentTablesEmpty()
    assertRowsIgnoringOrder(
      table = MAIN_SESSION_VALUE,
      expected = emptyList()
    )
    assertRowsIgnoringOrder(
      table = SUBMODULE_SESSION_VALUE,
      expected = emptyList()
    )
  }

  private fun newMainValue(value: String) = SimpleMutableEntity(
    id = null,
    value = value,
    boxedBoolean = true,
    primitiveBoolean = false
  )
}
