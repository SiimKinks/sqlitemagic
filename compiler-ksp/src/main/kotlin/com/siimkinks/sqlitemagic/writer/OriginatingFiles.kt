package com.siimkinks.sqlitemagic.writer

import com.google.devtools.ksp.symbol.KSFile

data class OriginatingFiles(
  val files: Set<KSFile>,
  val isComplete: Boolean
)
