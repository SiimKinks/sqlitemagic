package com.siimkinks.sqlitemagic;

import android.database.SQLException;
import android.support.annotation.NonNull;

import com.google.common.truth.Truth;
import com.siimkinks.sqlitemagic.Select.Select1;
import com.siimkinks.sqlitemagic.Select.SelectN;
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;
import com.siimkinks.sqlitemagic.model.Author;
import com.siimkinks.sqlitemagic.model.Book;
import com.siimkinks.sqlitemagic.model.ComplexObjectWithSameLeafs;
import com.siimkinks.sqlitemagic.model.Magazine;
import com.siimkinks.sqlitemagic.model.immutable.BuilderMagazine;
import com.siimkinks.sqlitemagic.model.immutable.ComplexValueWithCreator;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithCreator;
import com.siimkinks.sqlitemagic.model.view.ComplexInterfaceView;
import com.siimkinks.sqlitemagic.model.view.ComplexView;

import org.junit.Before;
import org.junit.Test;

import lombok.experimental.Builder;

import static com.google.common.truth.Truth.assertThat;
import static com.siimkinks.sqlitemagic.AuthorTable.AUTHOR;
import static com.siimkinks.sqlitemagic.BookTable.BOOK;
import static com.siimkinks.sqlitemagic.ComplexInterfaceViewTable.COMPLEX_INTERFACE_VIEW;
import static com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS;
import static com.siimkinks.sqlitemagic.ComplexValueWithCreatorTable.COMPLEX_VALUE_WITH_CREATOR;
import static com.siimkinks.sqlitemagic.ComplexViewTable.COMPLEX_VIEW;
import static com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE;
import static com.siimkinks.sqlitemagic.Select.abs;
import static com.siimkinks.sqlitemagic.Select.asColumn;
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
import static com.siimkinks.sqlitemagic.SimpleValueWithBuilderTable.SIMPLE_VALUE_WITH_BUILDER;
import static com.siimkinks.sqlitemagic.SimpleValueWithCreatorTable.SIMPLE_VALUE_WITH_CREATOR;
import static com.siimkinks.sqlitemagic.UnitTestUtil.assertSimpleArrayMapsAreEqualWithWildcardInKey;
import static com.siimkinks.sqlitemagic.UnitTestUtil.assertSimpleArrayMapsAreEqualWithWildcardInValue;
import static com.siimkinks.sqlitemagic.UnitTestUtil.assertStringsAreEqualOrMatching;
import static com.siimkinks.sqlitemagic.UnitTestUtil.replaceRandomTableNames;
import static com.siimkinks.sqlitemagic.model.view.ComplexInterfaceView.AUTHOR_NAME_ALIAS;
import static com.siimkinks.sqlitemagic.model.view.ComplexInterfaceView.MAGAZINE_ALIAS;
import static com.siimkinks.sqlitemagic.model.view.ComplexInterfaceView.VALUE_W_BUILDER_ALIAS;
import static org.mockito.Mockito.mock;

public final class SelectSqlCompilerTest {
  @Before
  public void setUp() {
    final SqliteMagic instance = SqliteMagic.SingletonHolder.instance;
    instance.defaultConnection = mock(DbConnectionImpl.class);
  }

