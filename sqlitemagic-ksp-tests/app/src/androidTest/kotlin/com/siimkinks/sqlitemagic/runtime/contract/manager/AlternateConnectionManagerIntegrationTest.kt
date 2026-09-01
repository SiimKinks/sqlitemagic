package com.siimkinks.sqlitemagic.runtime.contract.manager

import android.app.Application
import android.database.Cursor
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.DbConnection
import com.siimkinks.sqlitemagic.Delete
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.MainSessionValueTable.Companion.MAIN_SESSION_VALUE
import com.siimkinks.sqlitemagic.Select
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.SqliteMagic
import com.siimkinks.sqlitemagic.SqliteMagicDatabase
import com.siimkinks.sqlitemagic.SubmodulePersistentValueTable.Companion.SUBMODULE_PERSISTENT_VALUE
import com.siimkinks.sqlitemagic.SubmoduleSessionValueTable.Companion.SUBMODULE_SESSION_VALUE
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.Update
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.fixture.model.MainSessionValue
import com.siimkinks.sqlitemagic.fixture.model.SimpleMutableEntity
import com.siimkinks.sqlitemagic.insert
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmodulePersistentValue
import com.siimkinks.sqlitemagic.runtime.fixture.submodule.SubmoduleSessionValue
import com.siimkinks.sqlitemagic.runtime.support.RuntimeDatabaseTest
import com.siimkinks.sqlitemagic.update
import io.reactivex.schedulers.Schedulers
import org.junit.Test

private const val ALTERNATE_DATABASE_NAME = "cov03.db"

