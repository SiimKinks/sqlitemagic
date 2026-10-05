package com.siimkinks.sqlitemagic

interface MigrationTestDatabase : AutoCloseable {
  fun execute(
    sql: String,
    bindArgs: List<Any?> = emptyList()
  )

  fun query(
    sql: String,
    bindArgs: List<Any?> = emptyList()
  ): MigrationTestResult
}

data class MigrationTestResult(
  val columns: List<String>,
  val rows: List<List<Any?>>
) {
  override fun equals(other: Any?): Boolean =
    other is MigrationTestResult &&
        columns == other.columns &&
        rows.size == other.rows.size &&
        rows.zip(other.rows).all { (left, right) ->
          left.size == right.size && left.zip(right).all { (expected, actual) ->
            when {
              expected is ByteArray && actual is ByteArray -> expected.contentEquals(actual)
              else -> expected == actual
            }
          }
        }

  override fun hashCode() = 31 * columns.hashCode() + rows.fold(initial = 1) { result, row ->
    31 * result + row.fold(initial = 1) { rowResult, cell ->
      31 * rowResult + when (cell) {
        is ByteArray -> cell.contentHashCode()
        else -> cell.hashCode()
      }
    }
  }
}

/**
 * Selects migration paths among retained releases covered by the selected baseline. Each path starts with a separate
 * fresh database. A version step advances one database version; its SQL statement and module script counts can vary.
 */
enum class MigrationValidationMode {
  /**
   * Default for [SqliteMagicMigrationTestHelper.validateAllMigrations]: tests every covered historical release to the
   * current release. Covered versions 1, 2, 3 produce paths 1 -> 3 and 2 -> 3.
   *
   * For 200 consecutive releases 1..200, this means 199 paths and 19,900 version steps:
   * `N * (N - 1) / 2` for N consecutive covered releases.
   */
  EVERY_VERSION_TO_CURRENT,

  /**
   * Tests neighboring covered retained releases: versions 1, 2, 3 produce paths 1 -> 2 and 2 -> 3.
   * Gaps between retained releases still execute every intervening version step.
   *
   * For 200 consecutive releases 1..200, this means 199 paths and 199 version steps.
   */
  ADJACENT,

  /**
   * Tests only the oldest covered release to current: versions 1, 2, 3 produce path 1 -> 3.
   *
   * For 200 consecutive releases 1..200, this means one path and 199 version steps.
   */
  OLDEST_TO_CURRENT
}

data class MigrationValidationPath(
  val fromVersion: Int,
  val toVersion: Int
)

data class MigrationValidationReport(
  val databaseId: String,
  val releaseVariant: String,
  val baselineVersion: Int,
  val currentVersion: Int,
  val excludedVersions: List<Int>,
  val paths: List<MigrationValidationPath>,
  val mode: MigrationValidationMode,
  val driverImplementation: String,
  val sqliteVersion: String
) {
  val pathCount get() = paths.size
}
