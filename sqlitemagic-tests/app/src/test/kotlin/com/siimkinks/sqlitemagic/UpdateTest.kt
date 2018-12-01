package com.siimkinks.sqlitemagic

import android.database.sqlite.SQLiteDatabase
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockito_kotlin.mock
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.BookTable.BOOK
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.ComplexValueWithBuilderTable.COMPLEX_VALUE_WITH_BUILDER
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.SimpleAllPrimitiveBuilderTable.SIMPLE_ALL_PRIMITIVE_BUILDER
import com.siimkinks.sqlitemagic.SimpleAllValuesMutableTable.SIMPLE_ALL_VALUES_MUTABLE
import com.siimkinks.sqlitemagic.UnitTestUtil.assertStringsAreEqualOrMatching
import com.siimkinks.sqlitemagic.UnitTestUtil.replaceRandomTableNames
import com.siimkinks.sqlitemagic.model.Author
import org.junit.Before
import org.junit.Test

class UpdateTest {
  @Before
  fun setUp() {
    val instance = SqliteMagic.SingletonHolder.instance
    instance.defaultConnection = mock()
  }

  @Test
  fun setRawValue() {
    Update
        .table("book")
        .set("nr_of_releases", "1")
        .assertProduces(
            sql = "UPDATE book SET nr_of_releases=? ",
            nodeCount = 3,
            args = *arrayOf("1"))
  }

  @Test
  fun setValue() {
    Update
        .table(BOOK)
        .set(BOOK.NR_OF_RELEASES, 1)
        .assertProduces(
            sql = "UPDATE book SET nr_of_releases=? ",
            nodeCount = 3,
            args = *arrayOf("1"))
  }

  @Test
  fun setNullableValue() {
    Update
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .assertProduces(
            sql = "UPDATE author SET name=? ",
            nodeCount = 3,
            args = *arrayOf("asd"))
  }