  @Test
  public void selectAllFromSimple() {
    CompiledSelect<Author, SelectN> compiledSelect = Select
        .from(AUTHOR)
        .compile();
    CompiledSelectMetadata
        .assertThat()
        .sql("SELECT * FROM author ")
        .tableName("author")
        .observedTables("author")
        .queryDeep(false)
        .build()
        .isEqualTo(compiledSelect);

    CompiledFirstSelect<Author, SelectN> compiledFirstSelect = compiledSelect.takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM author LIMIT 1 ")
        .tableName("author")
        .observedTables("author")
        .build()
        .isEqualTo(compiledFirstSelect);

    compiledSelect = Select
        .from(AUTHOR)
        .queryDeep()
        .compile();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM author ")
        .tableName("author")
        .observedTables("author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledSelect);

    compiledFirstSelect = compiledSelect.takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM author LIMIT 1 ")
        .tableName("author")
        .observedTables("author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledFirstSelect);
  }

  @Test
  public void selectAllFromSimpleSubquery() {
    CompiledSelect<Author, SelectN> compiledSelect = Select
        .from(Select.from(AUTHOR))
        .compile();
    CompiledSelectMetadata
        .assertThat()
        .sql("SELECT * FROM (SELECT * FROM author ) ")
        .tableName("")
        .observedTables("author")
        .queryDeep(false)
        .build()
        .isEqualTo(compiledSelect);

    CompiledFirstSelect<Author, SelectN> compiledFirstSelect = compiledSelect.takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM (SELECT * FROM author ) LIMIT 1 ")
        .tableName("")
        .observedTables("author")
        .build()
        .isEqualTo(compiledFirstSelect);

    compiledSelect = Select
        .from(Select.from(AUTHOR))
        .queryDeep()
        .compile();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM (SELECT * FROM author ) ")
        .tableName("")
        .observedTables("author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledSelect);

    compiledFirstSelect = compiledSelect.takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM (SELECT * FROM author ) LIMIT 1 ")
        .tableName("")
        .observedTables("author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledFirstSelect);
  }

  @Test
  public void selectAllFromComplex() {
    CompiledSelect<Book, SelectN> compiledSelect = Select
        .from(BOOK)
        .compile();

    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book ")
        .tableName("book")
        .observedTables("book")
        .build()
        .isEqualTo(compiledSelect);

    CompiledFirstSelect<Book, SelectN> compiledFirstSelect = compiledSelect.takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book LIMIT 1 ")
        .tableName("book")
        .observedTables("book")
        .build()
        .isEqualTo(compiledFirstSelect);

    compiledSelect = Select
        .from(BOOK)
        .queryDeep()
        .compile();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book LEFT JOIN author ON book.author=author.id ")
        .tableName("book")
        .observedTables("book", "author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledSelect);

    compiledFirstSelect = compiledSelect.takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book LEFT JOIN author ON book.author=author.id LIMIT 1 ")
        .tableName("book")
        .observedTables("book", "author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledFirstSelect);
  }

  @Test
  public void selectAllFromComplexSubquery() {
    CompiledSelect<Book, SelectN> compiledSelect = Select
        .from(Select.from(BOOK))
        .compile();

    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM (SELECT * FROM book ) ")
        .tableName("")
        .observedTables("book")
        .build()
        .isEqualTo(compiledSelect);

    CompiledFirstSelect<Book, SelectN> compiledFirstSelect = compiledSelect.takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM (SELECT * FROM book ) LIMIT 1 ")
        .tableName("")
        .observedTables("book")
        .build()
        .isEqualTo(compiledFirstSelect);

    compiledSelect = Select
        .from(Select.from(BOOK).queryDeep())
        .queryDeep()
        .compile();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM (SELECT * FROM book LEFT JOIN author ON book.author=author.id ) ")
        .tableName("")
        .observedTables("book", "author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledSelect);

    compiledFirstSelect = compiledSelect.takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM (SELECT * FROM book LEFT JOIN author ON book.author=author.id ) LIMIT 1 ")
        .tableName("")
        .observedTables("book", "author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledFirstSelect);
  }

  @Test
  public void selectFromSubqueryArgsArePropagated() {
    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .from(Select.from(BOOK).where(BOOK.TITLE.is("asd").and(BOOK.NR_OF_RELEASES.is(12))))
        .compile();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM (SELECT * FROM book WHERE (book.title=? AND book.nr_of_releases=?) ) ")
        .tableName("")
        .observedTables("book")
        .args("asd", "12")
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectAllShallowFromComplexChild() {
    final CompiledSelect<ComplexObjectWithSameLeafs, SelectN> compiledSelect = Select
        .columns(COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME,
            BOOK.all(),
            COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE)
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("complex_object_with_same_leafs.name", 0);
    columns.put("book", 1);
    columns.put("complex_object_with_same_leafs.magazine", 5);

    CompiledSelectMetadata.assertThat()
        .sql("SELECT complex_object_with_same_leafs.name,book.*,complex_object_with_same_leafs.magazine " +
            "FROM complex_object_with_same_leafs ")
        .tableName("complex_object_with_same_leafs")
        .observedTables("complex_object_with_same_leafs")
        .queryDeep(false)
        .columns(columns)
        .tableGraphNodeNames(new SimpleArrayMap<String, String>())
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectAllDeepFromComplexChild() {
    final CompiledSelect<ComplexObjectWithSameLeafs, SelectN> compiledSelect = Select
        .columns(COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME,
            BOOK.all(),
            COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE)
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .queryDeep()
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("complex_object_with_same_leafs.name", 0);
    columns.put("book", 1);
    columns.put("complex_object_with_same_leafs.magazine", 5);
    final SimpleArrayMap<String, String> graphNodes = new SimpleArrayMap<>();
    graphNodes.put("book", "book");

    CompiledSelectMetadata.assertThat()
        .sql("SELECT complex_object_with_same_leafs.name,book.*,complex_object_with_same_leafs.magazine " +
            "FROM complex_object_with_same_leafs " +
            "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id ")
        .tableName("complex_object_with_same_leafs")
        .observedTables("complex_object_with_same_leafs", "book")
        .queryDeep(true)
        .columns(columns)
        .tableGraphNodeNames(graphNodes)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void compileComplexJoinWithRename() {
    final BookTable b = BOOK.as("b");
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book AS b LEFT JOIN author ON b.author=author.id ")
        .tableName("b")
        .observedTables("book", "author")
        .build()
        .isEqualTo(Select
            .from(b)
            .leftJoin(AUTHOR.on(b.AUTHOR.is(AUTHOR.ID)))
            .compile());

    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book AS b LEFT JOIN author ON b.author=author.id ")
        .tableName("b")
        .observedTables("book", "author")
        .queryDeep(true)
        .build()
        .isEqualTo(Select
            .from(b)
            .leftJoin(AUTHOR.on(b.AUTHOR.is(AUTHOR.ID)))
            .queryDeep()
            .compile());
  }

  @Test
  public void compileComplexWithRename() {
    final BookTable b = BOOK.as("b");
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book AS b ")
        .tableName("b")
        .observedTables("book")
        .build()
        .isEqualTo(Select
            .from(b)
            .compile());

    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book AS b LEFT JOIN author ON b.author=author.id ")
        .tableName("b")
        .observedTables("book", "author")
        .queryDeep(true)
        .build()
        .isEqualTo(Select
            .from(b)
            .queryDeep()
            .compile());

  }

  @Test(expected = SQLException.class)
  public void takeFirstFails() {
    final CompiledFirstSelect<Author, SelectN> select = Select
        .from(AUTHOR)
        .limit(5)
        .takeFirst();
  }

  // FIXME: 20.02.16 uncomment when limit supports expressions
//	@Test(expected = SQLException.class)
//	public void takeFirstDefinedLimitExpressionFails() {
//		final CompiledSelect.CompiledFirstSelect<Author> select = Select
//				.from(AUTHOR)
//				.limit("asd")
//				.takeFirst();
//	}

  @Test
  public void takeFirstAlreadyCorrectlyDefined() {
    CompiledFirstSelect<Author, SelectN> compiledFirstSelect = Select
        .from(AUTHOR)
        .limit(1)
        .takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM author LIMIT 1 ")
        .tableName("author")
        .observedTables("author")
        .build()
        .isEqualTo(compiledFirstSelect);

    compiledFirstSelect = Select
        .from(AUTHOR)
        .limit(1)
        .queryDeep()
        .takeFirst();
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM author LIMIT 1 ")
        .tableName("author")
        .observedTables("author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledFirstSelect);
  }

  @Test
  public void selectColumns() {
    final CompiledSelect<Book, SelectN> select = Select
        .columns(AUTHOR.NAME)
        .from(BOOK)
        .queryDeep()
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("author.name", 0);
    final SimpleArrayMap<String, String> graphNodes = new SimpleArrayMap<>();
    graphNodes.put("author", "author");
    CompiledSelectMetadata.assertThat()
        .sql("SELECT author.name FROM book LEFT JOIN author ON book.author=author.id ")
        .tableName("book")
        .observedTables("book", "author")
        .columns(columns)
        .tableGraphNodeNames(graphNodes)
        .queryDeep(true)
        .build()
        .isEqualTo(select);

    final CompiledSelect<ComplexObjectWithSameLeafs, SelectN> complexSelect = Select
        .columns(BOOK.BASE_ID, MAGAZINE._ID, AUTHOR.NAME)
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .queryDeep()
        .compile();

    columns.clear();
    columns.put("book.base_id", 0); // book base_id
    columns.put("?._id", 1); // magazine id
    columns.put("author.name", 2); // author name
    columns.put("?.name", 3); // magazine author name
    graphNodes.clear();
    graphNodes.put("book", "book");
    graphNodes.put("bookauthor", "author");
    graphNodes.put("magazine", "?");
    graphNodes.put("magazineauthor", "?");
    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards("SELECT book.base_id,?._id,author.name,?.name " +
            "FROM complex_object_with_same_leafs " +
            "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id " +
            "LEFT JOIN author ON book.author=author.id " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id " +
            "LEFT JOIN author AS ? ON ?.author=?.id ")
        .tableName("complex_object_with_same_leafs")
        .observedTables("complex_object_with_same_leafs", "book", "author", "magazine")
        .columns(columns)
        .tableGraphNodeNames(graphNodes)
        .queryDeep(true)
        .build()
        .isEqualTo(complexSelect);
  }

  @Test(expected = SQLException.class)
  public void ambiguousWhereClause() {
    final CompiledSelect<ComplexObjectWithSameLeafs, SelectN> select = Select
        .columns(BOOK.all(),
            MAGAZINE.all(),
            AUTHOR.all())
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .where(AUTHOR.NAME.is("asd"))
        .queryDeep()
        .compile();
  }

  @Test
  public void nonAmbiguousWhereClause() {
    final CompiledSelect<ComplexObjectWithSameLeafs, SelectN> select = Select
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .where(AUTHOR.NAME.is("asd"))
        .queryDeep()
        .compile();
    CompiledSelectMetadata.assertThat()
        .sql(replaceRandomTableNames("SELECT * FROM complex_object_with_same_leafs " +
            "LEFT JOIN simple_value_with_builder AS ? ON complex_object_with_same_leafs.simple_value_with_builder=?.id " +
            "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id " +
            "LEFT JOIN author ON book.author=author.id " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id " +
            "LEFT JOIN author AS ? ON ?.author=?.id " +
            "LEFT JOIN simple_value_with_builder AS ? ON complex_object_with_same_leafs.simple_value_with_builder_duplicate=?.id ") +
            "WHERE author.name=\\? ")
        .args("asd")
        .tableName("complex_object_with_same_leafs")
        .observedTables("complex_object_with_same_leafs", "simple_value_with_builder", "book", "author", "magazine")
        .queryDeep(true)
        .build()
        .isEqualTo(select);
  }

  @Test
  public void whereClause() {
    CompiledSelect<ComplexObjectWithSameLeafs, SelectN> select = Select
        .columns(SIMPLE_VALUE_WITH_BUILDER.all(),
            BOOK.all(),
            MAGAZINE.all())
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .where(BOOK.TITLE.is("asd").and(BOOK.NR_OF_RELEASES.is(12)))
        .queryDeep()
        .compile();
    assertWhereClause(select, "WHERE \\(book.title=\\? AND book.nr_of_releases=\\?\\) ", "asd", "12");

    select = Select
        .columns(SIMPLE_VALUE_WITH_BUILDER.all(),
            BOOK.all(),
            MAGAZINE.all())
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .where(BOOK.TITLE.is("asd").and(BOOK.NR_OF_RELEASES.is(12).or(COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME.is("asdasd"))))
        .queryDeep()
        .compile();
    assertWhereClause(select, "WHERE \\(book.title=\\? AND \\(book.nr_of_releases=\\? OR complex_object_with_same_leafs.name=\\?\\)\\) ", "asd", "12", "asdasd");

    select = Select
        .columns(
            SIMPLE_VALUE_WITH_BUILDER.all(),
            BOOK.all(),
            MAGAZINE.all()
        )
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .where(BOOK.TITLE.is("asd").or(BOOK.NR_OF_RELEASES.is(12).and(COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME.is("asdasd").and(COMPLEX_OBJECT_WITH_SAME_LEAFS.ID.is(123L)))))
        .queryDeep()
        .compile();
    assertWhereClause(select, "WHERE \\(book.title=\\? OR \\(book.nr_of_releases=\\? AND \\(complex_object_with_same_leafs.name=\\? AND complex_object_with_same_leafs.id=\\?\\)\\)\\) ", "asd", "12", "asdasd", "123");

    select = Select
        .columns(
            SIMPLE_VALUE_WITH_BUILDER.all(),
            BOOK.all(),
            MAGAZINE.all()
        )
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .where(BOOK.TITLE.is("asd").or(BOOK.BASE_ID.is(22L)).and(BOOK.NR_OF_RELEASES.is(12).or(COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME.is("asdasd"))))
        .queryDeep()
        .compile();
    assertWhereClause(select, "WHERE \\(\\(book.title=\\? OR book.base_id=\\?\\) AND \\(book.nr_of_releases=\\? OR complex_object_with_same_leafs.name=\\?\\)\\) ", "asd", "22", "12", "asdasd");

    select = Select
        .columns(
            SIMPLE_VALUE_WITH_BUILDER.all(),
            BOOK.all(),
            MAGAZINE.all()
        )
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .where(BOOK.TITLE.is("asd").or(BOOK.BASE_ID.is(22L)).and(BOOK.NR_OF_RELEASES.is(12).or(COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME.is("asdasd"))))
        .queryDeep()
        .compile();
    assertWhereClause(select, "WHERE \\(\\(book.title=\\? OR book.base_id=\\?\\) AND \\(book.nr_of_releases=\\? OR complex_object_with_same_leafs.name=\\?\\)\\) ", "asd", "22", "12", "asdasd");
  }

  private void assertWhereClause(CompiledSelect<ComplexObjectWithSameLeafs, SelectN> genericSelect, String expectedWhereClause, String... args) {
    final CompiledSelectImpl<ComplexObjectWithSameLeafs, SelectN> select = (CompiledSelectImpl<ComplexObjectWithSameLeafs, SelectN>) genericSelect;
    final SimpleArrayMap<String, String> graphNodes = new SimpleArrayMap<>();
    graphNodes.put("book", "book");
    graphNodes.put("simple_value_with_builder", "?");
    graphNodes.put("magazine", "?");
    graphNodes.put("simple_value_with_builder_duplicate", "?");
    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put(select.tableGraphNodeNames.get("simple_value_with_builder"), 0);
    columns.put(select.tableGraphNodeNames.get("simple_value_with_builder_duplicate"), 6);
    columns.put("book", 12);
    columns.put(select.tableGraphNodeNames.get("magazine"), 16);

    CompiledSelectMetadata.assertThat()
        .sql(replaceRandomTableNames("SELECT ?.*,?.*,book.*,?.* FROM complex_object_with_same_leafs " +
            "LEFT JOIN simple_value_with_builder AS ? ON complex_object_with_same_leafs.simple_value_with_builder=?.id " +
            "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id " +
            "LEFT JOIN simple_value_with_builder AS ? ON complex_object_with_same_leafs.simple_value_with_builder_duplicate=?.id ") +
            expectedWhereClause)
        .args(args)
        .columns(columns)
        .tableGraphNodeNames(graphNodes)
        .tableName("complex_object_with_same_leafs")
        .observedTables("complex_object_with_same_leafs", "simple_value_with_builder", "book", "magazine")
        .queryDeep(true)
        .build()
        .isEqualTo(select);
  }

  @Test
  public void userDefinedSelect() {
    final String magazineAuthorAlias = "ma";
    final AuthorTable magazineAuthor = AUTHOR.as(magazineAuthorAlias);
    final String bookAuthorAlias = "ba";
    final AuthorTable bookAuthor = AUTHOR.as(bookAuthorAlias);

    final CompiledSelect<ComplexObjectWithSameLeafs, SelectN> select = Select
        .columns(BOOK.all(),
            MAGAZINE.all(),
            magazineAuthor.all(),
            bookAuthor.all())
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .leftJoin(MAGAZINE.on(COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE.is(MAGAZINE._ID)))
        .leftJoin(magazineAuthor.on(MAGAZINE.AUTHOR.is(magazineAuthor.ID)))
        .leftJoin(BOOK.on(COMPLEX_OBJECT_WITH_SAME_LEAFS.BOOK.is(BOOK.BASE_ID)))
        .leftJoin(bookAuthor.on(BOOK.AUTHOR.is(bookAuthor.ID)))
        .where(magazineAuthor.NAME.is("asd").and(bookAuthor.NAME.is("dsa")))
        .queryDeep()
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("book", 0);
    columns.put("magazine", 4);
    columns.put(magazineAuthorAlias, 8);
    columns.put(bookAuthorAlias, 12);
    final SimpleArrayMap<String, String> graphNodes = new SimpleArrayMap<>();
    graphNodes.put("book", "book");
    graphNodes.put("bookauthor", bookAuthorAlias);
    graphNodes.put("magazine", "magazine");
    graphNodes.put("magazineauthor", magazineAuthorAlias);
    CompiledSelectMetadata.assertThat()
        .sql("SELECT book.*,magazine.*," + magazineAuthorAlias + ".*," + bookAuthorAlias + ".* FROM complex_object_with_same_leafs " +
            "LEFT JOIN magazine ON complex_object_with_same_leafs.magazine=magazine._id " +
            "LEFT JOIN author AS " + magazineAuthorAlias + " ON magazine.author=" + magazineAuthorAlias + ".id " +
            "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id " +
            "LEFT JOIN author AS " + bookAuthorAlias + " ON book.author=" + bookAuthorAlias + ".id " +
            "WHERE (" + magazineAuthorAlias + ".name=? " +
            "AND " + bookAuthorAlias + ".name=?) ")
        .args("asd", "dsa")
        .columns(columns)
        .tableGraphNodeNames(graphNodes)
        .tableName("complex_object_with_same_leafs")
        .observedTables("complex_object_with_same_leafs", "book", "author", "magazine")
        .queryDeep(true)
        .build()
        .isEqualTo(select);

    final CompiledSelectImpl<ComplexObjectWithSameLeafs, SelectN> select2 = (CompiledSelectImpl<ComplexObjectWithSameLeafs, SelectN>) Select
        .columns(BOOK.all(),
            MAGAZINE.all(),
            AUTHOR.all())
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .queryDeep()
        .compile();

    columns.clear();
    graphNodes.clear();
    final SimpleArrayMap<String, String> tableGraphNodeNames = select2.tableGraphNodeNames;
    assertThat(tableGraphNodeNames).isNotNull();
    columns.put("book", 0);
    columns.put(tableGraphNodeNames.get("magazine"), 4);
    columns.put("author", 8);
    columns.put(tableGraphNodeNames.get("magazineauthor"), 12);
    graphNodes.put("book", "book");
    graphNodes.put("bookauthor", "author");
    graphNodes.put("magazine", "?");
    graphNodes.put("magazineauthor", "?");
    final CompiledSelectMetadata compiledSelectMetadata = CompiledSelectMetadata.assertThat()
        .sqlWithWildcards("SELECT book.*,?.*,author.*,?.* FROM complex_object_with_same_leafs " +
            "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id " +
            "LEFT JOIN author ON book.author=author.id " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id " +
            "LEFT JOIN author AS ? ON ?.author=?.id ")
        .columns(columns)
        .tableGraphNodeNames(graphNodes)
        .tableName("complex_object_with_same_leafs")
        .observedTables("complex_object_with_same_leafs", "book", "author", "magazine")
        .queryDeep(true)
        .build();
    compiledSelectMetadata.isEqualTo(select2);
  }

  @Test
  public void userDefinedJoinDoesNotInterfereWithComplexColumnAllSelect() {
    final AuthorTable auth = AUTHOR.as("auth");
    final CompiledSelect<ComplexValueWithCreator, SelectN> compiledSelect = Select
        .columns(
            COMPLEX_VALUE_WITH_CREATOR.STRING,
            COMPLEX_VALUE_WITH_CREATOR.ID,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_AUTHOR,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_COMPLEX_OBJECT_WITH_SAME_LEAFS,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_CREATOR_SIMPLE_VALUE,
            COMPLEX_VALUE_WITH_CREATOR.NOT_PERSISTED_BUILDER_SIMPLE_VALUE,
            auth.all(),
            COMPLEX_OBJECT_WITH_SAME_LEAFS.all(),
            SIMPLE_VALUE_WITH_BUILDER.all(),
            SIMPLE_VALUE_WITH_CREATOR.all()
        )
        .from(COMPLEX_VALUE_WITH_CREATOR)
        .leftJoin(auth.on(COMPLEX_VALUE_WITH_CREATOR.AUTHOR.is(auth.ID)))
        .queryDeep()
        .compile();

    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards("SELECT " +
            "complex_value_with_creator.string," +
            "complex_value_with_creator.id," +
            "complex_value_with_creator.not_persisted_author," +
            "complex_value_with_creator.not_persisted_complex_object_with_same_leafs," +
            "complex_value_with_creator.not_persisted_creator_simple_value," +
            "complex_value_with_creator.not_persisted_builder_simple_value," +
            "auth.*," +
            "?.*," +
            "?.*," +
            "?.*," +
            "simple_value_with_builder.*," +
            "simple_value_with_creator.* " +
            "FROM complex_value_with_creator " +
            "LEFT JOIN author AS auth ON complex_value_with_creator.author=auth.id " +
            "LEFT JOIN author AS ? ON complex_value_with_creator.nullable_author=?.id " +
            "LEFT JOIN complex_object_with_same_leafs AS ? ON complex_value_with_creator.complex_object_with_same_leafs=?.id " +
            "LEFT JOIN simple_value_with_builder AS ? ON ?.simple_value_with_builder=?.id " +
            "LEFT JOIN simple_value_with_builder AS ? ON ?.simple_value_with_builder_duplicate=?.id " +
            "LEFT JOIN simple_value_with_builder ON complex_value_with_creator.builder_simple_value=simple_value_with_builder.id " +
            "LEFT JOIN simple_value_with_creator ON complex_value_with_creator.creator_simple_value=simple_value_with_creator.id ")
        .tableName("complex_value_with_creator")
        .observedTables("complex_value_with_creator", "complex_object_with_same_leafs", "simple_value_with_builder", "simple_value_with_creator", "author")
        .queryDeep(true)
        .columns(((CompiledSelectImpl) compiledSelect).columns)
        .tableGraphNodeNames(((CompiledSelectImpl) compiledSelect).tableGraphNodeNames)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void subqueryAddsItsObservedTables() {
    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .from(BOOK)
        .where(BOOK.AUTHOR.is(Select.column(MAGAZINE.AUTHOR).from(MAGAZINE)))
        .compile();

    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book WHERE book.author=(SELECT magazine.author FROM magazine ) ")
        .tableName("book")
        .observedTables("book", "magazine")
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void complexSubqueryAddsMissingJoinsAndObservedTables() {
    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .from(BOOK)
        .where(BOOK.TITLE.is(Select.column(AUTHOR.NAME).from(MAGAZINE)))
        .compile();

    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book WHERE book.title=(SELECT author.name FROM magazine LEFT JOIN author ON magazine.author=author.id ) ")
        .tableName("book")
        .observedTables("book", "magazine", "author")
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void complexQueryWithComplexSubqueryIsHandledCorrectly() {
    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .from(BOOK)
        .where(BOOK.TITLE.is(Select.column(AUTHOR.NAME).from(MAGAZINE)))
        .queryDeep()
        .compile();

    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM book LEFT JOIN author ON book.author=author.id WHERE book.title=(SELECT author.name FROM magazine LEFT JOIN author ON magazine.author=author.id ) ")
        .tableName("book")
        .observedTables("book", "magazine", "author")
        .queryDeep(true)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void simpleColumnQuery() {
    final CompiledSelect<String, Select1> compiledSelect = Select
        .column(BOOK.TITLE)
        .from(BOOK)
        .compile();

    CompiledSelectMetadata.assertThat()
        .sql("SELECT book.title FROM book ")
        .observedTables("book")
        .build()
        .isEqualToColumnSelect(compiledSelect);
  }

  @Test
  public void columnComplexQuery() {
    final CompiledSelect<String, Select1> compiledSelect = Select
        .column(AUTHOR.NAME)
        .from(BOOK)
        .compile();

    CompiledSelectMetadata.assertThat()
        .sql("SELECT author.name FROM book LEFT JOIN author ON book.author=author.id ")
        .observedTables("book", "author")
        .build()
        .isEqualToColumnSelect(compiledSelect);
  }

  @Test
  public void columnComplexQueryFromRenamedTable() {
    final BookTable b = BOOK.as("b");
    final CompiledSelect<String, Select1> compiledSelect = Select
        .column(AUTHOR.NAME)
        .from(b)
        .leftJoin(AUTHOR.on(b.AUTHOR.is(AUTHOR.ID)))
        .compile();

    CompiledSelectMetadata.assertThat()
        .sql("SELECT author.name FROM book AS b LEFT JOIN author ON b.author=author.id ")
        .observedTables("book", "author")
        .build()
        .isEqualToColumnSelect(compiledSelect);
  }

  @Test
  public void columnComplexQueryFromSystemRenamedTable() {
    final CompiledSelect<String, Select1> compiledSelect = Select
        .column(MAGAZINE.NAME)
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .compile();

    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards("SELECT ?.name FROM complex_object_with_same_leafs LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ")
        .observedTables("complex_object_with_same_leafs", "magazine")
        .build()
        .isEqualToColumnSelect(compiledSelect);
  }

  @Test
  public void userDefinedCommaJoinIsNotReAddedBySystem() {
    final CompiledSelect<Magazine, SelectN> compiledSelect = Select
        .columns(
            MAGAZINE.NAME,
            AUTHOR.NAME,
            SIMPLE_VALUE_WITH_BUILDER.all(),
            SIMPLE_VALUE_WITH_CREATOR.all())
        .from(MAGAZINE)
        .join(AUTHOR)
        .join(SIMPLE_VALUE_WITH_BUILDER)
        .join(SIMPLE_VALUE_WITH_CREATOR)
        .where(MAGAZINE.AUTHOR.is(AUTHOR.ID))
        .queryDeep()
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("magazine.name", 0);
    columns.put("author.name", 1);
    columns.put("simple_value_with_builder", 2);
    columns.put("simple_value_with_creator", 8);
    final SimpleArrayMap<String, String> graphNodes = new SimpleArrayMap<>();
    graphNodes.put("author", "author");

    CompiledSelectMetadata.assertThat()
        .sql("SELECT magazine.name,author.name,simple_value_with_builder.*,simple_value_with_creator.* " +
            "FROM magazine , author , simple_value_with_builder , simple_value_with_creator " +
            "WHERE magazine.author=author.id ")
        .tableName("magazine")
        .observedTables("magazine", "author", "simple_value_with_builder", "simple_value_with_creator")
        .columns(columns)
        .tableGraphNodeNames(graphNodes)
        .queryDeep(true)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void complexAvgFunction() {
    assertNumericFunction("avg(", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return avg(c);
      }
    });
    assertNumericFunction("avg(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return avgDistinct(c);
      }
    });
    assertNumericFunctionWithSystemRename("avg(", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return avg(c);
      }
    });
    assertNumericFunctionWithSystemRename("avg(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return avgDistinct(c);
      }
    });
  }

  @Test
  public void complexCountFunction() {
    assertNumericFunction("count(", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return count(c);
      }
    });
    assertNumericFunction("count(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return countDistinct(c);
      }
    });
    assertNumericFunctionWithSystemRename("count(", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return count(c);
      }
    });
    assertNumericFunctionWithSystemRename("count(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return countDistinct(c);
      }
    });
  }

  @Test
  public void complexGroupConcatFunction() {
    assertFunction("group_concat(", new Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>>() {
      @Override
      public Column<?, ?, ?, Author, ?> call(Column<String, String, CharSequence, Author, ?> c) {
        return groupConcat(c);
      }
    });
    assertFunction("group_concat(DISTINCT ", new Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>>() {
      @Override
      public Column<?, ?, ?, Author, ?> call(Column<String, String, CharSequence, Author, ?> c) {
        return groupConcatDistinct(c);
      }
    });
    assertFunctionWithSystemRename("group_concat(", new Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(Column<String, String, CharSequence, Magazine, ?> c) {
        return groupConcat(c);
      }
    });
    assertFunctionWithSystemRename("group_concat(DISTINCT ", new Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(Column<String, String, CharSequence, Magazine, ?> c) {
        return groupConcatDistinct(c);
      }
    });
    assertNumericFunction("group_concat(", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return groupConcat(c);
      }
    });
    assertNumericFunction("group_concat(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return groupConcatDistinct(c);
      }
    });
    assertNumericFunctionWithSystemRename("group_concat(", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return groupConcat(c);
      }
    });
    assertNumericFunctionWithSystemRename("group_concat(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return groupConcatDistinct(c);
      }
    });
  }

  @Test
  public void complexMaxFunction() {
    assertFunction("max(", new Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>>() {
      @Override
      public Column<?, ?, ?, Author, ?> call(Column<String, String, CharSequence, Author, ?> c) {
        return max(c);
      }
    });
    assertFunction("max(DISTINCT ", new Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>>() {
      @Override
      public Column<?, ?, ?, Author, ?> call(Column<String, String, CharSequence, Author, ?> c) {
        return maxDistinct(c);
      }
    });
    assertFunctionWithSystemRename("max(", new Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(Column<String, String, CharSequence, Magazine, ?> c) {
        return max(c);
      }
    });
    assertFunctionWithSystemRename("max(DISTINCT ", new Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(Column<String, String, CharSequence, Magazine, ?> c) {
        return maxDistinct(c);
      }
    });
    assertNumericFunction("max(", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return max(c);
      }
    });
    assertNumericFunction("max(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return maxDistinct(c);
      }
    });
    assertNumericFunctionWithSystemRename("max(", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return max(c);
      }
    });
    assertNumericFunctionWithSystemRename("max(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return maxDistinct(c);
      }
    });
  }

  @Test
  public void complexMinFunction() {
    assertFunction("min(", new Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>>() {
      @Override
      public Column<?, ?, ?, Author, ?> call(Column<String, String, CharSequence, Author, ?> c) {
        return min(c);
      }
    });
    assertFunction("min(DISTINCT ", new Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>>() {
      @Override
      public Column<?, ?, ?, Author, ?> call(Column<String, String, CharSequence, Author, ?> c) {
        return minDistinct(c);
      }
    });
    assertFunctionWithSystemRename("min(", new Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(Column<String, String, CharSequence, Magazine, ?> c) {
        return min(c);
      }
    });
    assertFunctionWithSystemRename("min(DISTINCT ", new Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(Column<String, String, CharSequence, Magazine, ?> c) {
        return minDistinct(c);
      }
    });
    assertNumericFunction("min(", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return min(c);
      }
    });
    assertNumericFunction("min(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return minDistinct(c);
      }
    });
    assertNumericFunctionWithSystemRename("min(", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return min(c);
      }
    });
    assertNumericFunctionWithSystemRename("min(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return minDistinct(c);
      }
    });
  }

  @Test
  public void complexSumFunction() {
    assertNumericFunction("total(", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return sum(c);
      }
    });
    assertNumericFunction("total(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return sumDistinct(c);
      }
    });
    assertNumericFunctionWithSystemRename("total(", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return sum(c);
      }
    });
    assertNumericFunctionWithSystemRename("total(DISTINCT ", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return sumDistinct(c);
      }
    });
  }

  @Test
  public void complexConcatFunction() {
    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards("SELECT book.nr_of_releases \\|\\| ?.name \\|\\| complex_object_with_same_leafs.name " +
            "FROM complex_object_with_same_leafs LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ")
        .observedTables("complex_object_with_same_leafs", "book", "magazine")
        .build()
        .isEqualToColumnSelect(Select
            .column(concat(BOOK.NR_OF_RELEASES, MAGAZINE.NAME, COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME))
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .compile());

    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards("SELECT book.nr_of_releases \\|\\| ?.name \\|\\| complex_object_with_same_leafs.name " +
            "FROM complex_object_with_same_leafs LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ")
        .observedTables("complex_object_with_same_leafs", "book", "magazine")
        .build()
        .isEqualToColumnSelect(Select
            .column(BOOK.NR_OF_RELEASES.concat(MAGAZINE.NAME.concat(COMPLEX_OBJECT_WITH_SAME_LEAFS.NAME)))
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .compile());
  }

  @Test
  public void complexAbsFunction() {
    assertNumericFunction("abs(", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return abs(c);
      }
    });
    assertNumericFunctionWithSystemRename("abs(", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return abs(c);
      }
    });
  }

  @Test
  public void complexLengthFunction() {
    assertFunction("length(", new Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>>() {
      @Override
      public Column<?, ?, ?, Author, ?> call(Column<String, String, CharSequence, Author, ?> c) {
        return length(c);
      }
    });
    assertFunctionWithSystemRename("length(", new Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(Column<String, String, CharSequence, Magazine, ?> c) {
        return length(c);
      }
    });
    assertNumericFunction("length(", new Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>>() {
      @Override
      public Column<?, ?, ?, Book, ?> call(NumericColumn<Integer, Integer, Number, Book, ?> c) {
        return length(c);
      }
    });
    assertNumericFunctionWithSystemRename("length(", new Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(NumericColumn<Integer, Integer, Number, Magazine, ?> c) {
        return length(c);
      }
    });
  }

  @Test
  public void complexLowerFunction() {
    assertFunction("lower(", new Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>>() {
      @Override
      public Column<?, ?, ?, Author, ?> call(Column<String, String, CharSequence, Author, ?> c) {
        return lower(c);
      }
    });
    assertFunctionWithSystemRename("lower(", new Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(Column<String, String, CharSequence, Magazine, ?> c) {
        return lower(c);
      }
    });
  }

  @Test
  public void complexUpperFunction() {
    assertFunction("upper(", new Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>>() {
      @Override
      public Column<?, ?, ?, Author, ?> call(Column<String, String, CharSequence, Author, ?> c) {
        return upper(c);
      }
    });
    assertFunctionWithSystemRename("upper(", new Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>>() {
      @Override
      public Column<?, ?, ?, Magazine, ?> call(Column<String, String, CharSequence, Magazine, ?> c) {
        return upper(c);
      }
    });
  }

  @Test
  public void addFunction() {
    assertArithmeticExpression('+',
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Integer, Integer, Number, Book, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Integer, Integer, Number, Book, ?> v2) {
            return v1.add(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Long, Long, Number, ?, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Long, Long, Number, ?, ?> v2) {
            return v1.add(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, Integer v2) {
            return v1.add(v2);
          }
        });
  }

  @Test
  public void subFunction() {
    assertArithmeticExpression('-',
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Integer, Integer, Number, Book, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Integer, Integer, Number, Book, ?> v2) {
            return v1.sub(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Long, Long, Number, ?, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Long, Long, Number, ?, ?> v2) {
            return v1.sub(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, Integer v2) {
            return v1.sub(v2);
          }
        });
  }

  @Test
  public void mulFunction() {
    assertArithmeticExpression('*',
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Integer, Integer, Number, Book, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Integer, Integer, Number, Book, ?> v2) {
            return v1.mul(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Long, Long, Number, ?, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Long, Long, Number, ?, ?> v2) {
            return v1.mul(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, Integer v2) {
            return v1.mul(v2);
          }
        });
  }

  @Test
  public void divFunction() {
    assertArithmeticExpression('/',
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Integer, Integer, Number, Book, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Integer, Integer, Number, Book, ?> v2) {
            return v1.div(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Long, Long, Number, ?, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Long, Long, Number, ?, ?> v2) {
            return v1.div(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, Integer v2) {
            return v1.div(v2);
          }
        });
  }

  @Test
  public void modFunction() {
    assertArithmeticExpression('%',
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Integer, Integer, Number, Book, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Integer, Integer, Number, Book, ?> v2) {
            return v1.mod(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Long, Long, Number, ?, ?>, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, NumericColumn<Long, Long, Number, ?, ?> v2) {
            return v1.mod(v2);
          }
        },
        new Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, Integer, NumericColumn>() {
          @Override
          public NumericColumn call(NumericColumn<Integer, Integer, Number, Magazine, ?> v1, Integer v2) {
            return v1.mod(v2);
          }
        });
  }

  @Test
  public void numericArithmeticExpressionsChained() {
    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards("SELECT (((?.nr_of_releases+(book.nr_of_releases*?.nr_of_releases))/(8%book.nr_of_releases))-?.nr_of_releases) FROM complex_object_with_same_leafs " +
            "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ")
        .observedTables("complex_object_with_same_leafs", "book", "magazine")
        .build()
        .isEqualToColumnSelect(Select
            .column(MAGAZINE.NR_OF_RELEASES.add(BOOK.NR_OF_RELEASES.mul(MAGAZINE.NR_OF_RELEASES))
                .div(asColumn(8).mod(BOOK.NR_OF_RELEASES)).sub(MAGAZINE.NR_OF_RELEASES))
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .compile());
  }

  @SuppressWarnings("unchecked")
  private void assertArithmeticExpression(char op,
                                          @NonNull Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Integer, Integer, Number, Book, ?>, NumericColumn> columnCallback,
                                          @NonNull Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, NumericColumn<Long, Long, Number, ?, ?>, NumericColumn> columnValueCallback,
                                          @NonNull Func2<NumericColumn<Integer, Integer, Number, Magazine, ?>, Integer, NumericColumn> valueCallback) {
    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards(String.format("SELECT (?.nr_of_releases%sbook.nr_of_releases) FROM complex_object_with_same_leafs " +
            "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ", op))
        .observedTables("complex_object_with_same_leafs", "book", "magazine")
        .build()
        .isEqualToColumnSelect(Select
            .column(columnCallback.call(MAGAZINE.NR_OF_RELEASES, BOOK.NR_OF_RELEASES))
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .compile());

    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards(String.format("SELECT (?.nr_of_releases%s5) FROM complex_object_with_same_leafs " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ", op))
        .observedTables("complex_object_with_same_leafs", "magazine")
        .build()
        .isEqualToColumnSelect(Select
            .column(columnValueCallback.call(MAGAZINE.NR_OF_RELEASES, asColumn(5L)))
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .compile());

    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards(String.format("SELECT (?.nr_of_releases%s5) FROM complex_object_with_same_leafs " +
            "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ", op))
        .observedTables("complex_object_with_same_leafs", "magazine")
        .build()
        .isEqualToColumnSelect(Select
            .column(valueCallback.call(MAGAZINE.NR_OF_RELEASES, 5))
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .compile());
  }

  private void assertFunction(@NonNull String func,
                              @NonNull Func1<Column<String, String, CharSequence, Author, ?>, Column<?, ?, ?, Author, ?>> funcCall) {
    CompiledSelectMetadata.assertThat()
        .sql(String.format("SELECT %sauthor.name) FROM book LEFT JOIN author ON book.author=author.id ", func))
        .observedTables("book", "author")
        .build()
        .isEqualToColumnSelect(Select
            .column(funcCall.call(AUTHOR.NAME))
            .from(BOOK)
            .compile());
  }

  private void assertFunctionWithSystemRename(@NonNull String func,
                                              @NonNull Func1<Column<String, String, CharSequence, Magazine, ?>, Column<?, ?, ?, Magazine, ?>> funcCall) {
    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards(String.format("SELECT %s?.name) FROM complex_object_with_same_leafs LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ", func))
        .observedTables("complex_object_with_same_leafs", "magazine")
        .build()
        .isEqualToColumnSelect(Select
            .column(funcCall.call(MAGAZINE.NAME))
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .compile());
  }

  private void assertNumericFunction(@NonNull String func,
                                     @NonNull Func1<NumericColumn<Integer, Integer, Number, Book, ?>, Column<?, ?, ?, Book, ?>> funcCall) {
    CompiledSelectMetadata.assertThat()
        .sql(String.format("SELECT %sbook.nr_of_releases) FROM complex_object_with_same_leafs LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id ", func))
        .observedTables("complex_object_with_same_leafs", "book")
        .build()
        .isEqualToColumnSelect(Select
            .column(funcCall.call(BOOK.NR_OF_RELEASES))
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .compile());
  }

  private void assertNumericFunctionWithSystemRename(@NonNull String func,
                                                     @NonNull Func1<NumericColumn<Integer, Integer, Number, Magazine, ?>, Column<?, ?, ?, Magazine, ?>> funcCall) {
    CompiledSelectMetadata.assertThat()
        .sqlWithWildcards(String.format("SELECT %s?.nr_of_releases) FROM complex_object_with_same_leafs LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ", func))
        .observedTables("complex_object_with_same_leafs", "magazine")
        .build()
        .isEqualToColumnSelect(Select
            .column(funcCall.call(MAGAZINE.NR_OF_RELEASES))
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
            .compile());
  }

  @Test
  public void unorderedAliasedViewBuildsImprovedStructure() {
    final CompiledSelect compiledSelect = Select
        .from(COMPLEX_INTERFACE_VIEW)
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("builder_magazine", 0);
    columns.put(MAGAZINE_ALIAS, 0);
    columns.put("author", 5);
    columns.put("simple_value_with_builder", 9);
    columns.put("simple_value_with_creator", 15);
    columns.put(AUTHOR_NAME_ALIAS, 21);
    columns.put("author.name", 21);
    columns.put(VALUE_W_BUILDER_ALIAS, 22);
    columns.put("simple_value_with_builder.string_value", 22);
    final CompiledSelectImpl query = (CompiledSelectImpl) ComplexInterfaceView.QUERY;
    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM complex_interface_view ")
        .tableName("complex_interface_view")
        .observedTables(SimpleValueWithBuilder.TABLE, BuilderMagazine.TABLE, Author.TABLE, SimpleValueWithCreator.TABLE)
        .tableGraphNodeNames(query.tableGraphNodeNames)
        .columns(columns)
        .queryDeep(true)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void viewWithDeepQueryPreservesItsDeepnessInSelect() {
    CompiledSelect compiledSelect = Select
        .from(COMPLEX_INTERFACE_VIEW)
        .queryDeep()
        .compile();
    assertThat(((CompiledSelectImpl) compiledSelect).queryDeep).isTrue();

    compiledSelect = Select
        .from(COMPLEX_INTERFACE_VIEW)
        .compile();
    assertThat(((CompiledSelectImpl) compiledSelect).queryDeep).isTrue();
  }

  @Test
  public void viewPerfectsSelectStatement() {
    final CompiledSelect<ComplexView, SelectN> compiledSelect = Select
        .from(COMPLEX_VIEW)
        .compile();
    final CompiledSelectImpl query = (CompiledSelectImpl) ComplexView.QUERY;

    CompiledSelectMetadata.assertThat()
        .sql("SELECT * FROM complex_view ")
        .tableName("complex_view")
        .observedTables(Book.TABLE, Magazine.TABLE, Author.TABLE)
        .queryDeep(true)
        .tableGraphNodeNames(query.tableGraphNodeNames)
        .columns(query.columns)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectionAsColumn() {
    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .columns(BOOK.TITLE,
            BOOK.AUTHOR,
            Select.column(count())
                .from(MAGAZINE)
                .where(MAGAZINE.AUTHOR.is(BOOK.AUTHOR))
                .toColumn("same_authors"))
        .from(BOOK)
        .compile();


    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("book.title", 0);
    columns.put("book.author", 1);
    columns.put("same_authors", 2);
    CompiledSelectMetadata.assertThat()
        .sql("SELECT book.title," +
            "book.author," +
            "(SELECT count(*) FROM magazine WHERE magazine.author=book.author ) AS 'same_authors' " +
            "FROM book ")
        .tableName(Book.TABLE)
        .observedTables(Book.TABLE, Magazine.TABLE)
        .tableGraphNodeNames(new SimpleArrayMap<String, String>())
        .columns(columns)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectionAsColumnUsedMultipleTimes() {
    final NumericColumn<Long, Long, Long, ?, ?> same_authors = Select
        .column(count())
        .from(MAGAZINE)
        .where(MAGAZINE.AUTHOR.is(BOOK.AUTHOR))
        .toColumn("same_authors");

    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .columns(BOOK.TITLE, BOOK.AUTHOR, same_authors)
        .from(BOOK)
        .where(same_authors.greaterThan(5L))
        .orderBy(same_authors.asc())
        .compile();


    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("book.title", 0);
    columns.put("book.author", 1);
    columns.put("same_authors", 2);
    CompiledSelectMetadata.assertThat()
        .sql("SELECT book.title," +
            "book.author," +
            "(SELECT count(*) FROM magazine WHERE magazine.author=book.author ) AS 'same_authors' " +
            "FROM book " +
            "WHERE same_authors>? " +
            "ORDER BY same_authors ASC ")
        .tableName(Book.TABLE)
        .observedTables(Book.TABLE, Magazine.TABLE)
        .tableGraphNodeNames(new SimpleArrayMap<String, String>())
        .columns(columns)
        .args("5")
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectionAsColumnWrappedInFunctionColumn() {
    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .columns(BOOK.TITLE,
            BOOK.AUTHOR,
            lower(Select.column(MAGAZINE.NAME)
                .from(MAGAZINE)
                .where(MAGAZINE.AUTHOR.is(BOOK.AUTHOR))
                .toColumn("mag_name")))
        .from(BOOK)
        .compile();


    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("book.title", 0);
    columns.put("book.author", 1);
    columns.put("mag_name", 2);
    CompiledSelectMetadata.assertThat()
        .sql("SELECT book.title," +
            "book.author," +
            "lower((SELECT magazine.name FROM magazine WHERE magazine.author=book.author )) AS 'mag_name' " +
            "FROM book ")
        .tableName(Book.TABLE)
        .observedTables(Book.TABLE, Magazine.TABLE)
        .tableGraphNodeNames(new SimpleArrayMap<String, String>())
        .columns(columns)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectionAsColumnWrappedInFunctionColumnUsedMultipleTimes() {
    final Column<String, String, CharSequence, ?, ?> mag_name = lower(Select
        .column(MAGAZINE.NAME)
        .from(MAGAZINE)
        .where(MAGAZINE.AUTHOR.is(BOOK.AUTHOR))
        .toColumn("mag_name"));

    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .columns(BOOK.TITLE, BOOK.AUTHOR, mag_name)
        .from(BOOK)
        .where(mag_name.isNot("foo"))
        .orderBy(mag_name.asc())
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("book.title", 0);
    columns.put("book.author", 1);
    columns.put("mag_name", 2);
    CompiledSelectMetadata.assertThat()
        .sql("SELECT book.title," +
            "book.author," +
            "lower((SELECT magazine.name FROM magazine WHERE magazine.author=book.author )) AS 'mag_name' " +
            "FROM book " +
            "WHERE mag_name!=? " +
            "ORDER BY mag_name ASC ")
        .tableName(Book.TABLE)
        .observedTables(Book.TABLE, Magazine.TABLE)
        .tableGraphNodeNames(new SimpleArrayMap<String, String>())
        .columns(columns)
        .args("foo")
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectionAsColumnWrappedInFunctionCopyColumn() {
    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .columns(BOOK.TITLE,
            BOOK.AUTHOR,
            abs(Select.column(count())
                .from(MAGAZINE)
                .where(MAGAZINE.AUTHOR.is(BOOK.AUTHOR))
                .toColumn("same_authors")))
        .from(BOOK)
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("book.title", 0);
    columns.put("book.author", 1);
    columns.put("same_authors", 2);
    columns.put("abs(same_authors)", 2);
    CompiledSelectMetadata.assertThat()
        .sql("SELECT book.title," +
            "book.author," +
            "abs((SELECT count(*) FROM magazine WHERE magazine.author=book.author )) AS 'same_authors' " +
            "FROM book ")
        .tableName(Book.TABLE)
        .observedTables(Book.TABLE, Magazine.TABLE)
        .tableGraphNodeNames(new SimpleArrayMap<String, String>())
        .columns(columns)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectionAsColumnWrappedInFunctionCopyColumnUsedMultipleTimes() {
    final NumericColumn<Long, Long, Long, ?, ?> same_authors = abs(Select
        .column(count())
        .from(MAGAZINE)
        .where(MAGAZINE.AUTHOR.is(BOOK.AUTHOR))
        .toColumn("same_authors"));

    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .columns(BOOK.TITLE, BOOK.AUTHOR, same_authors)
        .from(BOOK)
        .where(same_authors.greaterThan(5L))
        .orderBy(same_authors.desc())
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("book.title", 0);
    columns.put("book.author", 1);
    columns.put("same_authors", 2);
    columns.put("abs(same_authors)", 2);
    CompiledSelectMetadata.assertThat()
        .sql("SELECT book.title," +
            "book.author," +
            "abs((SELECT count(*) FROM magazine WHERE magazine.author=book.author )) AS 'same_authors' " +
            "FROM book " +
            "WHERE same_authors>? " +
            "ORDER BY same_authors DESC ")
        .tableName(Book.TABLE)
        .observedTables(Book.TABLE, Magazine.TABLE)
        .tableGraphNodeNames(new SimpleArrayMap<String, String>())
        .columns(columns)
        .args("5")
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectionAsColumnUsedInExpression() {
    final NumericColumn<Long, Long, Long, ?, ?> same_authors = Select
        .column(count())
        .from(MAGAZINE)
        .where(MAGAZINE.AUTHOR.is(BOOK.AUTHOR))
        .toColumn("same_authors");

    final CompiledSelect<Book, SelectN> compiledSelect = Select
        .columns(BOOK.TITLE, BOOK.AUTHOR, same_authors)
        .from(BOOK)
        .where(BOOK.NR_OF_RELEASES.greaterThan(same_authors))
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("book.title", 0);
    columns.put("book.author", 1);
    columns.put("same_authors", 2);
    columns.put("same_authors", 2);
    CompiledSelectMetadata.assertThat()
        .sql("SELECT book.title," +
            "book.author," +
            "(SELECT count(*) FROM magazine WHERE magazine.author=book.author ) AS 'same_authors' " +
            "FROM book " +
            "WHERE book.nr_of_releases>same_authors ")
        .tableName(Book.TABLE)
        .observedTables(Book.TABLE, Magazine.TABLE)
        .tableGraphNodeNames(new SimpleArrayMap<String, String>())
        .columns(columns)
        .build()
        .isEqualTo(compiledSelect);
  }

  @Test
  public void selectionAsColumnThrowsIfSelectedMultipleTables() {
    int exceptions = 0;
    try {
      final CompiledSelect<Book, SelectN> compiledSelect = Select
          .columns(BOOK.TITLE,
              BOOK.AUTHOR,
              Select.from(MAGAZINE)
                  .where(MAGAZINE.AUTHOR.is(BOOK.AUTHOR))
                  .toColumn("same_authors"))
          .from(BOOK)
          .compile();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(SQLException.class);
      exceptions++;
    }
    try {
      final CompiledSelect<Book, SelectN> compiledSelect = Select
          .columns(BOOK.TITLE,
              BOOK.AUTHOR,
              Select.columns(MAGAZINE.NAME, MAGAZINE.NR_OF_RELEASES)
                  .from(MAGAZINE)
                  .where(MAGAZINE.AUTHOR.is(BOOK.AUTHOR))
                  .toColumn("same_authors"))
          .from(BOOK)
          .compile();
    } catch (Exception e) {
      assertThat(e).isInstanceOf(SQLException.class);
      exceptions++;
    }
    assertThat(exceptions).isEqualTo(2);
  }

  @Test(expected = SQLException.class)
  public void ambiguousFunctionColumnThrows() {
    final CompiledSelect<ComplexObjectWithSameLeafs, SelectN> compiledSelect = Select
        .columns(lower(AUTHOR.NAME),
            BOOK.all(),
            MAGAZINE.all())
        .from(COMPLEX_OBJECT_WITH_SAME_LEAFS)
        .queryDeep()
        .compile();
  }

  @Test
  public void selectCountFromImmutableComplexTableDoesNotThrowOrAddAnyExtraJoins() {
    final CompiledSelect<Long, Select1> compiledSelect = Select
        .column(count())
        .from(COMPLEX_VALUE_WITH_CREATOR)
        .compile();

    final SimpleArrayMap<String, Integer> columns = new SimpleArrayMap<>();
    columns.put("count(*)", 0);
    CompiledSelectMetadata.assertThat()
        .sql("SELECT count(*) FROM complex_value_with_creator ")
        .tableName(ComplexValueWithCreator.TABLE)
        .observedTables(ComplexValueWithCreator.TABLE)
        .tableGraphNodeNames(new SimpleArrayMap<String, String>())
        .columns(columns)
        .build()
        .isEqualToColumnSelect(compiledSelect);
  }

  @Builder(builderMethodName = "assertThat")
  static class CompiledSelectMetadata {
    @lombok.NonNull
    final String sql;
    final String[] args;
    final String tableName;
    @lombok.NonNull
    final String[] observedTables;
    final SimpleArrayMap<String, Integer> columns;
    final SimpleArrayMap<String, String> tableGraphNodeNames;
    final boolean queryDeep;

    CompiledSelectMetadataBuilder copy() {
      return CompiledSelectMetadata.assertThat()
          .sql(sql)
          .args(args)
          .tableName(tableName)
          .observedTables(observedTables)
          .columns(columns)
          .tableGraphNodeNames(tableGraphNodeNames)
          .queryDeep(queryDeep);
    }

    void isEqualTo(CompiledSelect<?, SelectN> actualGeneric) {
      final CompiledSelectImpl<?, SelectN> actual = (CompiledSelectImpl<?, SelectN>) actualGeneric;
      assertStringsAreEqualOrMatching(actual.sql, sql);
      Truth.assertThat(actual.args).isEqualTo(args);
      Truth.assertThat(actual.table.nameInQuery).isEqualTo(tableName);
      Truth.assertThat(actual.observedTables).asList().containsExactly(observedTables);
      assertSimpleArrayMapsAreEqualWithWildcardInKey(actual.columns, columns);
      assertSimpleArrayMapsAreEqualWithWildcardInValue(actual.tableGraphNodeNames, tableGraphNodeNames);
      Truth.assertThat(actual.queryDeep).isEqualTo(queryDeep);
    }

    void isEqualTo(CompiledFirstSelect<?, SelectN> actualGeneric) {
      final CompiledSelectImpl.CompiledFirstSelectImpl<ComplexObjectWithSameLeafs, SelectN> actual = (CompiledSelectImpl.CompiledFirstSelectImpl<ComplexObjectWithSameLeafs, SelectN>) actualGeneric;
      assertStringsAreEqualOrMatching(actual.sql, sql);
      Truth.assertThat(actual.args).isEqualTo(args);
      Truth.assertThat(actual.table.nameInQuery).isEqualTo(tableName);
      Truth.assertThat(actual.observedTables).isEqualTo(observedTables);
      assertSimpleArrayMapsAreEqualWithWildcardInKey(actual.columns, columns);
      assertSimpleArrayMapsAreEqualWithWildcardInValue(actual.tableGraphNodeNames, tableGraphNodeNames);
      Truth.assertThat(actual.queryDeep).isEqualTo(queryDeep);
    }

    void isEqualToColumnSelect(CompiledSelect<?, Select1> actualGeneric) {
      final CompiledSelect1Impl<?, Select1> actual = (CompiledSelect1Impl<?, Select1>) actualGeneric;
      assertStringsAreEqualOrMatching(actual.sql, sql);
      Truth.assertThat(actual.args).isEqualTo(args);
      Truth.assertThat(actual.observedTables).asList().containsExactly(observedTables);
    }

    void isEqualToFirstColumnSelect(CompiledFirstSelect<?, Select1> actualGeneric) {
      final CompiledSelect1Impl.CompiledFirstSelect1Impl<?, Select1> actual = (CompiledSelect1Impl.CompiledFirstSelect1Impl<?, Select1>) actualGeneric;
      assertStringsAreEqualOrMatching(actual.sql, sql);
      Truth.assertThat(actual.args).isEqualTo(args);
      Truth.assertThat(actual.observedTables).asList().containsExactly(observedTables);
    }

    static class CompiledSelectMetadataBuilder {
      private String sql;
      private String[] args;
      private String[] observedTables;
      private SimpleArrayMap<String, Integer> columns;
      private SimpleArrayMap<String, String> tableGraphNodeNames;

      CompiledSelectMetadataBuilder sqlWithWildcards(String sql) {
        this.sql = replaceRandomTableNames(sql);
        return this;
      }

      CompiledSelectMetadataBuilder args(String... args) {
        this.args = args;
        return this;
      }

      CompiledSelectMetadataBuilder observedTables(String... observedTables) {
        this.observedTables = observedTables;
        return this;
      }

      CompiledSelectMetadataBuilder columns(SimpleArrayMap<String, Integer> columns) {
        this.columns = new SimpleArrayMap<>(columns);
        return this;
      }

      CompiledSelectMetadataBuilder tableGraphNodeNames(SimpleArrayMap<String, String> tableGraphNodeNames) {
        this.tableGraphNodeNames = new SimpleArrayMap<>(tableGraphNodeNames);
        return this;
      }
    }
  }
}
