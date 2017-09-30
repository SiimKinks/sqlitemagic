package com.siimkinks.sqlitemagic

import com.google.common.truth.Truth.assertThat
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.Utils.*
import org.junit.Before
import org.mockito.Mockito

interface DSLTests {
  @Before
  fun setUp() {
    val instance = SqliteMagic.SingletonHolder.instance
    instance.defaultConnection = Mockito.mock<DbConnectionImpl>(DbConnectionImpl::class.java)
  }
}

fun DeleteSqlNode.isEqualTo(expectedSql: String, vararg withArgs: String) {
  val sql = SqlCreator.getSql(this, 3)
  assertThat(sql).isEqualTo(expectedSql)
  assertThat(this.deleteBuilder.args).containsExactly(*withArgs)
}

fun UpdateSqlNode.isEqualTo(expectedSql: String, expectedNodeCount: Int, vararg expectedArgs: String) {
  val updateBuilder = updateBuilder
  val sql = SqlCreator.getSql(updateBuilder.sqlTreeRoot, updateBuilder.sqlNodeCount)
  assertThat(sql).isEqualTo(expectedSql)
  assertThat(updateBuilder.sqlNodeCount).isEqualTo(expectedNodeCount)
  assertThat(updateBuilder.args).isNotNull()
  assertThat(updateBuilder.args).containsExactly(*expectedArgs)
}

fun CompiledRawSelect.isEqualTo(expectedSql: String,
                                expectedObservedTables: Array<String> = emptyArray(),
                                expectedArgs: Array<String>? = null) {
  val select = this as RawSelect.CompiledRawSelectImpl
  assertThat(select.sql).isEqualTo(expectedSql)
  assertThat(select.observedTables).isEqualTo(expectedObservedTables)
  assertThat(select.args).isEqualTo(expectedArgs)
}

fun SelectSqlNode<*>.isEqualTo(expectedOutput: String) {
  val generatedSql = generateSql(this)
  assertThat(generatedSql).isEqualTo(expectedOutput)
}

fun SelectSqlNode<*>.isEqualTo(expectedOutput: String, vararg expectedArgs: String) {
  val generatedSql = generateSql(this)
  assertThat(generatedSql).isEqualTo(expectedOutput)
  assertThat(this.selectBuilder.args).containsExactly(*expectedArgs)
}

fun Expr.isEqualTo(expectedExpr: String,
                   vararg args: String) {
  (SELECT FROM AUTHOR WHERE this)
      .isEqualTo("SELECT * FROM author WHERE $expectedExpr ", *args)
}

fun generateSql(sqlNode: SelectSqlNode<*>): String {
  val selectBuilder = sqlNode.selectBuilder
  if (selectBuilder.columnsNode != null) {
    selectBuilder.columnsNode.compileColumns(null)
  }
  return SqlCreator.getSql(sqlNode, selectBuilder.sqlNodeCount)
}

fun <T, R, ET, P> Column<T, R, ET, P>.parsesWith(valueParser: ValueParser) {
  assertThat(this.valueParser).isEqualTo(when (valueParser) {
    ValueParser.STRING -> STRING_PARSER
    ValueParser.INTEGER -> INTEGER_PARSER
    ValueParser.LONG -> LONG_PARSER
    ValueParser.SHORT -> SHORT_PARSER
    ValueParser.BYTE -> BYTE_PARSER
    ValueParser.FLOAT -> FLOAT_PARSER
    ValueParser.DOUBLE -> DOUBLE_PARSER
  })
}

enum class ValueParser {
  STRING, INTEGER, LONG, SHORT, BYTE, FLOAT, DOUBLE
}
