package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.symbol.KSFile

internal data class OriginatingFiles(
  val files: Set<KSFile>,
  val isComplete: Boolean
)
