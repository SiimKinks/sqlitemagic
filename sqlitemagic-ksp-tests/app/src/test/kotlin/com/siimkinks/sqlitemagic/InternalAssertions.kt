package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.SimpleMutableEntityTable.Companion.SIMPLE_MUTABLE_ENTITY
import com.siimkinks.sqlitemagic.Utils.BYTE_PARSER
import com.siimkinks.sqlitemagic.Utils.DOUBLE_PARSER
import com.siimkinks.sqlitemagic.Utils.FLOAT_PARSER
import com.siimkinks.sqlitemagic.Utils.INTEGER_PARSER
import com.siimkinks.sqlitemagic.Utils.LONG_PARSER
import com.siimkinks.sqlitemagic.Utils.SHORT_PARSER
import com.siimkinks.sqlitemagic.Utils.STRING_PARSER
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.mock

interface DSLTests {
  @BeforeEach
  fun setUp() {
    val instance = SqliteMagic.SingletonHolder.instance
    instance.defaultConnection = mock()
  }
}

fun DeleteSqlNode.isEqualTo(
  expectedSql: String,
  vararg withArgs: String
) {
  val sql = SqlCreator.getSql(this, 3)
  assertThat(sql).isEqualTo(expectedSql)
  assertThat(this.deleteBuilder.args).containsExactly(*withArgs)
}

fun UpdateSqlNode.isEqualTo(
  sql: String,
  nodeCount: Int,
  vararg args: String?
) {
  val updateBuilder = updateBuilder
  val actualSql = SqlCreator.getSql(updateBuilder.sqlTreeRoot, updateBuilder.sqlNodeCount)
  assertThat(actualSql).isEqualTo(sql)
  assertThat(updateBuilder.sqlNodeCount).isEqualTo(nodeCount)
  assertThat(updateBuilder.args).isNotNull()
  assertThat(updateBuilder.args).containsExactly(*args)
}

fun CompiledRawSelect.isEqualTo(
  expectedSql: String,
  expectedObservedTables: Array<String> = emptyArray(),
  expectedArgs: Array<String>? = null
) {
  val select = this as RawSelect.CompiledRawSelectImpl
  assertThat(select.sql).isEqualTo(expectedSql)
  assertThat(select.observedTables).isEqualTo(expectedObservedTables)
  assertThat(select.args).isEqualTo(expectedArgs)
}

fun SelectSqlNode<*>.isEqualTo(expectedOutput: String) {
  val generatedSql = generateSql(this)
  assertThat(generatedSql).isEqualTo(expectedOutput)
}

fun SelectSqlNode<*>.isEqualTo(
  expectedOutput: String,
  vararg expectedArgs: String
) {
  val generatedSql = generateSql(this)
  assertThat(generatedSql).isEqualTo(expectedOutput)
  assertThat(this.selectBuilder.args).containsExactly(*expectedArgs)
}

fun SelectSqlNode.SelectNode<*, *, *>.compiledSql(): String =
  (compile() as CompiledSelectImpl<*, *>).sql

fun Expr.isEqualTo(
  expectedExpr: String,
  vararg args: String
) {
  (SELECT FROM SIMPLE_MUTABLE_ENTITY WHERE this)
    .isEqualTo("SELECT * FROM simple_mutable_entity WHERE $expectedExpr ", *args)
}

fun generateSql(sqlNode: SelectSqlNode<*>): String {
  val selectBuilder = sqlNode.selectBuilder
  if (selectBuilder.columnsNode != null) {
    selectBuilder.columnsNode.compileColumns(null)
  }
  return SqlCreator.getSql(sqlNode, selectBuilder.sqlNodeCount)
}

fun <T, R, ET, P, N> Column<T, R, ET, P, N>.parsesWith(valueParser: ValueParserType) {
  assertThat(this.valueParser).isEqualTo(
    when (valueParser) {
      ValueParserType.STRING -> STRING_PARSER
      ValueParserType.INTEGER -> INTEGER_PARSER
      ValueParserType.LONG -> LONG_PARSER
      ValueParserType.SHORT -> SHORT_PARSER
      ValueParserType.BYTE -> BYTE_PARSER
      ValueParserType.FLOAT -> FLOAT_PARSER
      ValueParserType.DOUBLE -> DOUBLE_PARSER
    }
  )
}

enum class ValueParserType {
  STRING, INTEGER, LONG, SHORT, BYTE, FLOAT, DOUBLE
}
