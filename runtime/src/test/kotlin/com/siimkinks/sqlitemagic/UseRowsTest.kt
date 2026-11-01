package com.siimkinks.sqlitemagic

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.TimeUnit.SECONDS

internal class UseRowsTest {
  @Test
  fun `typed rows are read lazily on every form that executes a list`() {
    personDatabase().use { database ->
      val connection = database.connection
      val minAge = PERSON.AGE.asParameter("minAge")
      val people = (SELECT FROM PERSON ORDER_BY PERSON.ID.ASC()).usingConnection(connection)
      val adults = (SELECT FROM PERSON WHERE (PERSON.AGE GREATER_THAN minAge) ORDER_BY PERSON.ID.ASC())
        .usingConnection(connection)
        .compile()
        .bind(minAge TO 18)
      val counting = SELECT
        .RAW("WITH RECURSIVE n(x) AS (SELECT 1 UNION ALL SELECT x + 1 FROM n) SELECT x FROM n")
        .readAs(LONG)
        .usingConnection(connection)

      val results = listOf(
        "query" to people.useRows { rows ->
          rows
            .take(2)
            .toList()
        },
        "compiled query with bound parameters" to adults.useRows(Sequence<Person>::toList),
        "raw query without an end" to counting.useRows { rows ->
          rows
            .take(3)
            .toList()
        },
        "compiled raw query without an end" to counting
          .compile()
          .useRows { rows ->
            rows
              .take(2)
              .toList()
          }
      )

      assertThat(results)
        .containsExactly(
          "query" to listOf(ada, grace),
          "compiled query with bound parameters" to listOf(ada, grace),
          "raw query without an end" to listOf(1L, 2L, 3L),
          "compiled raw query without an end" to listOf(1L, 2L)
        )
        .inOrder()
    }
  }

  @Test
  fun `rows are iterated once and only inside their block`() {
    personDatabase().use { database ->
      val people = (SELECT FROM PERSON).usingConnection(database.connection)
      val closed = "Rows can be read only inside their useRows block"
      val misuses = listOf<Triple<String, () -> Unit, String>>(
        Triple(
          first = "escaped rows",
          second = {
            people
              .useRows { it }
              .toList()
          },
          third = closed
        ),
        Triple(
          first = "escaped iterator",
          second = {
            people
              .useRows(Sequence<Person>::iterator)
              .hasNext()
          },
          third = closed
        ),
        Triple(
          first = "second iteration",
          second = {
            people.useRows { rows ->
              rows.toList()
              rows.toList()
            }
          },
          third = "Rows of useRows can be iterated only once"
        ),
        Triple(
          first = "escaped raw rows",
          second = {
            (SELECT RAW "SELECT 1")
              .usingConnection(database.connection)
              .useRows { it }
              .step()
          },
          third = closed
        )
      )

      for ((label, misuse, message) in misuses) {
        assertWithMessage(label)
          .that(assertThrows(IllegalStateException::class.java, misuse::invoke))
          .hasMessageThat()
          .isEqualTo(message)
      }
    }
  }

  @Test
  fun `raw rows read run-time columns with the types of readAs`() {
    data class RawRead(
      val columns: List<String>,
      val rows: List<Triple<Long, String?, Token>>,
      val nameIsNull: List<Boolean>,
      val stepAfterLastRow: Boolean
    )

    val read = personDatabase().use { database ->
      (SELECT RAW "SELECT id, name, token FROM person ORDER BY id")
        .usingConnection(database.connection)
        .useRows { rows ->
          val values = mutableListOf<Triple<Long, String?, Token>>()
          val nameIsNull = mutableListOf<Boolean>()
          while (rows.step()) {
            values += Triple(
              first = rows.read(
                index = 0,
                type = LONG
              ),
              second = rows.read(
                index = 1,
                type = STRING.nullable
              ),
              third = rows.read(
                index = 2,
                type = PERSON.TOKEN
              )
            )
            nameIsNull += rows.isNull(1)
          }
          RawRead(
            columns = List(
              size = rows.columnCount,
              init = rows::columnName
            ),
            rows = values,
            nameIsNull = nameIsNull,
            stepAfterLastRow = rows.step()
          )
        }
    }

    assertThat(read).isEqualTo(
      RawRead(
        columns = listOf("id", "name", "token"),
        rows = listOf(
          Triple(
            first = 1L,
            second = "Ada",
            third = Token("a")
          ),
          Triple(
            first = 2L,
            second = "Grace",
            third = Token("g")
          ),
          Triple(
            first = 3L,
            second = null,
            third = Token("n")
          ),
          Triple(
            first = 4L,
            second = "Linus",
            third = Token("l")
          )
        ),
        nameIsNull = listOf(false, false, true, false),
        stepAfterLastRow = false
      )
    )
  }

