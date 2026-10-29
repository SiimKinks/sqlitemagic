package com.siimkinks.sqlitemagic.manager

internal class SchemaMigration(
  val diff: SchemaDiff,
  val plan: TableMigrationPlan?,
  val statements: List<String>,
  val warnings: List<String>
)

internal fun planSchemaMigration(
  previous: DatabaseStructure,
  current: DatabaseStructure
): SchemaMigration {
  val diff = SchemaDiffer.diff(
    from = previous,
    to = current
  )
  val plan = diff
    .takeIf(SchemaDiff::hasPersistentTableOrIndexChanges)
    ?.let(MigrationPlanner::plan)
  return SchemaMigration(
    diff = diff,
    plan = plan,
    statements = plan
      ?.let {
        MigrationSqlRenderer.render(
          diff = diff,
          plan = it
        )
      }
      .orEmpty(),
    warnings = plan
      ?.let {
        migrationWarnings(
          diff = diff,
          plan = it,
          enforcesForeignKeys = current.enforcesForeignKeys
        )
      }
      .orEmpty()
  )
}
