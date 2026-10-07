package com.siimkinks.sqlitemagic.sample.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import com.siimkinks.sqlitemagic.FROM
import com.siimkinks.sqlitemagic.SELECT
import com.siimkinks.sqlitemagic.TodoListSummaryTable.Companion.TODO_LIST_SUMMARY
import com.siimkinks.sqlitemagic.sample.data.TodoList
import com.siimkinks.sqlitemagic.sample.data.TodoListSummary
import io.reactivex.android.schedulers.AndroidSchedulers

@Composable
internal fun TodoOverview(
  saving: Boolean,
  onSelect: (TodoList) -> Unit,
  onDialog: (TodoDialog) -> Unit
) {
  val summaries by remember {
    (SELECT FROM TODO_LIST_SUMMARY)
      .observe()
      .runQuery()
      .observeOn(AndroidSchedulers.mainThread())
  }.subscribeAsState(initial = null)
  val count by remember {
    TodoList.COUNT.observe()
      .runQuery()
      .observeOn(AndroidSchedulers.mainThread())
  }.subscribeAsState(initial = 0L)

  TodoLayout(
    title = "Todo lists",
    subtitle = "$count lists · Keep your day in order",
    saving = saving,
    canAdd = !saving,
    addLabel = "Add list",
    onAdd = { onDialog(TodoDialog.EditList()) },
    onBack = null
  ) {
    summaries?.let { lists ->
      Lists(
        lists = lists,
        saving = saving,
        onOpen = onSelect,
        onRename = { onDialog(TodoDialog.EditList(it)) },
        onDelete = { onDialog(TodoDialog.DeleteList(it)) }
      )
    }
  }
}

@Composable
private fun Lists(
  lists: List<TodoListSummary>,
  saving: Boolean,
  onOpen: (TodoList) -> Unit,
  onRename: (TodoList) -> Unit,
  onDelete: (TodoList) -> Unit
) {
  when {
    lists.isEmpty() -> EmptyMessage(
      title = "Make room for your next idea",
      body = "Tap Add list to get started."
    )
    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
      items(
        items = lists,
        key = { requireNotNull(it.list.id) }
      ) { summary ->
        val list = summary.list
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !saving) { onOpen(list) }
            .padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = list.name,
              style = MaterialTheme.typography.titleLarge
            )
            Text("${summary.itemsCount} remaining")
          }
          TextButton(
            onClick = { onRename(list) },
            enabled = !saving,
            modifier = Modifier.semantics {
              contentDescription = "Rename ${list.name}"
            }
          ) { Text("Rename") }
          TextButton(
            onClick = { onDelete(list) },
            enabled = !saving,
            modifier = Modifier.semantics {
              contentDescription = "Delete ${list.name}"
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
