package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.internal.EntityProperty
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ALWAYS
import com.siimkinks.sqlitemagic.internal.RelationshipRead.ID_ONLY
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.RelationshipPlanner
import com.siimkinks.sqlitemagic.internal.sql.SourceOccurrence
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Selection of columns read into partial model objects.
 *
 * Create one with `mapTo(table)`.
 *
 * @param M Model type
 */
class PartialEntitySelection<M> internal constructor(
  private val outputs: List<SqlExpression<*, *, *>>,
  private val table: Table<M>
) : Selection<M>() {
  override fun planRelatedEntities(planner: RelationshipPlanner): RelatedEntities {
    val fields = Array(outputs.size) { index ->
      requireNotNull(outputs[index].node as? ExpressionNode.Field) {
        "mapTo(${table.name}) reads plain fields, but selected value ${index + 1} is computed"
      }
    }
    val root = requireNotNull(planner.occurrenceOf(table)) {
      "mapTo(${table.name}) needs ${table.referenceName} among the query's sources"
    }
    val plan = PartialReadPlan(
      planner = planner,
      fields = fields
    )
    return plan
      .read(root)
      .also {
        plan.consumed.forEachIndexed { index, used ->
          require(used) {
            "${fields[index]} is not a field of ${table.referenceName} or of a table its relationships reach"
          }
        }
      }
  }

  override fun render(context: SqlRenderingContext) {
    context.requireEntityNotNullExtended(table)
    context.renderList(
      items = outputs,
      render = context::appendOutput
    )
  }

  override fun forEachField(action: (ExpressionNode.Field) -> Unit) = outputs.forEachField(action)

  override val exportedValues get() = outputs

  override fun createExecutionReader(context: SqlRenderingContext): ExecutionReader<M> {
    val related = requireNotNull(context.relatedEntities) {
      "mapTo(${table.name}) needs a FROM"
    }
    val positions = related.positions
    val read = { cursor: Cursor, _: Int ->
      table.readPartialEntityAt(
        cursor = cursor,
        positions = positions,
        related = related
      )
    }
    return ExecutionReader { read }
  }
}

private class PartialReadPlan(
  private val planner: RelationshipPlanner,
  private val fields: Array<ExpressionNode.Field>
) {
  private val owners = Array(fields.size) { planner.occurrenceOf(fields[it].table) }

  val consumed = BooleanArray(fields.size)

  fun read(occurrence: SourceOccurrence): RelatedEntities {
    val table = occurrence.table
    val entityFields = table.selectedEntityFields()
    val positions = IntArray(entityFields.size) { -1 }
    fields.forEachIndexed { index, field ->
      if (owners[index] === occurrence) {
        val fieldIndex = entityFields.indexOfFirst { it.name == field.name }
        if (fieldIndex >= 0) {
          if (positions[fieldIndex] < 0) {
            positions[fieldIndex] = index
          }
          consumed[index] = true
        }
      }
    }
    val relationships = table.tableRelationships
    val children = arrayOfNulls<RelatedEntities>(relationships.size)
    relationships.forEachIndexed { index, relationship ->
      if (relationship.read == ID_ONLY) {
        return@forEachIndexed
      }
      val child = planner.existingChild(
        parent = occurrence,
        relationship = index
      )
      if (child != null && owners.any { it === child }) {
        children[index] = read(child)
      }
    }
    requireSelected(
      table = table,
      positions = positions,
      children = children
    )
    return RelatedEntities(
      table = table,
      children = children,
      positions = positions
    )
  }
}

private fun requireSelected(table: Table<*>, positions: IntArray, children: Array<RelatedEntities?>) {
  val properties = requireNotNull(table.partialProperties) {
    "mapTo(${table.name}) cannot read a view whose required properties hold tables or views"
  }
  val relationships = table.tableRelationships
  table
    .selectedEntityFields()
    .forEachIndexed { index, field ->
      val property = properties[index]
      val checked = property.required && embeddedValueRead(
        properties = properties,
        positions = positions,
        withinPrefix = property.withinPrefix
      )
      if (!checked) {
        return@forEachIndexed
      }
      val relationshipIndex = relationships.indexOfFirst { it.columnName == field.name }
      val relationship = relationships.getOrNull(relationshipIndex)
      val selected = when {
        relationship == null -> positions[index] >= 0
        children[relationshipIndex] != null -> true
        else -> relationship.read != ALWAYS && positions[index] >= 0
      }
      require(selected) {
        when (relationship?.read) {
          ALWAYS -> "${property.path} is required; select its ${relationship.target().name} fields"
          else -> "${property.path} is required; select $field"
        }
      }
    }
}

private fun embeddedValueRead(properties: List<EntityProperty>, positions: IntArray, withinPrefix: String?) =
  withinPrefix == null || properties.indices.any { index ->
    positions[index] >= 0 && properties[index].path.startsWith(withinPrefix)
  }
