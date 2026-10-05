package com.siimkinks.sqlitemagic.migration.testing

import androidx.sqlite.SQLiteConnection

internal data class InspectedSchema(
  val tables: Map<String, InspectedTable>
)

internal data class InspectedTable(
  val kind: String,
  val withoutRowId: Boolean,
  val strict: Boolean,
  val columns: List<InspectedColumn>,
  val rowIdAlias: String?,
  val clauses: List<String>,
  val foreignKeys: List<List<InspectedForeignKeyColumn>>,
  val indices: List<InspectedIndex>
)

internal data class InspectedColumn(
  val name: String,
  val declaredType: String,
  val notNull: Boolean,
  val defaultExpression: String?,
  val primaryKeyPosition: Long,
  val hidden: Long
)

internal data class InspectedIndex(
  val name: String?,
  val unique: Boolean,
  val origin: String,
  val partial: Boolean,
  val columns: List<InspectedIndexColumn>,
  val definition: String?
)

internal data class InspectedForeignKeyColumn(
  val sequence: Long,
  val targetTable: String,
  val sourceColumn: String,
  val targetColumn: String?,
  val onUpdate: String,
  val onDelete: String,
  val match: String
)

internal data class InspectedIndexColumn(
  val sequence: Long,
  val columnId: Long,
  val name: String?,
  val descending: Long,
  val collation: String?,
  val key: Long
)

internal object SchemaIntrospector {
  fun inspect(connection: SQLiteConnection): InspectedSchema {
    val objects = query(
      connection = connection,
      sql = "SELECT type, name, tbl_name, sql FROM main.sqlite_schema WHERE type IN ('table', 'index')"
    ).filter { it.text("name").isApplicationObject() }
    val tableList = query(
      connection = connection,
      sql = "PRAGMA main.table_list"
    )
    requireInspection(
      condition = tableList.isNotEmpty() && tableList.all { "wr" in it && "strict" in it },
      message = "SQLite driver lacks required PRAGMA table_list capabilities"
    )
    val tableObjects = objects.filter { it.text("type") == "table" }
    val rawColumns = tableObjects
      .associate { objectRow ->
        val name = objectRow.text("name")
        name.sqliteIdentifier() to query(
          connection = connection,
          sql = "PRAGMA main.table_xinfo(${identifier(name)})"
        )
      }
    val tables = tableObjects
      .sortedBy { it.text("name") }
      .associate { objectRow ->
        val name = objectRow.text("name")
        val identifiers = rawColumns
          .getValue(name.sqliteIdentifier())
          .map { it.text("name").sqliteIdentifier() }
          .toSet()
        val metadata = tableList
          .singleOrNull { it["schema"] == "main" && it["name"] == name }
          ?: throw SchemaInspectionException("Missing table_list metadata for table $name")
        requireInspection(
          condition = metadata.text("type") == "table",
          message = "Unsupported table kind for $name: ${metadata["type"]}"
        )
        val columns = rawColumns
          .getValue(name.sqliteIdentifier())
          .map { column ->
            requireInspection(
              condition = setOf("cid", "name", "type", "notnull", "dflt_value", "pk", "hidden")
                .all(column::containsKey),
              message = "Incomplete table_xinfo metadata for $name"
            )
            InspectedColumn(
              name = column.text("name").sqliteIdentifier(),
              declaredType = column.text("type").sqliteIdentifier(),
              notNull = column.number("notnull") != 0L,
              defaultExpression = (column["dflt_value"] as String?)?.let {
                SchemaSql.canonical(
                  tokens = SchemaSql.tokens(it),
                  identifiers = emptySet(),
                  preserveQuotedValues = true
                )
              },
              primaryKeyPosition = column.number("pk"),
              hidden = column.number("hidden")
            )
          }
        val indices = query(
          connection = connection,
          sql = "PRAGMA main.index_list(${identifier(name)})"
        )
          .map { index ->
            inspectIndex(
              connection = connection,
              index = index,
              objects = objects,
              identifiers = identifiers
            )
          }
          .sortedWith(
            compareBy(
              InspectedIndex::origin,
              InspectedIndex::name,
              InspectedIndex::toString
            )
          )
        val primaryColumns = columns.filter { it.primaryKeyPosition != 0L }
        val rowIdAlias = primaryColumns
          .singleOrNull()
          ?.takeIf { primaryColumn ->
            primaryColumn.declaredType == "integer" &&
                metadata.number("wr") == 0L
                && indices.none { it.origin == "pk" }
          }
          ?.name
        val foreignKeys = query(
          connection = connection,
          sql = "PRAGMA main.foreign_key_list(${identifier(name)})"
        )
          .map(::inspectForeignKey)
          .groupBy(ForeignKeyRow::id)
          .values
          .map { group ->
            group
              .map(ForeignKeyRow::column)
              .sortedBy(InspectedForeignKeyColumn::sequence)
          }
          .sortedBy(List<InspectedForeignKeyColumn>::toString)
        name.sqliteIdentifier() to InspectedTable(
          kind = metadata.text("type"),
          withoutRowId = metadata.number("wr") != 0L,
          strict = metadata.number("strict") != 0L,
          columns = columns,
          rowIdAlias = rowIdAlias,
          clauses = SchemaSql.tableClauses(
            sql = objectRow.text("sql"),
            identifiers = identifiers
          ),
          foreignKeys = foreignKeys,
          indices = indices
        )
      }
    return InspectedSchema(tables)
  }

