package com.siimkinks.sqlitemagic.framework

import android.database.CursorWindow

internal class IdleCursorWindow {
  private var window: CursorWindow? = null

  fun take() = window.also { window = null }

  fun release(returned: CursorWindow) {
    when (window) {
      null -> window = returned
      else -> returned.close()
    }
  }

  fun close() {
    window?.close()
    window = null
  }
}
