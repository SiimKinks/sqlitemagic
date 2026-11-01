package com.siimkinks.sqlitemagic.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction

/**
 * Failure of a migration script.
 *
 * @property fromVersion Database version before the migration
 * @property toVersion Database version after the migration
 * @property stepVersion Database version of the step whose script failed
 * @property resourceName File name of the script that failed
 * @property physicalLine Line of the script where it failed, starting from 1
 * @property statementNumber Number of the statement that failed among the script's executable statements, starting
 *     from 1
 * @property statementText The statement that failed, up to its first 500 characters, or `null` when the script
 *     failed outside a statement, for example when it could not be read
 * @param cause The exception the script failed with
 */
class MigrationException(
  val fromVersion: Int,
  val toVersion: Int,
  val stepVersion: Int,
  val resourceName: String,
  val physicalLine: Int,
  val statementNumber: Int,
  val statementText: String?,
  cause: Exception
) : IllegalStateException(
  "Error executing migration script $resourceName at line $physicalLine " +
      "(statement $statementNumber, migration $fromVersion -> $toVersion, step $stepVersion)" +
      statementText?.let { ": $it" }.orEmpty(),
  cause
)

/**
 * Executor of migration plans.
 *
 * It does not open a transaction; run it in one so that a failed migration is rolled back.
 *
 * @param resources Source of the migration scripts
 * @param connection Connection to the database being migrated
 */
class MigrationExecutor(
  private val resources: MigrationResources,
  private val connection: SQLiteConnection
) {
  /**
   * Execute the migration plan.
   *
   * The persistent views of the database are dropped first, so that the scripts can change the tables the views
   * select from. The scripts of each step then run in order, and [createTargetViews] creates the views of the target
   * schema. A missing optional script is skipped.
   *
   * @param plan The migration plan
   * @param createTargetViews Function that creates the views of the target schema
   * @param onScript Function that is called before each existing script runs
   * @throws MigrationException if a required script is missing, a script cannot be read or has no statements when
   *     it must have them, or a statement fails
   * @throws IllegalStateException if a view cannot be dropped
   */
  fun execute(
    plan: MigrationPlan,
    createTargetViews: () -> Unit,
    onScript: (MigrationResource) -> Unit = {}
  ) {
    for (name in persistentViewNames()) {
      try {
        connection.execSQL("DROP VIEW IF EXISTS main.${quoteSqlIdentifier(name)}")
      } catch (exception: Exception) {
        throw IllegalStateException("Error removing persistent view $name", exception)
      }
    }
    for ((toVersion, sqlResources) in plan.steps) {
      for (resource in sqlResources) {
        executeScript(
          plan = plan,
          stepVersion = toVersion,
          resource = resource,
          onScript = onScript
        )
      }
    }
    createTargetViews()
  }

  private fun persistentViewNames() = connection
    .prepare("SELECT name FROM main.sqlite_master WHERE type = 'view'")
    .use { statement ->
      buildList {
        while (statement.step()) {
          add(statement.getText(0))
        }
      }
    }

  private fun executeScript(
    plan: MigrationPlan,
    stepVersion: Int,
    resource: MigrationResource,
    onScript: (MigrationResource) -> Unit
  ) {
    var lineNumber = 1
    var statementNumber = 0
    fun failure(
      cause: Exception,
      statementText: String? = null
    ) = MigrationException(
      fromVersion = plan.fromVersion,
      toVersion = plan.toVersion,
      stepVersion = stepVersion,
      resourceName = resource.name,
      physicalLine = lineNumber,
      statementNumber = statementNumber + 1,
      statementText = statementText,
      cause = cause
    )

    val stream = try {
      resources.open(resource)
    } catch (exception: FileNotFoundException) {
      when {
        resource.required -> throw failure(cause = exception)
        else -> return
      }
    } catch (exception: Exception) {
      throw failure(cause = exception)
    }
    try {
      InputStreamReader(
        stream,
        Charsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
      )
        .buffered()
        .use { reader ->
          onScript(resource)
          reader
            .lineSequence()
            .withIndex()
            .forEach { (index, line) ->
              lineNumber = index + 1
              if (line.isNotBlank() && !line.isSqlComment()) {
                try {
                  connection.execSQL(line)
                } catch (exception: Exception) {
                  throw failure(
                    cause = exception,
                    statementText = line.take(500)
                  )
                }
                statementNumber++
              }
            }
          check(resource.allowEmpty || statementNumber > 0) {
            "Migration script contains no executable statements"
          }
        }
    } catch (exception: MigrationException) {
      throw exception
    } catch (exception: Exception) {
      throw failure(cause = exception)
    }
  }

  private fun String.isSqlComment() = trimStart().startsWith("--")
}
