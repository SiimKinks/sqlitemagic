package com.siimkinks.sqlitemagic.manager

import java.io.BufferedReader
import java.io.File

internal class FileSnapshotTransaction {
  private val snapshots = linkedMapOf<File, Snapshot>()
  private val mutationTargets = linkedSetOf<File>()

  fun snapshot(file: File) = snapshots.getOrPut(file) {
    Snapshot(
      file
        .takeIf(File::isFile)
        ?.readBytes()
    )
  }

  fun track(file: File) {
    snapshot(file)
    mutationTargets.add(file)
  }

  fun writeTextIfChanged(
    file: File,
    text: String
  ) {
    val previous = snapshot(file)
    val desiredBytes = text.toByteArray(Charsets.UTF_8)
    if (file !in mutationTargets && previous.matchesBytes(desiredBytes)) return
    track(file)
    file.parentFile?.mkdirs()
    file.writeBytes(desiredBytes)
  }

  fun restore(originalFailure: Exception) {
    mutationTargets
      .toList()
      .asReversed()
      .forEach { file ->
        runCatching {
          val previous = snapshots.getValue(file)
          when {
            previous.isRegularFile -> {
              file.parentFile?.mkdirs()
              previous.restoreTo(file)
            }
            file.isFile -> check(file.delete()) {
              "Failed to remove partially published SqliteMagic state ${file.absolutePath}"
            }
          }
        }.exceptionOrNull()
          ?.let(originalFailure::addSuppressed)
      }
  }

  internal class Snapshot internal constructor(
    private val bytes: ByteArray?
  ) {
    val isRegularFile = bytes != null
    val text by lazy { bytes?.toString(Charsets.UTF_8) }

    fun matchesBytes(other: ByteArray) = bytes?.contentEquals(other) == true

    fun readLines() = text
      ?.reader()
      ?.buffered()
      ?.use(BufferedReader::readLines)
      .orEmpty()

    fun restoreTo(file: File) = file.writeBytes(checkNotNull(bytes))
  }
}
