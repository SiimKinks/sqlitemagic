package com.example

import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.Select.*
import com.siimkinks.sqlitemagic.SelectSqlNode.SelectNode
import com.siimkinks.sqlitemagic.ValueParser.*
import com.siimkinks.sqlitemagic.model.Author
import com.siimkinks.sqlitemagic.model.immutable.ImmutableValueWithFields
import com.siimkinks.sqlitemagic.transformer.BooleanTransformer
import org.junit.Test

class SelectSqlBuilderTest : DSLTests {
  @Test
  fun rawSelect() {
    (SELECT
        RAW "SELECT * FROM author")
        .compile()
        .isEqualTo("SELECT * FROM author")
  }

  @Test
  fun rawSelectWithArgs() {
    (SELECT
        RAW "SELECT * FROM author WHERE author.name=?"
        WITH_ARGS arrayOf("foo"))
        .compile()
        .isEqualTo(
            expectedSql = "SELECT * FROM author WHERE author.name=?",
            expectedArgs = arrayOf("foo"))

    (SELECT
        RAW "SELECT * FROM author WHERE author.name=?"
        WITH_ARGS arrayOf("foo")
        FROM AUTHOR)
        .compile()
        .isEqualTo(
            expectedSql = "SELECT * FROM author WHERE author.name=?",
            expectedObservedTables = arrayOf("author"),
            expectedArgs = arrayOf("foo"))

    (SELECT
        RAW "SELECT * FROM author WHERE author.name=?"
        FROM AUTHOR
        WITH_ARGS arrayOf("foo"))
        .compile()
        .isEqualTo(
            expectedSql = "SELECT * FROM author WHERE author.name=?",
            expectedObservedTables = arrayOf("author"),
            expectedArgs = arrayOf("foo"))
  }

  @Test
  fun rawSelectWithObservedTable() {
    (SELECT
        RAW "SELECT * FROM author"
        FROM AUTHOR)
        .compile()
        .isEqualTo(
            expectedSql = "SELECT * FROM author",
            expectedObservedTables = arrayOf("author"))

    (SELECT
        RAW "SELECT * FROM magazine, author"
        FROM arrayOf(MAGAZINE, AUTHOR))
        .compile()
        .isEqualTo(
            expectedSql = "SELECT * FROM magazine, author",
            expectedObservedTables = arrayOf("magazine", "author"))

    (SELECT
        RAW "SELECT * FROM author"
        FROM listOf(AUTHOR))
        .compile()
        .isEqualTo(
            expectedSql = "SELECT * FROM author",
            expectedObservedTables = arrayOf("author"))
  }

  @Test
  fun selectAllFrom() {
    (SELECT FROM MAGAZINE)
        .isEqualTo("SELECT * FROM magazine ")

    (SELECT.DISTINCT FROM MAGAZINE)
        .isEqualTo("SELECT DISTINCT * FROM magazine ")
  }

  @Test
  fun selectAllFromAliased() {
    (SELECT FROM (MAGAZINE AS "b"))
        .isEqualTo("SELECT * FROM magazine AS b ")
  }

  @Test
  fun selectColumn() {
    (SELECT
        COLUMN MAGAZINE.NAME
        FROM MAGAZINE)
        .isEqualTo("SELECT magazine.name FROM magazine ")

    (SELECT
        DISTINCT MAGAZINE.NAME
        FROM MAGAZINE)
        .isEqualTo("SELECT DISTINCT magazine.name FROM magazine ")
  }

  @Test
  fun selectColumnAliased() {
    val m = MAGAZINE AS "m"
    (SELECT
        COLUMN m.NAME
        FROM m)
        .isEqualTo("SELECT m.name FROM magazine AS m ")

    (SELECT
        DISTINCT m.NAME
        FROM m)
        .isEqualTo("SELECT DISTINCT m.name FROM magazine AS m ")
  }

  @Test
  fun selectColumns() {
    (SELECT
        COLUMNS arrayOf(MAGAZINE.AUTHOR, MAGAZINE.NAME, MAGAZINE.NR_OF_RELEASES)
        FROM MAGAZINE)
        .isEqualTo("SELECT magazine.author,magazine.name,magazine.nr_of_releases FROM magazine ")

    (SELECT
        DISTINCT arrayOf(MAGAZINE.AUTHOR, MAGAZINE.NAME, MAGAZINE.NR_OF_RELEASES)
        FROM MAGAZINE)
        .isEqualTo("SELECT DISTINCT magazine.author,magazine.name,magazine.nr_of_releases FROM magazine ")
  }

  @Test
  fun selectColumnsAliased() {
    val m = MAGAZINE AS "m"
    val nroe = m.NR_OF_RELEASES AS "nroe"
    (SELECT
        COLUMNS arrayOf(m.AUTHOR, m.NAME, nroe)
        FROM m)
        .isEqualTo("SELECT m.author,m.name,m.nr_of_releases AS 'nroe' FROM magazine AS m ")

    (SELECT
        DISTINCT arrayOf(m.AUTHOR, m.NAME, nroe)
        FROM m)
        .isEqualTo("SELECT DISTINCT m.author,m.name,m.nr_of_releases AS 'nroe' FROM magazine AS m ")
  }

