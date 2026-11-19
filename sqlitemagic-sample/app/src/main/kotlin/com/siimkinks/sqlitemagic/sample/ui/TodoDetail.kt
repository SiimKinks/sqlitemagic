package com.siimkinks.sqlitemagic.sample.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rxjava3.subscribeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.rxjava3.observe
import com.siimkinks.sqlitemagic.sample.data.MATCH_START
import com.siimkinks.sqlitemagic.sample.data.TodoItem
import com.siimkinks.sqlitemagic.sample.data.TodoList
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable

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
      .map { found ->
        found.map { item ->
          TodoRow(
            item = item,
            title = AnnotatedString(item.title)
          )
        }
      }
      .observeOn(AndroidSchedulers.mainThread())
  }.subscribeAsState(initial = null)
  var query by rememberSaveable { mutableStateOf("") }
  val matches by remember(query) {
    when {
      query.isBlank() -> Observable.empty()
      else -> TodoItem
        .search(
          listId = listId,
          text = query
        )
        .observe()
        .runQuery()
        .map { found ->
          found.map { match ->
            TodoRow(
              item = match.item,
              title = boldMatches(
                title = match.item.title,
                highlighted = match.highlightedTitle
              )
            )
          }
        }
        .observeOn(AndroidSchedulers.mainThread())
    }
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
        else -> Column {
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search items") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp)
          )
          val onEdit = { item: TodoItem ->
            onDialog(
              TodoDialog.EditItem(
                list = currentList,
                item = item
              )
            )
          }
          val onCompleted = { item: TodoItem, completed: Boolean ->
            onWrite(
              item.setCompleted(completed)
                .ignoreElement()
            )
          }
          val onDelete = { item: TodoItem ->
            onWrite(
              item.delete()
                .observe()
                .ignoreElement()
            )
          }
          when {
            query.isBlank() -> items?.let { rows ->
              Items(
                rows = rows,
                empty = {
                  EmptyMessage(
                    title = "A fresh start",
                    body = "Tap Add item to capture your first task."
                  )
                },
                saving = saving,
                onEdit = onEdit,
                onCompleted = onCompleted,
                onDelete = onDelete
              )
            }
            else -> matches?.let { rows ->
              Items(
                rows = rows,
                empty = {
                  EmptyMessage(
                    title = "No items match",
                    body = "Try another word."
                  )
                },
                saving = saving,
                onEdit = onEdit,
                onCompleted = onCompleted,
                onDelete = onDelete
              )
            }
          }
        }
      }
    }
  }
}

private data class TodoRow(
  val item: TodoItem,
  val title: AnnotatedString
)

@Composable
private fun Items(
  rows: List<TodoRow>,
  empty: @Composable () -> Unit,
  saving: Boolean,
  onEdit: (TodoItem) -> Unit,
  onCompleted: (TodoItem, Boolean) -> Unit,
  onDelete: (TodoItem) -> Unit
) {
  when {
    rows.isEmpty() -> empty()
    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
      items(
        items = rows,
        key = { requireNotNull(it.item.id) }
      ) { (item, title) ->
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
            text = title,
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

private val bold = SpanStyle(fontWeight = FontWeight.Bold)

internal fun boldMatches(title: String, highlighted: String) = buildAnnotatedString {
  var matchStart = -1
  highlighted.forEach { character ->
    when {
      length < title.length && character == title[length] -> append(character)
      character == MATCH_START.single() -> matchStart = length
      matchStart >= 0 -> {
        addStyle(
          style = bold,
          start = matchStart,
          end = length
        )
        matchStart = -1
      }
    }
  }
}
