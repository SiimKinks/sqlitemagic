package com.siimkinks.sqlitemagic;

import android.database.SQLException;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.Select.Select1;
import com.siimkinks.sqlitemagic.model.Author;
import com.siimkinks.sqlitemagic.model.SimpleAllValuesMutable;
import com.siimkinks.sqlitemagic.transformer.BooleanTransformer;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.Select.abs;
import static com.siimkinks.sqlitemagic.Select.avg;
import static com.siimkinks.sqlitemagic.Select.avgDistinct;
import static com.siimkinks.sqlitemagic.Select.concat;
import static com.siimkinks.sqlitemagic.Select.count;
import static com.siimkinks.sqlitemagic.Select.countDistinct;
import static com.siimkinks.sqlitemagic.Select.groupConcat;
import static com.siimkinks.sqlitemagic.Select.groupConcatDistinct;
import static com.siimkinks.sqlitemagic.Select.length;
import static com.siimkinks.sqlitemagic.Select.lower;
import static com.siimkinks.sqlitemagic.Select.max;
import static com.siimkinks.sqlitemagic.Select.maxDistinct;
import static com.siimkinks.sqlitemagic.Select.min;
import static com.siimkinks.sqlitemagic.Select.minDistinct;
import static com.siimkinks.sqlitemagic.Select.sum;
import static com.siimkinks.sqlitemagic.Select.sumDistinct;
import static com.siimkinks.sqlitemagic.Select.upper;
import static com.siimkinks.sqlitemagic.Select.val;
import static com.siimkinks.sqlitemagic.SimpleAllValuesMutableTable.SIMPLE_ALL_VALUES_MUTABLE;
import static com.siimkinks.sqlitemagic.Utils.BYTE_PARSER;
import static com.siimkinks.sqlitemagic.Utils.DOUBLE_PARSER;
import static com.siimkinks.sqlitemagic.Utils.FLOAT_PARSER;
import static com.siimkinks.sqlitemagic.Utils.INTEGER_PARSER;
import static com.siimkinks.sqlitemagic.Utils.LONG_PARSER;
import static com.siimkinks.sqlitemagic.Utils.SHORT_PARSER;
import static com.siimkinks.sqlitemagic.Utils.STRING_PARSER;
import static org.mockito.Mockito.mock;

public final class SelectSqlBuilderTest {
  @Before
  public void setUp() {
    final SqliteMagic instance = SqliteMagic.SingletonHolder.instance;
    instance.defaultConnection = mock(DbConnectionImpl.class);
  }

  @Test
  public void selectAllFrom() {
    final String expected = "SELECT * FROM book ";

    SelectSqlNode sqlNode = Select.from(BOOK);
    assertSql(sqlNode, expected);

    sqlNode = Select.all().from(BOOK);
    assertSql(sqlNode, expected);
  }

  @Test
  public void selectAllFromAliased() {
    final String expected = "SELECT * FROM book AS b ";

    final BookTable b = BOOK.as("b");
    SelectSqlNode sqlNode = Select.from(b);
    assertSql(sqlNode, expected);

    sqlNode = Select.all().from(b);
    assertSql(sqlNode, expected);
  }

  @Test
  public void selectColumns() {
    final String expected = "SELECT book.author,book.title,book.nr_of_releases FROM book ";

    SelectSqlNode sqlNode = Select
        .columns(BOOK.AUTHOR, BOOK.TITLE, BOOK.NR_OF_RELEASES)
        .from(BOOK);
    assertSql(sqlNode, expected);

    String expectedDistinct = "SELECT DISTINCT book.author,book.title,book.nr_of_releases FROM book ";
    sqlNode = Select
        .distinct(BOOK.AUTHOR, BOOK.TITLE, BOOK.NR_OF_RELEASES)
        .from(BOOK);
    assertSql(sqlNode, expectedDistinct);

    expectedDistinct = "SELECT DISTINCT * FROM book ";
    sqlNode = Select
        .distinct()
        .from(BOOK);
    assertSql(sqlNode, expectedDistinct);
  }

  @Test
  public void selectColumnsAliased() {
    final String expected = "SELECT b.author,b.title,b.nr_of_releases FROM book AS b ";

    final BookTable b = BOOK.as("b");
    SelectSqlNode sqlNode = Select
        .columns(b.AUTHOR, b.TITLE, b.NR_OF_RELEASES)
        .from(b);
    assertSql(sqlNode, expected);

    String expectedDistinct = "SELECT DISTINCT b.author,b.title,b.nr_of_releases FROM book AS b ";
    sqlNode = Select
        .distinct(b.AUTHOR, b.TITLE, b.NR_OF_RELEASES)
        .from(b);
    assertSql(sqlNode, expectedDistinct);

    expectedDistinct = "SELECT DISTINCT * FROM book AS b ";
    sqlNode = Select
        .distinct()
        .from(b);
    assertSql(sqlNode, expectedDistinct);
  }

  @Test
  public void joins() {
    String expected = "SELECT * FROM book LEFT OUTER JOIN author ON book.author=author.id ";

    Select.From sqlNode = Select
        .from(BOOK)
        .leftOuterJoin(AUTHOR.on(BOOK.AUTHOR.is(AUTHOR.ID)));
    assertSql(sqlNode, expected);

    sqlNode = sqlNode.naturalJoin(AUTHOR);
    expected += "NATURAL JOIN author ";
    assertSql(sqlNode, expected);

    sqlNode = sqlNode.innerJoin(AUTHOR.using(BOOK.AUTHOR, AUTHOR.ID));
    expected += "INNER JOIN author USING (author,id) ";
    assertSql(sqlNode, expected);

    sqlNode = sqlNode.crossJoin(AUTHOR.using(BOOK.AUTHOR, AUTHOR.ID));
    expected += "CROSS JOIN author USING (author,id) ";
    assertSql(sqlNode, expected);

    expected = "SELECT * FROM complex_object_with_same_leafs , book , author ";
    sqlNode = Select.from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .join(BOOK)
        .join(AUTHOR);
    assertSql(sqlNode, expected);
  }

  @Test
  public void joinsAliased() {
    String expected = "SELECT * FROM book AS b LEFT OUTER JOIN author AS a ON b.author=a.id ";

    final BookTable b = BOOK.as("b");
    final AuthorTable author = AUTHOR.as("a");
    Select.From sqlNode = Select
        .from(b)
        .leftOuterJoin(author.on(b.AUTHOR.is(author.ID)));
    assertSql(sqlNode, expected);

    sqlNode = sqlNode.naturalJoin(author);
    expected += "NATURAL JOIN author AS a ";
    assertSql(sqlNode, expected);

    sqlNode = sqlNode.innerJoin(author.using(b.AUTHOR, author.ID));
    expected += "INNER JOIN author AS a USING (author,id) ";
    assertSql(sqlNode, expected);

    sqlNode = sqlNode.crossJoin(author.using(b.AUTHOR, author.ID));
    expected += "CROSS JOIN author AS a USING (author,id) ";
    assertSql(sqlNode, expected);

    expected = "SELECT * FROM complex_object_with_same_leafs AS c , book AS b , author AS a ";
    sqlNode = Select.from(COMPLEX_OBJECT_WITH_SAME_LEAFS.as("c"))
        .join(b)
        .join(author);
    assertSql(sqlNode, expected);
  }

  @Test
  public void invalidWhereInCondition() {
    int exceptions = 0;
    Select.From selectBase = Select.from(BOOK);

    try {
      final Collection<String> values = new ArrayList<>();
      generateSql(selectBase.where(BOOK.TITLE.in(values)));
    } catch (SQLException e) {
      exceptions++;
    }
    try {
      final String[] values = {};
      generateSql(selectBase.where(BOOK.TITLE.in(values)));
    } catch (SQLException e) {
      exceptions++;
    }

    assertThat(exceptions).isEqualTo(2);
  }

  @Test
  public void invalidWhereNotInCondition() {
    int exceptions = 0;
    Select.From selectBase = Select.from(BOOK);

    try {
      final Collection<String> values = new ArrayList<>();
      generateSql(selectBase.where(BOOK.TITLE.notIn(values)));
    } catch (SQLException e) {
      exceptions++;
    }
    try {
      final String[] values = {};
      generateSql(selectBase.where(BOOK.TITLE.notIn(values)));
    } catch (SQLException e) {
      exceptions++;
    }

    assertThat(exceptions).isEqualTo(2);
  }

  @Test
  public void whereCondition() {
    final String expectedBase = "SELECT * FROM book WHERE ";

    final Expr titleIs = BOOK.TITLE.is("asd");
    final Expr intIs = BOOK.NR_OF_RELEASES.is(1920);
    final Expr titleIsNot = BOOK.TITLE.isNot("asd");
    final Expr titleIsNotNull = BOOK.TITLE.isNotNull();
    final Expr titleIsNull = BOOK.TITLE.isNull();
    final Expr titleGlob = BOOK.TITLE.glob("asd");
    final Expr titleLike = BOOK.TITLE.like("asd");
    final Expr lessThan = BOOK.NR_OF_RELEASES.lessThan(1990);
    final Expr greaterThan = BOOK.NR_OF_RELEASES.greaterThan(1990);
    final Expr between = BOOK.NR_OF_RELEASES.between(1910).and(2000);
    final Expr notBetween = BOOK.NR_OF_RELEASES.notBetween(1910).and(2000);
    final Expr in = BOOK.NR_OF_RELEASES.in(1910, 1999, 1920);
    final Expr notIn = BOOK.NR_OF_RELEASES.notIn(1910, 1999, 1920);
    final Expr oneIn = BOOK.NR_OF_RELEASES.in(1910);
    final Expr oneNotIn = BOOK.NR_OF_RELEASES.notIn(1910);

    String expected = expectedBase + "book.title=? ";
    SelectSqlNode sqlNode = Select.from(BOOK).where(titleIs);
    assertSql(sqlNode, expected, "asd");

    expected = expectedBase + "(book.title=? AND book.nr_of_releases=?) ";
    sqlNode = Select.from(BOOK).where(titleIs.and(intIs));
    assertSql(sqlNode, expected, "asd", "1920");

    expected = expectedBase + "book.nr_of_releases BETWEEN ? AND ? ";
    sqlNode = Select.from(BOOK).where(between);
    assertSql(sqlNode, expected, "1910", "2000");

    expected = expectedBase + "book.nr_of_releases NOT BETWEEN ? AND ? ";
    sqlNode = Select.from(BOOK).where(notBetween);
    assertSql(sqlNode, expected, "1910", "2000");

    expected = expectedBase + "book.nr_of_releases IN (?,?,?) ";
    sqlNode = Select.from(BOOK).where(in);
    assertSql(sqlNode, expected, "1910", "1999", "1920");

    String expectedLongWhereClause = "((((((((book.title!=? AND book.title IS NOT NULL) AND book.title IS NULL) " +
        "AND book.title GLOB ?) AND book.title LIKE ?) AND book.nr_of_releases<?) AND book.nr_of_releases>?) " +
        "AND book.nr_of_releases BETWEEN ? AND ?) AND book.nr_of_releases NOT BETWEEN ? AND ?) ";
    expected = expectedBase + expectedLongWhereClause;
    sqlNode = Select.from(BOOK).where(titleIsNot.and(titleIsNotNull).and(titleIsNull).and(titleGlob)
        .and(titleLike).and(lessThan).and(greaterThan).and(between).and(notBetween));
    assertSql(sqlNode, expected, "asd", "asd", "asd", "1990", "1990", "1910", "2000", "1910", "2000");

    expected = expectedBase + "(((book.nr_of_releases IN (?,?,?) AND book.nr_of_releases NOT IN (?,?,?)) " +
        "AND book.nr_of_releases IN (?)) AND book.nr_of_releases NOT IN (?)) ";
    sqlNode = Select.from(BOOK).where(in.and(notIn).and(oneIn).and(oneNotIn));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "1910", "1999", "1920", "1910", "1910");

