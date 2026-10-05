package com.siimkinks.sqlitemagic

import android.content.Context
import android.content.res.AssetManager
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.siimkinks.sqlitemagic.migration.MigrationDatabase
import com.siimkinks.sqlitemagic.migration.MigrationExecutor
import com.siimkinks.sqlitemagic.migration.MigrationPlanner
import com.siimkinks.sqlitemagic.migration.MigrationResource
import com.siimkinks.sqlitemagic.migration.MigrationResources

internal class DbCallback(
  private val context: Context,
  version: Int,
  private val database: GeneratedDatabase,
  private val downgrader: DbDowngrader
) : SupportSQLiteOpenHelper.Callback(version) {
  override fun onCreate(db: SupportSQLiteDatabase) = database.createSchema(db)

  override fun onOpen(db: SupportSQLiteDatabase) {
    super.onOpen(db)
    database.createTemporarySchema(db)
  }

  override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (SqliteMagic.loggingEnabled) {
      LogUtil.logDebug("Executing upgrade scripts")
    }
    val plan = MigrationPlanner.plan(
      fromVersion = oldVersion,
      toVersion = newVersion,
      submoduleNames = database
        .submoduleNames
        ?.toList()
        .orEmpty()
    )
    MigrationExecutor(
      resources = AssetMigrationResources(context.assets),
      database = SupportSQLiteMigrationDatabase(db)
    ).execute(
      plan = plan,
      createTargetViews = { database.migrateViews(db) },
      onScript = ::logScript
    )
  }

  private fun logScript(resource: MigrationResource) {
    if (SqliteMagic.loggingEnabled) {
      LogUtil.logDebug("Executing script %s", resource.name)
    }
  }

  override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
    downgrader.onDowngrade(db, oldVersion, newVersion)

  override fun onConfigure(db: SupportSQLiteDatabase) = database.configureDatabase(db)
}

internal class AssetMigrationResources(
  private val assets: AssetManager
) : MigrationResources {
  override fun open(resource: MigrationResource) = assets.open(resource.name)
}

internal class SupportSQLiteMigrationDatabase(
  private val database: SupportSQLiteDatabase
) : MigrationDatabase {
  override fun execute(sql: String) = database.execSQL(sql)

  override fun persistentViewNames() = database
    .query("SELECT name FROM main.sqlite_master WHERE type = 'view'")
    .use { cursor ->
      buildList {
        while (cursor.moveToNext()) {
          add(cursor.getString(0))
        }
      }
    }
}
