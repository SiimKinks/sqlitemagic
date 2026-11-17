package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ALWAYS
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
    context.renderEntityColumns(table)
    context.relatedEntities?.joined?.forEach { joined ->
      context.sql.append(", ")
      context.renderFields(
        table = joined.table,
        qualifier = context.qualifier(joined.table)
      )
    }
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

internal fun SqlRenderingContext.renderEntityValue(table: Table<*>) {
  require(table.tableRelationships.none { it.read == ALWAYS }) {
    val relationship = table.tableRelationships.first { it.read == ALWAYS }
    "The ${table.referenceName} entity reads ${table.referenceName}.${relationship.columnName} through a join, " +
        "which a selected entity value cannot plan yet; select it with SELECT FROM ${table.referenceName}"
  }
  renderEntityColumns(table)
}

private fun SqlRenderingContext.renderEntityColumns(table: Table<*>) {
  requireEntityNotNullExtended(table)
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
