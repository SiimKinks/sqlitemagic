package com.siimkinks.sqlitemagic.writer

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.joinToCode

internal class CursorPositions {
  private val nullChecks = CursorNullChecks()
  private val indexes = mutableListOf<CursorPosition>()

  fun positional(
    code: CodeBlock,
    nullCheckName: String
  ) = CursorPosition(
    code = code,
    mayBeMissing = false,
    nullCheckName = nullCheckName,
    nullChecks = nullChecks
  )

  fun indexed(
    indexName: String,
    code: CodeBlock,
    mayBeMissing: Boolean
  ) = CursorPosition(
    code = CodeBlock.of("%N", indexName),
    mayBeMissing = mayBeMissing,
    nullCheckName = "${indexName}IsNull",
    nullChecks = nullChecks,
    declaration = code
  ).also(indexes::add)

  fun generate(construction: () -> CodeBlock) = nullChecks.generate(construction)

  fun addDeclarations(function: FunSpec.Builder) {
    indexes
      .filter(CursorPosition::used)
      .forEach { function.addStatement("val %L = %L", it.code, it.declaration) }
    nullChecks.addDeclarations(function)
  }
}

internal class CursorPosition(
  code: CodeBlock,
  val mayBeMissing: Boolean,
  val nullCheckName: String,
  private val nullChecks: CursorNullChecks,
  val declaration: CodeBlock? = null
) {
  var used = false
    private set

  val code = code
    get() {
      used = true
      return field
    }

  fun missingCheck() = when {
    mayBeMissing -> CursorCheck(
      condition = CodeBlock.of("%L < 0", code),
      negation = CodeBlock.of("%L >= 0", code)
    )
    else -> CursorCheck.FALSE
  }

  fun nullCheck() = nullChecks.nullCheck(this)

  fun presentNullCheck() = nullChecks.presentNullCheck(this)
}

internal class CursorCheck(
  val condition: CodeBlock,
  val negation: CodeBlock = CodeBlock.of("!%L", condition),
  private val compound: Boolean = false
) {
  val operand
    get() = when {
      compound -> CodeBlock.of("(%L)", condition)
      else -> condition
    }

  private val negatedOperand
    get() = when {
      compound -> CodeBlock.of("(%L)", negation)
      else -> negation
    }

  companion object {
    val FALSE = CursorCheck(
      condition = CodeBlock.of("false"),
      negation = CodeBlock.of("true")
    )

    fun all(checks: List<CursorCheck>) = when (checks.size) {
      1 -> checks.single()
      else -> CursorCheck(
        condition = checks.map(CursorCheck::operand).joinToCode(separator = " && "),
        negation = checks.map(CursorCheck::negatedOperand).joinToCode(separator = " || "),
        compound = true
      )
    }

    fun either(first: CursorCheck, second: CursorCheck) = CursorCheck(
      condition = CodeBlock.of("%L || %L", first.operand, second.operand),
      negation = CodeBlock.of("%L && %L", first.negatedOperand, second.negatedOperand),
      compound = true
    )
  }
}

internal class CursorNullChecks {
  private val usages = linkedMapOf<CursorNullCheckKey, CursorNullCheckUsage>()
  private var prepared = false

  fun addDeclarations(function: FunSpec.Builder) {
    usages.values
      .filter { it.count > 1 }
      .forEach { usage ->
        function.addStatement(
          "val %N = %L",
          usage.name,
          when {
            usage.mayBeMissing -> CodeBlock.of("%L >= 0 && cursor.isNull(%L)", usage.code, usage.code)
            else -> CodeBlock.of("cursor.isNull(%L)", usage.code)
          }
        )
      }
  }

  fun generate(construction: () -> CodeBlock): CodeBlock {
    construction()
    prepared = true
    return construction()
  }

  fun nullCheck(position: CursorPosition): CursorCheck {
    val cached = cachedValue(position)
    return when {
      cached != null && position.mayBeMissing -> CursorCheck(
        condition = CodeBlock.of("%L < 0 || %L", position.code, cached),
        negation = CodeBlock.of("%L >= 0 && !%L", position.code, cached),
        compound = true
      )
      cached != null -> CursorCheck(condition = cached)
      position.mayBeMissing -> CursorCheck(
        condition = CodeBlock.of("%L < 0 || cursor.isNull(%L)", position.code, position.code),
        negation = CodeBlock.of("%L >= 0 && !cursor.isNull(%L)", position.code, position.code),
        compound = true
      )
      else -> CursorCheck(condition = CodeBlock.of("cursor.isNull(%L)", position.code))
    }
  }

  fun presentNullCheck(position: CursorPosition): CursorCheck {
    val cached = cachedValue(position)
    return when {
      cached != null -> CursorCheck(condition = cached)
      position.mayBeMissing -> CursorCheck(
        condition = CodeBlock.of("%L >= 0 && cursor.isNull(%L)", position.code, position.code),
        negation = CodeBlock.of("%L < 0 || !cursor.isNull(%L)", position.code, position.code),
        compound = true
      )
      else -> CursorCheck(condition = CodeBlock.of("cursor.isNull(%L)", position.code))
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