class AlternateConnectionManagerIntegrationTest : RuntimeDatabaseTest() {
  @Test
  fun generatedOperationsRouteMainAndSubmoduleManagers() = withAlternateConnection { connection ->
    val mainInserted = newMainValue(value = "main-inserted")
    mainInserted
      .insert()
      .usingConnection(connection)
      .execute()
    assertRows(
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
    assertRows(
      table = SIMPLE_MUTABLE_ENTITY,
      connection = connection,
      expected = listOf(mainPersisted)
    )
    mainPersisted
      .delete()
      .usingConnection(connection)
      .execute()

    val submoduleInserted = newSubmoduleValue(
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
    assertRows(
      table = SUBMODULE_PERSISTENT_VALUE,
      connection = connection,
      expected = listOf(submodulePersisted)
    )
    submodulePersisted
      .delete()
      .usingConnection(connection)
      .execute()

    assertRows(
      table = SIMPLE_MUTABLE_ENTITY,
      connection = connection,
      expected = emptyList()
    )
    assertRows(
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
      rows(
        table = SIMPLE_MUTABLE_ENTITY,
        connection = connection
      )
    ).containsExactly(inserted)
    assertThat(
      Select
        .column(SIMPLE_MUTABLE_ENTITY.VALUE)
        .from(SIMPLE_MUTABLE_ENTITY)
        .usingConnection(connection)
        .execute()
    ).containsExactly("query-inserted")
    assertThat(rawValues(connection = connection))
      .containsExactly("query-inserted")

    assertThat(
      Update
        .table(SIMPLE_MUTABLE_ENTITY)
        .setNullable(SIMPLE_MUTABLE_ENTITY.VALUE, "builder-updated")
        .where(SIMPLE_MUTABLE_ENTITY.ID IS insertedId)
        .usingConnection(connection)
        .execute()
    ).isEqualTo(1)
    assertThat(
      Select
        .column(SIMPLE_MUTABLE_ENTITY.VALUE)
        .from(SIMPLE_MUTABLE_ENTITY)
        .usingConnection(connection)
        .execute()
    ).containsExactly("builder-updated")

    assertThat(
      Delete
        .from(SIMPLE_MUTABLE_ENTITY)
        .where(SIMPLE_MUTABLE_ENTITY.ID IS insertedId)
        .usingConnection(connection)
        .execute()
    ).isEqualTo(1)
    assertRows(
      table = SIMPLE_MUTABLE_ENTITY,
      connection = connection,
      expected = emptyList()
    )
    assertDefaultPersistentTablesEmpty()
  }

  @Test
  fun observersAreIsolatedAndAlternateCloseCompletesOnlyItsObservers() {
    val application = application()
    application.deleteDatabase(ALTERNATE_DATABASE_NAME)
    val connection = openAlternateConnection(application = application)
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
      application.deleteDatabase(ALTERNATE_DATABASE_NAME)
    }
  }

  @Test
  fun reopenRetainsPersistentRowsAndRecreatesIsolatedTemporaryTables() {
    val application = application()
    application.deleteDatabase(ALTERNATE_DATABASE_NAME)
    var connection = openAlternateConnection(application = application)
    try {
      val mainPersistent = newMainValue(value = "main-persistent")
      val mainTemporary = MainSessionValue(
        id = "main-temporary",
        value = "main-temporary"
      )
      val submodulePersistent = newSubmoduleValue(
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
      assertRows(
        table = SIMPLE_MUTABLE_ENTITY,
        connection = connection,
        expected = listOf(mainPersistent)
      )
      assertRows(
        table = SUBMODULE_PERSISTENT_VALUE,
        connection = connection,
        expected = listOf(submodulePersistent)
      )
      assertRows(
        table = MAIN_SESSION_VALUE,
        connection = connection,
        expected = emptyList()
      )
      assertRows(
        table = SUBMODULE_SESSION_VALUE,
        connection = connection,
        expected = emptyList()
      )

      val afterReopen = newSubmoduleValue(
        id = "submodule-after-reopen",
        value = "submodule-after-reopen"
      )
      afterReopen
        .insert()
        .usingConnection(connection)
        .execute()
      assertRows(
        table = SUBMODULE_PERSISTENT_VALUE,
        connection = connection,
        expected = listOf(submodulePersistent, afterReopen)
      )
    } finally {
      connection.close()
      application.deleteDatabase(ALTERNATE_DATABASE_NAME)
    }
  }

  private fun withAlternateConnection(test: (DbConnection) -> Unit) {
    val application = application()
    application.deleteDatabase(ALTERNATE_DATABASE_NAME)
    val connection = openAlternateConnection(application = application)
    try {
      test(connection)
    } finally {
      connection.close()
      application.deleteDatabase(ALTERNATE_DATABASE_NAME)
    }
  }

  private fun openAlternateConnection(application: Application) = SqliteMagic
    .builder(application)
    .name(ALTERNATE_DATABASE_NAME)
    .database(SqliteMagicDatabase())
    .sqliteFactory(FrameworkSQLiteOpenHelperFactory())
    .scheduleRxQueriesOn(Schedulers.trampoline())
    .openNewConnection()

  private fun observeMainCount(connection: DbConnection) = Select
    .from(SIMPLE_MUTABLE_ENTITY)
    .usingConnection(connection)
    .count()
    .observe()
    .runQuery()

  private fun rawValues(connection: DbConnection) = Select
    .raw("SELECT value FROM simple_mutable_entity ORDER BY id")
    .from(SIMPLE_MUTABLE_ENTITY)
    .usingConnection(connection)
    .execute()
    .use(Cursor::readStrings)

  private fun temporaryTableNames(connection: DbConnection) = Select
    .raw(
      "SELECT name FROM sqlite_temp_master " +
          "WHERE type = 'table' AND name IN (?, ?)"
    )
    .from(SIMPLE_MUTABLE_ENTITY)
    .withArgs("main_session_value", "submodule_session_value")
    .usingConnection(connection)
    .execute()
    .use(Cursor::readStrings)
    .toSet()

  private fun <T> assertRows(
    table: Table<T>,
    connection: DbConnection,
    expected: List<T>
  ) {
    assertThat(rows(table = table, connection = connection))
      .containsExactlyElementsIn(expected)
  }

  private fun <T> rows(
    table: Table<T>,
    connection: DbConnection
  ) = Select
    .from(table)
    .usingConnection(connection)
    .queryDeep()
    .execute()

  private fun assertDefaultPersistentTablesEmpty() {
    assertRows(
      table = SIMPLE_MUTABLE_ENTITY,
      connection = SqliteMagic.getDefaultConnection(),
      expected = emptyList()
    )
    assertRows(
      table = SUBMODULE_PERSISTENT_VALUE,
      connection = SqliteMagic.getDefaultConnection(),
      expected = emptyList()
    )
  }

  private fun assertDefaultTablesEmpty() {
    assertDefaultPersistentTablesEmpty()
    assertRows(
      table = MAIN_SESSION_VALUE,
      connection = SqliteMagic.getDefaultConnection(),
      expected = emptyList()
    )
    assertRows(
      table = SUBMODULE_SESSION_VALUE,
      connection = SqliteMagic.getDefaultConnection(),
      expected = emptyList()
    )
  }

  private fun newMainValue(value: String) = SimpleMutableEntity(
    id = null,
    value = value,
    boxedBoolean = true,
    primitiveBoolean = false
  )

  private fun newSubmoduleValue(
    id: String,
    value: String
  ) = SubmodulePersistentValue(
    id = id,
    value = value
  )

  private fun application() = InstrumentationRegistry
    .getInstrumentation()
    .targetContext
    .applicationContext as Application
}

private fun Cursor.readStrings() = buildList {
  while (moveToNext()) {
    add(getString(0))
  }
}
