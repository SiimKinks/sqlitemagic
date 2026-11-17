package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.ValueRead
import com.siimkinks.sqlitemagic.internal.ViewDefinition
import com.siimkinks.sqlitemagic.internal.sql.RelationshipPlanner
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Selection of all columns of a table or view, read into its model objects.
 *
 * @param M Model type
 */
class EntitySelection<M> internal constructor(
  private val table: Table<M>,
  private val deep: Boolean = false
) : Selection<M>() {
  override fun planRelatedEntities(planner: RelationshipPlanner) = planner.entityRead(deep)

  override fun render(context: SqlRenderingContext) {
    context.requireEntityNotNullExtended(table)
    context.renderEntityColumns(
      table = table,
      related = context.relatedEntities
    )
  }

  override fun createExecutionReader(context: SqlRenderingContext): ExecutionReader<M> {
    val related = context.relatedEntities ?: table.viewDefinition?.blocks?.let { blocks ->
      RelatedEntities(
        table = table,
        children = blocks
      )
    }
    val read = ValueRead { statement, startIndex ->
      table.readEntityAt(
        statement = statement,
        startIndex = startIndex,
        related = related
      )
    }
    return ExecutionReader { read }
  }

  override val exportedValues get() = table.selectedEntityFields()

  internal fun deep() = EntitySelection(
    table = table,
    deep = true
  )
}

/**
 * Mark that data should be queried deep.
 *
 * If this method is called all complex columns and their complex columns will be queried.
 * If any join is missing then system adds them. It also respects user defined joins with
 * `ON (foreignKey IS id)` constraint and does not add another join for them.
 *
 * By default system performs shallow queries, which means that complex columns are created from their IDs
 * whenever possible, without joining their tables.
 *
 * In a compound SELECT all the constituent SELECTs are queried deep.
 *
 * @return SQL SELECT statement
 */
fun <T> SelectQuery<T, EntitySelection<T>>.queryDeep() = SelectQuery(
  node
    .withSelection(node.selection.deep())
    .withDeepArms()
)

/**
 * Mark that the selected entity values should be queried deep.
 *
 * Every entity value of the selection joins its relationships and reads them in full, as `queryDeep()` of
 * `SELECT FROM` does for its entity; without it, related objects that can be built from their stored IDs are not
 * joined. A relationship reuses a join of the query whose constraint is `foreignKey IS id`, or an earlier occurrence,
 * the FROM table included, that the parent's own join matches that way.
 *
 * In a compound SELECT all the constituent SELECTs are queried deep.
 *
 * Example:
 * ```
 * (SELECT.COLUMNS(BOOK, AUTHOR.NAME) FROM BOOK).queryDeep()
 * ```
 *
 * @return SQL SELECT statement
 * @throws IllegalArgumentException if no selected value is an entity
 */
@JvmName("queryRowsDeep")
fun <R, T> SelectQuery<T, RowSelection<R, T>>.queryDeep(): SelectQuery<T, RowSelection<R, T>> {
  require(node.selection.expressions == null) { "queryDeep() deepens entity values, and this selection has none" }
  return SelectQuery(
    node
      .withSelection(node.selection.deep())
      .withDeepArms()
  )
}

internal fun SqlRenderingContext.renderEntityColumns(table: Table<*>, related: RelatedEntities?) {
  val qualifier = entityQualifier(table)
  when (val view = table.viewDefinition) {
    null -> renderFields(
      table = table,
      qualifier = qualifier
    )
    else -> renderViewColumns(
      table = table,
      view = view,
      qualifier = qualifier
    )
  }
  related?.joined?.forEach { joined ->
    sql.append(", ")
    renderFields(
      table = joined.table,
      qualifier = qualifier(joined.table)
    )
  }
}

private fun SqlRenderingContext.renderViewColumns(table: Table<*>, view: ViewDefinition, qualifier: String) {
  if (!namesFieldOutputs) {
    sql.append(view.renderedColumns(qualifier))
    return
  }
  renderList(count = view.columnNames.size) { index ->
    sql
      .append(qualifier)
      .append('.')
      .append(view.quotedColumnNames[index])
    nameFieldOutput(
      table = table,
      name = view.columnNames[index]
    )
  }
}

private fun SqlRenderingContext.renderFields(table: Table<*>, qualifier: String) {
  if (!namesFieldOutputs) {
    sql.append(table.renderedFields(qualifier))
    return
  }
  renderList(items = table.selectedEntityFields()) { field ->
    field.node.render(this)
    nameFieldOutput(
      table = table,
      name = field.name
    )
  }
}
