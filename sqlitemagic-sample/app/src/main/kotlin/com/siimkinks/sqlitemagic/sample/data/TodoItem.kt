package com.siimkinks.sqlitemagic.sample.data

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.IS
import com.siimkinks.sqlitemagic.ORDER_BY
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.SET
import com.siimkinks.sqlitemagic.TABLE
import com.siimkinks.sqlitemagic.TodoItemTable.Companion.TODO_ITEM
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.WHERE
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.persist
import io.reactivex.Completable

@Table
data class TodoItem(
  @Id
  val id: Long? = null,
  val title: String,
  @Column(onDeleteCascade = true)
  val list: TodoList,
  @Column(defaultValue = "0")
  val completed: Boolean = false
) {
  fun rename(title: String) = (
      UPDATE
          TABLE TODO_ITEM
          SET (TODO_ITEM.TITLE to validName(title))
          WHERE (TODO_ITEM.ID IS checkNotNull(id))
      )
    .observe()

  fun setCompleted(value: Boolean) = (
      UPDATE
          TABLE TODO_ITEM
          SET (TODO_ITEM.COMPLETED to value)
          WHERE (TODO_ITEM.ID IS checkNotNull(id))
      )
    .observe()

  companion object {
    fun itemsFor(listId: Long) = (
        SELECT
            FROM TODO_ITEM
            WHERE (TODO_ITEM.LIST IS listId)
            ORDER_BY arrayOf(TODO_ITEM.COMPLETED.asc(), TODO_ITEM.TITLE.asc())
        )
      .queryDeep()
      .compile()

    fun countItemsFor(listId: Long) = (
        SELECT
            FROM TODO_ITEM
            WHERE ((TODO_ITEM.LIST IS listId) AND (TODO_ITEM.COMPLETED IS false))
        )
      .count()

    fun create(
      list: TodoList,
      title: String
    ) = Completable.fromAction {
      inTransaction {
        val persistedList = TodoList
          .find(checkNotNull(list.id))
          .execute()
          .singleOrNull()
        TodoItem(
          title = validName(title),
          list = checkNotNull(persistedList) {
            "The list no longer exists."
          }
        )
          .persist()
          .execute()
      }
    }
  }
}
