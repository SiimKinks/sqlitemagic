package com.siimkinks.sqlitemagic.index

import com.siimkinks.sqlitemagic.writer.OriginatingFiles

fun mockIndexRoundElement(
  index: IndexElement = mockIndexElement(),
  originatingFiles: OriginatingFiles = OriginatingFiles(
    files = emptySet(),
    isComplete = true
  )
) = IndexRoundElement(
  index = index,
  originatingFiles = originatingFiles
)
