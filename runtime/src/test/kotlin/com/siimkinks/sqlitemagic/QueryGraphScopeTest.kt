package com.siimkinks.sqlitemagic

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteStatement
import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.StringArraySet
import org.junit.Test
import org.mockito.kotlin.mock

internal class QueryGraphScopeTest {
  @Test
  fun `visit records aliased root when contributor is empty`() {
    val rootTable = testTable<Any>(name = "root", alias = "root_alias")
    val tableGraphNodeNames = SimpleArrayMap<String, String>()
    val scope = queryGraphScope(
      rootTable = rootTable,
      tableGraphNodeNames = tableGraphNodeNames
    )

    scope.visit(
      table = rootTable,
      tableAlias = rootTable,
      nodeName = ""
    )

    assertThat(tableGraphNodeNames[""])
      .isEqualTo("root_alias")
  }

  @Test
  fun `visit dispatches selected graph contributor with separate table occurrence`() {
    var deepCall: List<Any?>? = null
    var shallowCall: List<Any?>? = null
    val behaviorTable = testTable<Any>(
      name = "child",
      addDeepQueryParts = { table, tableAlias, nodeName ->
        deepCall = listOf(table, tableAlias, nodeName)
      },
      addShallowQueryParts = { table, tableAlias, nodeName ->
        shallowCall = listOf(table, tableAlias, nodeName)
      }
    )
    val tableOccurrence = testTable<Any>(name = "child", alias = "child_alias")

    queryGraphScope(
      rootTable = behaviorTable,
      queryDeep = true
    ).visit(
      table = behaviorTable,
      tableAlias = tableOccurrence,
      nodeName = "deep"
    )
    queryGraphScope(
      rootTable = behaviorTable,
      queryDeep = false
    ).visit(
      table = behaviorTable,
      tableAlias = tableOccurrence,
      nodeName = "shallow"
    )

    assertThat(deepCall)
      .containsExactly(behaviorTable, tableOccurrence, "deep")
      .inOrder()
    assertThat(shallowCall)
      .containsExactly(behaviorTable, tableOccurrence, "shallow")
      .inOrder()
  }

  @Test
  fun `first canonical occurrence uses canonical table`() {
    val rootTable = testTable<Any>(name = "root")
    val canonicalTable = testTable<Any>(name = "child")
    val context = queryGraphScope(rootTable)

    val actual = context.tableForAutomaticJoin(canonicalTable)

    assertThat(actual)
      .isSameInstanceAs(canonicalTable)
    assertTable(
      table = actual,
      expectedName = "child",
      expectedAlias = null,
      expectedNameInQuery = "child"
    )
  }

  @Test
  fun `repeated canonical table uses deterministic aliases`() {
    val rootTable = testTable<Any>(name = "root")
    val canonicalTable = testTable<Any>(name = "child")
    val context = queryGraphScope(rootTable)

    val first = context.tableForAutomaticJoin(canonicalTable)
    val second = context.tableForAutomaticJoin(canonicalTable)
    val third = context.tableForAutomaticJoin(canonicalTable)

    assertThat(listOf(first.nameInQuery, second.nameInQuery, third.nameInQuery))
      .containsExactly("child", "sm_0", "sm_1")
      .inOrder()
    assertTable(
      table = second,
      expectedName = "child",
      expectedAlias = "sm_0",
      expectedNameInQuery = "sm_0"
    )
    assertTable(
      table = third,
      expectedName = "child",
      expectedAlias = "sm_1",
      expectedNameInQuery = "sm_1"
    )
  }

  @Test
  fun `self join avoids root identifier`() {
    val rootTable = testTable<Any>(name = "node")
    val context = queryGraphScope(rootTable)

    val actual = context.tableForAutomaticJoin(rootTable)

    assertTable(
      table = actual,
      expectedName = "node",
      expectedAlias = "sm_0",
      expectedNameInQuery = "sm_0"
    )
    assertTable(
      table = rootTable,
      expectedName = "node",
      expectedAlias = null,
      expectedNameInQuery = "node"
    )
  }

  @Test
  fun `aliased root leaves its physical canonical name available`() {
    val rootTable = testTable<Any>(name = "node", alias = "root_alias")
    val canonicalTable = testTable<Any>(name = "node")
    val context = queryGraphScope(rootTable)

    val actual = context.tableForAutomaticJoin(canonicalTable)

    assertThat(actual)
      .isSameInstanceAs(canonicalTable)
    assertTable(
      table = actual,
      expectedName = "node",
      expectedAlias = null,
      expectedNameInQuery = "node"
    )
  }

