package com.siimkinks.sqlitemagic.writer

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.joinToCode

internal class ColumnPositions {
  private val nullChecks = ColumnNullChecks()
  private val indexes = mutableListOf<ColumnPosition>()

  fun positional(
    code: CodeBlock,
    nullCheckName: String
  ) = ColumnPosition(
    code = code,
    mayBeMissing = false,
    nullCheckName = nullCheckName,
    nullChecks = nullChecks
  )

  fun indexed(
    indexName: String,
    code: CodeBlock,
    mayBeMissing: Boolean
  ) = ColumnPosition(
    code = CodeBlock.of("%N", indexName),
    mayBeMissing = mayBeMissing,
    nullCheckName = "${indexName}IsNull",
    nullChecks = nullChecks,
    declaration = code
  ).also(indexes::add)

  fun generate(construction: () -> CodeBlock) = nullChecks.generate(construction)

  fun addDeclarations(function: FunSpec.Builder) {
    indexes
      .filter(ColumnPosition::used)
      .forEach { function.addStatement("val %L = %L", it.code, it.declaration) }
    nullChecks.addDeclarations(function)
  }
}

internal class ColumnPosition(
  code: CodeBlock,
  val mayBeMissing: Boolean,
  val nullCheckName: String,
  private val nullChecks: ColumnNullChecks,
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
    mayBeMissing -> ColumnCheck(
      condition = CodeBlock.of("%L < 0", code),
      negation = CodeBlock.of("%L >= 0", code)
    )
    else -> ColumnCheck.FALSE
  }

  fun nullCheck() = nullChecks.nullCheck(this)

  fun presentNullCheck() = nullChecks.presentNullCheck(this)

  fun sharesNullCheck() = nullChecks.isShared(this)
}

internal class ColumnCheck(
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
    val FALSE = ColumnCheck(
      condition = CodeBlock.of("false"),
      negation = CodeBlock.of("true")
    )

    fun all(checks: List<ColumnCheck>) = when (checks.size) {
      1 -> checks.single()
      else -> ColumnCheck(
        condition = checks.map(ColumnCheck::operand).joinToCode(separator = " && "),
        negation = checks.map(ColumnCheck::negatedOperand).joinToCode(separator = " || "),
        compound = true
      )
    }

    fun either(first: ColumnCheck, second: ColumnCheck) = ColumnCheck(
      condition = CodeBlock.of("%L || %L", first.operand, second.operand),
      negation = CodeBlock.of("%L && %L", first.negatedOperand, second.negatedOperand),
      compound = true
    )
  }
}

internal class ColumnNullChecks {
  private val usages = linkedMapOf<ColumnNullCheckKey, ColumnNullCheckUsage>()
  private var prepared = false

  fun addDeclarations(function: FunSpec.Builder) {
    usages.values
      .filter { it.count > 1 }
      .forEach { usage ->
        function.addStatement(
          "val %N = %L",
          usage.name,
          presentNull(
            code = usage.code,
            mayBeMissing = usage.mayBeMissing
          ).condition
        )
      }
  }

  fun generate(construction: () -> CodeBlock): CodeBlock {
    construction()
    prepared = true
    return construction()
  }

  fun nullCheck(position: ColumnPosition): ColumnCheck {
    val cached = cachedValue(position)
    return when {
      cached != null && position.mayBeMissing -> ColumnCheck(
        condition = CodeBlock.of("%L < 0 || %L", position.code, cached),
        negation = CodeBlock.of("%L >= 0 && !%L", position.code, cached),
        compound = true
      )
      cached != null -> ColumnCheck(condition = cached)
      position.mayBeMissing -> ColumnCheck(
        condition = CodeBlock.of("%L < 0 || statement.isNull(%L)", position.code, position.code),
        negation = CodeBlock.of("%L >= 0 && !statement.isNull(%L)", position.code, position.code),
        compound = true
      )
      else -> ColumnCheck(condition = CodeBlock.of("statement.isNull(%L)", position.code))
    }
  }

  fun presentNullCheck(position: ColumnPosition) = when (val cached = cachedValue(position)) {
    null -> presentNull(
      code = position.code,
      mayBeMissing = position.mayBeMissing
    )
    else -> ColumnCheck(condition = cached)
  }

  fun isShared(position: ColumnPosition) = cachedValue(position) != null

  private fun presentNull(code: CodeBlock, mayBeMissing: Boolean) = when {
    mayBeMissing -> ColumnCheck(
      condition = CodeBlock.of("%L >= 0 && statement.isNull(%L)", code, code),
      negation = CodeBlock.of("%L < 0 || !statement.isNull(%L)", code, code),
      compound = true
    )
    else -> ColumnCheck(condition = CodeBlock.of("statement.isNull(%L)", code))
  }

  private fun cachedValue(position: ColumnPosition): CodeBlock? {
    val key = ColumnNullCheckKey(
      code = position.code.toString(),
      mayBeMissing = position.mayBeMissing
    )
    val usage = usages.getOrPut(key) {
      ColumnNullCheckUsage(
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

private data class ColumnNullCheckKey(
  val code: String,
  val mayBeMissing: Boolean
)

private class ColumnNullCheckUsage(
  val name: String,
  val code: CodeBlock,
  val mayBeMissing: Boolean,
  var count: Int = 0
)
