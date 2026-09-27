package com.siimkinks.sqlitemagic

import android.content.Context
import android.content.res.AssetManager
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

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

  // This method already runs in a transaction.
  override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (SqliteMagic.LOGGING_ENABLED) {
      LogUtil.logDebug("Executing upgrade scripts")
    }
    val assets = context.assets
    val submoduleNames = database.submoduleNames
    for (versionIndex in oldVersion until newVersion) {
      val fileName = "${versionIndex + 1}.views"
      submoduleNames?.forEach { submoduleName ->
        runViewRemovalPlan(
          db = db,
          assets = assets,
          fileName = submoduleName + fileName
        )
      }
      runViewRemovalPlan(
        db = db,
        assets = assets,
        fileName = fileName
      )
    }
    for (versionIndex in oldVersion until newVersion) {
      val fileName = "${versionIndex + 1}.sql"
      submoduleNames?.forEach { submoduleName ->
        runMigrationScript(
          db = db,
          assets = assets,
          fileName = submoduleName + fileName
        )
      }
      runMigrationScript(
        db = db,
        assets = assets,
        fileName = fileName
      )
    }
    database.migrateViews(db)
  }

  private fun runViewRemovalPlan(
    db: SupportSQLiteDatabase,
    assets: AssetManager,
    fileName: String
  ) {
    val inputStream = openAsset(
      assets = assets,
      fileName = fileName,
      errorMessage = "Error opening view removal plan $fileName"
    ) ?: return

    var lineNumber = 1
    try {
      InputStreamReader(inputStream)
        .buffered()
        .useLines { lines ->
          lines.forEach { viewName ->
            try {
              db.query("SELECT type FROM main.sqlite_master WHERE name = ? AND type = 'view'", arrayOf(viewName))
                .use { cursor ->
                  if (cursor.moveToFirst() && cursor.getString(0) == "view") {
                    SqlUtil.dropView(db, viewName)
                  }
                }
            } catch (exception: RuntimeException) {
              throw viewRemovalException(fileName, lineNumber, exception)
            }
            lineNumber++
          }
        }
    } catch (exception: IOException) {
      throw viewRemovalException(fileName, lineNumber, exception)
    }
  }

  private fun viewRemovalException(
    fileName: String,
    lineNumber: Int,
    cause: Exception
  ) = IllegalStateException("Error executing view removal plan $fileName at line $lineNumber", cause)

  private fun runMigrationScript(
    db: SupportSQLiteDatabase,
    assets: AssetManager,
    fileName: String
  ) {
    val inputStream = openAsset(
      assets = assets,
      fileName = fileName,
      errorMessage = "Error opening migration script $fileName"
    ) ?: return

    var lineNumber = 0
    try {
      if (SqliteMagic.LOGGING_ENABLED) {
        LogUtil.logDebug("Executing script %s", fileName)
      }
      InputStreamReader(inputStream)
        .buffered()
        .useLines { lines ->
          lines.forEach { sql ->
            lineNumber++
            try {
              db.execSQL(sql)
            } catch (exception: RuntimeException) {
              throw migrationException(fileName, lineNumber, exception)
            }
          }
        }
    } catch (exception: IOException) {
      throw migrationException(fileName, lineNumber.coerceAtLeast(1), exception)
    }
  }

  private fun openAsset(
    assets: AssetManager,
    fileName: String,
    errorMessage: String
  ): InputStream? = try {
    assets.open(fileName)
  } catch (_: FileNotFoundException) {
    null
  } catch (exception: IOException) {
    throw IllegalStateException(errorMessage, exception)
  }

  private fun migrationException(
    fileName: String,
    lineNumber: Int,
    cause: Exception
  ) = IllegalStateException("Error executing migration script $fileName at line $lineNumber", cause)

  override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) =
    downgrader.onDowngrade(db, oldVersion, newVersion)

  override fun onConfigure(db: SupportSQLiteDatabase) = database.configureDatabase(db)
}
