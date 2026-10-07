package com.siimkinks.sqlitemagic.sample.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.siimkinks.sqlitemagic.delete
import com.siimkinks.sqlitemagic.sample.data.TodoItem
import com.siimkinks.sqlitemagic.sample.data.TodoList

internal sealed interface TodoDialog {
  data class EditList(val list: TodoList? = null) : TodoDialog
  data class EditItem(val list: TodoList, val item: TodoItem? = null) : TodoDialog
  data class DeleteList(val list: TodoList) : TodoDialog
}

@Composable
internal fun TodoDialogs(
  dialog: TodoDialog?,
  saving: Boolean,
  onDismiss: () -> Unit,
  onWrite: OnWrite,
  onListDeleted: (TodoList) -> Unit
) {
  key(dialog) {
    when (dialog) {
      is TodoDialog.EditList -> NameDialog(
        title = when (dialog.list) {
          null -> "Create list"
          else -> "Rename list"
        },
        label = "List name",
        initialValue = dialog.list?.name.orEmpty(),
        onDismiss = onDismiss,
        saving = saving,
        onSave = { name ->
          val operation = when (val list = dialog.list) {
            null -> TodoList.create(name)
            else -> list.rename(name)
          }
          onWrite(
            operation = operation.ignoreElement(),
            onSuccess = onDismiss
          )
        }
      )
      is TodoDialog.EditItem -> NameDialog(
        title = when (dialog.item) {
          null -> "Create item"
          else -> "Edit item"
        },
        label = "Item title",
        initialValue = dialog.item?.title.orEmpty(),
        onDismiss = onDismiss,
        saving = saving,
        onSave = { title ->
          val operation = when (val item = dialog.item) {
            null -> TodoItem.create(
              list = dialog.list,
              title = title
            )
            else -> item
              .rename(title)
              .ignoreElement()
          }
          onWrite(
            operation = operation,
            onSuccess = onDismiss
          )
        }
      )
      is TodoDialog.DeleteList -> AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Delete list?") },
        text = { Text("Delete “${dialog.list.name}” and all its items? This cannot be undone.") },
        confirmButton = {
          TextButton(
            onClick = {
              onWrite(
                operation = dialog.list
                  .delete()
                  .observe()
                  .ignoreElement(),
                onSuccess = {
                  onDismiss()
                  onListDeleted(dialog.list)
                }
              )
            },
            enabled = !saving
          ) { Text("Delete list") }
        },
        dismissButton = {
          TextButton(
            onClick = onDismiss,
            enabled = !saving
          ) { Text("Cancel") }
        }
      )
      null -> Unit
    }
  }
}

@Composable
private fun NameDialog(
  title: String,
  label: String,
  initialValue: String,
  onDismiss: () -> Unit,
  saving: Boolean,
  onSave: (String) -> Unit
) {
  var value by rememberSaveable { mutableStateOf(initialValue) }
  val save = { if (value.isNotBlank()) onSave(value.trim()) }
  AlertDialog(
    onDismissRequest = { if (!saving) onDismiss() },
    title = { Text(title) },
    text = {
      OutlinedTextField(
        value = value,
        onValueChange = { value = it },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (!saving) save() }),
        enabled = !saving
      )
    },
    confirmButton = {
      Button(
        onClick = save,
        enabled = value.isNotBlank() && !saving
      ) { Text("Save") }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        enabled = !saving
      ) { Text("Cancel") }
    }
  )
}