  @Test
  fun `existing unaliased join reserves target physical name`() {
    val rootTable = testTable<Any>(name = "root")
    val existingJoin = testTable<Any>(name = "target")
    val context = queryGraphScope(rootTable, JoinClause(existingJoin, "", null))

    val actual = context.tableForAutomaticJoin(testTable<Any>(name = "target"))

    assertTable(
      table = actual,
      expectedName = "target",
      expectedAlias = "sm_0",
      expectedNameInQuery = "sm_0"
    )
  }

  @Test
  fun `ordinary user alias equal to target canonical name is reserved`() {
    val rootTable = testTable<Any>(name = "root")
    val userAlias = testTable<Any>(name = "other", alias = "target")
    val context = queryGraphScope(rootTable, JoinClause(userAlias, "", null))

    val actual = context.tableForAutomaticJoin(testTable<Any>(name = "target"))

    assertTable(
      table = actual,
      expectedName = "target",
      expectedAlias = "sm_0",
      expectedNameInQuery = "sm_0"
    )
  }

  @Test
  fun `pre-reserved aliases advance to the lowest available alias`() {
    val rootTable = testTable<Any>(name = "target")
    val smZero = testTable<Any>(name = "other_zero", alias = "sm_0")
    val smOne = testTable<Any>(name = "other_one", alias = "sm_1")
    val context = queryGraphScope(
      rootTable,
      JoinClause(smZero, "", null),
      JoinClause(smOne, "", null)
    )

    val actual = context.tableForAutomaticJoin(testTable<Any>(name = "target"))

    assertTable(
      table = actual,
      expectedName = "target",
      expectedAlias = "sm_2",
      expectedNameInQuery = "sm_2"
    )
  }

  @Test
  fun `user alias sm zero is reserved`() {
    val rootTable = testTable<Any>(name = "root")
    val userAlias = testTable<Any>(name = "other", alias = "sm_0")
    val context = queryGraphScope(rootTable, JoinClause(userAlias, "", null))

    val actual = context.tableForAutomaticJoin(rootTable)

    assertTable(
      table = actual,
      expectedName = "root",
      expectedAlias = "sm_1",
      expectedNameInQuery = "sm_1"
    )
  }

  @Test
  fun `context owns join lookup and mutation for a select`() {
    val rootTable = testTable<Any>(name = "root")
    val joinedTable = testTable<Any>(name = "joined")
    val from = Select.from(rootTable)
    val context = queryGraphScope(from)
    val join = JoinClause(joinedTable, "", null)

    assertThat(
      context.findJoin(
        table = joinedTable,
        joinedOnColumn = rootTable.all()
      )
    ).isNull()

    context.addJoin(join)

    assertThat(
      context.findJoin(
        table = joinedTable,
        joinedOnColumn = rootTable.all()
      )
    ).isSameInstanceAs(joinedTable)
    assertThat(context.tableForAutomaticJoin(joinedTable).nameInQuery)
      .isEqualTo("sm_0")
  }

  @Test
  fun `rebindColumn preserves source behavior and recomputes query name`() {
    val sourceTable = testTable<Any>(name = "source")
    val targetTable = testTable<Any>(name = "target", alias = "joined")
    val source = object : Column<String, String, CharSequence, Any, NotNullable>(
      table = sourceTable,
      name = "id",
      allFromTable = false,
      valueParser = Utils.STRING_PARSER,
      nullable = false,
      alias = null
    ) {
      override fun toSqlArg(value: String) = "encoded:$value"

      @Suppress("UNCHECKED_CAST")
      override fun <V> getFromCursor(cursor: Cursor): V? = "cursor-value" as V?

      @Suppress("UNCHECKED_CAST")
      override fun <V> getFromStatement(statement: SupportSQLiteStatement): V? = "statement-value" as V?
    }

    val rebound = queryGraphScope(sourceTable).rebindColumn(
      newTable = targetTable,
      column = source
    )

    assertThat(rebound.table).isSameInstanceAs(targetTable)
    assertThat(rebound.nameInQuery).isEqualTo("joined.id")
    assertThat(rebound.toSqlArg("value")).isEqualTo("encoded:value")
    assertThat(rebound.getFromCursor<String>(mock())).isEqualTo("cursor-value")
    assertThat(rebound.getFromStatement<String>(mock())).isEqualTo("statement-value")
  }

