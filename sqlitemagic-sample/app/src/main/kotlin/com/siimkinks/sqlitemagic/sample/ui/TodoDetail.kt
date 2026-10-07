package com.siimkinks.sqlitemagic.sample.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rxjava2.subscribeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.sample.data.TodoItem
import com.siimkinks.sqlitemagic.sample.data.TodoList
import io.reactivex.android.schedulers.AndroidSchedulers

@Composable
internal fun TodoDetail(
  listId: Long,
  saving: Boolean,
  onBack: () -> Unit,
  onDialog: (TodoDialog) -> Unit,
  onWrite: OnWrite
) {
  val parent by remember {
    TodoList.find(listId)
      .observe()
      .runQuery()
      .observeOn(AndroidSchedulers.mainThread())
  }.subscribeAsState(initial = null)
  val items by remember {
    TodoItem.itemsFor(listId)
      .observe()
      .runQuery()
      .observeOn(AndroidSchedulers.mainThread())
  }.subscribeAsState(initial = null)
  val remaining by remember {
    TodoItem.countItemsFor(listId)
      .observe()
      .runQuery()
      .observeOn(AndroidSchedulers.mainThread())
  }.subscribeAsState(initial = 0L)

  val list = parent?.singleOrNull()
  BackHandler {
    if (!saving) onBack()
  }
  TodoLayout(
    title = list?.name ?: "Todo list",
    subtitle = "$remaining items remaining",
    saving = saving,
    canAdd = !saving && list != null,
    addLabel = "Add item",
    onAdd = {
      list?.let {
        onDialog(TodoDialog.EditItem(list = it))
      }
    },
    onBack = onBack
  ) {
    parent?.let { lists ->
      when (val currentList = lists.singleOrNull()) {
        null -> EmptyMessage(
          title = "This list is unavailable",
          body = "Go back to your lists."
        )
        else -> items?.let { currentItems ->
          Items(
            items = currentItems,
            saving = saving,
            onEdit = {
              onDialog(
                TodoDialog.EditItem(
                  list = currentList,
                  item = it
                )
              )
            },
            onCompleted = { item, completed ->
              onWrite(
                item.setCompleted(completed)
                  .ignoreElement()
              )
            },
            onDelete = { item ->
              onWrite(
                item.delete()
                  .observe()
                  .ignoreElement()
              )
            }
          )
        }
      }
    }
  }
}

@Composable
private fun Items(
  items: List<TodoItem>,
  saving: Boolean,
  onEdit: (TodoItem) -> Unit,
  onCompleted: (TodoItem, Boolean) -> Unit,
  onDelete: (TodoItem) -> Unit
) {
  when {
    items.isEmpty() -> EmptyMessage(
      title = "A fresh start",
      body = "Tap Add item to capture your first task."
    )
    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
      items(
        items = items,
        key = { requireNotNull(it.id) }
      ) { item ->
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Checkbox(
            checked = item.completed,
            onCheckedChange = { onCompleted(item, it) },
            enabled = !saving,
            modifier = Modifier.semantics {
              contentDescription = "Complete ${item.title}"
            }
          )
          Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            textDecoration = when {
              item.completed -> TextDecoration.LineThrough
              else -> TextDecoration.None
            },
            modifier = Modifier.weight(1f)
          )
          TextButton(
            onClick = { onEdit(item) },
            enabled = !saving,
            modifier = Modifier.semantics {
              contentDescription = "Edit ${item.title}"
            }
          ) { Text("Edit") }
          TextButton(
            onClick = { onDelete(item) },
            enabled = !saving,
            modifier = Modifier.semantics {
              contentDescription = "Delete ${item.title}"
            }
          ) { Text("Delete") }
        }
        HorizontalDivider()
      }
      item {
        Spacer(modifier = Modifier.size(96.dp))
      }
    }
  }
}
