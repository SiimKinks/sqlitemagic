package com.siimkinks.sqlitemagic.migration

import java.io.FileNotFoundException
import java.io.InputStream

/** Source of migration scripts, such as the application's assets. */
fun interface MigrationResources {
  /**
   * Open a migration script.
   *
   * @param resource The script to open
   * @return Stream of the script's UTF-8 text, which the caller closes
   * @throws FileNotFoundException if the script does not exist
   */
  fun open(resource: MigrationResource): InputStream
}

/**
 * A migration script.
 *
 * Each line of the script that is not blank or a `--` comment is executed as one SQL statement.
 *
 * @property name File name of the script, for example `3.sql`
 * @property required Whether a missing script fails the migration instead of being skipped
 * @property allowEmpty Whether the script can contain no executable statements
 */
data class MigrationResource(
  val name: String,
  val required: Boolean = true,
  val allowEmpty: Boolean = false
)

/**
 * Migration to a single database version.
 *
 * @property toVersion Database version after this step
 * @property sqlResources Scripts of this step, in the order they are executed
 */
data class MigrationStep(
  val toVersion: Int,
  val sqlResources: List<MigrationResource>
)

/**
 * Migration from one database version to another.
 *
 * @property fromVersion Database version before the migration
 * @property toVersion Database version after the migration
 * @property steps Steps of the migration, in the order they are executed
 */
data class MigrationPlan(
  val fromVersion: Int,
  val toVersion: Int,
  val steps: List<MigrationStep>
)

/** Planner of the migration scripts between database versions. */
object MigrationPlanner {
  /**
   * Plan the migration from [fromVersion] to [toVersion].
   *
   * The plan has one step for each version after [fromVersion] up to [toVersion]. A step runs the script of each
   * submodule, named `<submodule name><version>.sql`, and then the application's script, named `<version>.sql`. Each
   * script is optional and can be empty.
   *
   * @param fromVersion Current database version
   * @param toVersion Database version to migrate to
   * @param submoduleNames Names of the submodules whose scripts run before the application's script, in the order
   *     they run
   * @return The migration plan, which has no steps when [toVersion] is not higher than [fromVersion]
   */
  fun plan(
    fromVersion: Int,
    toVersion: Int,
    submoduleNames: List<String> = emptyList()
  ) = MigrationPlan(
    fromVersion = fromVersion,
    toVersion = toVersion,
    steps = when {
      fromVersion == toVersion -> emptyList()
      else -> (fromVersion + 1..toVersion).map { version ->
        MigrationStep(
          toVersion = version,
          sqlResources = buildList(capacity = submoduleNames.size + 1) {
            submoduleNames.forEach { submoduleName ->
              add(optionalResource("$submoduleName$version.sql"))
            }
            add(optionalResource("$version.sql"))
          }
        )
      }
    }
  )

  private fun optionalResource(name: String) = MigrationResource(
    name = name,
    required = false,
    allowEmpty = true
  )
}