    expected = expectedBase + "(book.nr_of_releases IN (?,?,?) OR book.title!=?) ";
    sqlNode = Select.from(BOOK).where(in.or(titleIsNot));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "asd");

    expected = expectedBase + "(book.nr_of_releases IN (?,?,?) AND (book.title!=? AND book.title IS NOT NULL)) ";
    sqlNode = Select.from(BOOK).where(in.and(titleIsNot.and(titleIsNotNull)));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "asd");

    expected = expectedBase + "(book.nr_of_releases IN (?,?,?) OR (book.nr_of_releases BETWEEN ? AND ? AND book.title IS NOT NULL)) ";
    sqlNode = Select.from(BOOK).where(in.or(between.and(titleIsNotNull)));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "1910", "2000");

    expected = expectedBase + "(book.nr_of_releases IN (?,?,?) AND ((book.title!=? OR book.title IS NOT NULL) OR book.title IS NULL)) ";
    sqlNode = Select.from(BOOK).where(in.and(titleIsNot.or(titleIsNotNull).or(titleIsNull)));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "asd");

    expected = expectedBase + "(book.nr_of_releases IN (?,?,?) OR book.title!=?) ";
    sqlNode = Select.from(BOOK).where(in.or(titleIsNot));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "asd");
  }

  @Test
  public void whereConditionAliased() {
    final String expectedBase = "SELECT * FROM book AS b WHERE ";

    final BookTable b = BOOK.as("b");
    final Expr titleIs = b.TITLE.is("asd");
    final Expr intIs = b.NR_OF_RELEASES.is(1920);
    final Expr titleIsNot = b.TITLE.isNot("asd");
    final Expr titleIsNotNull = b.TITLE.isNotNull();
    final Expr titleIsNull = b.TITLE.isNull();
    final Expr titleGlob = b.TITLE.glob("asd");
    final Expr titleLike = b.TITLE.like("asd");
    final Expr lessThan = b.NR_OF_RELEASES.lessThan(1990);
    final Expr greaterThan = b.NR_OF_RELEASES.greaterThan(1990);
    final Expr between = b.NR_OF_RELEASES.between(1910).and(2000);
    final Expr notBetween = b.NR_OF_RELEASES.notBetween(1910).and(2000);
    final Expr in = b.NR_OF_RELEASES.in(1910, 1999, 1920);
    final Expr notIn = b.NR_OF_RELEASES.notIn(1910, 1999, 1920);
    final Expr oneIn = b.NR_OF_RELEASES.in(1910);
    final Expr oneNotIn = b.NR_OF_RELEASES.notIn(1910);

    String expected = expectedBase + "b.title=? ";
    SelectSqlNode sqlNode = Select.from(b).where(titleIs);
    assertSql(sqlNode, expected, "asd");

    expected = expectedBase + "(b.title=? AND b.nr_of_releases=?) ";
    sqlNode = Select.from(b).where(titleIs.and(intIs));
    assertSql(sqlNode, expected, "asd", "1920");

    expected = expectedBase + "b.nr_of_releases BETWEEN ? AND ? ";
    sqlNode = Select.from(b).where(between);
    assertSql(sqlNode, expected, "1910", "2000");

    expected = expectedBase + "b.nr_of_releases NOT BETWEEN ? AND ? ";
    sqlNode = Select.from(b).where(notBetween);
    assertSql(sqlNode, expected, "1910", "2000");

    expected = expectedBase + "b.nr_of_releases IN (?,?,?) ";
    sqlNode = Select.from(b).where(in);
    assertSql(sqlNode, expected, "1910", "1999", "1920");

    String expectedLongWhereClause = "((((((((b.title!=? AND b.title IS NOT NULL) AND b.title IS NULL) " +
        "AND b.title GLOB ?) AND b.title LIKE ?) AND b.nr_of_releases<?) AND b.nr_of_releases>?) " +
        "AND b.nr_of_releases BETWEEN ? AND ?) AND b.nr_of_releases NOT BETWEEN ? AND ?) ";
    expected = expectedBase + expectedLongWhereClause;
    sqlNode = Select.from(b).where(titleIsNot.and(titleIsNotNull).and(titleIsNull).and(titleGlob)
        .and(titleLike).and(lessThan).and(greaterThan).and(between).and(notBetween));
    assertSql(sqlNode, expected, "asd", "asd", "asd", "1990", "1990", "1910", "2000", "1910", "2000");

    expected = expectedBase + "(((b.nr_of_releases IN (?,?,?) AND b.nr_of_releases NOT IN (?,?,?)) " +
        "AND b.nr_of_releases IN (?)) AND b.nr_of_releases NOT IN (?)) ";
    sqlNode = Select.from(b).where(in.and(notIn).and(oneIn).and(oneNotIn));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "1910", "1999", "1920", "1910", "1910");

    expected = expectedBase + "(b.nr_of_releases IN (?,?,?) OR b.title!=?) ";
    sqlNode = Select.from(b).where(in.or(titleIsNot));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "asd");

    expected = expectedBase + "(b.nr_of_releases IN (?,?,?) AND (b.title!=? AND b.title IS NOT NULL)) ";
    sqlNode = Select.from(b).where(in.and(titleIsNot.and(titleIsNotNull)));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "asd");

    expected = expectedBase + "(b.nr_of_releases IN (?,?,?) OR (b.nr_of_releases BETWEEN ? AND ? AND b.title IS NOT NULL)) ";
    sqlNode = Select.from(b).where(in.or(between.and(titleIsNotNull)));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "1910", "2000");

    expected = expectedBase + "(b.nr_of_releases IN (?,?,?) AND ((b.title!=? OR b.title IS NOT NULL) OR b.title IS NULL)) ";
    sqlNode = Select.from(b).where(in.and(titleIsNot.or(titleIsNotNull).or(titleIsNull)));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "asd");

    expected = expectedBase + "(b.nr_of_releases IN (?,?,?) OR b.title!=?) ";
    sqlNode = Select.from(b).where(in.or(titleIsNot));
    assertSql(sqlNode, expected, "1910", "1999", "1920", "asd");
  }

  @Test
  public void columnNotRedefinedWhenAliased() {
    String expected = "SELECT book.*,book.title || ' ' || book.nr_of_releases AS 'search_column' " +
        "FROM book " +
        "WHERE book.title=search_column ";
    final Column<String, String, CharSequence, ?> searchColumn = concat(BOOK.TITLE, val(" "), BOOK.NR_OF_RELEASES).as("search_column");
    SelectSqlNode sqlNode = Select
        .columns(BOOK.all(), searchColumn)
        .from(BOOK)
        .where(BOOK.TITLE.is(searchColumn));
    assertSql(sqlNode, expected);

    expected = "SELECT trim(book.title) AS 'trimmed_title' " +
        "FROM book " +
        "WHERE trim(book.title)=trimmed_title ";
    final Column<String, String, CharSequence, ?> trimmedTitle = BOOK.TITLE.trim().as("trimmed_title");
    sqlNode = Select
        .column(trimmedTitle)
        .from(BOOK)
        .where(BOOK.TITLE.trim().is(trimmedTitle));
    assertSql(sqlNode, expected);
  }

  @Test
  public void betweenComplex() {
    final String expectedBase = "SELECT * FROM book ";
    final Author randomAuthor = Author.newRandom();
    final Author randomAuthor2 = Author.newRandom();

    SelectSqlNode sqlNode = Select.from(BOOK)
        .where(BOOK.AUTHOR.between(randomAuthor).and(randomAuthor2));
    String expected = expectedBase + "WHERE book.author BETWEEN ? AND ? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString(), randomAuthor2.id.toString());

    sqlNode = Select.from(BOOK)
        .where(BOOK.AUTHOR.between(randomAuthor).and(AUTHOR.ID));
    expected = expectedBase + "WHERE book.author BETWEEN ? AND author.id ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());

    sqlNode = Select.from(BOOK)
        .where(BOOK.AUTHOR.between(AUTHOR.ID).and(randomAuthor));
    expected = expectedBase + "WHERE book.author BETWEEN author.id AND ? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());

    sqlNode = Select.from(BOOK)
        .where(BOOK.AUTHOR.between(AUTHOR.ID).and(BOOK.BASE_ID));
    expected = expectedBase + "WHERE book.author BETWEEN author.id AND book.base_id ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(BOOK)
        .where(BOOK.AUTHOR.between(MAGAZINE.AUTHOR).and(BOOK.BASE_ID));
    expected = expectedBase + "WHERE book.author BETWEEN magazine.author AND book.base_id ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(BOOK)
        .where(BOOK.AUTHOR.between(MAGAZINE.AUTHOR).and(BOOK.AUTHOR));
    expected = expectedBase + "WHERE book.author BETWEEN magazine.author AND book.author ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(BOOK)
        .where(BOOK.AUTHOR.between(MAGAZINE.AUTHOR).and(randomAuthor));
    expected = expectedBase + "WHERE book.author BETWEEN magazine.author AND ? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());
  }

  @Test
  public void betweenComplexAliased() {
    final String expectedBase = "SELECT * FROM book AS b ";
    final Author randomAuthor = Author.newRandom();
    final Author randomAuthor2 = Author.newRandom();
    final BookTable b = BOOK.as("b");
    final AuthorTable a = AUTHOR.as("a");
    final MagazineTable m = MAGAZINE.as("m");

    SelectSqlNode sqlNode = Select.from(b)
        .where(b.AUTHOR.between(randomAuthor).and(randomAuthor2));
    String expected = expectedBase + "WHERE b.author BETWEEN ? AND ? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString(), randomAuthor2.id.toString());

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(randomAuthor).and(AUTHOR.ID));
    expected = expectedBase + "WHERE b.author BETWEEN ? AND author.id ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(randomAuthor).and(a.ID));
    expected = expectedBase + "WHERE b.author BETWEEN ? AND a.id ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(AUTHOR.ID).and(randomAuthor));
    expected = expectedBase + "WHERE b.author BETWEEN author.id AND ? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(a.ID).and(randomAuthor));
    expected = expectedBase + "WHERE b.author BETWEEN a.id AND ? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(AUTHOR.ID).and(b.BASE_ID));
    expected = expectedBase + "WHERE b.author BETWEEN author.id AND b.base_id ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(a.ID).and(b.BASE_ID));
    expected = expectedBase + "WHERE b.author BETWEEN a.id AND b.base_id ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(MAGAZINE.AUTHOR).and(b.AUTHOR));
    expected = expectedBase + "WHERE b.author BETWEEN magazine.author AND b.author ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(m.AUTHOR).and(b.AUTHOR));
    expected = expectedBase + "WHERE b.author BETWEEN m.author AND b.author ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(MAGAZINE.AUTHOR).and(randomAuthor));
    expected = expectedBase + "WHERE b.author BETWEEN magazine.author AND ? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());

    sqlNode = Select.from(b)
        .where(b.AUTHOR.between(m.AUTHOR).and(randomAuthor));
    expected = expectedBase + "WHERE b.author BETWEEN m.author AND ? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());
  }

  @Test
  public void rawExpr() {
    String sql = "author.name IS NOT NULL";
    Expr expr = Expr.raw(sql);
    assertRawExpr(sql, expr);

    sql = "author.name = ?";
    expr = Expr.raw(sql, "asd");
    assertRawExpr(sql, expr, "asd");

    sql = "author.name = ? AND author.name != ?";
    expr = Expr.raw(sql, "asd", "dsa");
    assertRawExpr(sql, expr, "asd", "dsa");
  }

  @Test
  public void rawExprWithNonRawExpr() {
    String sql = "author.name = ?";
    Expr expr = AUTHOR.NAME.isNot("dsa").and(Expr.raw(sql, "asd"));
    assertRawExpr("(author.name!=? AND " + sql + ")", expr, "dsa", "asd");

    sql = "author.name = ?";
    expr = Expr.raw(sql, "asd").and(AUTHOR.NAME.isNot("dsa"));
    assertRawExpr("(" + sql + " AND author.name!=?)", expr, "asd", "dsa");

    sql = "author.name IS NOT NULL";
    expr = AUTHOR.NAME.isNot("asd").and(Expr.raw(sql));
    assertRawExpr("(author.name!=? AND " + sql + ")", expr, "asd");

    sql = "author.name IS NOT NULL";
    expr = Expr.raw(sql).and(AUTHOR.NAME.isNot("asd"));
    assertRawExpr("(" + sql + " AND author.name!=?)", expr, "asd");
  }

  private void assertRawExpr(@NonNull String expectedExpr,
                             @NonNull Expr expr,
                             @NonNull String... args) {
    final String expected = "SELECT * FROM author WHERE " + expectedExpr + " ";

    assertSql(Select.from(AUTHOR).where(expr),
        expected,
        args);
  }

  @Test
  public void exprSimple() {
    assertSimpleExpr("=?", AUTHOR.NAME.is("asd"));
    assertSimpleExpr("!=?", AUTHOR.NAME.isNot("asd"));
    assertSimpleExpr(" IN (?)", AUTHOR.NAME.in("asd"));
    assertSimpleExpr(" NOT IN (?)", AUTHOR.NAME.notIn("asd"));

    assertSimpleColumnExpr("=", AUTHOR.NAME.is(BOOK.TITLE));
    assertSimpleColumnExpr("!=", AUTHOR.NAME.isNot(BOOK.TITLE));
  }

  private void assertSimpleExpr(@NonNull String operator, @NonNull Expr expr) {
    final String expectedBase = "SELECT * FROM author WHERE author.name%s ";

    assertSql(Select.from(AUTHOR).where(expr),
        String.format(expectedBase, operator),
        "asd");
  }

  private void assertSimpleColumnExpr(@NonNull String operator, @NonNull Expr expr) {
    final String expectedBase = "SELECT * FROM author WHERE author.name%sbook.title ";

    assertSql(Select.from(AUTHOR).where(expr),
        String.format(expectedBase, operator));
  }

  @Test
  public void numericExprWithSameType() {
    assertNumericExpr("=?", BOOK.NR_OF_RELEASES.is(4));
    assertNumericExpr("!=?", BOOK.NR_OF_RELEASES.isNot(4));
    assertNumericExpr(" IN (?)", BOOK.NR_OF_RELEASES.in(4));
    assertNumericExpr(" NOT IN (?)", BOOK.NR_OF_RELEASES.notIn(4));
    assertNumericExpr(">?", BOOK.NR_OF_RELEASES.greaterThan(4));
    assertNumericExpr(">=?", BOOK.NR_OF_RELEASES.greaterOrEqual(4));
    assertNumericExpr("<?", BOOK.NR_OF_RELEASES.lessThan(4));
    assertNumericExpr("<=?", BOOK.NR_OF_RELEASES.lessOrEqual(4));
  }

  @Test
  public void numericExprWithSameColumnType() {
    assertNumericSameTypeExpr("=", BOOK.NR_OF_RELEASES.is(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT));
    assertNumericSameTypeExpr("!=", BOOK.NR_OF_RELEASES.isNot(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT));
    assertNumericSameTypeExpr(">", BOOK.NR_OF_RELEASES.greaterThan(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT));
    assertNumericSameTypeExpr(">=", BOOK.NR_OF_RELEASES.greaterOrEqual(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT));
    assertNumericSameTypeExpr("<", BOOK.NR_OF_RELEASES.lessThan(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT));
    assertNumericSameTypeExpr("<=", BOOK.NR_OF_RELEASES.lessOrEqual(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT));
  }

  @Test
  public void numericExprWithEquivalentColumnType() {
    assertNumericEquivalentTypeExpr("=", BOOK.NR_OF_RELEASES.is(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG));
    assertNumericEquivalentTypeExpr("!=", BOOK.NR_OF_RELEASES.isNot(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG));
    assertNumericEquivalentTypeExpr(">", BOOK.NR_OF_RELEASES.greaterThan(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG));
    assertNumericEquivalentTypeExpr(">=", BOOK.NR_OF_RELEASES.greaterOrEqual(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG));
    assertNumericEquivalentTypeExpr("<", BOOK.NR_OF_RELEASES.lessThan(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG));
    assertNumericEquivalentTypeExpr("<=", BOOK.NR_OF_RELEASES.lessOrEqual(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG));
  }

  private void assertNumericExpr(@NonNull String operator, @NonNull Expr expr) {
    final String expectedBase = "SELECT * FROM book WHERE book.nr_of_releases%s ";

    assertSql(Select.from(BOOK).where(expr),
        String.format(expectedBase, operator),
        "4");
  }

  private void assertNumericSameTypeExpr(@NonNull String operator, @NonNull Expr expr) {
    final String expectedBase = "SELECT * FROM book WHERE book.nr_of_releases%ssimple_all_values_mutable.primitive_int ";

    assertSql(Select.from(BOOK).where(expr),
        String.format(expectedBase, operator));
  }

  private void assertNumericEquivalentTypeExpr(@NonNull String operator, @NonNull Expr expr) {
    final String expectedBase = "SELECT * FROM book WHERE book.nr_of_releases%ssimple_all_values_mutable.primitive_long ";

    assertSql(Select.from(BOOK).where(expr),
        String.format(expectedBase, operator));
  }

  @Test
  public void complexExprWithSameType() {
    final Author randomAuthor = Author.newRandom();

    assertComplexSameTypeExpr("=?", randomAuthor, BOOK.AUTHOR.is(randomAuthor));
    assertComplexSameTypeExpr("!=?", randomAuthor, BOOK.AUTHOR.isNot(randomAuthor));
    assertComplexSameTypeExpr(" IN (?)", randomAuthor, BOOK.AUTHOR.in(randomAuthor));
    assertComplexSameTypeExpr(" IN (?)", randomAuthor, BOOK.AUTHOR.in(randomAuthor));
    assertComplexSameTypeExpr(" NOT IN (?)", randomAuthor, BOOK.AUTHOR.notIn(randomAuthor));
    assertComplexSameTypeExpr(">?", randomAuthor, BOOK.AUTHOR.greaterThan(randomAuthor));
    assertComplexSameTypeExpr(">=?", randomAuthor, BOOK.AUTHOR.greaterOrEqual(randomAuthor));
    assertComplexSameTypeExpr("<?", randomAuthor, BOOK.AUTHOR.lessThan(randomAuthor));
    assertComplexSameTypeExpr("<=?", randomAuthor, BOOK.AUTHOR.lessOrEqual(randomAuthor));
  }

  @Test
  public void complexExprWithEquivalentType() {
    final long authorId = 42;

    assertComplexEquivalentTypeExpr("=?", authorId, BOOK.AUTHOR.is(authorId));
    assertComplexEquivalentTypeExpr("!=?", authorId, BOOK.AUTHOR.isNot(authorId));
    assertComplexEquivalentTypeExpr(" IN (?)", authorId, BOOK.AUTHOR.in(authorId));
    assertComplexEquivalentTypeExpr(" NOT IN (?)", authorId, BOOK.AUTHOR.notIn(authorId));
    assertComplexEquivalentTypeExpr(">?", authorId, BOOK.AUTHOR.greaterThan(authorId));
    assertComplexEquivalentTypeExpr(">=?", authorId, BOOK.AUTHOR.greaterOrEqual(authorId));
    assertComplexEquivalentTypeExpr("<?", authorId, BOOK.AUTHOR.lessThan(authorId));
    assertComplexEquivalentTypeExpr("<=?", authorId, BOOK.AUTHOR.lessOrEqual(authorId));
  }

  @Test
  public void complexExprWithSameColumnType() {
    assertComplexSameColumnTypeExpr("=", BOOK.AUTHOR.is(MAGAZINE.AUTHOR));
    assertComplexSameColumnTypeExpr("!=", BOOK.AUTHOR.isNot(MAGAZINE.AUTHOR));
    assertComplexSameColumnTypeExpr(">", BOOK.AUTHOR.greaterThan(MAGAZINE.AUTHOR));
    assertComplexSameColumnTypeExpr(">=", BOOK.AUTHOR.greaterOrEqual(MAGAZINE.AUTHOR));
    assertComplexSameColumnTypeExpr("<", BOOK.AUTHOR.lessThan(MAGAZINE.AUTHOR));
    assertComplexSameColumnTypeExpr("<=", BOOK.AUTHOR.lessOrEqual(MAGAZINE.AUTHOR));
  }

  @Test
  public void complexExprWithEquivalentColumnType() {
    assertComplexEquivalentColumnTypeExpr("=", BOOK.AUTHOR.is(MAGAZINE._ID));
    assertComplexEquivalentColumnTypeExpr("!=", BOOK.AUTHOR.isNot(MAGAZINE._ID));
    assertComplexEquivalentColumnTypeExpr(">", BOOK.AUTHOR.greaterThan(MAGAZINE._ID));
    assertComplexEquivalentColumnTypeExpr(">=", BOOK.AUTHOR.greaterOrEqual(MAGAZINE._ID));
    assertComplexEquivalentColumnTypeExpr("<", BOOK.AUTHOR.lessThan(MAGAZINE._ID));
    assertComplexEquivalentColumnTypeExpr("<=", BOOK.AUTHOR.lessOrEqual(MAGAZINE._ID));
  }

  private void assertComplexSameTypeExpr(@NonNull String operator, @NonNull Author val, @NonNull Expr expr) {
    final String expectedBase = "SELECT * FROM book WHERE book.author%s ";

    assertSql(Select.from(BOOK).where(expr),
        String.format(expectedBase, operator),
        val.id.toString());
  }

  private void assertComplexEquivalentTypeExpr(@NonNull String operator, long val, @NonNull Expr expr) {
    final String expectedBase = "SELECT * FROM book WHERE book.author%s ";

    assertSql(Select.from(BOOK).where(expr),
        String.format(expectedBase, operator),
        Long.toString(val));
  }

  private void assertComplexSameColumnTypeExpr(@NonNull String operator, @NonNull Expr expr) {
    final String expectedBase = "SELECT * FROM book WHERE book.author%smagazine.author ";

    assertSql(Select.from(BOOK).where(expr),
        String.format(expectedBase, operator));
  }

  private void assertComplexEquivalentColumnTypeExpr(@NonNull String operator, @NonNull Expr expr) {
    final String expectedBase = "SELECT * FROM book WHERE book.author%smagazine._id ";

    assertSql(Select.from(BOOK).where(expr),
        String.format(expectedBase, operator));
  }

  @Test
  public void exprComplexAliased() {
    final String expectedBase = "SELECT * FROM book AS b ";
    final Author randomAuthor = Author.newRandom();
    final BookTable b = BOOK.as("b");
    final MagazineTable m = MAGAZINE.as("m");

    SelectSqlNode sqlNode = Select.from(b)
        .where(b.AUTHOR.is(randomAuthor));
    String expected = expectedBase + "WHERE b.author=? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());

    sqlNode = Select.from(b)
        .where(b.AUTHOR.is(MAGAZINE.AUTHOR));
    expected = expectedBase + "WHERE b.author=magazine.author ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .where(b.AUTHOR.is(m.AUTHOR));
    expected = expectedBase + "WHERE b.author=m.author ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .where(b.AUTHOR.is(MAGAZINE._ID));
    expected = expectedBase + "WHERE b.author=magazine._id ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .where(b.AUTHOR.is(m._ID));
    expected = expectedBase + "WHERE b.author=m._id ";
    assertSql(sqlNode, expected);
  }

  @Test
  public void joinComplex() {
    final Author randomAuthor = Author.newRandom();
    final String expectedBase = "SELECT * FROM book ";

    SelectSqlNode sqlNode = Select.from(BOOK)
        .leftJoin(MAGAZINE.on(BOOK.AUTHOR.isNot(MAGAZINE.AUTHOR)));
    String expected = expectedBase + "LEFT JOIN magazine ON book.author!=magazine.author ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(BOOK)
        .leftJoin(MAGAZINE.on(BOOK.AUTHOR.isNot(MAGAZINE._ID)));
    expected = expectedBase + "LEFT JOIN magazine ON book.author!=magazine._id ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(BOOK)
        .leftJoin(MAGAZINE.on(BOOK.AUTHOR.is(randomAuthor)));
    expected = expectedBase + "LEFT JOIN magazine ON book.author=? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());
  }

  @Test
  public void joinComplexAliased() {
    final Author randomAuthor = Author.newRandom();
    final String expectedBase = "SELECT * FROM book AS b ";
    final BookTable b = BOOK.as("b");
    final MagazineTable m = MAGAZINE.as("m");

    SelectSqlNode sqlNode = Select.from(b)
        .leftJoin(MAGAZINE.on(b.AUTHOR.isNot(MAGAZINE.AUTHOR)));
    String expected = expectedBase + "LEFT JOIN magazine ON b.author!=magazine.author ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .leftJoin(m.on(b.AUTHOR.isNot(m.AUTHOR)));
    expected = expectedBase + "LEFT JOIN magazine AS m ON b.author!=m.author ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .leftJoin(MAGAZINE.on(b.AUTHOR.isNot(MAGAZINE._ID)));
    expected = expectedBase + "LEFT JOIN magazine ON b.author!=magazine._id ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .leftJoin(m.on(b.AUTHOR.isNot(m._ID)));
    expected = expectedBase + "LEFT JOIN magazine AS m ON b.author!=m._id ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b)
        .leftJoin(MAGAZINE.on(b.AUTHOR.is(randomAuthor)));
    expected = expectedBase + "LEFT JOIN magazine ON b.author=? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());

    sqlNode = Select.from(b)
        .leftJoin(m.on(b.AUTHOR.is(randomAuthor)));
    expected = expectedBase + "LEFT JOIN magazine AS m ON b.author=? ";
    assertSql(sqlNode, expected, randomAuthor.id.toString());
  }

  @Test
  public void betweenTransformer() {
    final String expectedBase = "SELECT * FROM author ";

    SelectSqlNode sqlNode = Select.from(AUTHOR)
        .where(AUTHOR.PRIMITIVE_BOOLEAN.between(true).and(false));
    String expected = expectedBase + "WHERE author.primitive_boolean BETWEEN ? AND ? ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString(), BooleanTransformer.objectToDbValue(false).toString());

    sqlNode = Select.from(AUTHOR)
        .where(AUTHOR.PRIMITIVE_BOOLEAN.between(true).and(AUTHOR.BOXED_BOOLEAN));
    expected = expectedBase + "WHERE author.primitive_boolean BETWEEN ? AND author.boxed_boolean ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());

    sqlNode = Select.from(AUTHOR)
        .where(AUTHOR.PRIMITIVE_BOOLEAN.between(AUTHOR.BOXED_BOOLEAN).and(true));
    expected = expectedBase + "WHERE author.primitive_boolean BETWEEN author.boxed_boolean AND ? ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());

    sqlNode = Select.from(AUTHOR)
        .where(AUTHOR.PRIMITIVE_BOOLEAN.between(AUTHOR.BOXED_BOOLEAN).and(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BOOLEAN));
    expected = expectedBase + "WHERE author.primitive_boolean BETWEEN author.boxed_boolean AND simple_all_values_mutable.boxed_boolean ";
    assertSql(sqlNode, expected);
  }

  @Test
  public void betweenTransformerAliased() {
    final String expectedBase = "SELECT * FROM author AS a ";
    final AuthorTable a = AUTHOR.as("a");

    SelectSqlNode sqlNode = Select.from(a)
        .where(a.PRIMITIVE_BOOLEAN.between(true).and(false));
    String expected = expectedBase + "WHERE a.primitive_boolean BETWEEN ? AND ? ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString(), BooleanTransformer.objectToDbValue(false).toString());

    sqlNode = Select.from(a)
        .where(a.PRIMITIVE_BOOLEAN.between(true).and(a.BOXED_BOOLEAN));
    expected = expectedBase + "WHERE a.primitive_boolean BETWEEN ? AND a.boxed_boolean ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());

    sqlNode = Select.from(a)
        .where(a.PRIMITIVE_BOOLEAN.between(a.BOXED_BOOLEAN).and(true));
    expected = expectedBase + "WHERE a.primitive_boolean BETWEEN a.boxed_boolean AND ? ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());

    sqlNode = Select.from(a)
        .where(a.PRIMITIVE_BOOLEAN.between(a.BOXED_BOOLEAN).and(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BOOLEAN));
    expected = expectedBase + "WHERE a.primitive_boolean BETWEEN a.boxed_boolean AND simple_all_values_mutable.boxed_boolean ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(a)
        .where(a.PRIMITIVE_BOOLEAN.between(a.BOXED_BOOLEAN).and(SIMPLE_ALL_VALUES_MUTABLE.as("s").BOXED_BOOLEAN));
    expected = expectedBase + "WHERE a.primitive_boolean BETWEEN a.boxed_boolean AND s.boxed_boolean ";
    assertSql(sqlNode, expected);
  }

  @Test
  public void exprTransformer() {
    final String expectedBase = "SELECT * FROM author WHERE ";

    SelectSqlNode sqlNode = Select.from(AUTHOR)
        .where(AUTHOR.PRIMITIVE_BOOLEAN.is(true).and(AUTHOR.BOXED_BOOLEAN.isNot(false)));
    String expected = expectedBase + "(author.primitive_boolean=? AND author.boxed_boolean!=?) ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString(), BooleanTransformer.objectToDbValue(false).toString());

    sqlNode = Select.from(AUTHOR)
        .where(AUTHOR.PRIMITIVE_BOOLEAN.is(AUTHOR.BOXED_BOOLEAN));
    expected = expectedBase + "author.primitive_boolean=author.boxed_boolean ";
    assertSql(sqlNode, expected);
  }

  @Test
  public void exprTransformerAliased() {
    final String expectedBase = "SELECT * FROM author AS a WHERE ";
    final AuthorTable a = AUTHOR.as("a");

    SelectSqlNode sqlNode = Select.from(a)
        .where(a.PRIMITIVE_BOOLEAN.is(true).and(a.BOXED_BOOLEAN.isNot(false)));
    String expected = expectedBase + "(a.primitive_boolean=? AND a.boxed_boolean!=?) ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString(), BooleanTransformer.objectToDbValue(false).toString());

    sqlNode = Select.from(a)
        .where(a.PRIMITIVE_BOOLEAN.is(a.BOXED_BOOLEAN));
    expected = expectedBase + "a.primitive_boolean=a.boxed_boolean ";
    assertSql(sqlNode, expected);
  }

  @Test
  public void joinTransformer() {
    SelectSqlNode sqlNode = Select.from(AUTHOR)
        .join(SIMPLE_ALL_VALUES_MUTABLE.on(AUTHOR.BOXED_BOOLEAN.is(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BOOLEAN)));
    String expected = "SELECT * FROM author , simple_all_values_mutable ON author.boxed_boolean=simple_all_values_mutable.boxed_boolean ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(AUTHOR)
        .join(SIMPLE_ALL_VALUES_MUTABLE.on(AUTHOR.BOXED_BOOLEAN.is(true)));
    expected = "SELECT * FROM author , simple_all_values_mutable ON author.boxed_boolean=? ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());
  }

  @Test
  public void joinTransformerAliased() {
    final AuthorTable a = AUTHOR.as("a");
    final SimpleAllValuesMutableTable s = SIMPLE_ALL_VALUES_MUTABLE.as("s");

    SelectSqlNode sqlNode = Select.from(a)
        .join(SIMPLE_ALL_VALUES_MUTABLE.on(a.BOXED_BOOLEAN.is(SIMPLE_ALL_VALUES_MUTABLE.BOXED_BOOLEAN)));
    String expected = "SELECT * FROM author AS a , simple_all_values_mutable ON a.boxed_boolean=simple_all_values_mutable.boxed_boolean ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(a)
        .join(s.on(a.BOXED_BOOLEAN.is(s.BOXED_BOOLEAN)));
    expected = "SELECT * FROM author AS a , simple_all_values_mutable AS s ON a.boxed_boolean=s.boxed_boolean ";
    assertSql(sqlNode, expected);

    sqlNode = Select.from(a)
        .join(SIMPLE_ALL_VALUES_MUTABLE.on(a.BOXED_BOOLEAN.is(true)));
    expected = "SELECT * FROM author AS a , simple_all_values_mutable ON a.boxed_boolean=? ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());

    sqlNode = Select.from(a)
        .join(s.on(a.BOXED_BOOLEAN.is(true)));
    expected = "SELECT * FROM author AS a , simple_all_values_mutable AS s ON a.boxed_boolean=? ";
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());
  }

  @Test
  public void groupByTest() {
    final String expectedBase = "SELECT * FROM book ";

    String expected = expectedBase + "GROUP BY book.title ";
    SelectSqlNode sqlNode = Select.from(BOOK).groupBy(BOOK.TITLE);
    assertSql(sqlNode, expected);

    sqlNode = Select.from(BOOK).groupBy(BOOK.AUTHOR);
    expected = expectedBase + "GROUP BY book.author ";
    assertSql(sqlNode, expected);

    expected = expectedBase + "GROUP BY book.base_id,book.title,book.author ";
    sqlNode = Select.from(BOOK).groupBy(BOOK.BASE_ID, BOOK.TITLE, BOOK.AUTHOR);
    assertSql(sqlNode, expected);

    expected = expectedBase + "GROUP BY book.title HAVING book.nr_of_releases=? ";
    sqlNode = Select.from(BOOK).groupBy(BOOK.TITLE)
        .having(BOOK.NR_OF_RELEASES.is(1990));
    assertSql(sqlNode, expected, "1990");

    expected = expectedBase + "GROUP BY book.title,book.author HAVING book.nr_of_releases=? ";
    sqlNode = Select.from(BOOK).groupBy(BOOK.TITLE, BOOK.AUTHOR)
        .having(BOOK.NR_OF_RELEASES.is(1990));
    assertSql(sqlNode, expected, "1990");

    expected = expectedBase + "LEFT JOIN simple_all_values_mutable GROUP BY book.title,book.author HAVING book.nr_of_releases=simple_all_values_mutable.boxed_integer ";
    sqlNode = Select.from(BOOK)
        .leftJoin(SIMPLE_ALL_VALUES_MUTABLE)
        .groupBy(BOOK.TITLE, BOOK.AUTHOR)
        .having(BOOK.NR_OF_RELEASES.is(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER));
    assertSql(sqlNode, expected);

    expected = expectedBase + "LEFT JOIN author GROUP BY author.boxed_boolean HAVING author.primitive_boolean=? ";
    sqlNode = Select.from(BOOK)
        .leftJoin(AUTHOR)
        .groupBy(AUTHOR.BOXED_BOOLEAN)
        .having(AUTHOR.PRIMITIVE_BOOLEAN.is(true));
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());

    expected = expectedBase + "LEFT JOIN author GROUP BY author.boxed_boolean HAVING author.primitive_boolean=author.primitive_boolean ";
    sqlNode = Select.from(BOOK)
        .leftJoin(AUTHOR)
        .groupBy(AUTHOR.BOXED_BOOLEAN)
        .having(AUTHOR.PRIMITIVE_BOOLEAN.is(AUTHOR.PRIMITIVE_BOOLEAN));
    assertSql(sqlNode, expected);
  }

  @Test
  public void groupByTestAliased() {
    final String expectedBase = "SELECT * FROM book AS b ";
    final BookTable b = BOOK.as("b");
    final SimpleAllValuesMutableTable s = SIMPLE_ALL_VALUES_MUTABLE.as("s");
    final AuthorTable a = AUTHOR.as("a");

    String expected = expectedBase + "GROUP BY b.title ";
    SelectSqlNode sqlNode = Select.from(b).groupBy(b.TITLE);
    assertSql(sqlNode, expected);

    sqlNode = Select.from(b).groupBy(b.AUTHOR);
    expected = expectedBase + "GROUP BY b.author ";
    assertSql(sqlNode, expected);

    expected = expectedBase + "GROUP BY b.base_id,b.title,b.author ";
    sqlNode = Select.from(b).groupBy(b.BASE_ID, b.TITLE, b.AUTHOR);
    assertSql(sqlNode, expected);

    expected = expectedBase + "GROUP BY b.title HAVING b.nr_of_releases=? ";
    sqlNode = Select.from(b).groupBy(b.TITLE)
        .having(b.NR_OF_RELEASES.is(1990));
    assertSql(sqlNode, expected, "1990");

    expected = expectedBase + "GROUP BY b.title,b.author HAVING b.nr_of_releases=? ";
    sqlNode = Select.from(b).groupBy(b.TITLE, b.AUTHOR)
        .having(b.NR_OF_RELEASES.is(1990));
    assertSql(sqlNode, expected, "1990");

    expected = expectedBase + "LEFT JOIN simple_all_values_mutable GROUP BY b.title,b.author HAVING b.nr_of_releases=simple_all_values_mutable.boxed_integer ";
    sqlNode = Select.from(b)
        .leftJoin(SIMPLE_ALL_VALUES_MUTABLE)
        .groupBy(b.TITLE, b.AUTHOR)
        .having(b.NR_OF_RELEASES.is(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER));
    assertSql(sqlNode, expected);

    expected = expectedBase + "LEFT JOIN simple_all_values_mutable AS s GROUP BY b.title,b.author HAVING b.nr_of_releases=s.boxed_integer ";
    sqlNode = Select.from(b)
        .leftJoin(s)
        .groupBy(b.TITLE, b.AUTHOR)
        .having(b.NR_OF_RELEASES.is(s.BOXED_INTEGER));
    assertSql(sqlNode, expected);

    expected = expectedBase + "LEFT JOIN author GROUP BY author.boxed_boolean HAVING author.primitive_boolean=? ";
    sqlNode = Select.from(b)
        .leftJoin(AUTHOR)
        .groupBy(AUTHOR.BOXED_BOOLEAN)
        .having(AUTHOR.PRIMITIVE_BOOLEAN.is(true));
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());

    expected = expectedBase + "LEFT JOIN author AS a GROUP BY a.boxed_boolean HAVING a.primitive_boolean=? ";
    sqlNode = Select.from(b)
        .leftJoin(a)
        .groupBy(a.BOXED_BOOLEAN)
        .having(a.PRIMITIVE_BOOLEAN.is(true));
    assertSql(sqlNode, expected, BooleanTransformer.objectToDbValue(true).toString());

    expected = expectedBase + "LEFT JOIN author GROUP BY author.boxed_boolean HAVING author.primitive_boolean=author.primitive_boolean ";
    sqlNode = Select.from(b)
        .leftJoin(AUTHOR)
        .groupBy(AUTHOR.BOXED_BOOLEAN)
        .having(AUTHOR.PRIMITIVE_BOOLEAN.is(AUTHOR.PRIMITIVE_BOOLEAN));
    assertSql(sqlNode, expected);

    expected = expectedBase + "LEFT JOIN author AS a GROUP BY a.boxed_boolean HAVING a.primitive_boolean=a.primitive_boolean ";
    sqlNode = Select.from(b)
        .leftJoin(a)
        .groupBy(a.BOXED_BOOLEAN)
        .having(a.PRIMITIVE_BOOLEAN.is(a.PRIMITIVE_BOOLEAN));
    assertSql(sqlNode, expected);
  }

  @Test
  public void orderByTest() {
    final String expectedBase = "SELECT * FROM book ";

    String expected = expectedBase + "ORDER BY book.nr_of_releases ASC ";
    SelectSqlNode sqlNode = Select.from(BOOK)
        .orderBy(BOOK.NR_OF_RELEASES.asc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY book.nr_of_releases ASC,book.title ASC ";
    sqlNode = Select.from(BOOK)
        .orderBy(BOOK.NR_OF_RELEASES.asc(), BOOK.TITLE.asc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY book.nr_of_releases ASC,book.title ASC,book.author ASC ";
    sqlNode = Select.from(BOOK)
        .orderBy(BOOK.NR_OF_RELEASES.asc(), BOOK.TITLE.asc(), BOOK.AUTHOR.asc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY book.nr_of_releases DESC ";
    sqlNode = Select.from(BOOK)
        .orderBy(BOOK.NR_OF_RELEASES.desc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY book.nr_of_releases DESC,book.title ASC ";
    sqlNode = Select.from(BOOK)
        .orderBy(BOOK.NR_OF_RELEASES.desc(), BOOK.TITLE.asc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY book.nr_of_releases ASC,book.title DESC ";
    sqlNode = Select.from(BOOK)
        .orderBy(BOOK.NR_OF_RELEASES.asc(), BOOK.TITLE.desc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY trim(book.nr_of_releases) DESC ";
    sqlNode = Select.from(BOOK)
        .orderBy(BOOK.NR_OF_RELEASES.trim().desc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY book.nr_of_releases || ' ' || book.title ASC ";
    sqlNode = Select.from(BOOK)
        .orderBy(BOOK.NR_OF_RELEASES.concat(val(" ")).concat(BOOK.TITLE).asc());
    assertSql(sqlNode, expected);
  }

  @Test
  public void orderByTestAliased() {
    final String expectedBase = "SELECT * FROM book AS b ";
    final BookTable b = BOOK.as("b");

    String expected = expectedBase + "ORDER BY b.nr_of_releases ASC ";
    SelectSqlNode sqlNode = Select.from(b)
        .orderBy(b.NR_OF_RELEASES.asc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY b.nr_of_releases ASC,b.title ASC ";
    sqlNode = Select.from(b)
        .orderBy(b.NR_OF_RELEASES.asc(), b.TITLE.asc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY b.nr_of_releases ASC,b.title ASC,b.author ASC ";
    sqlNode = Select.from(b)
        .orderBy(b.NR_OF_RELEASES.asc(), b.TITLE.asc(), b.AUTHOR.asc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY b.nr_of_releases DESC ";
    sqlNode = Select.from(b)
        .orderBy(b.NR_OF_RELEASES.desc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY b.nr_of_releases DESC,b.title ASC ";
    sqlNode = Select.from(b)
        .orderBy(b.NR_OF_RELEASES.desc(), b.TITLE.asc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY b.nr_of_releases ASC,b.title DESC ";
    sqlNode = Select.from(b)
        .orderBy(b.NR_OF_RELEASES.asc(), b.TITLE.desc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY trim(b.nr_of_releases) DESC ";
    sqlNode = Select.from(b)
        .orderBy(b.NR_OF_RELEASES.trim().desc());
    assertSql(sqlNode, expected);

    expected = expectedBase + "ORDER BY b.nr_of_releases || ' ' || b.title ASC ";
    sqlNode = Select.from(b)
        .orderBy(b.NR_OF_RELEASES.concat(val(" ")).concat(b.TITLE).asc());
    assertSql(sqlNode, expected);
  }

  @Test
  public void limitTest() {
    final String expectedBase = "SELECT * FROM book ";

    String expected = expectedBase + "LIMIT 4 ";
    SelectSqlNode sqlNode = Select.from(BOOK).limit(4);
    assertSql(sqlNode, expected);

    expected = expectedBase + "LIMIT 55 OFFSET 6 ";
    sqlNode = Select.from(BOOK).limit(55).offset(6);
    assertSql(sqlNode, expected);
  }

  @Test
  public void simpleSubquery() {
    assertSimpleSubquery("=", new Func1<SelectSqlNode.SelectNode<String, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<String, Select1> selectNode) {
        return AUTHOR.NAME.is(selectNode);
      }
    });

    assertSimpleSubquery("!=", new Func1<SelectSqlNode.SelectNode<String, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<String, Select1> selectNode) {
        return AUTHOR.NAME.isNot(selectNode);
      }
    });

    assertSimpleSubquery(" IN ", new Func1<SelectSqlNode.SelectNode<String, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<String, Select1> selectNode) {
        return AUTHOR.NAME.in(selectNode);
      }
    });

    assertSimpleSubquery(" NOT IN ", new Func1<SelectSqlNode.SelectNode<String, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<String, Select1> selectNode) {
        return AUTHOR.NAME.notIn(selectNode);
      }
    });
  }

  private void assertSimpleSubquery(@NonNull String operator, @NonNull Func1<SelectSqlNode.SelectNode<String, Select1>, Expr> callback) {
    final String expectedBase = "SELECT * FROM author WHERE author.name%s(SELECT simple_all_values_mutable.string FROM simple_all_values_mutable ) ";
    final SelectSqlNode.SelectNode<String, Select1> subQuery = Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.STRING)
        .from(SIMPLE_ALL_VALUES_MUTABLE);

    assertSql(Select.from(AUTHOR).where(callback.call(subQuery)),
        String.format(expectedBase, operator));
  }

  @Test
  public void numericSubquery() {
    assertSameTypeNumericSubquery("=", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.is(selectNode);
      }
    });
    assertSameTypeNumericSubquery("!=", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.isNot(selectNode);
      }
    });
    assertSameTypeNumericSubquery(" IN ", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.in(selectNode);
      }
    });
    assertSameTypeNumericSubquery(" NOT IN ", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.notIn(selectNode);
      }
    });
    assertSameTypeNumericSubquery(">", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.greaterThan(selectNode);
      }
    });
    assertSameTypeNumericSubquery(">=", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.greaterOrEqual(selectNode);
      }
    });
    assertSameTypeNumericSubquery("<", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.lessThan(selectNode);
      }
    });
    assertSameTypeNumericSubquery("<=", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.lessOrEqual(selectNode);
      }
    });

    assertEquivalentTypeNumericSubquery(">", new Func1<SelectSqlNode.SelectNode<? extends Number, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<? extends Number, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.greaterThan(selectNode);
      }
    });
    assertEquivalentTypeNumericSubquery(">=", new Func1<SelectSqlNode.SelectNode<? extends Number, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<? extends Number, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.greaterOrEqual(selectNode);
      }
    });
    assertEquivalentTypeNumericSubquery("<", new Func1<SelectSqlNode.SelectNode<? extends Number, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<? extends Number, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.lessThan(selectNode);
      }
    });
    assertEquivalentTypeNumericSubquery("<=", new Func1<SelectSqlNode.SelectNode<? extends Number, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<? extends Number, Select1> selectNode) {
        return BOOK.NR_OF_RELEASES.lessOrEqual(selectNode);
      }
    });
  }

  private void assertSameTypeNumericSubquery(@NonNull String operator, @NonNull Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr> callback) {
    final String expectedBase = "SELECT * FROM book WHERE book.nr_of_releases%s(SELECT simple_all_values_mutable.primitive_int FROM simple_all_values_mutable ) ";
    final SelectSqlNode.SelectNode<Integer, Select1> subQuery = Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT)
        .from(SIMPLE_ALL_VALUES_MUTABLE);

    assertSql(Select.from(BOOK).where(callback.call(subQuery)),
        String.format(expectedBase, operator));
  }

  private void assertEquivalentTypeNumericSubquery(@NonNull String operator, @NonNull Func1<SelectSqlNode.SelectNode<? extends Number, Select1>, Expr> callback) {
    final String expectedBase = "SELECT * FROM book WHERE book.nr_of_releases%s(SELECT simple_all_values_mutable.boxed_double FROM simple_all_values_mutable ) ";
    final SelectSqlNode.SelectNode<Double, Select1> subQuery = Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.BOXED_DOUBLE)
        .from(SIMPLE_ALL_VALUES_MUTABLE);

    assertSql(Select.from(BOOK).where(callback.call(subQuery)),
        String.format(expectedBase, operator));
  }

  @Test
  public void complexSubquery() {
    assertSameTypeComplexSubquery("=", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.is(selectNode);
      }
    });
    assertSameTypeComplexSubquery("!=", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.isNot(selectNode);
      }
    });
    assertSameTypeComplexSubquery(" IN ", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.in(selectNode);
      }
    });
    assertSameTypeComplexSubquery(" NOT IN ", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.notIn(selectNode);
      }
    });
    assertSameTypeComplexSubquery(">", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.greaterThan(selectNode);
      }
    });
    assertSameTypeComplexSubquery(">=", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.greaterOrEqual(selectNode);
      }
    });
    assertSameTypeComplexSubquery("<", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.lessThan(selectNode);
      }
    });
    assertSameTypeComplexSubquery("<=", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.lessOrEqual(selectNode);
      }
    });


    assertIdTypeComplexSubquery("=", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.is(selectNode);
      }
    });
    assertIdTypeComplexSubquery("!=", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.isNot(selectNode);
      }
    });
    assertIdTypeComplexSubquery(" IN ", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.in(selectNode);
      }
    });
    assertIdTypeComplexSubquery(" NOT IN ", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.notIn(selectNode);
      }
    });
    assertIdTypeComplexSubquery(">", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.greaterThan(selectNode);
      }
    });
    assertIdTypeComplexSubquery(">=", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.greaterOrEqual(selectNode);
      }
    });
    assertIdTypeComplexSubquery("<", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.lessThan(selectNode);
      }
    });
    assertIdTypeComplexSubquery("<=", new Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Long, Select1> selectNode) {
        return BOOK.AUTHOR.lessOrEqual(selectNode);
      }
    });


    assertEquivalentTypeComplexSubquery("=", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.AUTHOR.is(selectNode);
      }
    });
    assertEquivalentTypeComplexSubquery("!=", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.AUTHOR.isNot(selectNode);
      }
    });
    assertEquivalentTypeComplexSubquery(" IN ", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.AUTHOR.in(selectNode);
      }
    });
    assertEquivalentTypeComplexSubquery(" NOT IN ", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.AUTHOR.notIn(selectNode);
      }
    });
    assertEquivalentTypeComplexSubquery(">", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.AUTHOR.greaterThan(selectNode);
      }
    });
    assertEquivalentTypeComplexSubquery(">=", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.AUTHOR.greaterOrEqual(selectNode);
      }
    });
    assertEquivalentTypeComplexSubquery("<", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.AUTHOR.lessThan(selectNode);
      }
    });
    assertEquivalentTypeComplexSubquery("<=", new Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr>() {
      @Override
      public Expr call(SelectSqlNode.SelectNode<Integer, Select1> selectNode) {
        return BOOK.AUTHOR.lessOrEqual(selectNode);
      }
    });
  }

  private void assertSameTypeComplexSubquery(@NonNull String operator, @NonNull Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr> callback) {
    final String expectedBase = "SELECT * FROM book WHERE book.author%s(SELECT magazine.author FROM magazine ) ";
    final SelectSqlNode.SelectNode<Long, Select1> subQuery = Select
        .column(MAGAZINE.AUTHOR)
        .from(MAGAZINE);

    assertSql(Select.from(BOOK).where(callback.call(subQuery)),
        String.format(expectedBase, operator));
  }

  private void assertIdTypeComplexSubquery(@NonNull String operator, @NonNull Func1<SelectSqlNode.SelectNode<Long, Select1>, Expr> callback) {
    final String expectedBase = "SELECT * FROM book WHERE book.author%s(SELECT magazine._id FROM magazine ) ";
    final SelectSqlNode.SelectNode<Long, Select1> subQuery = Select
        .column(MAGAZINE._ID)
        .from(MAGAZINE);

    assertSql(Select.from(BOOK).where(callback.call(subQuery)),
        String.format(expectedBase, operator));
  }

  private void assertEquivalentTypeComplexSubquery(@NonNull String operator, @NonNull Func1<SelectSqlNode.SelectNode<Integer, Select1>, Expr> callback) {
    final String expectedBase = "SELECT * FROM book WHERE book.author%s(SELECT simple_all_values_mutable.primitive_int FROM simple_all_values_mutable ) ";
    final SelectSqlNode.SelectNode<Integer, Select1> subQuery = Select
        .column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT)
        .from(SIMPLE_ALL_VALUES_MUTABLE);

    assertSql(Select.from(BOOK).where(callback.call(subQuery)),
        String.format(expectedBase, operator));
  }

  @Test
  public void avgFunction() {
    assertAggregateFunctionWithNumericColumn("avg", new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return avg(column);
      }
    }, new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return avgDistinct(column);
      }
    });

    final String expected = "SELECT simple_all_values_mutable.id FROM simple_all_values_mutable WHERE (avg(simple_all_values_mutable.primitive_int)>? AND avg(simple_all_values_mutable.primitive_long)<?) ";
    assertSql(Select.column(SIMPLE_ALL_VALUES_MUTABLE.ID)
            .from(SIMPLE_ALL_VALUES_MUTABLE)
            .where(avg(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT).greaterThan(5555.0)
                .and(avg(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_LONG).lessThan(8888.8))),
        expected);
  }

  @Test
  public void countFunction() {
    String expected = "SELECT count(*) FROM author ";
    assertSql(Select.column(count())
            .from(AUTHOR),
        expected);

    assertAggregateFunctionWithColumn("count", new Func1<Column, Column>() {
      @Override
      public Column call(Column column) {
        return count(column);
      }
    }, new Func1<Column, Column>() {
      @Override
      public Column call(Column column) {
        return countDistinct(column);
      }
    });

    assertAggregateFunctionWithNumericColumn("count", new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return count(column);
      }
    }, new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return countDistinct(column);
      }
    });
  }

  @Test
  public void groupConcatFunction() {
    assertAggregateFunctionWithColumn("group_concat", new Func1<Column, Column>() {
      @Override
      public Column call(Column column) {
        return groupConcat(column);
      }
    }, new Func1<Column, Column>() {
      @Override
      public Column call(Column column) {
        return groupConcatDistinct(column);
      }
    });

    final String expected = "SELECT group_concat(author.name,'-') FROM author ";
    assertSql(Select.column(groupConcat(AUTHOR.NAME, "-"))
            .from(AUTHOR),
        expected);
  }

  @Test
  public void maxFunction() {
    assertAggregateFunctionWithColumn("max", new Func1<Column, Column>() {
      @Override
      public Column call(Column column) {
        return max(column);
      }
    }, new Func1<Column, Column>() {
      @Override
      public Column call(Column column) {
        return maxDistinct(column);
      }
    });
    assertAggregateFunctionWithNumericColumn("max", new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return max(column);
      }
    }, new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return maxDistinct(column);
      }
    });
  }

  @Test
  public void minFunction() {
    assertAggregateFunctionWithColumn("min", new Func1<Column, Column>() {
      @Override
      public Column call(Column column) {
        return min(column);
      }
    }, new Func1<Column, Column>() {
      @Override
      public Column call(Column column) {
        return minDistinct(column);
      }
    });
    assertAggregateFunctionWithNumericColumn("min", new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return min(column);
      }
    }, new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return minDistinct(column);
      }
    });
  }

  @Test
  public void sumFunction() {
    assertAggregateFunctionWithNumericColumn("total", new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return sum(column);
      }
    }, new Func1<NumericColumn, NumericColumn>() {
      @Override
      public NumericColumn call(NumericColumn column) {
        return sumDistinct(column);
      }
    });
  }

  @Test
  public void concatFunction() {
    String expected = "SELECT author.id || author.name FROM author ";
    assertSql(Select.column(AUTHOR.ID.concat(AUTHOR.NAME))
            .from(AUTHOR),
        expected);

    expected = "SELECT author.id || author.name || author.primitive_boolean FROM author ";
    assertSql(Select.column(concat(AUTHOR.ID, AUTHOR.NAME, AUTHOR.PRIMITIVE_BOOLEAN))
            .from(AUTHOR),
        expected);

    assertSql(Select.column(AUTHOR.ID.concat(AUTHOR.NAME).concat(AUTHOR.PRIMITIVE_BOOLEAN))
            .from(AUTHOR),
        expected);
  }

  @Test
  public void replaceFunction() {
    String expected = "SELECT replace(author.name,'a','____') FROM author ";
    assertSql(Select.column(AUTHOR.NAME.replace("a", "____"))
            .from(AUTHOR),
        expected);

    expected = "SELECT replace(author.name,'a','____') AS 'asd' FROM author ";
    assertSql(Select.column(AUTHOR.NAME.replace("a", "____").as("asd"))
            .from(AUTHOR),
        expected);
  }

  @Test
  public void substringFunction() {
    String expected = "SELECT substr(author.name,2) FROM author ";
    assertSql(Select.column(AUTHOR.NAME.substring(2))
            .from(AUTHOR),
        expected);

    expected = "SELECT substr(author.name,-2) AS 'asd' FROM author ";
    assertSql(Select.column(AUTHOR.NAME.substring(-2).as("asd"))
            .from(AUTHOR),
        expected);

    expected = "SELECT substr(author.name,2,4) FROM author ";
    assertSql(Select.column(AUTHOR.NAME.substring(2, 4))
            .from(AUTHOR),
        expected);

    expected = "SELECT substr(author.name,-2,-4) AS 'asd' FROM author ";
    assertSql(Select.column(AUTHOR.NAME.substring(-2, -4).as("asd"))
            .from(AUTHOR),
        expected);
  }

  @Test
  public void trimFunction() {
    String expected = "SELECT trim(author.name) FROM author ";
    assertSql(Select.column(AUTHOR.NAME.trim())
            .from(AUTHOR),
        expected);

    expected = "SELECT trim(author.name) AS 'asd' FROM author ";
    assertSql(Select.column(AUTHOR.NAME.trim().as("asd"))
            .from(AUTHOR),
        expected);

    expected = "SELECT trim(author.name,'a') FROM author ";
    assertSql(Select.column(AUTHOR.NAME.trim("a"))
            .from(AUTHOR),
        expected);

    expected = "SELECT trim(author.name,'a') AS 'asd' FROM author ";
    assertSql(Select.column(AUTHOR.NAME.trim("a").as("asd"))
            .from(AUTHOR),
        expected);
  }

  @Test
  public void valColumn() {
    String expected = "SELECT author.id || ' ' || author.name FROM author ";
    final Column<String, String, CharSequence, ?> strVal = val(" ");
    assertSql(Select.column(concat(AUTHOR.ID, strVal, AUTHOR.NAME))
            .from(AUTHOR),
        expected);
    assertThat(strVal.valueParser).isEqualTo(STRING_PARSER);

    expected = "SELECT author.id || 3 || author.name FROM author ";
    final NumericColumn<Integer, Integer, Number, ?> intVal = val(3);
    assertSql(Select.column(concat(AUTHOR.ID, intVal, AUTHOR.NAME))
            .from(AUTHOR),
        expected);
    assertThat(intVal.valueParser).isEqualTo(INTEGER_PARSER);

    expected = "SELECT author.id || 3 || author.name FROM author ";
    final NumericColumn<Long, Long, Number, ?> longVal = val(3L);
    assertSql(Select.column(concat(AUTHOR.ID, longVal, AUTHOR.NAME))
            .from(AUTHOR),
        expected);
    assertThat(longVal.valueParser).isEqualTo(LONG_PARSER);

    final short s = 3;
    expected = "SELECT author.id || 3 || author.name FROM author ";
    final NumericColumn<Short, Short, Number, ?> shortVal = val(s);
    assertSql(Select.column(concat(AUTHOR.ID, shortVal, AUTHOR.NAME))
            .from(AUTHOR),
        expected);
    assertThat(shortVal.valueParser).isEqualTo(SHORT_PARSER);

    final byte b = 3;
    expected = "SELECT author.id || 3 || author.name FROM author ";
    final NumericColumn<Byte, Byte, Number, ?> byteVal = val(b);
    assertSql(Select.column(concat(AUTHOR.ID, byteVal, AUTHOR.NAME))
            .from(AUTHOR),
        expected);
    assertThat(byteVal.valueParser).isEqualTo(BYTE_PARSER);

    final float f = 3.3f;
    expected = "SELECT author.id || 3.3 || author.name FROM author ";
    final NumericColumn<Float, Float, Number, ?> floatVal = val(f);
    assertSql(Select.column(concat(AUTHOR.ID, floatVal, AUTHOR.NAME))
            .from(AUTHOR),
        expected);
    assertThat(floatVal.valueParser).isEqualTo(FLOAT_PARSER);

    final double d = 3.3;
    expected = "SELECT author.id || 3.3 || author.name FROM author ";
    final NumericColumn<Double, Double, Number, ?> doubleVal = val(d);
    assertSql(Select.column(concat(AUTHOR.ID, doubleVal, AUTHOR.NAME))
            .from(AUTHOR),
        expected);
    assertThat(doubleVal.valueParser).isEqualTo(DOUBLE_PARSER);
  }

  @Test
  public void absFunction() {
    String expected = "SELECT abs(book.nr_of_releases) FROM book ";
    assertSql(Select.column(abs(BOOK.NR_OF_RELEASES))
            .from(BOOK),
        expected);

    expected = "SELECT simple_all_values_mutable.id FROM simple_all_values_mutable WHERE abs(simple_all_values_mutable.primitive_int)>=? ";
    assertSql(Select.column(SIMPLE_ALL_VALUES_MUTABLE.ID)
            .from(SIMPLE_ALL_VALUES_MUTABLE)
            .where(abs(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT).greaterOrEqual(5)),
        expected);
  }

  @Test
  public void lengthFunction() {
    String expected = "SELECT length(book.title) FROM book ";
    assertSql(Select.column(length(BOOK.TITLE))
            .from(BOOK),
        expected);

    expected = "SELECT simple_all_values_mutable.id FROM simple_all_values_mutable WHERE length(simple_all_values_mutable.primitive_int)<? ";
    assertSql(Select.column(SIMPLE_ALL_VALUES_MUTABLE.ID)
            .from(SIMPLE_ALL_VALUES_MUTABLE)
            .where(length(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT).lessThan(6L)),
        expected);
  }

  @Test
  public void lowerFunction() {
    final String expected = "SELECT lower(book.title) FROM book ";
    assertSql(Select.column(lower(BOOK.TITLE))
            .from(BOOK),
        expected);
  }

  @Test
  public void upperFunction() {
    final String expected = "SELECT upper(book.title) FROM book ";
    assertSql(Select.column(upper(BOOK.TITLE))
            .from(BOOK),
        expected);
  }

  @Test
  public void addFunction() {
    assertArithmeticExpression('+',
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Short, Short, Number, SimpleAllValuesMutable>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Short, Short, Number, SimpleAllValuesMutable> v2) {
            return v1.add(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Long, Long, Number, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Long, Long, Number, ?> v2) {
            return v1.add(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, Integer v2) {
            return v1.add(v2);
          }
        });
  }

  @Test
  public void subFunction() {
    assertArithmeticExpression('-',
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Short, Short, Number, SimpleAllValuesMutable>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Short, Short, Number, SimpleAllValuesMutable> v2) {
            return v1.sub(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Long, Long, Number, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Long, Long, Number, ?> v2) {
            return v1.sub(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, Integer v2) {
            return v1.sub(v2);
          }
        });
  }

  @Test
  public void mulFunction() {
    assertArithmeticExpression('*',
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Short, Short, Number, SimpleAllValuesMutable>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Short, Short, Number, SimpleAllValuesMutable> v2) {
            return v1.mul(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Long, Long, Number, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Long, Long, Number, ?> v2) {
            return v1.mul(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, Integer v2) {
            return v1.mul(v2);
          }
        });
  }

  @Test
  public void divFunction() {
    assertArithmeticExpression('/',
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Short, Short, Number, SimpleAllValuesMutable>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Short, Short, Number, SimpleAllValuesMutable> v2) {
            return v1.div(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Long, Long, Number, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Long, Long, Number, ?> v2) {
            return v1.div(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, Integer v2) {
            return v1.div(v2);
          }
        });
  }

  @Test
  public void modFunction() {
    assertArithmeticExpression('%',
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Short, Short, Number, SimpleAllValuesMutable>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Short, Short, Number, SimpleAllValuesMutable> v2) {
            return v1.mod(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Long, Long, Number, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, NumericColumn<Long, Long, Number, ?> v2) {
            return v1.mod(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable> v1, Integer v2) {
            return v1.mod(v2);
          }
        });
  }

  @Test
  public void numericArithmeticExpressionsChained() {
    String expected = "SELECT ((((1+2)*(5-3))/2.0)%10.0) FROM simple_all_values_mutable ";
    assertSql(Select.column(val(1).add(2).mul(val(5).sub(3)).div(2.0).mod(10.0))
            .from(SIMPLE_ALL_VALUES_MUTABLE),
        expected);

    expected = "SELECT ((((simple_all_values_mutable.primitive_int+2)*(5-3))/2.0)%10.0) FROM simple_all_values_mutable ";
    assertSql(Select.column(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT.add(2).mul(val(5).sub(3)).div(2.0).mod(10.0))
            .from(SIMPLE_ALL_VALUES_MUTABLE),
        expected);
  }

  private void assertArithmeticExpression(char op,
                                          @NonNull Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Short, Short, Number, SimpleAllValuesMutable>, NumericColumn> columnCallback,
                                          @NonNull Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, NumericColumn<Long, Long, Number, ?>, NumericColumn> columnValueCallback,
                                          @NonNull Func2<NumericColumn<Integer, Integer, Number, SimpleAllValuesMutable>, Integer, NumericColumn> valueCallback) {
    String expected = String.format("SELECT (simple_all_values_mutable.primitive_int%ssimple_all_values_mutable.primitive_short) FROM simple_all_values_mutable ", op);
    assertSql(Select.column(columnCallback.call(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT, SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_SHORT))
            .from(SIMPLE_ALL_VALUES_MUTABLE),
        expected);

    expected = String.format("SELECT (simple_all_values_mutable.primitive_int%s5) FROM simple_all_values_mutable ", op);
    assertSql(Select.column(columnValueCallback.call(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT, val(5L)))
            .from(SIMPLE_ALL_VALUES_MUTABLE),
        expected);

    expected = String.format("SELECT (simple_all_values_mutable.primitive_int%s5) FROM simple_all_values_mutable ", op);
    assertSql(Select.column(valueCallback.call(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT, 5))
            .from(SIMPLE_ALL_VALUES_MUTABLE),
        expected);
  }

  @SuppressWarnings("unchecked")
  private void assertAggregateFunctionWithColumn(@NonNull String funcName,
                                                 @NonNull Func1<Column, Column> func,
                                                 @NonNull Func1<Column, Column> distinctFunc) {
    String expected = String.format("SELECT %s(author.name) FROM author ", funcName);
    assertSql(Select.column(func.call(AUTHOR.NAME))
            .from(AUTHOR),
        expected);

    expected = String.format("SELECT %s(DISTINCT author.name) FROM author ", funcName);
    assertSql(Select.column(distinctFunc.call(AUTHOR.NAME))
            .from(AUTHOR),
        expected);

    expected = String.format("SELECT simple_all_values_mutable.id FROM simple_all_values_mutable WHERE %s(simple_all_values_mutable.string)=? ", funcName);
    assertSql(Select.column(SIMPLE_ALL_VALUES_MUTABLE.ID)
            .from(SIMPLE_ALL_VALUES_MUTABLE)
            .where(func.call(SIMPLE_ALL_VALUES_MUTABLE.STRING).is(888)),
        expected);

    expected = String.format("SELECT simple_all_values_mutable.id FROM simple_all_values_mutable WHERE %s(DISTINCT simple_all_values_mutable.string)!=? ", funcName);
    assertSql(Select.column(SIMPLE_ALL_VALUES_MUTABLE.ID)
            .from(SIMPLE_ALL_VALUES_MUTABLE)
            .where(distinctFunc.call(SIMPLE_ALL_VALUES_MUTABLE.STRING).isNot(888)),
        expected);
  }

  @SuppressWarnings("unchecked")
  private void assertAggregateFunctionWithNumericColumn(@NonNull String funcName,
                                                        @NonNull Func1<NumericColumn, NumericColumn> func,
                                                        @NonNull Func1<NumericColumn, NumericColumn> distinctFunc) {
    String expected = String.format("SELECT %s(book.nr_of_releases) FROM book ", funcName);
    assertSql(Select.column(func.call(BOOK.NR_OF_RELEASES))
            .from(BOOK),
        expected);

    expected = String.format("SELECT %s(DISTINCT book.nr_of_releases) FROM book ", funcName);
    assertSql(Select.column(distinctFunc.call(BOOK.NR_OF_RELEASES))
            .from(BOOK),
        expected);

    expected = String.format("SELECT simple_all_values_mutable.id FROM simple_all_values_mutable WHERE %s(simple_all_values_mutable.primitive_int)<? ", funcName);
    assertSql(Select.column(SIMPLE_ALL_VALUES_MUTABLE.ID)
            .from(SIMPLE_ALL_VALUES_MUTABLE)
            .where(func.call(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT).lessThan(888)),
        expected);

    expected = String.format("SELECT simple_all_values_mutable.id FROM simple_all_values_mutable WHERE %s(DISTINCT simple_all_values_mutable.primitive_int)>=? ", funcName);
    assertSql(Select.column(SIMPLE_ALL_VALUES_MUTABLE.ID)
            .from(SIMPLE_ALL_VALUES_MUTABLE)
            .where(distinctFunc.call(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT).greaterOrEqual(888)),
        expected);
  }

  private void assertSql(SelectSqlNode sqlNode, String expectedOutput) {
    final String generatedSql = generateSql(sqlNode);
    assertThat(generatedSql).isEqualTo(expectedOutput);
  }

  private void assertSql(SelectSqlNode sqlNode, String expectedOutput, String... expectedArgs) {
    final String generatedSql = generateSql(sqlNode);
    assertThat(generatedSql).isEqualTo(expectedOutput);
    assertThat(sqlNode.selectBuilder.args).containsExactly(expectedArgs);
  }

  private String generateSql(SelectSqlNode sqlNode) {
    final SelectBuilder selectBuilder = sqlNode.selectBuilder;
    if (selectBuilder.columnsNode != null) {
      selectBuilder.columnsNode.compileColumns(null);
    }
    return SqlCreator.getSql(sqlNode, selectBuilder.sqlNodeCount);
  }
}