  @Test
  fun `automatic table occurrence keeps current rename map behavior`() {
    val rootTable = testTable<Any>(name = "root")
    val aliasedTable = testTable<Any>(name = "child", alias = "sm_0")
    val context = queryGraphScope(
      rootTable = rootTable,
      selectFromTables = StringArraySet()
    )

    context.recordAutomaticTableOccurrence(aliasedTable)

    val renamedTables = checkNotNull(context.renamedTablesOrNull())
    assertThat(renamedTables[aliasedTable.name])
      .containsExactly("sm_0")
  }

  @Test
  fun `existing table occurrence records graph node without automatic rename`() {
    val rootTable = testTable<Any>(name = "root")
    val existingJoin = testTable<Any>(name = "child", alias = "joined_child")
    val tableGraphNodeNames = SimpleArrayMap<String, String>()
    val context = queryGraphScope(
      rootTable = rootTable,
      selectFromTables = StringArraySet(),
      tableGraphNodeNames = tableGraphNodeNames
    )

    context.visit(
      table = testTable<Any>(name = "child"),
      tableAlias = existingJoin,
      nodeName = "child"
    )

    assertThat(tableGraphNodeNames["child"]).isEqualTo("joined_child")
    assertThat(context.renamedTablesOrNull()).isNull()
  }

  @Test
  fun `addLeftJoin registers a left join and reserves its table`() {
    val rootTable = testTable<Any>(name = "root")
    val joinedTable = testTable<Any>(name = "joined")
    val from = Select.from(rootTable)
    val context = queryGraphScope(from)

    context.addLeftJoin(
      table = joinedTable,
      on = rootTable.all().toExpr()
    )

    assertThat(
      context.findJoin(
        table = joinedTable,
        joinedOnColumn = rootTable.all()
      )
    ).isSameInstanceAs(joinedTable)
    assertThat(context.tableForAutomaticJoin(joinedTable).nameInQuery)
      .isEqualTo("sm_0")

    val sql = StringBuilder()
    from.appendSql(sql)
    assertThat(sql.toString()).contains("LEFT JOIN joined")
  }

  @Test
  fun `independent contexts produce identical aliases`() {
    val firstRoot = testTable<Any>(name = "root")
    val secondRoot = testTable<Any>(name = "root")
    val firstCanonical = testTable<Any>(name = "child")
    val secondCanonical = testTable<Any>(name = "child")
    val firstContext = queryGraphScope(firstRoot)
    val secondContext = queryGraphScope(secondRoot)

    val firstNames = listOf(
      firstContext.tableForAutomaticJoin(firstCanonical).nameInQuery,
      firstContext.tableForAutomaticJoin(firstCanonical).nameInQuery
    )
    val secondNames = listOf(
      secondContext.tableForAutomaticJoin(secondCanonical).nameInQuery,
      secondContext.tableForAutomaticJoin(secondCanonical).nameInQuery
    )

    assertThat(firstNames)
      .containsExactlyElementsIn(secondNames)
      .inOrder()
    assertThat(firstNames)
      .containsExactly("child", "sm_0")
      .inOrder()
  }

  private fun queryGraphScope(
    rootTable: Table<*>,
    vararg joins: JoinClause,
    selectFromTables: StringArraySet? = null,
    tableGraphNodeNames: SimpleArrayMap<String, String>? = null,
    queryDeep: Boolean = true,
    select1: Boolean = false
  ): QueryGraphScope {
    val from = Select.from(rootTable)
    joins.forEach(from::join)
    return queryGraphScope(
      from = from,
      selectFromTables = selectFromTables,
      tableGraphNodeNames = tableGraphNodeNames,
      queryDeep = queryDeep,
      select1 = select1
    )
  }

  private fun queryGraphScope(
    from: Select.From<*, *, *, *>,
    selectFromTables: StringArraySet? = null,
    tableGraphNodeNames: SimpleArrayMap<String, String>? = null,
    queryDeep: Boolean = true,
    select1: Boolean = false
  ) = QueryGraphScope(
    from = from,
    selectFromTables = selectFromTables,
    tableGraphNodeNames = tableGraphNodeNames,
    queryDeep = queryDeep,
    select1 = select1
  )

  private fun assertTable(
    table: Table<*>,
    expectedName: String,
    expectedAlias: String?,
    expectedNameInQuery: String
  ) {
    assertThat(table.name).isEqualTo(expectedName)
    assertThat(table.alias).isEqualTo(expectedAlias)
    assertThat(table.nameInQuery).isEqualTo(expectedNameInQuery)
  }
}
