package com.example

import android.database.sqlite.SQLiteDatabase
import com.siimkinks.sqlitemagic.*
import com.siimkinks.sqlitemagic.AuthorTable.AUTHOR
import com.siimkinks.sqlitemagic.ComplexObjectWithSameLeafsTable.COMPLEX_OBJECT_WITH_SAME_LEAFS
import com.siimkinks.sqlitemagic.MagazineTable.MAGAZINE
import com.siimkinks.sqlitemagic.model.Author
import org.junit.Test

class UpdateTest : DSLTest {
  @Test
  fun updateSqlBuilder() {
    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd"))
        .isEqualTo(
            expectedSql = "UPDATE author SET name=? ",
            expectedNodeCount = 3,
            expectedArgs = "asd")

    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.BOXED_BOOLEAN to true))
        .isEqualTo(
            expectedSql = "UPDATE author SET boxed_boolean=? ",
            expectedNodeCount = 3,
            expectedArgs = "1")

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_IGNORE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd"))
        .isEqualTo(
            expectedSql = "UPDATE OR IGNORE author SET name=? ",
            expectedNodeCount = 4,
            expectedArgs = "asd")

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_ROLLBACK
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        SET (AUTHOR.BOXED_BOOLEAN to true)
        SET (AUTHOR._ID to 2L)
        SET (AUTHOR.PRIMITIVE_BOOLEAN to false))
        .isEqualTo(
            expectedSql = "UPDATE OR ROLLBACK author SET name=?,boxed_boolean=?,_id=?,primitive_boolean=? ",
            expectedNodeCount = 4,
            expectedArgs = *arrayOf("asd", "1", "2", "0"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_ROLLBACK
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        SET (AUTHOR.BOXED_BOOLEAN to true))
        .isEqualTo(
            expectedSql = "UPDATE OR ROLLBACK author SET name=?,boxed_boolean=? ",
            expectedNodeCount = 4,
            expectedArgs = *arrayOf("asd", "1"))
  }

  @Test
  fun updateWhereBuilder() {
    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        WHERE (AUTHOR._ID IS 2))
        .isEqualTo(
            expectedSql = "UPDATE author SET name=? WHERE author._id=? ",
            expectedNodeCount = 4,
            expectedArgs = *arrayOf("asd", "2"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_IGNORE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        SET (AUTHOR.BOXED_BOOLEAN to false)
        WHERE ((AUTHOR._ID IS 2) AND (AUTHOR.NAME IS_NOT "asd")))
        .isEqualTo(
            expectedSql = "UPDATE OR IGNORE author SET name=?,boxed_boolean=? WHERE (author._id=? AND author.name!=?) ",
            expectedNodeCount = 5,
            expectedArgs = *arrayOf("asd", "0", "2", "asd"))

    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        SET (AUTHOR.BOXED_BOOLEAN to false)
        WHERE ((AUTHOR._ID IS 2) OR (AUTHOR.NAME IS_NOT "asd")))
        .isEqualTo(
            expectedSql = "UPDATE author SET name=?,boxed_boolean=? WHERE (author._id=? OR author.name!=?) ",
            expectedNodeCount = 4,
            expectedArgs = *arrayOf("asd", "0", "2", "asd"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_FAIL
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        WHERE ((AUTHOR._ID IS 2) OR (AUTHOR.NAME IS_NOT "asd")))
        .isEqualTo(
            expectedSql = "UPDATE OR FAIL author SET name=? WHERE (author._id=? OR author.name!=?) ",
            expectedNodeCount = 5,
            expectedArgs = *arrayOf("asd", "2", "asd"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_FAIL
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        WHERE (
        (((AUTHOR._ID IS 2)
            AND AUTHOR.NAME.isNotNull)
            AND (AUTHOR.NAME IS_NOT "asd"))
            AND (AUTHOR.PRIMITIVE_BOOLEAN IS false)
        ))
        .isEqualTo(
            expectedSql = "UPDATE OR FAIL author SET name=? WHERE (((author._id=? AND author.name IS NOT NULL) AND author.name!=?) AND author.primitive_boolean=?) ",
            expectedNodeCount = 5,
            expectedArgs = *arrayOf("asd", "2", "asd", "0"))

    (UPDATE
        WITH_CONFLICT_ALGORITHM SQLiteDatabase.CONFLICT_FAIL
        TABLE AUTHOR
        SET (AUTHOR.NAME to "asd")
        WHERE (
        ((((AUTHOR._ID IS 2)
            AND AUTHOR.NAME.isNotNull)
            OR (AUTHOR.NAME IS_NOT "asd"))
            OR ((AUTHOR.PRIMITIVE_BOOLEAN IS false) AND AUTHOR.BOXED_BOOLEAN.isNotNull))
        ))
        .isEqualTo(
            expectedSql = "UPDATE OR FAIL author SET name=? WHERE (((author._id=? AND author.name IS NOT NULL) OR author.name!=?) OR (author.primitive_boolean=? AND author.boxed_boolean IS NOT NULL)) ",
            expectedNodeCount = 5,
            expectedArgs = *arrayOf("asd", "2", "asd", "0"))
  }

  @Test
  fun updateComplexColumn() {
    val author: Author = Author()
    val id = 42L
    author.id = id
    val idStr = id.toString()
    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to author)
        SET (MAGAZINE.AUTHOR to author))
        .isEqualTo(
            expectedSql = "UPDATE magazine SET author=?,author=? ",
            expectedNodeCount = 3,
            expectedArgs = *arrayOf(idStr, idStr))

    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to id)
        SET (MAGAZINE.AUTHOR to id))
        .isEqualTo(
            expectedSql = "UPDATE magazine SET author=?,author=? ",
            expectedNodeCount = 3,
            expectedArgs = *arrayOf(idStr, idStr))
  }

  @Test
  fun updateWithColumn() {
    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.NR_OF_RELEASES to MAGAZINE.NR_OF_RELEASES + 6))
        .isEqualTo(
            expectedSql = "UPDATE magazine SET nr_of_releases=(magazine.nr_of_releases+6) ",
            expectedNodeCount = 3)

    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.NR_OF_RELEASES to MAGAZINE.ID))
        .isEqualTo(
            expectedSql = "UPDATE magazine SET nr_of_releases=magazine.id ",
            expectedNodeCount = 3)

    (UPDATE
        TABLE AUTHOR
        SET (AUTHOR.BOXED_BOOLEAN to AUTHOR.PRIMITIVE_BOOLEAN))
        .isEqualTo(
            expectedSql = "UPDATE author SET boxed_boolean=author.primitive_boolean ",
            expectedNodeCount = 3)

    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to MAGAZINE.AUTHOR))
        .isEqualTo(
            expectedSql = "UPDATE magazine SET author=magazine.author ",
            expectedNodeCount = 3)

    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to MAGAZINE.ID))
        .isEqualTo(
            expectedSql = "UPDATE magazine SET author=magazine.id ",
            expectedNodeCount = 3)
  }

  @Test
  fun updateWithSelect() {
    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.NR_OF_RELEASES to (SELECT COLUMN MAGAZINE.NR_OF_RELEASES FROM MAGAZINE)))
        .isEqualTo(
            expectedSql = "UPDATE magazine SET nr_of_releases=(SELECT magazine.nr_of_releases FROM magazine ) ",
            expectedNodeCount = 3)

    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.AUTHOR to (SELECT COLUMN MAGAZINE.AUTHOR FROM MAGAZINE)))
        .isEqualTo(
            expectedSql = "UPDATE magazine SET author=(SELECT magazine.author FROM magazine ) ",
            expectedNodeCount = 3)

    (UPDATE
        TABLE MAGAZINE
        SET (MAGAZINE.NR_OF_RELEASES to (SELECT COLUMN MAGAZINE.NR_OF_RELEASES FROM COMPLEX_OBJECT_WITH_SAME_LEAFS)))
        .isEqualTo(
            expectedSql = "UPDATE magazine SET nr_of_releases=(SELECT magazine.nr_of_releases FROM complex_object_with_same_leafs " +
                "LEFT JOIN magazine ON complex_object_with_same_leafs.magazine=magazine.id ) ",
            expectedNodeCount = 3)
  }
}