  @Test
  fun setNullValue() {
    Update
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, null)
        .assertProduces(
            sql = "UPDATE author SET name=? ",
            nodeCount = 3,
            args = *arrayOf(null as? String?))
  }

  @Test
  fun setPrimitiveBoolean() {
    Update
        .table(AUTHOR)
        .set(AUTHOR.PRIMITIVE_BOOLEAN, false)
        .assertProduces(
            sql = "UPDATE author SET primitive_boolean=? ",
            nodeCount = 3,
            args = *arrayOf("0"))
  }

  @Test
  fun setBoxedBoolean() {
    Update
        .table(AUTHOR)
        .setNullable(AUTHOR.BOXED_BOOLEAN, true)
        .assertProduces(
            sql = "UPDATE author SET boxed_boolean=? ",
            nodeCount = 3,
            args = *arrayOf("1"))
  }

  @Test
  fun rawWithCustomConflictAlgorithm() {
    Update
        .withConflictAlgorithm(SQLiteDatabase.CONFLICT_FAIL)
        .table("author")
        .set("name", "asd")
        .assertProduces(
            sql = "UPDATE  OR FAIL author SET name=? ",
            nodeCount = 4,
            args = *arrayOf("asd"))
  }

  @Test
  fun withCustomConflictAlgorithm() {
    Update
        .withConflictAlgorithm(SQLiteDatabase.CONFLICT_FAIL)
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .assertProduces(
            sql = "UPDATE  OR FAIL author SET name=? ",
            nodeCount = 4,
            args = *arrayOf("asd"))
  }

  @Test
  fun withDefaultConflictAlgorithm() {
    Update
        .withConflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .assertProduces(
            sql = "UPDATE  OR IGNORE author SET name=? ",
            nodeCount = 4,
            args = *arrayOf("asd"))
  }

  @Test
  fun setRawChainedValue() {
    Update
        .table("simple_all_primitive_builder")
        .set("bool", "1")
        .set("integer", "1")
        .assertProduces(
            sql = "UPDATE simple_all_primitive_builder SET bool=?,integer=? ",
            nodeCount = 3,
            args = *arrayOf("1", "1"))
  }

  @Test
  fun setChainedValue() {
    Update
        .table(SIMPLE_ALL_PRIMITIVE_BUILDER)
        .set(SIMPLE_ALL_PRIMITIVE_BUILDER.BOOL, true)
        .set(SIMPLE_ALL_PRIMITIVE_BUILDER.INTEGER, 1)
        .assertProduces(
            sql = "UPDATE simple_all_primitive_builder SET bool=?,integer=? ",
            nodeCount = 3,
            args = *arrayOf("1", "1"))
  }

  @Test
  fun setChainedNullableValue() {
    Update
        .table(AUTHOR)
        .set(AUTHOR.PRIMITIVE_BOOLEAN, true)
        .setNullable(AUTHOR.BOXED_BOOLEAN, false)
        .assertProduces(
            sql = "UPDATE author SET primitive_boolean=?,boxed_boolean=? ",
            nodeCount = 3,
            args = *arrayOf("1", "0"))
  }

  @Test
  fun setChainedNullableValues() {
    Update
        .table(AUTHOR)
        .set(AUTHOR.PRIMITIVE_BOOLEAN, true)
        .setNullable(AUTHOR.BOXED_BOOLEAN, false)
        .setNullable(AUTHOR.NAME, "asd")
        .assertProduces(
            sql = "UPDATE author SET primitive_boolean=?,boxed_boolean=?,name=? ",
            nodeCount = 3,
            args = *arrayOf("1", "0", "asd"))
  }

  @Test
  fun setChainedNullValue() {
    Update
        .table(AUTHOR)
        .set(AUTHOR.PRIMITIVE_BOOLEAN, true)
        .setNullable(AUTHOR.NAME, null)
        .assertProduces(
            sql = "UPDATE author SET primitive_boolean=?,name=? ",
            nodeCount = 3,
            args = *arrayOf("1", null))
  }

  @Test
  fun setChainedMiddleNullValue() {
    Update
        .table(AUTHOR)
        .set(AUTHOR.PRIMITIVE_BOOLEAN, true)
        .setNullable(AUTHOR.NAME, null)
        .set(AUTHOR.PRIMITIVE_BOOLEAN, false)
        .assertProduces(
            sql = "UPDATE author SET primitive_boolean=?,name=?,primitive_boolean=? ",
            nodeCount = 3,
            args = *arrayOf("1", null, "0"))
  }

  @Test
  fun setRawChainedValuesWithConflictAlgorithm() {
    Update
        .withConflictAlgorithm(SQLiteDatabase.CONFLICT_ROLLBACK)
        .table("author")
        .set("name", "asd")
        .set("boxed_boolean", "1")
        .set("id", "2")
        .set("primitive_boolean", "0")
        .assertProduces(
            sql = "UPDATE  OR ROLLBACK author SET name=?,boxed_boolean=?,id=?,primitive_boolean=? ",
            nodeCount = 4,
            args = *arrayOf("asd", "1", "2", "0"))
  }

  @Test
  fun setChainedValuesWithConflictAlgorithm() {
    Update
        .withConflictAlgorithm(SQLiteDatabase.CONFLICT_ROLLBACK)
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .setNullable(AUTHOR.BOXED_BOOLEAN, java.lang.Boolean.TRUE)
        .setNullable(AUTHOR.ID, 2L)
        .set(AUTHOR.PRIMITIVE_BOOLEAN, false)
        .assertProduces(
            sql = "UPDATE  OR ROLLBACK author SET name=?,boxed_boolean=?,id=?,primitive_boolean=? ",
            nodeCount = 4,
            args = *arrayOf("asd", "1", "2", "0"))
  }

  @Test
  fun rawUpdateWithWhereClause() {
    Update
        .table("author")
        .set("name", "asd")
        .where("author.id=?", "2")
        .assertProduces(
            sql = "UPDATE author SET name=? WHERE author.id=? ",
            nodeCount = 4,
            args = *arrayOf("asd", "2"))
  }

  @Test
  fun updateWithWhereClause() {
    Update
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .where(AUTHOR.ID.`is`(2L))
        .assertProduces(
            sql = "UPDATE author SET name=? WHERE author.id=? ",
            nodeCount = 4,
            args = *arrayOf("asd", "2"))

    Update
        .withConflictAlgorithm(SQLiteDatabase.CONFLICT_IGNORE)
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .setNullable(AUTHOR.BOXED_BOOLEAN, java.lang.Boolean.FALSE)
        .where(AUTHOR.ID.`is`(2L).and(AUTHOR.NAME.isNot("asd")))
        .assertProduces(
            sql = "UPDATE  OR IGNORE author SET name=?,boxed_boolean=? WHERE (author.id=? AND author.name!=?) ",
            nodeCount = 5,
            args = *arrayOf("asd", "0", "2", "asd"))

    Update
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .setNullable(AUTHOR.BOXED_BOOLEAN, java.lang.Boolean.FALSE)
        .where(AUTHOR.ID.`is`(2L).or(AUTHOR.NAME.isNot("asd")))
        .assertProduces(
            sql = "UPDATE author SET name=?,boxed_boolean=? WHERE (author.id=? OR author.name!=?) ",
            nodeCount = 4,
            args = *arrayOf("asd", "0", "2", "asd"))

    Update
        .withConflictAlgorithm(SQLiteDatabase.CONFLICT_FAIL)
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .where(AUTHOR.ID.`is`(2L).and(AUTHOR.NAME.isNot("asd")))
        .assertProduces(
            sql = "UPDATE  OR FAIL author SET name=? WHERE (author.id=? AND author.name!=?) ",
            nodeCount = 5,
            args = *arrayOf("asd", "2", "asd"))

    Update
        .withConflictAlgorithm(SQLiteDatabase.CONFLICT_FAIL)
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .where(AUTHOR.ID.`is`(2L)
            .and(AUTHOR.NAME.isNotNull)
            .and(AUTHOR.NAME.isNot("asd"))
            .and(AUTHOR.PRIMITIVE_BOOLEAN.`is`(false)))
        .assertProduces(
            sql = "UPDATE  OR FAIL author SET name=? WHERE (((author.id=? AND author.name IS NOT NULL) AND author.name!=?) AND author.primitive_boolean=?) ",
            nodeCount = 5,
            args = *arrayOf("asd", "2", "asd", "0"))

    Update
        .withConflictAlgorithm(SQLiteDatabase.CONFLICT_FAIL)
        .table(AUTHOR)
        .setNullable(AUTHOR.NAME, "asd")
        .where(AUTHOR.ID.`is`(2L)
            .and(AUTHOR.NAME.isNotNull)
            .or(AUTHOR.NAME.isNot("asd"))
            .or(AUTHOR.PRIMITIVE_BOOLEAN.`is`(false)
                .and(AUTHOR.BOXED_BOOLEAN.isNotNull)))
        .assertProduces(
            sql = "UPDATE  OR FAIL author SET name=? WHERE (((author.id=? AND author.name IS NOT NULL) OR author.name!=?) OR (author.primitive_boolean=? AND author.boxed_boolean IS NOT NULL)) ",
            nodeCount = 5,
            args = *arrayOf("asd", "2", "asd", "0"))
  }

  @Test
  fun updateComplexColumn() {
    val author = Author.newRandom()
    val id = author.id
    val idStr = java.lang.Long.toString(id!!)
    Update
        .table(COMPLEX_VALUE_WITH_BUILDER)
        .set(COMPLEX_VALUE_WITH_BUILDER.AUTHOR, author)
        .set(COMPLEX_VALUE_WITH_BUILDER.AUTHOR, author)
        .assertProduces(
            sql = "UPDATE complex_value_with_builder SET author=?,author=? ",
            nodeCount = 3,
            args = *arrayOf(idStr, idStr))

    Update
        .table(COMPLEX_VALUE_WITH_BUILDER)
        .set(COMPLEX_VALUE_WITH_BUILDER.AUTHOR, id)
        .set(COMPLEX_VALUE_WITH_BUILDER.AUTHOR, id)
        .assertProduces(
            sql = "UPDATE complex_value_with_builder SET author=?,author=? ",
            nodeCount = 3,
            args = *arrayOf(idStr, idStr))
  }

  @Test
  fun updateNullableComplexColumn() {
    val author = Author.newRandom()
    val id = author.id
    val idStr = java.lang.Long.toString(id!!)
    Update
        .table(BOOK)
        .setNullable(BOOK.AUTHOR, author)
        .setNullable(BOOK.AUTHOR, author)
        .assertProduces(
            sql = "UPDATE book SET author=?,author=? ",
            nodeCount = 3,
            args = *arrayOf(idStr, idStr))

    Update
        .table(BOOK)
        .set(BOOK.AUTHOR, id)
        .set(BOOK.AUTHOR, id)
        .assertProduces(
            sql = "UPDATE book SET author=?,author=? ",
            nodeCount = 3,
            args = *arrayOf(idStr, idStr))
  }

  @Test
  fun setNullComplexColumn() {
    Update
        .table(BOOK)
        .setNullable(BOOK.AUTHOR, null)
        .setNullable(BOOK.AUTHOR, null)
        .assertProduces(
            sql = "UPDATE book SET author=?,author=? ",
            nodeCount = 3,
            args = *arrayOf<String?>(null, null))
  }

  @Test
  fun setColumn() {
    Update
        .table(BOOK)
        .set(BOOK.NR_OF_RELEASES, BOOK.NR_OF_RELEASES.add(6))
        .assertProduces(
            sql = "UPDATE book SET nr_of_releases=(book.nr_of_releases+6) ",
            nodeCount = 3)
  }

  @Test
  fun setNotNullableColumnWithNullableColumn() {
    Update
        .table(BOOK)
        .set(BOOK.NR_OF_RELEASES, BOOK.BASE_ID.toNotNullable())
        .assertProduces(
            sql = "UPDATE book SET nr_of_releases=book.base_id ",
            nodeCount = 3)

    Update
        .table(SIMPLE_ALL_VALUES_MUTABLE)
        .set(SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT, SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER.add(6).toNotNullable())
        .assertProduces(
            sql = "UPDATE simple_all_values_mutable SET primitive_int=(simple_all_values_mutable.boxed_integer+6) ",
            nodeCount = 3)
  }

  @Test
  fun setNullableColumnWithNullableColumn() {
    Update
        .table(SIMPLE_ALL_VALUES_MUTABLE)
        .set(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER, SIMPLE_ALL_VALUES_MUTABLE.BOXED_LONG.add(6))
        .assertProduces(
            sql = "UPDATE simple_all_values_mutable SET boxed_integer=(simple_all_values_mutable.boxed_long+6) ",
            nodeCount = 3)
  }

  @Test
  fun setNullableColumnWithNotNullableColumn() {
    Update
        .table(AUTHOR)
        .set(AUTHOR.BOXED_BOOLEAN, AUTHOR.PRIMITIVE_BOOLEAN)
        .assertProduces(
            sql = "UPDATE author SET boxed_boolean=author.primitive_boolean ",
            nodeCount = 3)

    Update
        .table(SIMPLE_ALL_VALUES_MUTABLE)
        .set(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER, SIMPLE_ALL_VALUES_MUTABLE.PRIMITIVE_INT.add(6))
        .assertProduces(
            sql = "UPDATE simple_all_values_mutable SET boxed_integer=(simple_all_values_mutable.primitive_int+6) ",
            nodeCount = 3)
  }

  @Test
  fun setComplexColumnToComplexColumn() {
    Update
        .table(BOOK)
        .set(BOOK.AUTHOR, BOOK.AUTHOR)
        .assertProduces(
            sql = "UPDATE book SET author=book.author ",
            nodeCount = 3)
  }

  @Test
  fun setColumnToComplexColumnId() {
    Update
        .table(BOOK)
        .set(BOOK.AUTHOR, BOOK.BASE_ID)
        .assertProduces(
            sql = "UPDATE book SET author=book.base_id ",
            nodeCount = 3)
  }

  @Test
  fun updateWithSelect() {
    Update
        .table(BOOK)
        .set(BOOK.NR_OF_RELEASES, Select
            .column(MAGAZINE.NR_OF_RELEASES)
            .from(MAGAZINE))
        .assertProduces(
            sql = "UPDATE book SET nr_of_releases=(SELECT magazine.nr_of_releases FROM magazine ) ",
            nodeCount = 3)

    Update
        .table(SIMPLE_ALL_VALUES_MUTABLE)
        .set(SIMPLE_ALL_VALUES_MUTABLE.BOXED_INTEGER, Select
            .column(MAGAZINE.NR_OF_RELEASES)
            .from(MAGAZINE))
        .assertProduces(
            sql = "UPDATE simple_all_values_mutable SET boxed_integer=(SELECT magazine.nr_of_releases FROM magazine ) ",
            nodeCount = 3)

    Update
        .table(BOOK)
        .set(BOOK.AUTHOR, Select
            .column(MAGAZINE.AUTHOR)
            .from(MAGAZINE))
        .assertProduces(
            sql = "UPDATE book SET author=(SELECT magazine.author FROM magazine ) ",
            nodeCount = 3)

    Update
        .table(BOOK)
        .set(BOOK.NR_OF_RELEASES, Select
            .column(BOOK.NR_OF_RELEASES)
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS))
        .assertProduces(
            sql = "UPDATE book SET nr_of_releases=(SELECT book.nr_of_releases FROM complex_object_with_same_leafs " + "LEFT JOIN book ON complex_object_with_same_leafs.book=book.base_id ) ",
            nodeCount = 3)

    Update
        .table(BOOK)
        .set(BOOK.NR_OF_RELEASES, Select
            .column(MAGAZINE.NR_OF_RELEASES)
            .from(COMPLEX_OBJECT_WITH_SAME_LEAFS))
        .assertProducesWithWildcards(
            sql = "UPDATE book SET nr_of_releases=(SELECT ?.nr_of_releases FROM complex_object_with_same_leafs " + "LEFT JOIN magazine AS ? ON complex_object_with_same_leafs.magazine=?._id ) ",
            nodeCount = 3)
  }

  private fun UpdateSqlNode.assertProduces(sql: String,
                                           nodeCount: Int,
                                           vararg args: String?) {
    val updateBuilder = updateBuilder
    val actualSql = SqlCreator.getSql(updateBuilder.sqlTreeRoot, updateBuilder.sqlNodeCount)
    assertThat(actualSql).isEqualTo(sql)
    assertThat(updateBuilder.sqlNodeCount).isEqualTo(nodeCount)
    assertThat(updateBuilder.args).isNotNull()
    assertThat(updateBuilder.args).containsExactly(*args)
  }

  private fun UpdateSqlNode.assertProducesWithWildcards(sql: String,
                                                        nodeCount: Int,
                                                        vararg args: String?) {
    val updateBuilder = updateBuilder
    val actualSql = SqlCreator.getSql(updateBuilder.sqlTreeRoot, updateBuilder.sqlNodeCount)
    assertStringsAreEqualOrMatching(actualSql, replaceRandomTableNames(sql))
    assertThat(updateBuilder.sqlNodeCount).isEqualTo(nodeCount)
    assertThat(updateBuilder.args).isNotNull()
    assertThat(updateBuilder.args).containsExactly(*args)
  }
}