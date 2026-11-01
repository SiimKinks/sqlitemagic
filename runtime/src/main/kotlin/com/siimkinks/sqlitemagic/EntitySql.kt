package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.ConflictAlgorithm.Companion.CONFLICT_VALUES
import com.siimkinks.sqlitemagic.internal.MutableScatterMap

internal class EntitySql {
  private val insertSql = arrayOfNulls<String>(CONFLICT_VALUES.size)
  private val defaultUpdateSql = arrayOfNulls<String>(CONFLICT_VALUES.size)
  private val updateSql = MutableScatterMap<String, Array<String?>>(initialCapacity = 0)
  private val deleteOneSql = MutableScatterMap<String, String>(initialCapacity = 0)
  private var deleteTableSql: String? = null

  fun insert(template: String, conflictAlgorithm: Int) = insertSql.formatted(
    template = template,
    conflictAlgorithm = conflictAlgorithm
  )

  fun defaultUpdate(template: String, conflictAlgorithm: Int) = defaultUpdateSql.formatted(
    template = template,
    conflictAlgorithm = conflictAlgorithm
  )

  fun update(template: String, conflictAlgorithm: Int) =
    (updateSql[template] ?: arrayOfNulls<String>(CONFLICT_VALUES.size).also { updateSql[template] = it })
      .formatted(
        template = template,
        conflictAlgorithm = conflictAlgorithm
      )

  fun deleteOne(tableName: String, columnName: String) = deleteOneSql[columnName]
    ?: "DELETE FROM $tableName WHERE $columnName IN (?)".also { deleteOneSql[columnName] = it }

  fun deleteTable(tableName: String) = deleteTableSql ?: "DELETE FROM $tableName WHERE 1".also { deleteTableSql = it }

  private fun Array<String?>.formatted(template: String, conflictAlgorithm: Int) = this[conflictAlgorithm]
    ?: template
      .format(CONFLICT_VALUES[conflictAlgorithm])
      .also { this[conflictAlgorithm] = it }
}
