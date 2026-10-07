package com.siimkinks.sqlitemagic.sample.data

import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SET
import com.siimkinks.sqlitemagic.TABLE
import com.siimkinks.sqlitemagic.TodoListTable.Companion.TODO_LIST
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.insert

@Table
data class TodoList(
  @Id
  val id: Long? = null,
  val name: String
) {
  fun rename(name: String) = (
      UPDATE
          TABLE TODO_LIST
          SET (TODO_LIST.NAME to validName(name))
          WHERE (TODO_LIST.ID IS checkNotNull(id))
      )
    .observe()

  companion object {
    val COUNT = (SELECT FROM TODO_LIST)
      .count()

    fun find(id: Long) =
      (SELECT
          FROM TODO_LIST
          WHERE (TODO_LIST.ID IS id))
        .compile()

    fun create(name: String) = TodoList(name = validName(name))
      .insert()
      .observe()
  }
}
