package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.symbol.KSFile

internal class OriginatingFilesAccumulator {
  private val files = linkedSetOf<KSFile>()
  private var complete = true

  fun add(file: KSFile?) {
    file?.let(files::add)
  }

  fun add(files: Iterable<KSFile>) {
    this.files += files
  }

  fun add(originatingFiles: OriginatingFiles) {
    add(originatingFiles.files)
    complete = complete && originatingFiles.isComplete
  }

  fun markIncomplete() {
    complete = false
  }

  fun build(): OriginatingFiles = OriginatingFiles(
    files = files.toSet(),
    isComplete = complete
  )
}
