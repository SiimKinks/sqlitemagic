package com.siimkinks.sqlitemagic.sample.data

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.AS
import com.siimkinks.sqlitemagic.COLUMN
import com.siimkinks.sqlitemagic.COLUMNS
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.ORDER_BY
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.Select.Companion.asColumn
import com.siimkinks.sqlitemagic.Select.Companion.count
import com.siimkinks.sqlitemagic.TodoItemTable.Companion.TODO_ITEM
import com.siimkinks.sqlitemagic.TodoListTable.Companion.TODO_LIST
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery

@View
data class TodoListSummary(
  @ViewColumn("todo_list")
  val list: TodoList,
  @ViewColumn("items_count")
  val itemsCount: Long
) {
  companion object {
    @ViewQuery
    val QUERY = (
        SELECT COLUMNS arrayOf(
          TODO_LIST.all() AS "todo_list",
          (SELECT
              COLUMN count()
              FROM TODO_ITEM
              WHERE ((TODO_ITEM.COMPLETED IS asColumn(false)) AND (TODO_ITEM.LIST IS TODO_LIST.ID)))
            .toColumn("items_count")
        )
            FROM TODO_LIST
            ORDER_BY TODO_LIST.NAME.asc()
        )
      .compile()
  }
}