  @Test
  fun joins() {
    var expected = "SELECT * FROM magazine LEFT OUTER JOIN author ON magazine.author=author._id "
    var sqlNode = (SELECT
        FROM MAGAZINE
        LEFT_OUTER_JOIN (AUTHOR ON (MAGAZINE.AUTHOR IS AUTHOR._ID)))
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode NATURAL_JOIN AUTHOR)
    expected += "NATURAL JOIN author "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode INNER_JOIN (AUTHOR USING arrayOf(MAGAZINE.AUTHOR, AUTHOR._ID)))
    expected += "INNER JOIN author USING (author,_id) "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode CROSS_JOIN (AUTHOR USING arrayOf(MAGAZINE.AUTHOR, AUTHOR._ID)))
    expected += "CROSS JOIN author USING (author,_id) "
    sqlNode.isEqualTo(expected)

    (SELECT
        FROM COMPLEX_OBJECT_WITH_SAME_LEAFS
        JOIN MAGAZINE
        JOIN AUTHOR)
        .isEqualTo("SELECT * FROM complex_object_with_same_leafs , magazine , author ")
  }

  @Test
  fun joinsAliased() {
    var expected = "SELECT * FROM magazine AS m LEFT OUTER JOIN author AS a ON m.author=a._id "

    val m = MAGAZINE AS "m"
    val a = AUTHOR AS "a"
    var sqlNode = (SELECT
        FROM m
        LEFT_OUTER_JOIN (a ON (m.AUTHOR IS a._ID)))
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode NATURAL_JOIN a)
    expected += "NATURAL JOIN author AS a "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode INNER_JOIN (a USING arrayOf(m.AUTHOR, a._ID)))
    expected += "INNER JOIN author AS a USING (author,_id) "
    sqlNode.isEqualTo(expected)

    sqlNode = (sqlNode CROSS_JOIN (a USING arrayOf(m.AUTHOR, a._ID)))
    expected += "CROSS JOIN author AS a USING (author,_id) "
    sqlNode.isEqualTo(expected)

    (SELECT
        FROM (COMPLEX_OBJECT_WITH_SAME_LEAFS AS "c")
        JOIN m
        JOIN a)
        .isEqualTo("SELECT * FROM complex_object_with_same_leafs AS c , magazine AS m , author AS a ")
  }

  @Test
  fun whereCondition() {
    val expectedBase = "SELECT * FROM magazine WHERE "

    val titleIs = MAGAZINE.NAME IS "asd"
    val intIs = MAGAZINE.NR_OF_RELEASES IS 1920
    val titleIsNot = MAGAZINE.NAME IS_NOT "asd"
    val titleIsNotNull = MAGAZINE.NAME.isNotNull
    val titleIsNull = MAGAZINE.NAME.isNull
    val titleGlob = MAGAZINE.NAME GLOB "asd"
    val titleLike = MAGAZINE.NAME LIKE "asd"
    val lessThan = MAGAZINE.NR_OF_RELEASES LESS_THAN 1990
    val greaterThan = MAGAZINE.NR_OF_RELEASES GREATER_THAN 1990
    val between = MAGAZINE.NR_OF_RELEASES BETWEEN (1910 AND 2000)
    val notBetween = MAGAZINE.NR_OF_RELEASES NOT_BETWEEN (1910 AND 2000)
    val `in` = MAGAZINE.NR_OF_RELEASES IN arrayOf(1910, 1999, 1920)
    val notIn = MAGAZINE.NR_OF_RELEASES NOT_IN arrayOf(1910, 1999, 1920)
    val oneIn = MAGAZINE.NR_OF_RELEASES IN arrayOf(1910)
    val oneNotIn = MAGAZINE.NR_OF_RELEASES NOT_IN arrayOf(1910)

    var expected = expectedBase + "magazine.name=? "
    (SELECT FROM MAGAZINE WHERE titleIs)
        .isEqualTo(expected, "asd")

    expected = expectedBase + "(magazine.name=? AND magazine.nr_of_releases=?) "
    (SELECT FROM MAGAZINE WHERE (titleIs AND intIs))
        .isEqualTo(expected, "asd", "1920")

    expected = expectedBase + "magazine.nr_of_releases BETWEEN ? AND ? "
    (SELECT FROM MAGAZINE WHERE between)
        .isEqualTo(expected, "1910", "2000")

    expected = expectedBase + "magazine.nr_of_releases NOT BETWEEN ? AND ? "
    (SELECT FROM MAGAZINE WHERE notBetween)
        .isEqualTo(expected, "1910", "2000")

    expected = expectedBase + "magazine.nr_of_releases IN (?,?,?) "
    (SELECT FROM MAGAZINE WHERE `in`)
        .isEqualTo(expected, "1910", "1999", "1920")

    val expectedLongWhereClause = "((((((((magazine.name!=? AND magazine.name IS NOT NULL) AND magazine.name IS NULL) " +
        "AND magazine.name GLOB ?) AND magazine.name LIKE ?) AND magazine.nr_of_releases<?) AND magazine.nr_of_releases>?) " +
        "AND magazine.nr_of_releases BETWEEN ? AND ?) AND magazine.nr_of_releases NOT BETWEEN ? AND ?) "
    expected = expectedBase + expectedLongWhereClause
    (SELECT FROM MAGAZINE WHERE ((((((((titleIsNot AND titleIsNotNull) AND titleIsNull) AND titleGlob)
        AND titleLike) AND lessThan) AND greaterThan) AND between) AND notBetween))
        .isEqualTo(expected, "asd", "asd", "asd", "1990", "1990", "1910", "2000", "1910", "2000")

    expected = expectedBase + "(((magazine.nr_of_releases IN (?,?,?) AND magazine.nr_of_releases NOT IN (?,?,?)) " +
        "AND magazine.nr_of_releases IN (?)) AND magazine.nr_of_releases NOT IN (?)) "
    (SELECT FROM MAGAZINE WHERE (((`in` AND notIn) AND oneIn) AND oneNotIn))
        .isEqualTo(expected, "1910", "1999", "1920", "1910", "1999", "1920", "1910", "1910")

    expected = expectedBase + "(magazine.nr_of_releases IN (?,?,?) OR magazine.name!=?) "
    (SELECT FROM MAGAZINE WHERE (`in` OR titleIsNot))
        .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = expectedBase + "(magazine.nr_of_releases IN (?,?,?) AND (magazine.name!=? AND magazine.name IS NOT NULL)) "
    (SELECT FROM MAGAZINE WHERE (`in` AND (titleIsNot AND titleIsNotNull)))
        .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = expectedBase + "(magazine.nr_of_releases IN (?,?,?) OR (magazine.nr_of_releases BETWEEN ? AND ? AND magazine.name IS NOT NULL)) "
    (SELECT FROM MAGAZINE WHERE (`in` OR (between AND titleIsNotNull)))
        .isEqualTo(expected, "1910", "1999", "1920", "1910", "2000")

    expected = expectedBase + "(magazine.nr_of_releases IN (?,?,?) AND ((magazine.name!=? OR magazine.name IS NOT NULL) OR magazine.name IS NULL)) "
    (SELECT FROM MAGAZINE WHERE (`in` AND ((titleIsNot OR titleIsNotNull) OR titleIsNull)))
        .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = expectedBase + "(magazine.nr_of_releases IN (?,?,?) OR magazine.name!=?) "
    (SELECT FROM MAGAZINE WHERE (`in` OR titleIsNot))
        .isEqualTo(expected, "1910", "1999", "1920", "asd")
  }

  @Test
  fun whereConditionAliased() {
    val expectedBase = "SELECT * FROM magazine AS m WHERE "

    val m = MAGAZINE AS "m"
    val titleIs = m.NAME IS "asd"
    val intIs = m.NR_OF_RELEASES IS 1920
    val titleIsNot = m.NAME IS_NOT "asd"
    val titleIsNotNull = m.NAME.isNotNull
    val titleIsNull = m.NAME.isNull
    val titleGlob = m.NAME GLOB "asd"
    val titleLike = m.NAME LIKE "asd"
    val lessThan = m.NR_OF_RELEASES LESS_THAN 1990
    val greaterThan = m.NR_OF_RELEASES GREATER_THAN 1990
    val between = m.NR_OF_RELEASES BETWEEN (1910 AND 2000)
    val notBetween = m.NR_OF_RELEASES NOT_BETWEEN (1910 AND 2000)
    val `in` = m.NR_OF_RELEASES IN arrayOf(1910, 1999, 1920)
    val notIn = m.NR_OF_RELEASES NOT_IN arrayOf(1910, 1999, 1920)
    val oneIn = m.NR_OF_RELEASES IN arrayOf(1910)
    val oneNotIn = m.NR_OF_RELEASES NOT_IN arrayOf(1910)

    var expected = expectedBase + "m.name=? "
    (SELECT FROM m WHERE titleIs)
        .isEqualTo(expected, "asd")

    expected = expectedBase + "(m.name=? AND m.nr_of_releases=?) "
    (SELECT FROM m WHERE (titleIs AND intIs))
        .isEqualTo(expected, "asd", "1920")

    expected = expectedBase + "m.nr_of_releases BETWEEN ? AND ? "
    (SELECT FROM m WHERE between)
        .isEqualTo(expected, "1910", "2000")

    expected = expectedBase + "m.nr_of_releases NOT BETWEEN ? AND ? "
    (SELECT FROM m WHERE notBetween)
        .isEqualTo(expected, "1910", "2000")

    expected = expectedBase + "m.nr_of_releases IN (?,?,?) "
    (SELECT FROM m WHERE `in`)
        .isEqualTo(expected, "1910", "1999", "1920")

    val expectedLongWhereClause = "((((((((m.name!=? AND m.name IS NOT NULL) AND m.name IS NULL) " +
        "AND m.name GLOB ?) AND m.name LIKE ?) AND m.nr_of_releases<?) AND m.nr_of_releases>?) " +
        "AND m.nr_of_releases BETWEEN ? AND ?) AND m.nr_of_releases NOT BETWEEN ? AND ?) "
    expected = expectedBase + expectedLongWhereClause
    (SELECT FROM m WHERE ((((((((titleIsNot AND titleIsNotNull) AND titleIsNull) AND titleGlob)
        AND titleLike) AND lessThan) AND greaterThan) AND between) AND notBetween))
        .isEqualTo(expected, "asd", "asd", "asd", "1990", "1990", "1910", "2000", "1910", "2000")

    expected = expectedBase + "(((m.nr_of_releases IN (?,?,?) AND m.nr_of_releases NOT IN (?,?,?)) " +
        "AND m.nr_of_releases IN (?)) AND m.nr_of_releases NOT IN (?)) "
    (SELECT FROM m WHERE (((`in` AND notIn) AND oneIn) AND oneNotIn))
        .isEqualTo(expected, "1910", "1999", "1920", "1910", "1999", "1920", "1910", "1910")

    expected = expectedBase + "(m.nr_of_releases IN (?,?,?) OR m.name!=?) "
    (SELECT FROM m WHERE (`in` OR titleIsNot))
        .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = expectedBase + "(m.nr_of_releases IN (?,?,?) AND (m.name!=? AND m.name IS NOT NULL)) "
    (SELECT FROM m WHERE (`in` AND (titleIsNot AND titleIsNotNull)))
        .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = expectedBase + "(m.nr_of_releases IN (?,?,?) OR (m.nr_of_releases BETWEEN ? AND ? AND m.name IS NOT NULL)) "
    (SELECT FROM m WHERE (`in` OR (between AND titleIsNotNull)))
        .isEqualTo(expected, "1910", "1999", "1920", "1910", "2000")

    expected = expectedBase + "(m.nr_of_releases IN (?,?,?) AND ((m.name!=? OR m.name IS NOT NULL) OR m.name IS NULL)) "
    (SELECT FROM m WHERE (`in` AND ((titleIsNot OR titleIsNotNull) OR titleIsNull)))
        .isEqualTo(expected, "1910", "1999", "1920", "asd")

    expected = expectedBase + "(m.nr_of_releases IN (?,?,?) OR m.name!=?) "
    (SELECT FROM m WHERE (`in` OR titleIsNot))
        .isEqualTo(expected, "1910", "1999", "1920", "asd")
  }

  @Test
  fun columnNotRedefinedWhenAliased() {
    var expected = "SELECT magazine.*,magazine.name || ' ' || magazine.nr_of_releases AS 'search_column' " +
        "FROM magazine " +
        "WHERE magazine.name=search_column "
    val searchColumn = concat(MAGAZINE.NAME, " ".value, MAGAZINE.NR_OF_RELEASES) AS "search_column"
    (SELECT
        COLUMNS arrayOf(MAGAZINE.all(), searchColumn)
        FROM MAGAZINE
        WHERE (MAGAZINE.NAME IS (searchColumn)))
        .isEqualTo(expected)

    expected = "SELECT trim(magazine.name) AS 'trimmed_title' " +
        "FROM magazine " +
        "WHERE trim(magazine.name)=trimmed_title "
    val trimmedTitle = MAGAZINE.NAME.trim() AS "trimmed_title"
    (SELECT
        COLUMN trimmedTitle
        FROM MAGAZINE
        WHERE (MAGAZINE.NAME.trim() IS trimmedTitle))
        .isEqualTo(expected)
  }

  @Test
  fun betweenComplex() {
    val expectedBase = "SELECT * FROM magazine "
    val randomAuthor = Author.newRandom()
    val randomAuthor2 = Author.newRandom()

    var expected = expectedBase + "WHERE magazine.author BETWEEN ? AND ? "
    (SELECT
        FROM MAGAZINE
        WHERE (MAGAZINE.AUTHOR BETWEEN (randomAuthor AND randomAuthor2)))
        .isEqualTo(expected, randomAuthor.id.toString(), randomAuthor2.id.toString())

    expected = expectedBase + "WHERE magazine.author BETWEEN ? AND author._id "
    (SELECT
        FROM MAGAZINE
        WHERE (MAGAZINE.AUTHOR BETWEEN (randomAuthor AND AUTHOR._ID)))
        .isEqualTo(expected, randomAuthor.id.toString())

    expected = expectedBase + "WHERE magazine.author BETWEEN author._id AND ? "
    (SELECT
        FROM MAGAZINE
        WHERE (MAGAZINE.AUTHOR BETWEEN (AUTHOR._ID AND randomAuthor)))
        .isEqualTo(expected, randomAuthor.id.toString())

    expected = expectedBase + "WHERE magazine.author BETWEEN author._id AND magazine.id "
    (SELECT
        FROM MAGAZINE
        WHERE (MAGAZINE.AUTHOR BETWEEN (AUTHOR._ID AND MAGAZINE.ID)))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE magazine.author BETWEEN magazine.author AND magazine.id "
    (SELECT
        FROM MAGAZINE
        WHERE (MAGAZINE.AUTHOR BETWEEN (MAGAZINE.AUTHOR AND MAGAZINE.ID)))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE magazine.author BETWEEN magazine.author AND magazine.author "
    (SELECT
        FROM MAGAZINE
        WHERE (MAGAZINE.AUTHOR BETWEEN (MAGAZINE.AUTHOR AND MAGAZINE.AUTHOR)))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE magazine.author BETWEEN magazine.author AND ? "
    (SELECT
        FROM MAGAZINE
        WHERE (MAGAZINE.AUTHOR BETWEEN (MAGAZINE.AUTHOR AND randomAuthor)))
        .isEqualTo(expected, randomAuthor.id.toString())
  }

  @Test
  fun betweenComplexAliased() {
    val expectedBase = "SELECT * FROM magazine AS m "
    val randomAuthor = Author.newRandom()
    val randomAuthor2 = Author.newRandom()
    val a = AUTHOR AS "a"
    val m = MAGAZINE AS "m"

    var expected = expectedBase + "WHERE m.author BETWEEN ? AND ? "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (randomAuthor AND randomAuthor2)))
        .isEqualTo(expected, randomAuthor.id.toString(), randomAuthor2.id.toString())

    expected = expectedBase + "WHERE m.author BETWEEN ? AND author._id "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (randomAuthor AND AUTHOR._ID)))
        .isEqualTo(expected, randomAuthor.id.toString())

    expected = expectedBase + "WHERE m.author BETWEEN ? AND a._id "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (randomAuthor AND a._ID)))
        .isEqualTo(expected, randomAuthor.id.toString())

    expected = expectedBase + "WHERE m.author BETWEEN author._id AND ? "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (AUTHOR._ID AND randomAuthor)))
        .isEqualTo(expected, randomAuthor.id.toString())

    expected = expectedBase + "WHERE m.author BETWEEN a._id AND ? "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (a._ID AND randomAuthor)))
        .isEqualTo(expected, randomAuthor.id.toString())

    expected = expectedBase + "WHERE m.author BETWEEN author._id AND m.id "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (AUTHOR._ID AND m.ID)))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE m.author BETWEEN a._id AND m.id "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (a._ID AND m.ID)))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE m.author BETWEEN magazine.author AND m.author "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (MAGAZINE.AUTHOR AND m.AUTHOR)))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE m.author BETWEEN m.author AND m.author "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (m.AUTHOR AND m.AUTHOR)))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE m.author BETWEEN magazine.author AND ? "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (MAGAZINE.AUTHOR AND randomAuthor)))
        .isEqualTo(expected, randomAuthor.id.toString())

    expected = expectedBase + "WHERE m.author BETWEEN m.author AND ? "
    (SELECT
        FROM m
        WHERE (m.AUTHOR BETWEEN (m.AUTHOR AND randomAuthor)))
        .isEqualTo(expected, randomAuthor.id.toString())
  }

  @Test
  fun rawExpr() {
    var sql = "author.name IS NOT NULL"
    sql.expr.isEqualTo(sql)

    sql = "author.name = ?"
    sql.expr("asd").isEqualTo(sql, "asd")

    sql = "author.name = ? AND author.name != ?"
    sql.expr("asd", "dsa").isEqualTo(sql, "asd", "dsa")
  }

  @Test
  fun rawExprWithNonRawExpr() {
    var sql = "author.name = ?"
    var expr = (AUTHOR.NAME IS_NOT "dsa") AND sql.expr("asd")
    expr.isEqualTo("(author.name!=? AND $sql)", "dsa", "asd")

    sql = "author.name = ?"
    expr = sql.expr("asd") AND (AUTHOR.NAME IS_NOT "dsa")
    expr.isEqualTo("($sql AND author.name!=?)", "asd", "dsa")

    sql = "author.name IS NOT NULL"
    expr = AUTHOR.NAME IS_NOT "asd" AND sql.expr
    expr.isEqualTo("(author.name!=? AND $sql)", "asd")

    sql = "author.name IS NOT NULL"
    expr = sql.expr AND (AUTHOR.NAME IS_NOT "asd")
    expr.isEqualTo("($sql AND author.name!=?)", "asd")
  }

  @Test
  fun exprSimple() {
    assertSimpleExpr("=?", AUTHOR.NAME IS "asd")
    assertSimpleExpr("!=?", AUTHOR.NAME IS_NOT "asd")
    assertSimpleExpr(" IN (?)", AUTHOR.NAME IN arrayOf("asd"))
    assertSimpleExpr(" NOT IN (?)", AUTHOR.NAME NOT_IN arrayOf("asd"))

    assertSimpleColumnExpr("=", AUTHOR.NAME IS MAGAZINE.NAME)
    assertSimpleColumnExpr("!=", AUTHOR.NAME IS_NOT MAGAZINE.NAME)
  }

  private fun assertSimpleExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM author WHERE author.name%s "

    (SELECT FROM AUTHOR WHERE expr)
        .isEqualTo(String.format(expectedBase, operator), "asd")
  }

  private fun assertSimpleColumnExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM author WHERE author.name%smagazine.name "

    (SELECT FROM AUTHOR WHERE expr)
        .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun unaryExpr() {
    (SELECT
        FROM AUTHOR
        WHERE !(AUTHOR.PRIMITIVE_BOOLEAN.isNotNull))
        .isEqualTo("SELECT * FROM author WHERE NOT(author.primitive_boolean IS NOT NULL) ")

    (SELECT
        FROM AUTHOR
        WHERE !((AUTHOR.PRIMITIVE_BOOLEAN GREATER_THAN AUTHOR.BOXED_BOOLEAN) AND AUTHOR.BOXED_BOOLEAN.isNotNull))
        .isEqualTo("SELECT * FROM author WHERE NOT((author.primitive_boolean>author.boxed_boolean AND author.boxed_boolean IS NOT NULL)) ")
  }

  @Test
  fun numericExprWithSameType() {
    assertNumericExpr("=?", (MAGAZINE.NR_OF_RELEASES IS 4))
    assertNumericExpr("!=?", (MAGAZINE.NR_OF_RELEASES IS_NOT 4))
    assertNumericExpr(" IN (?)", (MAGAZINE.NR_OF_RELEASES IN arrayOf(4)))
    assertNumericExpr(" NOT IN (?)", (MAGAZINE.NR_OF_RELEASES NOT_IN arrayOf(4)))
    assertNumericExpr(">?", (MAGAZINE.NR_OF_RELEASES GREATER_THAN 4))
    assertNumericExpr(">=?", (MAGAZINE.NR_OF_RELEASES GREATER_OR_EQUAL 4))
    assertNumericExpr("<?", (MAGAZINE.NR_OF_RELEASES LESS_THAN 4))
    assertNumericExpr("<=?", (MAGAZINE.NR_OF_RELEASES LESS_OR_EQUAL 4))
  }

  @Test
  fun numericExprWithSameColumnType() {
    assertNumericSameTypeExpr("=", (MAGAZINE.NR_OF_RELEASES IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr("!=", (MAGAZINE.NR_OF_RELEASES IS_NOT IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr(">", (MAGAZINE.NR_OF_RELEASES GREATER_THAN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr(">=", (MAGAZINE.NR_OF_RELEASES GREATER_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr("<", (MAGAZINE.NR_OF_RELEASES LESS_THAN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
    assertNumericSameTypeExpr("<=", (MAGAZINE.NR_OF_RELEASES LESS_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
  }

  @Test
  fun numericExprWithEquivalentColumnType() {
    assertNumericEquivalentTypeExpr("=", (MAGAZINE.NR_OF_RELEASES IS IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr("!=", (MAGAZINE.NR_OF_RELEASES IS_NOT IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr(">", (MAGAZINE.NR_OF_RELEASES GREATER_THAN IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr(">=", (MAGAZINE.NR_OF_RELEASES GREATER_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr("<", (MAGAZINE.NR_OF_RELEASES LESS_THAN IMMUTABLE_VALUE_WITH_FIELDS.ID))
    assertNumericEquivalentTypeExpr("<=", (MAGAZINE.NR_OF_RELEASES LESS_OR_EQUAL IMMUTABLE_VALUE_WITH_FIELDS.ID))
  }

  private fun assertNumericExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.nr_of_releases%s "

    (SELECT FROM MAGAZINE WHERE expr)
        .isEqualTo(String.format(expectedBase, operator), "4")
  }

  private fun assertNumericSameTypeExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.nr_of_releases%simmutable_value_with_fields.integer "

    (SELECT FROM MAGAZINE WHERE expr)
        .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertNumericEquivalentTypeExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.nr_of_releases%simmutable_value_with_fields.id "

    (SELECT FROM MAGAZINE WHERE expr)
        .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun complexExprWithSameType() {
    val randomAuthor = Author.newRandom()

    assertComplexSameTypeExpr("=?", randomAuthor, MAGAZINE.AUTHOR IS randomAuthor)
    assertComplexSameTypeExpr("!=?", randomAuthor, MAGAZINE.AUTHOR.isNot(randomAuthor))
    assertComplexSameTypeExpr(" IN (?)", randomAuthor, MAGAZINE.AUTHOR.`in`(randomAuthor))
    assertComplexSameTypeExpr(" IN (?)", randomAuthor, MAGAZINE.AUTHOR.`in`(randomAuthor))
    assertComplexSameTypeExpr(" NOT IN (?)", randomAuthor, MAGAZINE.AUTHOR.notIn(randomAuthor))
    assertComplexSameTypeExpr(">?", randomAuthor, MAGAZINE.AUTHOR.greaterThan(randomAuthor))
    assertComplexSameTypeExpr(">=?", randomAuthor, MAGAZINE.AUTHOR.greaterOrEqual(randomAuthor))
    assertComplexSameTypeExpr("<?", randomAuthor, MAGAZINE.AUTHOR.lessThan(randomAuthor))
    assertComplexSameTypeExpr("<=?", randomAuthor, MAGAZINE.AUTHOR.lessOrEqual(randomAuthor))
  }

  @Test
  fun complexExprWithEquivalentType() {
    val authorId: Long = 42

    assertComplexEquivalentTypeExpr("=?", authorId, MAGAZINE.AUTHOR IS authorId)
    assertComplexEquivalentTypeExpr("!=?", authorId, MAGAZINE.AUTHOR IS_NOT authorId)
    assertComplexEquivalentTypeExpr(" IN (?)", authorId, MAGAZINE.AUTHOR IN longArrayOf(authorId))
    assertComplexEquivalentTypeExpr(" NOT IN (?)", authorId, MAGAZINE.AUTHOR NOT_IN longArrayOf(authorId))
    assertComplexEquivalentTypeExpr(">?", authorId, MAGAZINE.AUTHOR GREATER_THAN authorId)
    assertComplexEquivalentTypeExpr(">=?", authorId, MAGAZINE.AUTHOR GREATER_OR_EQUAL authorId)
    assertComplexEquivalentTypeExpr("<?", authorId, MAGAZINE.AUTHOR LESS_THAN authorId)
    assertComplexEquivalentTypeExpr("<=?", authorId, MAGAZINE.AUTHOR LESS_OR_EQUAL authorId)
  }

  @Test
  fun complexExprWithSameColumnType() {
    assertComplexSameColumnTypeExpr("=", MAGAZINE.AUTHOR IS MAGAZINE.AUTHOR)
    assertComplexSameColumnTypeExpr("!=", MAGAZINE.AUTHOR IS_NOT MAGAZINE.AUTHOR)
    assertComplexSameColumnTypeExpr(">", MAGAZINE.AUTHOR GREATER_THAN MAGAZINE.AUTHOR)
    assertComplexSameColumnTypeExpr(">=", MAGAZINE.AUTHOR GREATER_OR_EQUAL MAGAZINE.AUTHOR)
    assertComplexSameColumnTypeExpr("<", MAGAZINE.AUTHOR LESS_THAN MAGAZINE.AUTHOR)
    assertComplexSameColumnTypeExpr("<=", MAGAZINE.AUTHOR LESS_OR_EQUAL MAGAZINE.AUTHOR)
  }

  @Test
  fun complexExprWithEquivalentColumnType() {
    assertComplexEquivalentColumnTypeExpr("=", MAGAZINE.AUTHOR IS MAGAZINE.ID)
    assertComplexEquivalentColumnTypeExpr("!=", MAGAZINE.AUTHOR IS_NOT MAGAZINE.ID)
    assertComplexEquivalentColumnTypeExpr(">", MAGAZINE.AUTHOR GREATER_THAN MAGAZINE.ID)
    assertComplexEquivalentColumnTypeExpr(">=", MAGAZINE.AUTHOR GREATER_OR_EQUAL MAGAZINE.ID)
    assertComplexEquivalentColumnTypeExpr("<", MAGAZINE.AUTHOR LESS_THAN MAGAZINE.ID)
    assertComplexEquivalentColumnTypeExpr("<=", MAGAZINE.AUTHOR LESS_OR_EQUAL MAGAZINE.ID)
  }

  private fun assertComplexSameTypeExpr(operator: String, `val`: Author, expr: Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.author%s "

    (SELECT FROM MAGAZINE WHERE expr)
        .isEqualTo(String.format(expectedBase, operator),
            `val`.id.toString())
  }

  private fun assertComplexEquivalentTypeExpr(operator: String, `val`: Long, expr: Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.author%s "

    (SELECT FROM MAGAZINE WHERE expr)
        .isEqualTo(String.format(expectedBase, operator),
            java.lang.Long.toString(`val`))
  }

  private fun assertComplexSameColumnTypeExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.author%smagazine.author "

    (SELECT FROM MAGAZINE WHERE expr)
        .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertComplexEquivalentColumnTypeExpr(operator: String, expr: Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.author%smagazine.id "

    (SELECT FROM MAGAZINE WHERE expr)
        .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun exprComplexAliased() {
    val expectedBase = "SELECT * FROM magazine AS m "
    val randomAuthor = Author.newRandom()
    val m = MAGAZINE AS "m"

    var expected = expectedBase + "WHERE m.author=? "
    (SELECT
        FROM m
        WHERE (m.AUTHOR IS randomAuthor))
        .isEqualTo(expected, randomAuthor.id.toString())

    expected = expectedBase + "WHERE m.author=magazine.author "
    (SELECT
        FROM m
        WHERE (m.AUTHOR IS MAGAZINE.AUTHOR))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE m.author=m.author "
    (SELECT
        FROM m
        WHERE (m.AUTHOR IS m.AUTHOR))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE m.author=magazine.id "
    (SELECT
        FROM m
        WHERE (m.AUTHOR IS MAGAZINE.ID))
        .isEqualTo(expected)

    expected = expectedBase + "WHERE m.author=m.id "
    (SELECT
        FROM m
        WHERE (m.AUTHOR IS m.ID))
        .isEqualTo(expected)
  }

  @Test
  fun joinComplex() {
    val randomAuthor = Author.newRandom()
    val expectedBase = "SELECT * FROM magazine "

    var expected = expectedBase + "LEFT JOIN magazine ON magazine.author!=magazine.author "
    (SELECT
        FROM MAGAZINE
        LEFT_JOIN (MAGAZINE ON (MAGAZINE.AUTHOR IS_NOT MAGAZINE.AUTHOR)))
        .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN magazine ON magazine.author!=magazine.id "
    (SELECT
        FROM MAGAZINE
        LEFT_JOIN (MAGAZINE ON (MAGAZINE.AUTHOR IS_NOT MAGAZINE.ID)))
        .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN magazine ON magazine.author=? "
    (SELECT
        FROM MAGAZINE
        LEFT_JOIN (MAGAZINE ON (MAGAZINE.AUTHOR IS randomAuthor)))
        .isEqualTo(expected, randomAuthor.id.toString())
  }

  @Test
  fun joinComplexAliased() {
    val randomAuthor = Author.newRandom()
    val expectedBase = "SELECT * FROM magazine AS m "
    val m = MAGAZINE AS "m"

    var expected = expectedBase + "LEFT JOIN magazine ON m.author!=magazine.author "
    (SELECT
        FROM m
        LEFT_JOIN (MAGAZINE.on(m.AUTHOR IS_NOT MAGAZINE.AUTHOR)))
        .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN magazine AS m ON m.author!=m.author "
    (SELECT
        FROM m
        LEFT_JOIN (m.on(m.AUTHOR IS_NOT m.AUTHOR)))
        .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN magazine ON m.author!=magazine.id "
    (SELECT
        FROM m
        LEFT_JOIN (MAGAZINE.on(m.AUTHOR IS_NOT MAGAZINE.ID)))
        .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN magazine AS m ON m.author!=m.id "
    (SELECT
        FROM m
        LEFT_JOIN (m.on(m.AUTHOR IS_NOT m.ID)))
        .isEqualTo(expected)

    expected = expectedBase + "LEFT JOIN magazine ON m.author=? "
    (SELECT
        FROM m
        LEFT_JOIN (MAGAZINE.on(m.AUTHOR IS randomAuthor)))
        .isEqualTo(expected, randomAuthor.id.toString())

    expected = expectedBase + "LEFT JOIN magazine AS m ON m.author=? "
    (SELECT
        FROM m
        LEFT_JOIN (m.on(m.AUTHOR IS randomAuthor)))
        .isEqualTo(expected, randomAuthor.id.toString())
  }

  @Test
  fun groupByTest() {
    val expectedBase = "SELECT * FROM magazine "

    (SELECT
        FROM MAGAZINE
        GROUP_BY MAGAZINE.NAME)
        .isEqualTo(expectedBase + "GROUP BY magazine.name ")

    (SELECT
        FROM MAGAZINE
        GROUP_BY MAGAZINE.AUTHOR)
        .isEqualTo(expectedBase + "GROUP BY magazine.author ")


    (SELECT
        FROM MAGAZINE
        GROUP_BY arrayOf(MAGAZINE.ID, MAGAZINE.NAME, MAGAZINE.AUTHOR))
        .isEqualTo(expectedBase + "GROUP BY magazine.id,magazine.name,magazine.author ")


    (SELECT
        FROM MAGAZINE
        GROUP_BY MAGAZINE.NAME
        HAVING (MAGAZINE.NR_OF_RELEASES IS 1990))
        .isEqualTo(expectedBase + "GROUP BY magazine.name HAVING magazine.nr_of_releases=? ", "1990")


    (SELECT
        FROM MAGAZINE
        GROUP_BY arrayOf(MAGAZINE.NAME, MAGAZINE.AUTHOR)
        HAVING (MAGAZINE.NR_OF_RELEASES IS 1990))
        .isEqualTo(expectedBase + "GROUP BY magazine.name,magazine.author HAVING magazine.nr_of_releases=? ", "1990")


    (SELECT
        FROM MAGAZINE
        LEFT_JOIN IMMUTABLE_VALUE_WITH_FIELDS
        GROUP_BY arrayOf(MAGAZINE.NAME, MAGAZINE.AUTHOR)
        HAVING (MAGAZINE.NR_OF_RELEASES IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
        .isEqualTo(expectedBase + "LEFT JOIN immutable_value_with_fields GROUP BY magazine.name,magazine.author HAVING magazine.nr_of_releases=immutable_value_with_fields.integer ")


    (SELECT
        FROM MAGAZINE
        LEFT_JOIN AUTHOR
        GROUP_BY AUTHOR.BOXED_BOOLEAN
        HAVING (AUTHOR.PRIMITIVE_BOOLEAN IS true))
        .isEqualTo(expectedBase + "LEFT JOIN author GROUP BY author.boxed_boolean HAVING author.primitive_boolean=? ", BooleanTransformer.objectToDbValue(true)!!.toString())


    (SELECT
        FROM MAGAZINE
        LEFT_JOIN AUTHOR
        GROUP_BY AUTHOR.BOXED_BOOLEAN
        HAVING (AUTHOR.PRIMITIVE_BOOLEAN IS AUTHOR.PRIMITIVE_BOOLEAN))
        .isEqualTo(expectedBase + "LEFT JOIN author GROUP BY author.boxed_boolean HAVING author.primitive_boolean=author.primitive_boolean ")
  }

  @Test
  fun groupByTestAliased() {
    val expectedBase = "SELECT * FROM magazine AS m "
    val m = MAGAZINE AS "m"
    val s = IMMUTABLE_VALUE_WITH_FIELDS AS "s"
    val a = AUTHOR AS "a"

    (SELECT
        FROM m
        GROUP_BY m.NAME)
        .isEqualTo(expectedBase + "GROUP BY m.name ")

    (SELECT
        FROM m
        GROUP_BY m.AUTHOR)
        .isEqualTo(expectedBase + "GROUP BY m.author ")

    (SELECT
        FROM m
        GROUP_BY arrayOf(m.ID, m.NAME, m.AUTHOR))
        .isEqualTo(expectedBase + "GROUP BY m.id,m.name,m.author ")

    (SELECT
        FROM m
        GROUP_BY m.NAME
        HAVING (m.NR_OF_RELEASES IS 1990))
        .isEqualTo(expectedBase + "GROUP BY m.name HAVING m.nr_of_releases=? ", "1990")

    (SELECT
        FROM m
        GROUP_BY arrayOf(m.NAME, m.AUTHOR)
        HAVING (m.NR_OF_RELEASES IS 1990))
        .isEqualTo(expectedBase + "GROUP BY m.name,m.author HAVING m.nr_of_releases=? ", "1990")

    (SELECT
        FROM m
        LEFT_JOIN IMMUTABLE_VALUE_WITH_FIELDS
        GROUP_BY arrayOf(m.NAME, m.AUTHOR)
        HAVING (m.NR_OF_RELEASES IS IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
        .isEqualTo(expectedBase + "LEFT JOIN immutable_value_with_fields GROUP BY m.name,m.author HAVING m.nr_of_releases=immutable_value_with_fields.integer ")

    (SELECT
        FROM m
        LEFT_JOIN s
        GROUP_BY arrayOf(m.NAME, m.AUTHOR)
        HAVING (m.NR_OF_RELEASES IS s.INTEGER))
        .isEqualTo(expectedBase + "LEFT JOIN immutable_value_with_fields AS s GROUP BY m.name,m.author HAVING m.nr_of_releases=s.integer ")

    (SELECT
        FROM m
        LEFT_JOIN AUTHOR
        GROUP_BY AUTHOR.BOXED_BOOLEAN
        HAVING (AUTHOR.PRIMITIVE_BOOLEAN IS true))
        .isEqualTo(expectedBase + "LEFT JOIN author GROUP BY author.boxed_boolean HAVING author.primitive_boolean=? ", BooleanTransformer.objectToDbValue(true)!!.toString())

    (SELECT
        FROM m
        LEFT_JOIN a
        GROUP_BY a.BOXED_BOOLEAN
        HAVING (a.PRIMITIVE_BOOLEAN IS true))
        .isEqualTo(expectedBase + "LEFT JOIN author AS a GROUP BY a.boxed_boolean HAVING a.primitive_boolean=? ", BooleanTransformer.objectToDbValue(true)!!.toString())

    (SELECT
        FROM m
        LEFT_JOIN AUTHOR
        GROUP_BY AUTHOR.BOXED_BOOLEAN
        HAVING (AUTHOR.PRIMITIVE_BOOLEAN IS AUTHOR.PRIMITIVE_BOOLEAN))
        .isEqualTo(expectedBase + "LEFT JOIN author GROUP BY author.boxed_boolean HAVING author.primitive_boolean=author.primitive_boolean ")

    (SELECT
        FROM m
        LEFT_JOIN a
        GROUP_BY a.BOXED_BOOLEAN
        HAVING (a.PRIMITIVE_BOOLEAN IS a.PRIMITIVE_BOOLEAN))
        .isEqualTo(expectedBase + "LEFT JOIN author AS a GROUP BY a.boxed_boolean HAVING a.primitive_boolean=a.primitive_boolean ")
  }

  @Test
  fun orderByTest() {
    val expectedBase = "SELECT * FROM magazine "

    var expected = expectedBase + "ORDER BY magazine.nr_of_releases ASC "
    (SELECT
        FROM MAGAZINE
        ORDER_BY MAGAZINE.NR_OF_RELEASES.asc())
        .isEqualTo(expected)

    expected = expectedBase + "ORDER BY magazine.nr_of_releases ASC,magazine.name ASC "
    (SELECT
        FROM MAGAZINE
        ORDER_BY arrayOf(MAGAZINE.NR_OF_RELEASES.asc(), MAGAZINE.NAME.asc()))
        .isEqualTo(expected)

    expected = expectedBase + "ORDER BY magazine.nr_of_releases ASC,magazine.name ASC,magazine.author ASC "
    (SELECT
        FROM MAGAZINE
        ORDER_BY arrayOf(MAGAZINE.NR_OF_RELEASES.asc(), MAGAZINE.NAME.asc(), MAGAZINE.AUTHOR.asc()))
        .isEqualTo(expected)

    expected = expectedBase + "ORDER BY magazine.nr_of_releases DESC "
    (SELECT
        FROM MAGAZINE
        ORDER_BY MAGAZINE.NR_OF_RELEASES.desc())
        .isEqualTo(expected)

    expected = expectedBase + "ORDER BY magazine.nr_of_releases DESC,magazine.name ASC "
    (SELECT
        FROM MAGAZINE
        ORDER_BY arrayOf(MAGAZINE.NR_OF_RELEASES.desc(), MAGAZINE.NAME.asc()))
        .isEqualTo(expected)

    expected = expectedBase + "ORDER BY magazine.nr_of_releases ASC,magazine.name DESC "
    (SELECT
        FROM MAGAZINE
        ORDER_BY arrayOf(MAGAZINE.NR_OF_RELEASES.asc(), MAGAZINE.NAME.desc()))
        .isEqualTo(expected)

    expected = expectedBase + "ORDER BY trim(magazine.nr_of_releases) DESC "
    (SELECT
        FROM MAGAZINE
        ORDER_BY MAGAZINE.NR_OF_RELEASES.trim().desc())
        .isEqualTo(expected)

    expected = expectedBase + "ORDER BY magazine.nr_of_releases || ' ' || magazine.name ASC "
    (SELECT
        FROM MAGAZINE
        ORDER_BY ((MAGAZINE.NR_OF_RELEASES concat " ".value) concat MAGAZINE.NAME).asc())
        .isEqualTo(expected)
  }

  @Test
  fun limitTest() {
    val expectedBase = "SELECT * FROM magazine "

    (SELECT
        FROM MAGAZINE
        LIMIT 4)
        .isEqualTo(expectedBase + "LIMIT 4 ")

    (SELECT
        FROM MAGAZINE
        LIMIT 55
        OFFSET 6)
        .isEqualTo(expectedBase + "LIMIT 55 OFFSET 6 ")
  }

  @Test
  fun simpleSubquery() {
    assertSimpleSubquery("=") { AUTHOR.NAME IS it }

    assertSimpleSubquery("!=") { AUTHOR.NAME IS_NOT it }

    assertSimpleSubquery(" IN ") { AUTHOR.NAME IN it }

    assertSimpleSubquery(" NOT IN ") { AUTHOR.NAME NOT_IN it }
  }

  private fun assertSimpleSubquery(operator: String, callback: (SelectNode<String, Select1, *>) -> Expr) {
    val expectedBase = "SELECT * FROM author WHERE author.name%s(SELECT immutable_value_with_fields.string_value FROM immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.STRING_VALUE
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM AUTHOR
        WHERE callback(subQuery))
        .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun numericSubquery() {
    assertSameTypeNumericSubquery("=") { MAGAZINE.NR_OF_RELEASES IS it }
    assertSameTypeNumericSubquery("!=") { MAGAZINE.NR_OF_RELEASES IS_NOT it }
    assertSameTypeNumericSubquery(" IN ") { MAGAZINE.NR_OF_RELEASES IN it }
    assertSameTypeNumericSubquery(" NOT IN ") { MAGAZINE.NR_OF_RELEASES NOT_IN it }
    assertSameTypeNumericSubquery(">") { MAGAZINE.NR_OF_RELEASES GREATER_THAN it }
    assertSameTypeNumericSubquery(">=") { MAGAZINE.NR_OF_RELEASES GREATER_OR_EQUAL it }
    assertSameTypeNumericSubquery("<") { MAGAZINE.NR_OF_RELEASES LESS_THAN it }
    assertSameTypeNumericSubquery("<=") { MAGAZINE.NR_OF_RELEASES LESS_OR_EQUAL it }

    assertEquivalentTypeNumericSubquery(">") { MAGAZINE.NR_OF_RELEASES GREATER_THAN it }
    assertEquivalentTypeNumericSubquery(">=") { MAGAZINE.NR_OF_RELEASES GREATER_OR_EQUAL it }
    assertEquivalentTypeNumericSubquery("<") { MAGAZINE.NR_OF_RELEASES LESS_THAN it }
    assertEquivalentTypeNumericSubquery("<=") { MAGAZINE.NR_OF_RELEASES LESS_OR_EQUAL it }
  }

  private fun assertSameTypeNumericSubquery(operator: String, callback: (SelectNode<Int, Select1, *>) -> Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.nr_of_releases%s(SELECT immutable_value_with_fields.integer FROM immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM MAGAZINE
        WHERE callback(subQuery))
        .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertEquivalentTypeNumericSubquery(operator: String, callback: (SelectNode<out Number, Select1, *>) -> Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.nr_of_releases%s(SELECT immutable_value_with_fields.a_double FROM immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM MAGAZINE
        WHERE callback(subQuery))
        .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun complexSubquery() {
    assertSameTypeComplexSubquery("=") { MAGAZINE.AUTHOR IS it }
    assertSameTypeComplexSubquery("!=") { MAGAZINE.AUTHOR IS_NOT it }
    assertSameTypeComplexSubquery(" IN ") { MAGAZINE.AUTHOR IN it }
    assertSameTypeComplexSubquery(" NOT IN ") { MAGAZINE.AUTHOR NOT_IN it }
    assertSameTypeComplexSubquery(">") { MAGAZINE.AUTHOR GREATER_THAN it }
    assertSameTypeComplexSubquery(">=") { MAGAZINE.AUTHOR GREATER_OR_EQUAL it }
    assertSameTypeComplexSubquery("<") { MAGAZINE.AUTHOR LESS_THAN it }
    assertSameTypeComplexSubquery("<=") { MAGAZINE.AUTHOR LESS_OR_EQUAL it }


    assertIdTypeComplexSubquery("=") { MAGAZINE.AUTHOR IS it }
    assertIdTypeComplexSubquery("!=") { MAGAZINE.AUTHOR IS_NOT it }
    assertIdTypeComplexSubquery(" IN ") { MAGAZINE.AUTHOR IN it }
    assertIdTypeComplexSubquery(" NOT IN ") { MAGAZINE.AUTHOR NOT_IN it }
    assertIdTypeComplexSubquery(">") { MAGAZINE.AUTHOR GREATER_THAN it }
    assertIdTypeComplexSubquery(">=") { MAGAZINE.AUTHOR GREATER_OR_EQUAL it }
    assertIdTypeComplexSubquery("<") { MAGAZINE.AUTHOR LESS_THAN it }
    assertIdTypeComplexSubquery("<=") { MAGAZINE.AUTHOR LESS_OR_EQUAL it }


    assertEquivalentTypeComplexSubquery("=") { MAGAZINE.AUTHOR IS it }
    assertEquivalentTypeComplexSubquery("!=") { MAGAZINE.AUTHOR IS_NOT it }
    assertEquivalentTypeComplexSubquery(" IN ") { MAGAZINE.AUTHOR IN it }
    assertEquivalentTypeComplexSubquery(" NOT IN ") { MAGAZINE.AUTHOR NOT_IN it }
    assertEquivalentTypeComplexSubquery(">") { MAGAZINE.AUTHOR GREATER_THAN it }
    assertEquivalentTypeComplexSubquery(">=") { MAGAZINE.AUTHOR GREATER_OR_EQUAL it }
    assertEquivalentTypeComplexSubquery("<") { MAGAZINE.AUTHOR LESS_THAN it }
    assertEquivalentTypeComplexSubquery("<=") { MAGAZINE.AUTHOR LESS_OR_EQUAL it }
  }

  private fun assertSameTypeComplexSubquery(operator: String, callback: (SelectNode<Long, Select1, *>) -> Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.author%s(SELECT magazine.author FROM magazine ) "
    val subQuery = (SELECT
        COLUMN MAGAZINE.AUTHOR
        FROM MAGAZINE)

    (SELECT
        FROM MAGAZINE
        WHERE callback(subQuery))
        .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertIdTypeComplexSubquery(operator: String, callback: (SelectNode<Long, Select1, *>) -> Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.author%s(SELECT magazine.id FROM magazine ) "
    val subQuery = (SELECT
        COLUMN MAGAZINE.ID
        FROM MAGAZINE)

    (SELECT
        FROM MAGAZINE
        WHERE callback(subQuery))
        .isEqualTo(String.format(expectedBase, operator))
  }

  private fun assertEquivalentTypeComplexSubquery(operator: String, callback: (SelectNode<Int, Select1, *>) -> Expr) {
    val expectedBase = "SELECT * FROM magazine WHERE magazine.author%s(SELECT immutable_value_with_fields.integer FROM immutable_value_with_fields ) "
    val subQuery = (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.INTEGER
        FROM IMMUTABLE_VALUE_WITH_FIELDS)

    (SELECT
        FROM MAGAZINE
        WHERE callback(subQuery))
        .isEqualTo(String.format(expectedBase, operator))
  }

  @Test
  fun unaryMinus() {
    (SELECT
        COLUMN -(MAGAZINE.NR_OF_RELEASES - 42)
        FROM MAGAZINE)
        .isEqualTo("SELECT -((magazine.nr_of_releases-42)) FROM magazine ")

    (SELECT
        COLUMN -(MAGAZINE.NR_OF_RELEASES - -42)
        FROM MAGAZINE)
        .isEqualTo("SELECT -((magazine.nr_of_releases-(-42))) FROM magazine ")

    (SELECT
        COLUMN -(MAGAZINE.NR_OF_RELEASES - 42.value)
        FROM MAGAZINE)
        .isEqualTo("SELECT -((magazine.nr_of_releases-42)) FROM magazine ")

    (SELECT
        COLUMN -(MAGAZINE.NR_OF_RELEASES - (-42).value)
        FROM MAGAZINE)
        .isEqualTo("SELECT -((magazine.nr_of_releases-(-42))) FROM magazine ")
  }

  @Test
  fun avgFunction() {
    (SELECT
        COLUMN IMMUTABLE_VALUE_WITH_FIELDS.ID
        FROM IMMUTABLE_VALUE_WITH_FIELDS
        WHERE ((avg(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER) GREATER_THAN 5555.0)
        AND (avg(IMMUTABLE_VALUE_WITH_FIELDS.A_DOUBLE) LESS_THAN 8888.8)))
        .isEqualTo("SELECT immutable_value_with_fields.id FROM immutable_value_with_fields WHERE (avg(immutable_value_with_fields.integer)>? AND avg(immutable_value_with_fields.a_double)<?) ")
  }

  @Test
  fun concatFunction() {
    var expected = "SELECT author._id || author.name FROM author "
    (SELECT
        COLUMN (AUTHOR._ID concat AUTHOR.NAME)
        FROM AUTHOR)
        .isEqualTo(expected)

    expected = "SELECT author._id || author.name || author.primitive_boolean FROM author "
    (SELECT
        COLUMN concat(AUTHOR._ID, AUTHOR.NAME, AUTHOR.PRIMITIVE_BOOLEAN)
        FROM AUTHOR)
        .isEqualTo(expected)

    (SELECT
        COLUMN ((AUTHOR._ID concat AUTHOR.NAME) concat AUTHOR.PRIMITIVE_BOOLEAN)
        FROM AUTHOR)
        .isEqualTo(expected)
  }

  @Test
  fun replaceFunction() {
    var expected = "SELECT replace(author.name,'a','____') FROM author "
    (SELECT
        COLUMN AUTHOR.NAME.replace("a" with "____")
        FROM AUTHOR)
        .isEqualTo(expected)

    expected = "SELECT replace(author.name,'a','____') AS 'asd' FROM author "
    (SELECT
        COLUMN (AUTHOR.NAME.replace("a" with "____") AS "asd")
        FROM AUTHOR)
        .isEqualTo(expected)
  }

  @Test
  fun valColumn() {
    var expected = "SELECT author._id || ' ' || author.name FROM author "
    val strVal = " ".value
    (SELECT
        COLUMN concat(AUTHOR._ID, strVal, AUTHOR.NAME)
        FROM AUTHOR)
        .isEqualTo(expected)
    strVal.parsesWith(STRING)

    expected = "SELECT author._id || 3 || author.name FROM author "
    val intVal = 3.value
    (SELECT
        COLUMN concat(AUTHOR._ID, intVal, AUTHOR.NAME)
        FROM AUTHOR)
        .isEqualTo(expected)
    intVal.parsesWith(INTEGER)

    expected = "SELECT author._id || 3 || author.name FROM author "
    val longVal = 3L.value
    (SELECT
        COLUMN concat(AUTHOR._ID, longVal, AUTHOR.NAME)
        FROM AUTHOR)
        .isEqualTo(expected)
    longVal.parsesWith(LONG)

    val s: Short = 3
    expected = "SELECT author._id || 3 || author.name FROM author "
    val shortVal = s.value
    (SELECT
        COLUMN concat(AUTHOR._ID, shortVal, AUTHOR.NAME)
        FROM AUTHOR)
        .isEqualTo(expected)
    shortVal.parsesWith(SHORT)

    val b: Byte = 3
    expected = "SELECT author._id || 3 || author.name FROM author "
    val byteVal = b.value
    (SELECT
        COLUMN concat(AUTHOR._ID, byteVal, AUTHOR.NAME)
        FROM AUTHOR)
        .isEqualTo(expected)
    byteVal.parsesWith(BYTE)

    val f = 3.3f
    expected = "SELECT author._id || 3.3 || author.name FROM author "
    val floatVal = f.value
    (SELECT
        COLUMN concat(AUTHOR._ID, floatVal, AUTHOR.NAME)
        FROM AUTHOR)
        .isEqualTo(expected)
    floatVal.parsesWith(FLOAT)

    val d = 3.3
    expected = "SELECT author._id || 3.3 || author.name FROM author "
    val doubleVal = d.value
    (SELECT
        COLUMN concat(AUTHOR._ID, doubleVal, AUTHOR.NAME)
        FROM AUTHOR)
        .isEqualTo(expected)
    doubleVal.parsesWith(DOUBLE)
  }

  @Test
  fun addFunction() {
    assertArithmeticExpression('+',
        { v1, v2 -> v1 + v2 },
        { v1, v2 -> v1 + v2 },
        { v1, v2 -> v1 + v2 })
  }

  @Test
  fun subFunction() {
    assertArithmeticExpression('-',
        { v1, v2 -> v1 - v2 },
        { v1, v2 -> v1 - v2 },
        { v1, v2 -> v1 - v2 })
  }

  @Test
  fun mulFunction() {
    assertArithmeticExpression('*',
        { v1, v2 -> v1 * v2 },
        { v1, v2 -> v1 * v2 },
        { v1, v2 -> v1 * v2 })
  }

  @Test
  fun divFunction() {
    assertArithmeticExpression('/',
        { v1, v2 -> v1 / v2 },
        { v1, v2 -> v1 / v2 },
        { v1, v2 -> v1 / v2 })
  }

  @Test
  fun modFunction() {
    assertArithmeticExpression('%',
        { v1, v2 -> v1 % v2 },
        { v1, v2 -> v1 % v2 },
        { v1, v2 -> v1 % v2 })
  }

  @Test
  fun numericArithmeticExpressionsChained() {
    var expected = "SELECT ((((1+2)*(5-3))/2.0)%10.0) FROM immutable_value_with_fields "
    (SELECT
        COLUMN ((((1.value + 2) * (5.value - 3)) / 2.0) % 10.0)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
        .isEqualTo(expected)

    expected = "SELECT ((((immutable_value_with_fields.integer+2)*(5-3))/2.0)%10.0) FROM immutable_value_with_fields "
    (SELECT
        COLUMN ((((IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 2) * (5.value - 3)) / 2.0) % 10.0)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
        .isEqualTo(expected)
  }

  private fun assertArithmeticExpression(op: Char,
                                         columnCallback: (NumericColumn<Int, Int, Number, ImmutableValueWithFields, *>, NumericColumn<Short, Short, Number, ImmutableValueWithFields, *>) -> NumericColumn<*, *, *, *, *>,
                                         columnValueCallback: (NumericColumn<Int, Int, Number, ImmutableValueWithFields, *>, NumericColumn<Long, Long, Number, *, *>) -> NumericColumn<*, *, *, *, *>,
                                         valueCallback: (NumericColumn<Int, Int, Number, ImmutableValueWithFields, *>, Int) -> NumericColumn<*, *, *, *, *>) {
    var expected = String.format("SELECT (immutable_value_with_fields.integer%simmutable_value_with_fields.a_short) FROM immutable_value_with_fields ", op)
    (SELECT
        COLUMN columnCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, IMMUTABLE_VALUE_WITH_FIELDS.A_SHORT)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
        .isEqualTo(expected)

    expected = String.format("SELECT (immutable_value_with_fields.integer%s5) FROM immutable_value_with_fields ", op)
    (SELECT
        COLUMN columnValueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, 5L.value)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
        .isEqualTo(expected)

    expected = String.format("SELECT (immutable_value_with_fields.integer%s(-5)) FROM immutable_value_with_fields ", op)
    (SELECT
        COLUMN columnValueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, (-5L).value)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
        .isEqualTo(expected)

    expected = String.format("SELECT (immutable_value_with_fields.integer%s5) FROM immutable_value_with_fields ", op)
    (SELECT
        COLUMN valueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, 5)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
        .isEqualTo(expected)

    expected = String.format("SELECT (immutable_value_with_fields.integer%s(-5)) FROM immutable_value_with_fields ", op)
    (SELECT
        COLUMN valueCallback(IMMUTABLE_VALUE_WITH_FIELDS.INTEGER, -5)
        FROM IMMUTABLE_VALUE_WITH_FIELDS)
        .isEqualTo(expected)
  }
}