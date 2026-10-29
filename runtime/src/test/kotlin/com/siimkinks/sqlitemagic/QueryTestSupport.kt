package com.siimkinks.sqlitemagic

import android.database.SQLException
import androidx.sqlite.SQLiteException
import com.siimkinks.sqlitemagic.internal.sql.NativeValue
import org.junit.Assert.assertThrows
import org.mockito.Mockito.mockConstruction

internal data class MappedId(val value: Long)

internal fun <T> SelectQuery<T, *>.rows(database: BundledSqliteDatabase) = usingConnection(database.dbConnection())
  .execute()

internal fun <T> SelectQuery<T, *>.rowsIn(database: () -> BundledSqliteDatabase) = database().use(this::rows)

internal data class PlanSnapshot(
  val sql: String,
  val arguments: List<NativeValue>
)

internal fun snapshot(plan: SelectPlan<*>) = PlanSnapshot(
  sql = plan.sql,
  arguments = plan.boundArguments()
)

internal fun snapshot(plan: CompiledMutation) = PlanSnapshot(
  sql = plan.sql,
  arguments = plan.arguments
)

internal data class ObservedPlanSnapshot(
  val sql: String,
  val arguments: List<NativeValue>,
  val sources: List<String>
)

internal fun observedSnapshot(plan: SelectPlan<*>) = ObservedPlanSnapshot(
  sql = plan.sql,
  arguments = plan.boundArguments(),
  sources = plan.prepared.dependencies.observedTables.asList()
)

internal data class DependencySnapshot(
  val observed: List<String>,
  val direct: List<SqliteQuerySource>
)

internal fun dependencySnapshot(dependencies: QueryDependencies) = DependencySnapshot(
  observed = dependencies.observedTables.asList(),
  direct = dependencies.directSources
)

internal fun SelectQuery<*, *>.observedTables() = compile()
  .prepared
  .dependencies
  .observedTables
  .asList()

internal fun sqlExceptionMessage(block: () -> Unit): String? {
  val messages = mutableListOf<Any?>()
  mockConstruction(SQLException::class.java) { _, context -> messages += context.arguments().firstOrNull() }.use {
    assertThrows(SQLException::class.java, block)
  }
  return messages.single() as String?
}

internal fun sqliteErrorMessage(block: () -> Unit) = assertThrows(SQLiteException::class.java, block)
  .message
  ?.substringAfter("message: ")
