package com.siimkinks.sqlitemagic.internal

import androidx.sqlite.SQLiteStatement
import com.siimkinks.sqlitemagic.Table
import com.siimkinks.sqlitemagic.internal.ContainerHelpers.EmptyIntArray
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier

@InternalSqliteMagicApi
enum class RelationshipRead {
  /** Built from the stored ID, never joined. */
  ID_ONLY,

  /** Joined by `queryDeep()`, otherwise built from the stored ID. */
  DEEP,

  /** Always joined, since the target cannot be built from its ID. */
  ALWAYS
}

@InternalSqliteMagicApi
class TableRelationship(
  internal val columnName: String,
  internal val target: () -> Table<*>,
  internal val targetIdColumnName: String,
  internal val nullable: Boolean,
  internal val read: RelationshipRead
) {
  internal val quotedColumnName = quoteSqlIdentifier(columnName)
  internal val quotedTargetIdColumnName = quoteSqlIdentifier(targetIdColumnName)
}

/**
 * Relationships that one entity read joined. Generated readers read the joined relationships with [read] and build the
 * others from their stored IDs.
 */
@InternalSqliteMagicApi
class RelatedEntities internal constructor(
  internal val table: Table<*>,
  private val children: Array<RelatedEntities?>,
  internal val startIndex: Int = 0,
  internal val width: Int = 0,
  internal val positions: IntArray = EmptyIntArray,
  internal val joined: List<RelatedEntities> = emptyList(),
  private val idOffset: Int = NO_ID
) {
  /**
   * Read a related entity that this read joined.
   *
   * @param statement Statement positioned on the row
   * @param relationship Position of the relationship in the table's relationships
   * @return Related entity, or `null` when this read did not join [relationship] or the joined row is missing
   */
  fun read(statement: SQLiteStatement, relationship: Int): Any? {
    val child = children[relationship] ?: return null
    return when {
      child.idOffset != NO_ID && statement.isNull(child.startIndex + child.idOffset) -> null
      else -> child.table.readEntityAt(
        statement = statement,
        startIndex = child.startIndex,
        related = child
      )
    }
  }

  /**
   * Read a related entity from the selected columns of a partial read.
   *
   * @param statement Statement positioned on the row
   * @param relationship Position of the relationship in the table's relationships
   * @return Related entity, or `null` when none of the columns of [relationship] are selected or all of them are NULL
   */
  fun readSelected(statement: SQLiteStatement, relationship: Int): Any? {
    val child = children[relationship] ?: return null
    val positions = child.positions
    return when {
      positions.any { it >= 0 && !statement.isNull(it) } -> child.table.readPartialEntityAt(
        statement = statement,
        positions = positions,
        related = child
      )
      else -> null
    }
  }

  /**
   * Read a related entity that this read joined optionally.
   *
   * @param statement Statement positioned on the row
   * @param relationship Position of the relationship in the table's relationships
   * @return Related entity, or `null` when all columns of [relationship] are NULL
   */
  fun readOrNull(statement: SQLiteStatement, relationship: Int): Any? {
    val child = children[relationship] ?: return null
    val start = child.startIndex
    for (index in start..<start + child.width) {
      if (!statement.isNull(index)) {
        return child.table.readEntityAt(
          statement = statement,
          startIndex = start,
          related = child
        )
      }
    }
    return null
  }

  internal fun setChild(relationship: Int, child: RelatedEntities) {
    children[relationship] = child
  }

  internal fun shifted(offset: Int): RelatedEntities = RelatedEntities(
    table = table,
    children = Array(children.size) { children[it]?.shifted(offset) },
    startIndex = startIndex + offset,
    width = width,
    idOffset = idOffset
  )
}

private const val NO_ID = -1

/**
 * When [within] is the path of an optional embedded value that contains the property, the property is required
 * only when one of that value's columns is selected.
 */
@InternalSqliteMagicApi
class EntityProperty(
  internal val path: String,
  internal val required: Boolean,
  within: String? = null
) {
  internal val withinPrefix = within?.let { "$it." }
}
