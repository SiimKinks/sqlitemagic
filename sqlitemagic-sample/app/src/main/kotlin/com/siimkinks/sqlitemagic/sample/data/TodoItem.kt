package com.siimkinks.sqlitemagic.sample.data

import com.siimkinks.sqlitemagic.AND
import com.siimkinks.sqlitemagic.HIGHLIGHT
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.TodoItemFtsTable.Companion.TODO_ITEM_FTS
import com.siimkinks.sqlitemagic.TodoItemTable.Companion.TODO_ITEM
import com.siimkinks.sqlitemagic.UPDATE
import com.siimkinks.sqlitemagic.annotation.Column
import com.siimkinks.sqlitemagic.annotation.FullText
import com.siimkinks.sqlitemagic.annotation.FullTextIndex
import com.siimkinks.sqlitemagic.annotation.Id
import com.siimkinks.sqlitemagic.annotation.Index
import com.siimkinks.sqlitemagic.annotation.Table
import com.siimkinks.sqlitemagic.inTransaction
import com.siimkinks.sqlitemagic.mapTo
import com.siimkinks.sqlitemagic.persist
import com.siimkinks.sqlitemagic.rxjava3.observe
import io.reactivex.rxjava3.core.Completable

@Table
@FullTextIndex(tokenize = "porter unicode61")
data class TodoItem(
  @Id
  val id: Long? = null,
  @FullText
  val title: String,
  @Index
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
            ORDER_BY arrayOf(TODO_ITEM.COMPLETED.ASC(), TODO_ITEM.TITLE.ASC())
        )

    private val searchList = TODO_ITEM.LIST.asParameter("listId")
    private val searchTerms = TODO_ITEM.TITLE.asParameter("terms")

    private val searchPlan = (
        SELECT.COLUMNS(TODO_ITEM, HIGHLIGHT(TODO_ITEM_FTS.TITLE, MATCH_START, MATCH_END))
            FROM TODO_ITEM
            WHERE ((TODO_ITEM.LIST IS searchList) AND (TODO_ITEM_FTS MATCH searchTerms))
            ORDER_BY TODO_ITEM_FTS.RANK.ASC()
        )
      .mapTo(::TodoItemMatch)
      .compile()

    fun search(listId: Long, text: String) = searchPlan.bind(
      searchList TO listId,
      searchTerms TO TODO_ITEM_FTS.terms(text = text, prefix = true)
    )

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

data class TodoItemMatch(
  val item: TodoItem,
  val highlightedTitle: String
)

internal const val MATCH_START = "\u0002"
internal const val MATCH_END = "\u0003"
