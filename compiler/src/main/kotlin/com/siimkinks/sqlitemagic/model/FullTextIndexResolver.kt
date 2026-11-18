package com.siimkinks.sqlitemagic.model

import com.siimkinks.sqlitemagic.SqlAffinity.INTEGER
import com.siimkinks.sqlitemagic.annotation.FullTextDetail.FULL
import com.siimkinks.sqlitemagic.annotation.FullTextIndex
import com.siimkinks.sqlitemagic.annotation.FullTextMatchinfo
import com.siimkinks.sqlitemagic.annotation.FullTextOrder.ASC
import com.siimkinks.sqlitemagic.annotation.FullTextRole.INDEXED
import com.siimkinks.sqlitemagic.annotation.FullTextRole.LANGUAGE_ID
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS4
import com.siimkinks.sqlitemagic.annotation.FullTextVersion.FTS5
import com.siimkinks.sqlitemagic.annotation.TableOption.TEMPORARY
import com.siimkinks.sqlitemagic.annotation.TableOption.WITHOUT_ROWID
import com.siimkinks.sqlitemagic.fulltext.FullTextColumnElement
import com.siimkinks.sqlitemagic.fulltext.FullTextIndexElement
import com.siimkinks.sqlitemagic.fulltext.fullTextIndexIdentity
import com.siimkinks.sqlitemagic.fulltext.fullTextIndexName
import com.siimkinks.sqlitemagic.utils.displayName
import java.util.Locale

internal class FullTextIndexResolver(
  private val reporter: ModelCollectionReporter
) {
  fun resolve(
    seed: TableSeed,
    table: TableElement
  ): List<FullTextIndexElement> {
    val modelName = seed.classDeclaration.displayName()
    val memberships = table.allColumns.flatMap { column -> column.fullText.map { column to it } }
    fun report(message: String) = reporter.error(
      message = message,
      symbol = seed.classDeclaration
    )
    fun describe(column: ColumnElement) = "$modelName.${column.access.path.displayName}"

    val declaredNames = seed.fullTextIndexes.map(FullTextIndex::value)
    val (members, undeclaredMemberships) = memberships.partition { (_, fullText) -> fullText.index in declaredNames }
    undeclaredMemberships
      .filterNot { (column, _) -> column.access.path.isNested }
      .forEach { (column, fullText) ->
        val index = when {
          fullText.index.isEmpty() -> "the unnamed @FullTextIndex"
          else -> "@FullTextIndex '${fullText.index}'"
        }
        report("@FullText refers to $index, which $modelName does not declare: ${describe(column)}")
      }
    memberships
      .groupBy { (column, fullText) -> column to fullText.index }
      .filterValues { it.size > 1 }
      .keys
      .forEach { (column, index) ->
        report("Duplicate @FullText of full-text index '${index.displayName(table)}': ${describe(column)}")
      }
    if (seed.fullTextIndexes.isEmpty()) return emptyList()
    if (WITHOUT_ROWID in table.options || TEMPORARY in table.options) {
      report("@FullTextIndex is not allowed in WITHOUT_ROWID or TEMPORARY tables: $modelName")
      return emptyList()
    }
    val idColumn = table.idColumn
    val rowIdColumn = table.rowIdColumn ?: idColumn?.takeIf { it.sqlStorageType.affinity == INTEGER }
    if (rowIdColumn == null) {
      when (idColumn) {
        null -> report(
          "A full-text index needs a stable row ID; a table without an @Id declares it as " +
              "`@Id val id: Long? = null`: $modelName"
        )
        else -> report(
          "A full-text index needs a stable row ID; an @Id that is not stored as INTEGER needs " +
              "`@RowId val rowId: Long? = null` beside it: ${describe(idColumn)}"
        )
      }
      return emptyList()
    }
    seed.fullTextIndexes
      .groupBy { it.displayName(table).lowercase(Locale.ROOT) }
      .values
      .filter { it.size > 1 }
      .forEach { report("Duplicate full-text index name '${it.first().displayName(table)}': $modelName") }
    return seed.fullTextIndexes.mapNotNull { index ->
      reporter.takeIfNoNewErrors {
        val name = index.displayName(table)
        val indexMembers = members.filter { (_, fullText) -> fullText.index == index.value }
        val languageIds = indexMembers.filter { (_, fullText) -> fullText.role == LANGUAGE_ID }
        val reservedNames = when (index.version) {
          FTS5 -> {
            listOfNotNull(
              "matchinfo".takeIf { index.matchinfo != FullTextMatchinfo.FTS4 },
              "order".takeIf { index.order != ASC }
            ).forEach { option ->
              report("FTS5 index '$name' cannot use the FTS4 option $option: $modelName")
            }
            languageIds.forEach { (column, _) ->
              report("FTS5 index '$name' cannot use the FTS4 role LANGUAGE_ID: ${describe(column)}")
            }
            setOf("rank", "rowid")
          }
          FTS4 -> {
            listOfNotNull(
              "detail".takeIf { index.detail != FULL },
              "columnSize".takeUnless { index.columnSize }
            ).forEach { option ->
              report("FTS4 index '$name' cannot use the FTS5 option $option: $modelName")
            }
            if (languageIds.size > 1) {
              report("FTS4 index '$name' has more than one LANGUAGE_ID column: $modelName")
            }
            table.allColumns
              .filter { column ->
                column != rowIdColumn && column.columnName.equals(other = "rowid", ignoreCase = true)
              }
              .forEach { column ->
                report(
                  "Column name '${column.columnName}' hides the row ID that FTS4 index '$name' reads: " +
                      describe(column)
                )
              }
            setOf("docid")
          }
        } + name.lowercase(Locale.ROOT)
        indexMembers
          .filter { (column, _) -> column.columnName.lowercase(Locale.ROOT) in reservedNames }
          .forEach { (column, _) ->
            report(
              "Column name '${column.columnName}' is reserved in ${index.version} index '$name': ${describe(column)}"
            )
          }
        when {
          indexMembers.isEmpty() -> report("Full-text index '$name' has no @FullText column: $modelName")
          indexMembers.none { (_, fullText) -> fullText.role == INDEXED } -> report(
            "Full-text index '$name' has no INDEXED column: $modelName"
          )
        }
        FullTextIndexElement(
          identity = fullTextIndexIdentity(name),
          tableName = table.tableName,
          rowIdColumnName = rowIdColumn.columnName,
          version = index.version,
          columns = indexMembers.map { (column, fullText) ->
            FullTextColumnElement(
              columnName = column.columnName,
              role = fullText.role
            )
          },
          tokenize = index.tokenize,
          prefix = index.prefix.toList(),
          detail = index.detail,
          columnSize = index.columnSize,
          matchinfo = index.matchinfo,
          order = index.order
        )
      }
    }
  }
}

private fun FullTextIndex.displayName(table: TableElement) = value.displayName(table)

private fun String.displayName(table: TableElement) = fullTextIndexName(
  tableName = table.tableName,
  value = this
)