  private fun inspectIndex(
    connection: SQLiteConnection,
    index: Map<String, Any?>,
    objects: List<Map<String, Any?>>,
    identifiers: Set<String>
  ): InspectedIndex {
    requireInspection(
      condition = setOf("name", "unique", "origin", "partial").all(index::containsKey),
      message = "Incomplete index_list metadata"
    )
    val name = index.text("name")
    val origin = index.text("origin")
    val columns = query(
      connection = connection,
      sql = "PRAGMA main.index_xinfo(${identifier(name)})"
    )
      .map { column ->
        requireInspection(
          condition = setOf("seqno", "cid", "name", "desc", "coll", "key").all(column::containsKey),
          message = "Incomplete index_xinfo metadata for $name"
        )
        InspectedIndexColumn(
          sequence = column.number("seqno"),
          columnId = column.number("cid"),
          name = column.nullableText("name")?.sqliteIdentifier(),
          descending = column.number("desc"),
          collation = column.nullableText("coll")?.sqliteIdentifier(),
          key = column.number("key")
        )
      }
    val sql = objects
      .singleOrNull { it["type"] == "index" && it["name"] == name }
      ?.get("sql") as String?
    requireInspection(
      condition = origin != "c" || sql != null,
      message = "Missing defining SQL for explicit index $name"
    )
    return InspectedIndex(
      name = name
        .sqliteIdentifier()
        .takeIf { origin == "c" },
      unique = index.number("unique") != 0L,
      origin = origin,
      partial = index.number("partial") != 0L,
      columns = columns,
      definition = sql?.let {
        SchemaSql.indexDefinition(
          sql = it,
          identifiers = identifiers
        )
      }
    )
  }

  private fun inspectForeignKey(row: Map<String, Any?>): ForeignKeyRow {
    requireInspection(
      condition = setOf("id", "seq", "table", "from", "to", "on_update", "on_delete", "match")
        .all(row::containsKey),
      message = "Incomplete foreign_key_list metadata"
    )
    return ForeignKeyRow(
      id = row.number("id"),
      column = InspectedForeignKeyColumn(
        sequence = row.number("seq"),
        targetTable = row.text("table").sqliteIdentifier(),
        sourceColumn = row.text("from").sqliteIdentifier(),
        targetColumn = row.nullableText("to")?.sqliteIdentifier(),
        onUpdate = row.text("on_update").sqliteIdentifier(),
        onDelete = row.text("on_delete").sqliteIdentifier(),
        match = row.text("match").sqliteIdentifier()
      )
    )
  }

  private data class ForeignKeyRow(
    val id: Long,
    val column: InspectedForeignKeyColumn
  )

  private fun query(
    connection: SQLiteConnection,
    sql: String
  ) = SQLiteQueries
    .query(
      connection = connection,
      sql = sql
    )
    .records()

  private fun identifier(value: String) = "\"" + value.replace(
    oldValue = "\"",
    newValue = "\"\""
  ) + "\""

  private fun String.isApplicationObject() = !startsWith("sqlite_") && this != "android_metadata"

  private fun Map<String, Any?>.text(key: String): String =
    get(key) as? String
      ?: throw SchemaInspectionException("Missing text metadata '$key'")

  private fun Map<String, Any?>.nullableText(key: String): String? {
    val value = get(key)
    requireInspection(
      condition = containsKey(key) && (value == null || value is String),
      message = "Missing or invalid nullable text metadata '$key'"
    )
    return value as String?
  }

  private fun Map<String, Any?>.number(key: String): Long =
    get(key) as? Long
      ?: throw SchemaInspectionException("Missing integer metadata '$key'")
}
