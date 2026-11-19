package com.siimkinks.sqlitemagic.fulltext

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
import com.siimkinks.sqlitemagic.internal.SqliteSchemaKey
import com.siimkinks.sqlitemagic.manager.normalizedSqlIdentifier
import com.siimkinks.sqlitemagic.model.ColumnElement
import com.siimkinks.sqlitemagic.model.ModelCollectionReporter
import com.siimkinks.sqlitemagic.model.TableSeed
import com.siimkinks.sqlitemagic.schema.SchemaIdentityOwner
import com.siimkinks.sqlitemagic.schema.SchemaIdentityRegistry
import com.siimkinks.sqlitemagic.schema.sqliteIdentifierProblem
import com.siimkinks.sqlitemagic.utils.displayName

internal class FullTextIndexResolver(
  private val reporter: ModelCollectionReporter
) {
  fun validateNames(
    tableSeeds: Collection<TableSeed>,
    accepted: SchemaIdentityRegistry
  ) {
    val owners = mutableMapOf<SqliteSchemaKey, SchemaIdentityOwner>()
    tableSeeds.forEach { seed ->
      owners.getOrPut(seed.normalizedKey) {
        SchemaIdentityOwner(
          kind = "table",
          rawName = seed.tableName,
          identity = seed.identity,
          typeKey = seed.typeKey
        )
      }
    }
    for (seed in tableSeeds) {
      val indexesByKey = seed.fullTextIndexes.groupBy { index ->
        fullTextIndexIdentity(seed.fullTextIndexName(index.value)).normalizedKey
      }
      indexesByKey.values
        .filter { it.size > 1 }
        .forEach { indexes ->
          reporter.error(
            message = "Duplicate full-text index name '${seed.fullTextIndexName(indexes.first().value)}': " +
                seed.classDeclaration.displayName(),
            symbol = seed.classDeclaration
          )
        }
      for (index in indexesByKey.values.map(List<FullTextIndex>::first)) {
        val name = seed.fullTextIndexName(index.value)
        val problem = fullTextIndexIdentity(name).sqliteIdentifierProblem(kind = "Full-text index")
        if (problem != null) {
          reporter.error(
            message = problem,
            symbol = seed.classDeclaration
          )
          continue
        }
        fullTextSchemaOwners(
          name = name,
          shadowTableNames = fullTextShadowTableNames(
            name = name,
            version = index.version,
            columnSize = index.columnSize,
            matchinfo = index.matchinfo
          ),
          typeKey = seed.typeKey
        ).forEach { owner ->
          val key = owner.normalizedKey
          val existing = owners[key] ?: accepted.lookup(key)?.takeIf { it.typeKey != seed.typeKey }
          when (existing) {
            null -> owners[key] = owner
            else -> reporter.error(
              message = "${owner.kind.replaceFirstChar(Char::uppercaseChar)} name '${owner.rawName}' " +
                  "conflicts with ${existing.kind} name '${existing.rawName}': ${seed.qualifiedName}",
              symbol = seed.classDeclaration
            )
          }
        }
      }
    }
  }

  fun resolve(
    seed: TableSeed,
    columns: List<ColumnElement>
  ): List<FullTextIndexElement> {
    val modelName = seed.classDeclaration.displayName()
    val memberships = columns.flatMap { column -> column.fullTextMemberships.map { column to it } }
    fun report(message: String) = reporter.error(
      message = message,
      symbol = seed.classDeclaration
    )
    fun describe(column: ColumnElement) = "$modelName.${column.access.path.displayName}"

    val declaredNames = seed.fullTextIndexes.map(FullTextIndex::value)
    val (members, undeclaredMemberships) = memberships.partition { (_, membership) ->
      membership.index in declaredNames
    }
    undeclaredMemberships
      .filterNot { (column, _) -> column.access.path.isNested }
      .forEach { (column, membership) ->
        val index = when {
          membership.index.isEmpty() -> "the unnamed @FullTextIndex"
          else -> "@FullTextIndex '${membership.index}'"
        }
        report("@FullText refers to $index, which $modelName does not declare: ${describe(column)}")
      }
    memberships
      .groupBy { (column, membership) -> column to membership.index }
      .filterValues { it.size > 1 }
      .keys
      .forEach { (column, index) ->
        report("Duplicate @FullText of full-text index '${seed.fullTextIndexName(index)}': ${describe(column)}")
      }
    if (seed.fullTextIndexes.isEmpty()) return emptyList()
    if (WITHOUT_ROWID in seed.options || TEMPORARY in seed.options) {
      report("@FullTextIndex is not allowed in WITHOUT_ROWID or TEMPORARY tables: $modelName")
      return emptyList()
    }
    val idColumn = columns.singleOrNull(ColumnElement::isId)
    val rowIdColumn = columns.singleOrNull(ColumnElement::isRowId)
      ?: idColumn?.takeIf { it.sqlStorageType.affinity == INTEGER }
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
    return seed.fullTextIndexes.mapNotNull { index ->
      reporter.takeIfNoNewErrors {
        val name = seed.fullTextIndexName(index.value)
        val indexMembers = members.filter { (_, membership) -> membership.index == index.value }
        val languageIds = indexMembers.filter { (_, membership) -> membership.role == LANGUAGE_ID }
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
            setOf("docid", "rowid")
          }
        } + name.normalizedSqlIdentifier()
        indexMembers
          .filter { (column, _) -> column.columnName.normalizedSqlIdentifier() in reservedNames }
          .forEach { (column, _) ->
            report(
              "Column name '${column.columnName}' is reserved in ${index.version} index '$name': ${describe(column)}"
            )
          }
        when {
          indexMembers.isEmpty() -> report("Full-text index '$name' has no @FullText column: $modelName")
          indexMembers.none { (_, membership) -> membership.role == INDEXED } -> report(
            "Full-text index '$name' has no INDEXED column: $modelName"
          )
        }
        FullTextIndexElement(
          identity = fullTextIndexIdentity(name),
          tableName = seed.tableName,
          rowIdColumnName = rowIdColumn.columnName,
          version = index.version,
          columns = indexMembers.map { (column, membership) ->
            FullTextColumnElement(
              columnName = column.columnName,
              role = membership.role
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
