package com.siimkinks.sqlitemagic.migration

import java.io.InputStream

interface MigrationDatabase {
  fun execute(sql: String)
  fun persistentViewNames(): List<String>
}

fun interface MigrationResources {
  fun open(resource: MigrationResource): InputStream
}

data class MigrationResource(
  val name: String,
  val required: Boolean = true,
  val allowEmpty: Boolean = false
)

data class MigrationStep(
  val toVersion: Int,
  val sqlResources: List<MigrationResource>
)

data class MigrationPlan(
  val fromVersion: Int,
  val toVersion: Int,
  val steps: List<MigrationStep>
)

object MigrationPlanner {
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
