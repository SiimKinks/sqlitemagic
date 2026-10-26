package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.internal.RelatedEntities
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
    when (val view = table.viewDefinition) {
      null -> {
        renderFields(
          context = context,
          table = table
        )
        context.relatedEntities?.joined?.forEach { joined ->
          context.sql.append(", ")
          renderFields(
            context = context,
            table = joined.table
          )
        }
      }
      else -> renderViewColumns(
        context = context,
        view = view
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
    val read = { cursor: Cursor, startIndex: Int ->
      table.readEntityAt(
        cursor = cursor,
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

  private fun renderViewColumns(context: SqlRenderingContext, view: ViewDefinition) {
    val qualifier = context.qualifier(table)
    context.renderList(count = view.columnNames.size) { index ->
      context.sql
        .append(qualifier)
        .append('.')
        .append(view.quotedColumnNames[index])
      context.nameFieldOutput(
        table = table,
        name = view.columnNames[index]
      )
    }
  }
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

private fun renderFields(context: SqlRenderingContext, table: Table<*>) =
  context.renderList(items = table.selectedEntityFields()) { field ->
    field.node.render(context)
    context.nameFieldOutput(
      table = table,
      name = field.name
    )
  }
