@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.SelectNode
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Aliased subquery, which can be used as a source in FROM and JOIN clauses.
 *
 * Create one with `query AS "alias"`.
 *
 * @param T Row type
 */
class DerivedSource<T> internal constructor(
  override val occurrence: DerivedTable<T>
) : QueryOutputSource<T>() {
  /**
   * Convert this subquery into a common table expression named by the alias, which can then be declared with [WITH].
   *
   * @return Common table expression
   */
  fun asCte() = cte()

  /**
   * Create another alias for the same subquery.
   *
   * @param alias The alias name
   * @return New aliased subquery
   */
  @CheckResult
  infix fun AS(alias: String) = DerivedSource(
    DerivedTable(
      query = occurrence.query,
      alias = alias
    )
  )

  override fun cte() = declared(
    query = occurrence.query,
    name = occurrence.alias
  )
}

internal abstract class QueryOutputTable<T>(
  val node: SelectNode<*, *>,
  name: String,
  alias: String?
) : Table<T>(
  name = name,
  alias = alias
) {
  protected abstract val label: String?

  protected abstract val kind: String

  override val isPhysical get() = false

  override val isDefaultHandle get() = false

  abstract fun renderOutputs(context: SqlRenderingContext)

  abstract fun reader(context: SqlRenderingContext): ExecutionReader<T>

  fun <W, R, N : SqlNullability, V> export(selected: EncodableSqlExpression<W, R, N, V>) = DerivedField(
    node = exportNode(selected),
    selected = selected
  )

  fun <R, N : SqlNullability, V> exportValue(selected: SqlExpression<R, N, V>) = DerivedValue(
    node = exportNode(selected),
    selected = selected
  )

  fun <M> exportEntity(entity: EntityValue<M>) = DerivedEntity<M>(
    occurrence = this,
    export = exportOf(
      value = entity,
      otherForm = entity.nullable()
    ) { "the ${entity.label} as nullable; read it with .nullable()" }
  )

  fun <M> exportNullableEntity(entity: NullableEntity<M>) = DerivedEntity<M>(
    occurrence = this,
    export = exportOf(
      value = entity,
      otherForm = entity.entity
    ) { "the ${entity.entity.label} as not nullable; read it without .nullable()" }
  ).nullable()

  private inline fun exportOf(
    value: SelectValue<*, *>,
    otherForm: SelectValue<*, *>,
    mismatch: () -> String
  ): EntityExport {
    val exported = node.selection.exportedEntities
    val found = exported.firstOrNull { it.value === value }
    require(found != null) {
      when {
        exported.any { it.value === otherForm } -> "$sourceName exports ${mismatch()}"
        else -> "$sourceName exports only the entities its query selects; use the selected instance"
      }
    }
    return found.export
  }

  private val sourceName get() = label ?: "A $kind"

  private fun exportNode(selected: SqlExpression<*, *, *>): ExpressionNode.Field {
    val selection = node.selection
    val widenedIndex = selection.widenedIndex(selected)
    val index = when {
      widenedIndex >= 0 -> widenedIndex
      else -> selection.exportedValues.indexOfExport(selected)
    }
    require(index >= 0) {
      val name = sourceName
      when {
        selection.exportedValues.indexOfExport(selected.nullable()) >= 0 ->
          "$name exports $selected as nullable; read it with .nullable()"
        else -> "$name exports only the values its query selects; use the selected instance"
      }
    }
    return ExpressionNode.Field(
      table = this,
      name = selection.exportedName(index),
      widened = widenedIndex >= 0
    )
  }

  override fun toString() = label ?: "the $kind"
}

private fun List<SqlExpression<*, *, *>?>.indexOfExport(selected: SqlExpression<*, *, *>) = indexOfFirst { value ->
  value === selected || value is KeyedField<*, *, *> && value.field === selected
}

internal fun Selection<*>.requireUniqueExportedNames(source: String) {
  val names = exportedNames
  val columns = MutableObjectIntMap<String>(initialCapacity = names.size)
  names.forEachIndexed { column, name ->
    val key = name.lowercase()
    val first = columns.getOrDefault(
      key = key,
      defaultValue = -1
    )
    require(first < 0) {
      "$source exports two columns named $name, from ${exportOwner(first)} and ${exportOwner(column)}; alias one " +
          "of them"
    }
    columns[key] = column
  }
}

internal class DerivedTable<T>(
  val query: SelectQuery<T, *>,
  alias: String?
) : QueryOutputTable<T>(
  node = query.nestedNode(),
  name = alias ?: "",
  alias = alias
) {
  override val sqlQualifier = alias?.let(::quoteSqlIdentifier) ?: ""

  override val label get() = alias

  override val kind get() = "derived source"

  init {
    node.selection.requireUniqueExportedNames(
      source = when (alias) {
        null -> "A derived source"
        else -> "Derived source $alias"
      }
    )
  }

  override fun renderSource(context: SqlRenderingContext) {
    context.sql.append('(')
    context.renderDerived(
      occurrence = this,
      body = node
    )
    context.sql.append(')')
    if (alias != null) {
      context.sql
        .append(" AS ")
        .append(sqlQualifier)
    }
  }

  override fun renderOutputs(context: SqlRenderingContext) = context.deferDerivedOutputs()

  @Suppress("UNCHECKED_CAST")
  override fun reader(context: SqlRenderingContext) = context.derivedReader as ExecutionReader<T>

  override fun equals(other: Any?) = this === other ||
      alias == null && other is DerivedTable<*> && other.alias == null && other.query === query

  override fun hashCode() = when (alias) {
    null -> System.identityHashCode(query)
    else -> System.identityHashCode(this)
  }
}

/**
 * Selection of all columns of a subquery or a common table expression.
 *
 * @param T Row type
 */
class QuerySelection<T> internal constructor(
  private val occurrence: QueryOutputTable<T>
) : Selection<T>() {
  override fun render(context: SqlRenderingContext) {
    context.requireNotNullExtended(occurrence) {
      "An outer join can leave the result of $occurrence NULL; select its values with .nullable()"
    }
    occurrence.renderOutputs(context)
  }

  override fun createExecutionReader(context: SqlRenderingContext) = occurrence.reader(context)

  override val exportedValues get() = occurrence.node.selection.exportedValues

  override val exportedNames get() = occurrence.node.selection.exportedNames

  override val exportedEntities get() = occurrence.node.selection.exportedEntities

  override val selectedValues get() = occurrence.node.selection.selectedValues

  override val mappedWithFunction get() = occurrence.node.selection.mappedWithFunction

  override fun widenedIndex(selected: SqlExpression<*, *, *>) = occurrence.node.selection.widenedIndex(selected)

  override fun exportOwner(column: Int) = occurrence.node.selection.exportOwner(column)
}
