package com.siimkinks.sqlitemagic.sample.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers

@Composable
fun TodoApp() {
  val colors = when {
    isSystemInDarkTheme() -> darkColorScheme(primary = Color(0xFFB6CEFF))
    else -> lightColorScheme(primary = Color(0xFF315DA8))
  }
  var selectedListId by rememberSaveable { mutableStateOf<Long?>(null) }
  var dialog by remember { mutableStateOf<TodoDialog?>(null) }
  var saving by remember { mutableStateOf(false) }
  val writes = remember { CompositeDisposable() }
  DisposableEffect(writes) {
    onDispose(writes::dispose)
  }

  val onWrite = OnWrite { operation, onSuccess ->
    if (saving) return@OnWrite
    val isDialogWrite = dialog != null
    if (isDialogWrite) saving = true
    // Schedule all writes here, including generated delete operations.
    writes.add(
      operation.subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe {
          if (isDialogWrite) saving = false
          onSuccess()
        }
    )
  }

  MaterialTheme(colorScheme = colors) {
    key(selectedListId) {
      when (val listId = selectedListId) {
        null -> TodoOverview(
          saving = saving,
          onSelect = { selectedListId = it.id },
          onDialog = { dialog = it }
        )
        else -> TodoDetail(
          listId = listId,
          saving = saving,
          onBack = { selectedListId = null },
          onDialog = { dialog = it },
          onWrite = onWrite
        )
      }
    }
    TodoDialogs(
      dialog = dialog,
      saving = saving,
      onDismiss = { dialog = null },
      onWrite = onWrite,
      onListDeleted = {
        if (selectedListId == it.id) selectedListId = null
      }
    )
  }
}
