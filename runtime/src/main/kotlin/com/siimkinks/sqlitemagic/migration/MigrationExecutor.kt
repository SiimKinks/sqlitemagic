package com.siimkinks.sqlitemagic.migration

import java.io.FileNotFoundException
import java.io.InputStreamReader
import java.nio.charset.CodingErrorAction

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

class MigrationExecutor(
  private val resources: MigrationResources,
  private val database: MigrationDatabase
) {
  fun execute(
    plan: MigrationPlan,
    createTargetViews: () -> Unit,
    onScript: (MigrationResource) -> Unit = {}
  ) {
    for (name in database.persistentViewNames()) {
      val escapedName = name.replace(oldValue = "\"", newValue = "\"\"")
      try {
        database.execute("DROP VIEW IF EXISTS main.\"$escapedName\"")
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
      ).buffered().use { reader ->
        onScript(resource)
        reader
          .lineSequence()
          .withIndex()
          .forEach { (index, line) ->
            lineNumber = index + 1
            if (line.isNotBlank() && !line.isSqlComment()) {
              try {
                database.execute(line)
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
    } catch (exception: Exception) {
      when {
        exception is MigrationException -> throw exception
        else -> throw failure(cause = exception)
      }
    }
  }

  private fun String.isSqlComment() = trimStart().startsWith("--")
}
