package com.siimkinks.sqlitemagic.manager

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path

internal class FileSnapshotTransactionTest {
  @TempDir
  lateinit var temporaryDirectory: Path

  @Test
  fun `repeated snapshots and tracking retain the original bytes`() {
    val file = temporaryDirectory.resolve("original.txt").toFile()
    val original = "first\n".toByteArray()
    file.writeBytes(original)
    val transaction = FileSnapshotTransaction()

    val first = transaction.snapshot(file)
    file.writeText("second\n")
    val second = transaction.snapshot(file)
    transaction.track(file)
    transaction.track(file)
    transaction.restore(IOException("publication failed"))

    assertThat(first).isSameInstanceAs(second)
    assertThat(first.text).isEqualTo("first\n")
    assertThat(file.readBytes()).isEqualTo(original)
  }

  @Test
  fun `snapshot lines follow buffered file reading for terminated and blank lines`() {
    val file = temporaryDirectory.resolve("lines.txt").toFile()
    val transaction = FileSnapshotTransaction()
    file.writeText("first\n\nlast\n")

    assertThat(transaction.snapshot(file).readLines())
      .containsExactly("first", "", "last")
      .inOrder()
  }

  @Test
  fun `write recreates a tracked file deleted after its original snapshot`() {
    val file = temporaryDirectory.resolve("replaced.txt").toFile()
    file.writeText("unchanged contents")
    val transaction = FileSnapshotTransaction()
    transaction.snapshot(file)
    transaction.track(file)
    check(file.delete())

    transaction.writeTextIfChanged(
      file = file,
      text = "unchanged contents"
    )

    assertThat(file.readText()).isEqualTo("unchanged contents")
  }

  @Test
  fun `restore leaves read-only snapshots alone and removes created mutation targets`() {
    val readOnly = temporaryDirectory.resolve("read-only.txt").toFile()
    val created = temporaryDirectory.resolve("created.txt").toFile()
    readOnly.writeText("before")
    val transaction = FileSnapshotTransaction()
    transaction.snapshot(readOnly)
    transaction.track(created)
    readOnly.writeText("after")
    created.writeText("partial")

    transaction.restore(IOException("publication failed"))

    assertThat(readOnly.readText()).isEqualTo("after")
    assertThat(created.exists()).isFalse()
  }
}
