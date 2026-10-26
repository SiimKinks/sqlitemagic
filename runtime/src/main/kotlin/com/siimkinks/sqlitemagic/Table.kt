@file:Suppress("FunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.annotation.CheckResult
import com.siimkinks.sqlitemagic.SqliteObjectKind.TABLE
import com.siimkinks.sqlitemagic.SqliteObjectKind.VIEW
import com.siimkinks.sqlitemagic.internal.EntityProperty
import com.siimkinks.sqlitemagic.internal.GeneratedView
import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.SqlUtil.quoteSqlIdentifier
import com.siimkinks.sqlitemagic.internal.SqliteSchema.MAIN
import com.siimkinks.sqlitemagic.internal.SqliteSchema.TEMPORARY
import com.siimkinks.sqlitemagic.internal.SqliteSchemaIdentity
import com.siimkinks.sqlitemagic.internal.TableRelationship
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Metadata of a table or view in a database.
 *
 * @param T Model type
 */
open class Table<T> @InternalSqliteMagicApi protected constructor(
  internal val name: String,
  internal val alias: String?,
  private val generatedView: GeneratedView? = null,
  internal val temporary: Boolean = false
) : Source {
  internal val objectIdentity = generatedView
    ?.identity
    ?: SqliteSchemaIdentity.from(
      schema = when {
        temporary -> TEMPORARY
        else -> MAIN
      },
      rawName = name
    )

  private val querySource = SqliteQuerySource(
    identity = objectIdentity,
    kind = when (generatedView) {
      null -> TABLE
      else -> VIEW
    }
  )

  internal val referenceName get() = alias ?: name

  internal open val sqlQualifier = quoteSqlIdentifier(referenceName)

  internal open fun qualifier(context: SqlRenderingContext) = sqlQualifier

  internal val sqlSource = buildString {
    append(quoteSqlIdentifier(objectIdentity.schema.qualifier))
    append('.')
    append(quoteSqlIdentifier(name))
    if (alias != null) {
      append(" AS ")
      append(quoteSqlIdentifier(alias))
    }
  }

  internal open fun renderSource(context: SqlRenderingContext) {
    context.sql.append(sqlSource)
    addDependencies(context.dependencies)
  }

  internal open val isPhysical get() = true

  internal open fun addDependencies(destination: QueryDependencies.Builder) {
    destination.addSource(querySource)
    if (generatedView != null) {
      destination.addObservedTables(generatedView.definition.queryDependencies.observedTables)
    }
  }

  @InternalSqliteMagicApi
  protected open val entityFields: List<Column<T, *, *, *, *, *, *>>? = null

  @InternalSqliteMagicApi
  protected open val relationships: List<TableRelationship>
    get() = emptyList()

  @InternalSqliteMagicApi
  protected open fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?): T =
    throw UnsupportedOperationException("$name has no generated entity reader")

  @InternalSqliteMagicApi
  protected open val entityProperties: List<EntityProperty>? = null

  /**
   * [positions] holds the column position of each entity field, or -1 when the field is not selected.
   */
  @InternalSqliteMagicApi
  protected open fun readPartialEntity(cursor: Cursor, positions: IntArray, related: RelatedEntities?): T =
    throw UnsupportedOperationException("$name has no generated partial reader")

  internal val partialProperties get() = entityProperties

  internal fun readPartialEntityAt(cursor: Cursor, positions: IntArray, related: RelatedEntities?) = readPartialEntity(
    cursor = cursor,
    positions = positions,
    related = related
  )

  internal fun selectedEntityFields() = checkNotNull(entityFields) { "$name has no generated entity reader" }

  /**
   * All columns of this table.
   *
   * Use it to select all columns of this table together with other values.
   *
   * Example:
   * ```
   * SELECT
   *     .COLUMNS(BOOK.ID, AUTHOR.all())
   *     .FROM(BOOK)
   * ```
   *
   * @return All columns of this table
   */
  fun all() = TableFields(selectedEntityFields())

  internal val tableRelationships get() = relationships

  internal val viewDefinition get() = generatedView?.definition

  internal open val isDefaultHandle get() = alias == null

  internal fun readEntityAt(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = readEntity(
    cursor = cursor,
    startIndex = startIndex,
    related = related
  )

  /**
   * Create an alias for this table.
   *
   * Example:
   * ```
   * val peer = AUTHOR AS "peer"
   * ```
   *
   * @param alias The alias name
   * @return New table with provided alias
   */
  @CheckResult
  open infix fun AS(alias: String) = Table<T>(
    name = name,
    alias = alias,
    generatedView = generatedView,
    temporary = temporary
  )

  override fun toString() = name
}
