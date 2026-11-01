package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLiteConnection
import com.siimkinks.sqlitemagic.connection.SchemaCallback
import com.siimkinks.sqlitemagic.internal.LogUtil.logDebug
import com.siimkinks.sqlitemagic.migration.MigrationExecutor
import com.siimkinks.sqlitemagic.migration.MigrationPlanner
import com.siimkinks.sqlitemagic.migration.MigrationResources

internal class DatabaseSchema(
  private val resources: MigrationResources,
  private val database: GeneratedDatabase,
  private val downgrader: DbDowngrader
) : SchemaCallback {
  override val version get() = database.dbVersion

  override fun applySchemaPragmas(connection: SQLiteConnection) = database.applySchemaPragmas(connection)

  override fun create(connection: SQLiteConnection) = database.createSchema(connection)

  override fun upgrade(
    connection: SQLiteConnection,
    oldVersion: Int,
    newVersion: Int
  ) {
    logDebug { "Executing upgrade scripts" }
    val plan = MigrationPlanner.plan(
      fromVersion = oldVersion,
      toVersion = newVersion,
      submoduleNames = database
        .submoduleNames
        ?.asList()
        .orEmpty()
    )
    MigrationExecutor(
      resources = resources,
      connection = connection
    )
      .execute(
        plan = plan,
        createTargetViews = { database.migrateViews(connection) },
        onScript = { resource -> logDebug { "Executing script ${resource.name}" } }
      )
  }

  override fun downgrade(
    connection: SQLiteConnection,
    oldVersion: Int,
    newVersion: Int
  ) = downgrader.onDowngrade(
    connection = connection,
    oldVersion = oldVersion,
    newVersion = newVersion
  )

  override fun open(connection: SQLiteConnection) = database.createTemporarySchema(connection)
}
