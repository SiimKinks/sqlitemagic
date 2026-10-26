package com.siimkinks.sqlitemagic.sample.data

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.COUNT
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.TodoItemTable.Companion.TODO_ITEM
import com.siimkinks.sqlitemagic.TodoListTable.Companion.TODO_LIST
import com.siimkinks.sqlitemagic.annotation.View
import com.siimkinks.sqlitemagic.annotation.ViewColumn
import com.siimkinks.sqlitemagic.annotation.ViewQuery
import com.siimkinks.sqlitemagic.asScalar

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
        SELECT.COLUMNS(
          TODO_LIST.all() AS "todo_list",
          (SELECT
              COLUMN COUNT()
              FROM TODO_ITEM
              WHERE ((TODO_ITEM.COMPLETED IS false) AND (TODO_ITEM.LIST IS TODO_LIST.ID)))
            .asScalar() AS "items_count"
        )
            FROM TODO_LIST
            ORDER_BY TODO_LIST.NAME.ASC()
        )
  }
}
