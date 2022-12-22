package com.example

import android.database.sqlite.SQLiteDatabase
import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.ImmutableValueWithFieldsTable.IMMUTABLE_VALUE_WITH_FIELDS
import com.siimkinks.sqlitemagic.ImmutableValueWithNullableFieldsTable.IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.model.Author
import com.siimkinks.sqlitemagic.model.Magazine
import org.junit.Test

class UpdateTest : DSLTests {
  @Test
  fun setRawValue() {
    (UPDATE
        TABLE "book"
        SET ("nr_of_releases" to "1"))
        .isEqualTo(
            sql = "UPDATE book SET nr_of_releases=? ",
            nodeCount = 3,
            args = arrayOf("1"))
  }

  @Test
  fun setValue() {
    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.NR_OF_RELEASES to 1))
        .isEqualTo(
            sql = "UPDATE magazine SET nr_of_releases=? ",
            nodeCount = 3,
            args = arrayOf("1"))
  }

  @Test
  fun setNullableValue() {
    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd"))
        .isEqualTo(
            sql = "UPDATE author SET name=? ",
            nodeCount = 3,
            args = arrayOf("asd"))
  }

  @Test
  fun setNullValue() {
    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.NAME to null))
        .isEqualTo(
            sql = "UPDATE author SET name=? ",
            nodeCount = 3,
            args = arrayOf(null))
  }

  @Test
  fun setPrimitiveBoolean() {
    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.PRIMITIVE_BOOLEAN to true))
        .isEqualTo(
            sql = "UPDATE author SET primitive_boolean=? ",
            nodeCount = 3,
            args = arrayOf("1"))
  }

  @Test
  fun setBoxedBoolean() {
    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.BOXED_BOOLEAN to true))
        .isEqualTo(
            sql = "UPDATE author SET boxed_boolean=? ",
            nodeCount = 3,
            args = arrayOf("1"))
  }

  @Test
  fun rawWithDefaultConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_IGNORE
        TABLE "author"
        SET ("name" to "asd"))
        .isEqualTo(
            sql = "UPDATE  OR IGNORE author SET name=? ",
            nodeCount = 4,
            args = arrayOf("asd"))
  }

  @Test
  fun withDefaultConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_IGNORE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd"))
        .isEqualTo(
            sql = "UPDATE  OR IGNORE author SET name=? ",
            nodeCount = 4,
            args = arrayOf("asd"))
  }

  @Test
  fun withCustomConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_FAIL
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd"))
        .isEqualTo(
            sql = "UPDATE  OR FAIL author SET name=? ",
            nodeCount = 4,
            args = arrayOf("asd"))
  }

  @Test
  fun setRawChainedValue() {
    (UPDATE
        TABLE "immutable_value_with_fields"
        SET ("a_boolean" to "1")
        SET ("integer" to "1"))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_fields SET a_boolean=?,integer=? ",
            nodeCount = 3,
            args = arrayOf("1", "1"))
  }

  @Test
  fun setChainedValue() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to 1))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_fields SET a_boolean=?,integer=? ",
            nodeCount = 3,
            args = arrayOf("1", "1"))
  }

  @Test
  fun setChainedNullableValue() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to 1))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_nullable_fields SET a_boolean=?,integer=? ",
            nodeCount = 3,
            args = arrayOf("1", "1"))
  }

  @Test
  fun setChainedNullableValues() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to 1)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING to "asd"))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_nullable_fields SET a_boolean=?,integer=?,string=? ",
            nodeCount = 3,
            args = arrayOf("1", "1", "asd"))
  }

  @Test
  fun setChainedNullValue() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to null))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_nullable_fields SET a_boolean=?,integer=? ",
            nodeCount = 3,
            args = arrayOf("1", null))
  }

  @Test
  fun setChainedMiddleNullValue() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.A_BOOLEAN to true)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to null)
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.STRING to "foo"))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_nullable_fields SET a_boolean=?,integer=?,string=? ",
            nodeCount = 3,
            args = arrayOf("1", null, "foo"))
  }

  @Test
  fun setRawChainedValuesWithConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_ROLLBACK
        TABLE "author"
        SET ("name" to "asd")
        SET ("boxed_boolean" to "1")
        SET ("id" to "2")
        SET ("primitive_boolean" to "0"))
        .isEqualTo(
            sql = "UPDATE  OR ROLLBACK author SET name=?,boxed_boolean=?,id=?,primitive_boolean=? ",
            nodeCount = 4,
            args = arrayOf("asd", "1", "2", "0"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_ROLLBACK
        TABLE "author"
        SET ("name" to "asd")
        SET ("boxed_boolean" to "1"))
        .isEqualTo(
            sql = "UPDATE  OR ROLLBACK author SET name=?,boxed_boolean=? ",
            nodeCount = 4,
            args = arrayOf("asd", "1"))
  }

  @Test
  fun setChainedValuesWithConflictAlgorithm() {
    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_ROLLBACK
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        SET (AUTHOR.BOXED_BOOLEAN to true)
        SET (AUTHOR.ID to 2L)
        SET (AUTHOR.PRIMITIVE_BOOLEAN to false))
        .isEqualTo(
            sql = "UPDATE  OR ROLLBACK author SET name=?,boxed_boolean=?,id=?,primitive_boolean=? ",
            nodeCount = 4,
            args = arrayOf("asd", "1", "2", "0"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_ROLLBACK
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        SET (AUTHOR.BOXED_BOOLEAN to true))
        .isEqualTo(
            sql = "UPDATE  OR ROLLBACK author SET name=?,boxed_boolean=? ",
            nodeCount = 4,
            args = arrayOf("asd", "1"))
  }

  @Test
  fun rawUpdateWithWhereClause() {
    (UPDATE
        TABLE "author"
        SET ("name" to "asd")
        WHERE ("author.id=?" to arrayOf("2")))
        .isEqualTo(
            sql = "UPDATE author SET name=? WHERE author.id=? ",
            nodeCount = 4,
            args = arrayOf("asd", "2"))
  }

  @Test
  fun updateWhereBuilder() {
    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        WHERE (AUTHOR.ID IS 2))
        .isEqualTo(
            sql = "UPDATE author SET name=? WHERE author.id=? ",
            nodeCount = 4,
            args = arrayOf("asd", "2"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_IGNORE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        SET (AUTHOR.BOXED_BOOLEAN to false)
        WHERE ((AUTHOR.ID IS 2) AND (AUTHOR.NAME IS_NOT "asd")))
        .isEqualTo(
            sql = "UPDATE  OR IGNORE author SET name=?,boxed_boolean=? WHERE (author.id=? AND author.name!=?) ",
            nodeCount = 5,
            args = arrayOf("asd", "0", "2", "asd"))

    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        SET (AUTHOR.BOXED_BOOLEAN to false)
        WHERE ((AUTHOR.ID IS 2) OR (AUTHOR.NAME IS_NOT "asd")))
        .isEqualTo(
            sql = "UPDATE author SET name=?,boxed_boolean=? WHERE (author.id=? OR author.name!=?) ",
            nodeCount = 4,
            args = arrayOf("asd", "0", "2", "asd"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_FAIL
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        WHERE ((AUTHOR.ID IS 2) OR (AUTHOR.NAME IS_NOT "asd")))
        .isEqualTo(
            sql = "UPDATE  OR FAIL author SET name=? WHERE (author.id=? OR author.name!=?) ",
            nodeCount = 5,
            args = arrayOf("asd", "2", "asd"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_FAIL
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        WHERE (
        (((AUTHOR.ID IS 2)
            AND AUTHOR.NAME.isNotNull)
            AND (AUTHOR.NAME IS_NOT "asd"))
            AND (AUTHOR.PRIMITIVE_BOOLEAN IS false)
        ))
        .isEqualTo(
            sql = "UPDATE  OR FAIL author SET name=? WHERE (((author.id=? AND author.name IS NOT NULL) AND author.name!=?) AND author.primitive_boolean=?) ",
            nodeCount = 5,
            args = arrayOf("asd", "2", "asd", "0"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_FAIL
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        WHERE (
        ((((AUTHOR.ID IS 2)
            AND AUTHOR.NAME.isNotNull)
            OR (AUTHOR.NAME IS_NOT "asd"))
            OR ((AUTHOR.PRIMITIVE_BOOLEAN IS false) AND AUTHOR.BOXED_BOOLEAN.isNotNull))
        ))
        .isEqualTo(
            sql = "UPDATE  OR FAIL author SET name=? WHERE (((author.id=? AND author.name IS NOT NULL) OR author.name!=?) OR (author.primitive_boolean=? AND author.boxed_boolean IS NOT NULL)) ",
            nodeCount = 5,
            args = arrayOf("asd", "2", "asd", "0"))
  }

  @Test
  fun updateComplexColumn() {
    val magazine = Magazine()
    val id = 42L
    magazine.id = id
    val idStr = id.toString()
    (UPDATE
        TABLE COMPLEX_OBJECT_WITH_SAME_LEAFS
        SET (COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE to magazine)
        SET (COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE to magazine))
        .isEqualTo(
            sql = "UPDATE complex_object_with_same_leafs SET magazine=?,magazine=? ",
            nodeCount = 3,
            args = arrayOf(idStr, idStr))

    (UPDATE
        TABLE COMPLEX_OBJECT_WITH_SAME_LEAFS
        SET (COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE to id)
        SET (COMPLEX_OBJECT_WITH_SAME_LEAFS.MAGAZINE to id))
        .isEqualTo(
            sql = "UPDATE complex_object_with_same_leafs SET magazine=?,magazine=? ",
            nodeCount = 3,
            args = arrayOf(idStr, idStr))
  }

  @Test
  fun updateNullableComplexColumn() {
    val author = Author()
    val id = 42L
    author.id = id
    val idStr = id.toString()
    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to author)
        SET (MAGAZINE.AUTHOR to author))
        .isEqualTo(
            sql = "UPDATE magazine SET author=?,author=? ",
            nodeCount = 3,
            args = arrayOf(idStr, idStr))

    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to id)
        SET (MAGAZINE.AUTHOR to id))
        .isEqualTo(
            sql = "UPDATE magazine SET author=?,author=? ",
            nodeCount = 3,
            args = arrayOf(idStr, idStr))
  }

  @Test
  fun setNullComplexColumn() {
    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to null)
        SET (MAGAZINE.AUTHOR to null))
        .isEqualTo(
            sql = "UPDATE magazine SET author=?,author=? ",
            nodeCount = 3,
            args = arrayOf(null, null))
  }

  @Test
  fun setColumn() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 6))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_fields SET integer=(immutable_value_with_fields.integer+6) ",
            nodeCount = 3)
  }

  @Test
  fun setNotNullableColumnWithNullableColumn() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to MAGAZINE.ID.toNotNullable()))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_fields SET integer=magazine.id ",
            nodeCount = 3)

    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_FIELDS
        SET (IMMUTABLE_VALUE_WITH_FIELDS.INTEGER to (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER + 6).toNotNullable()))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_fields SET integer=(immutable_value_with_nullable_fields.integer+6) ",
            nodeCount = 3)
  }

  @Test
  fun setNullableColumnWithNullableColumn() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to MAGAZINE.NR_OF_RELEASES + 6))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_nullable_fields SET integer=(magazine.nr_of_releases+6) ",
            nodeCount = 3)
  }

  @Test
  fun setNullableColumnWithNotNullableColumn() {
    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to IMMUTABLE_VALUE_WITH_FIELDS.INTEGER))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_nullable_fields SET integer=immutable_value_with_fields.integer ",
            nodeCount = 3)

    (UPDATE
        TABLE IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS
        SET (IMMUTABLE_VALUE_WITH_NULLABLE_FIELDS.INTEGER to IMMUTABLE_VALUE_WITH_FIELDS.INTEGER + 6))
        .isEqualTo(
            sql = "UPDATE immutable_value_with_nullable_fields SET integer=(immutable_value_with_fields.integer+6) ",
            nodeCount = 3)
  }

  @Test
  fun setComplexColumnToComplexColumn() {
    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to MAGAZINE.AUTHOR))
        .isEqualTo(
            sql = "UPDATE magazine SET author=magazine.author ",
            nodeCount = 3)
  }

  @Test
  fun setColumnToComplexColumnId() {
    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to MAGAZINE.ID))
        .isEqualTo(
            sql = "UPDATE magazine SET author=magazine.id ",
            nodeCount = 3)
  }

  @Test
  fun updateWithSelect() {
    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.NR_OF_RELEASES to (SELECT COLUMN MAGAZINE.NR_OF_RELEASES FROM MAGAZINE)))
        .isEqualTo(
            sql = "UPDATE magazine SET nr_of_releases=(SELECT magazine.nr_of_releases FROM magazine ) ",
            nodeCount = 3)

    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to (SELECT COLUMN MAGAZINE.AUTHOR FROM MAGAZINE)))
        .isEqualTo(
            sql = "UPDATE magazine SET author=(SELECT magazine.author FROM magazine ) ",
            nodeCount = 3)

    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.NR_OF_RELEASES to (SELECT COLUMN MAGAZINE.NR_OF_RELEASES FROM COMPLEX_OBJECT_WITH_SAME_LEAFS)))
        .isEqualTo(
            sql = "UPDATE magazine SET nr_of_releases=(SELECT magazine.nr_of_releases FROM complex_object_with_same_leafs " +
                "LEFT JOIN magazine ON complex_object_with_same_leafs.magazine=magazine.id ) ",
            nodeCount = 3)
  }
}