package com.siimkinks.sqlitemagic.writer

import com.siimkinks.sqlitemagic.WriterTypes.SQL_EXCEPTION
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec

internal class CursorPositions {
  private val nullChecks = CursorNullChecks()

  fun positional(
    code: CodeBlock,
    nullCheckName: String
  ) = CursorPosition(
    code = code,
    mayBeMissing = false,
    nullCheckName = nullCheckName,
    nullChecks = nullChecks
  )

  fun selected(
    function: FunSpec.Builder,
    indexName: String,
    lookup: CodeBlock,
    required: Boolean,
    missingMessage: String
  ): CursorPosition {
    when {
      required -> function.addStatement(
        "val %N = %L ?: throw %T(%S)",
        indexName,
        lookup,
        SQL_EXCEPTION,
        missingMessage
      )
      else -> function.addStatement("val %N = %L ?: -1", indexName, lookup)
    }
    return CursorPosition(
      code = CodeBlock.of("%N", indexName),
      mayBeMissing = !required,
      nullCheckName = "${indexName}IsNull",
      nullChecks = nullChecks
    )
  }

  fun generate(construction: () -> CodeBlock) = nullChecks.generate(construction)

  fun addNullCheckDeclarations(function: FunSpec.Builder) {
    nullChecks.declarations.forEach { declaration ->
      function.addStatement(
        "val %N = %L",
        declaration.name,
        when {
          declaration.mayBeMissing -> CodeBlock.of(
            "(%L >= 0 && cursor.isNull(%L))",
            declaration.code,
            declaration.code
          )
          else -> CodeBlock.of("cursor.isNull(%L)", declaration.code)
        }
      )
    }
  }
}

internal class CursorPosition internal constructor(
  val code: CodeBlock,
  val mayBeMissing: Boolean,
  val nullCheckName: String,
  private val nullChecks: CursorNullChecks
) {
  fun missingCheck() = when {
    mayBeMissing -> CodeBlock.of("%L < 0", code)
    else -> CodeBlock.of("false")
  }

  fun nullCheck() = nullChecks.nullCheck(this)

  fun presentNullCheck() = nullChecks.presentNullCheck(this)
}

internal class CursorNullChecks {
  private val usages = linkedMapOf<CursorNullCheckKey, CursorNullCheckUsage>()
  private var prepared = false

  val declarations: List<CursorNullCheckDeclaration>
    get() = usages.values
      .filter { it.count > 1 }
      .map { usage ->
        CursorNullCheckDeclaration(
          name = usage.name,
          code = usage.code,
          mayBeMissing = usage.mayBeMissing
        )
      }

  fun generate(construction: () -> CodeBlock): CodeBlock {
    construction()
    prepared = true
    return construction()
  }

  fun nullCheck(position: CursorPosition): CodeBlock {
    val cached = cachedValue(position)
    return when {
      cached != null && position.mayBeMissing -> CodeBlock.of("(%L < 0 || %L)", position.code, cached)
      cached != null -> cached
      position.mayBeMissing -> CodeBlock.of("(%L < 0 || cursor.isNull(%L))", position.code, position.code)
      else -> CodeBlock.of("cursor.isNull(%L)", position.code)
    }
  }

  fun presentNullCheck(position: CursorPosition): CodeBlock {
    val cached = cachedValue(position)
    return when {
      cached != null -> cached
      position.mayBeMissing -> CodeBlock.of("(%L >= 0 && cursor.isNull(%L))", position.code, position.code)
      else -> CodeBlock.of("cursor.isNull(%L)", position.code)
    }
  }

  private fun cachedValue(position: CursorPosition): CodeBlock? {
    val key = CursorNullCheckKey(
      code = position.code.toString(),
      mayBeMissing = position.mayBeMissing
    )
    val usage = usages.getOrPut(key) {
      CursorNullCheckUsage(
        name = position.nullCheckName,
        code = position.code,
        mayBeMissing = position.mayBeMissing
      )
    }
    if (!prepared) usage.count++
    return when {
      prepared && usage.count > 1 -> CodeBlock.of("%N", usage.name)
      else -> null
    }
  }
}

private data class CursorNullCheckKey(
  val code: String,
  val mayBeMissing: Boolean
)

private class CursorNullCheckUsage(
  val name: String,
  val code: CodeBlock,
  val mayBeMissing: Boolean,
  var count: Int = 0
)

internal data class CursorNullCheckDeclaration(
  val name: String,
  val code: CodeBlock,
  val mayBeMissing: Boolean
)