  @Test
  fun `raw rows report the storage class of each value`() {
    val types = personDatabase().use { database ->
      (SELECT RAW "SELECT 1, 1.5, 'text', x'00', NULL")
        .usingConnection(database.connection)
        .useRows { rows ->
          rows.step()
          List(
            size = rows.columnCount,
            init = rows::columnType
          )
        }
    }

    assertThat(types).isEqualTo(
      listOf(SQLITE_DATA_INTEGER, SQLITE_DATA_FLOAT, SQLITE_DATA_TEXT, SQLITE_DATA_BLOB, SQLITE_DATA_NULL)
    )
  }

  @Test
  fun `a required raw read of SQL NULL throws SQLException`() {
    personDatabase().use { database ->
      val message = sqlExceptionMessage {
        (SELECT RAW "SELECT NULL")
          .usingConnection(database.connection)
          .useRows { rows ->
            rows.step()
            rows.read(
              index = 0,
              type = STRING
            )
          }
      }

      assertThat(message).isEqualTo("Required SQL result returned SQL NULL")
    }
  }

  @Test
  fun `raw rows reject reads without a current row or outside the columns`() {
    val messages = personDatabase().use { database ->
      val firstPersonQuery = (SELECT RAW "SELECT id, name FROM person WHERE id = 1")
        .usingConnection(database.connection)
      mapOf<String, (RawRows) -> Unit>(
        "read before the first step" to { rows ->
          rows.read(
            index = 0,
            type = LONG
          )
        },
        "isNull after the last row" to { rows ->
          repeat(2) { rows.step() }
          rows.isNull(0)
        },
        "read past the last column" to { rows ->
          rows.step()
          rows.read(
            index = 2,
            type = STRING.nullable
          )
        },
        "isNull of a negative index" to { rows ->
          rows.step()
          rows.isNull(-1)
        },
        "columnType before the first step" to { rows -> rows.columnType(0) },
        "columnType past the last column" to { rows ->
          rows.step()
          rows.columnType(2)
        }
      ).mapValues { (_, read) -> sqlExceptionMessage { firstPersonQuery.useRows(read) } }
    }

    assertThat(messages).isEqualTo(
      mapOf(
        "read before the first step" to "No current row",
        "isNull after the last row" to "No current row",
        "read past the last column" to "Column index 2 out of range",
        "isNull of a negative index" to "Column index -1 out of range",
        "columnType before the first step" to "No current row",
        "columnType past the last column" to "Column index 2 out of range"
      )
    )
  }

  @Test
  fun `rows inside a transaction see its writes and a failing block releases the connection`() {
    personDatabase().use { database ->
      val connection = database.connection
      val count = (SELECT FROM PERSON).usingConnection(connection)
      val transaction = connection.newTransaction()
      database.execSQL("INSERT INTO person VALUES (5, 'Barbara', 40, NULL, 'db:b', 1)")
      val inTransaction = count.useRows(Sequence<Person>::count)
      transaction.end()
      val failure = assertThrows(IllegalStateException::class.java) {
        count.useRows { error("block failed") }
      }
      var afterFailure = 0
      withWorkers { workers ->
        afterFailure = workers
          .submit<Int> { count.useRows(Sequence<Person>::count) }
          .get(5, SECONDS)
      }

      assertThat(listOf(inTransaction, afterFailure))
        .containsExactly(5, 4)
        .inOrder()
      assertThat(failure)
        .hasMessageThat()
        .isEqualTo("block failed")
    }
  }
}
