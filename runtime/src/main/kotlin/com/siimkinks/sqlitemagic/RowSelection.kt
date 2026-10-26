package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Selection of multiple columns.
 *
 * @param T Row type
 */
class RowSelection<T> internal constructor(
  internal val outputs: List<SqlExpression<*, *, *>>,
  private val reader: ExecutionReader<T>
) : Selection<T>() {
  override fun render(context: SqlRenderingContext) = context.renderList(
    items = outputs,
    render = context::renderOutput
  )

  override fun forEachField(action: (ExpressionNode.Field) -> Unit) = outputs.forEachField(action)

  override fun createExecutionReader(context: SqlRenderingContext) = reader

  override val exportedValues get() = outputs

  internal val columnReads get() = outputs.selectReads()

  internal fun <M> withReader(read: (Cursor, Int) -> M) = withReader(ExecutionReader { read })

  internal fun <M> withReader(reader: ExecutionReader<M>) = RowSelection(
    outputs = outputs,
    reader = reader
  )
}

internal fun List<SqlExpression<*, *, *>>.selectReads() = map(SqlExpression<*, *, *>::selectRead)

internal fun List<SqlExpression<*, *, *>>.forEachField(action: (ExpressionNode.Field) -> Unit) = forEach {
  it.node.forEachField(action)
}

@Suppress("UNCHECKED_CAST")
private fun <V> List<(Cursor, Int) -> Any?>.read(index: Int) = this[index] as (Cursor, Int) -> V

internal fun <A, B, M> read2(
  readA: (Cursor, Int) -> A,
  readB: (Cursor, Int) -> B,
  create: (A, B) -> M
): (Cursor, Int) -> M = { cursor, startIndex ->
  create(
    readA(cursor, startIndex),
    readB(cursor, startIndex + 1)
  )
}

internal fun <A, B, C, M> read3(
  readA: (Cursor, Int) -> A,
  readB: (Cursor, Int) -> B,
  readC: (Cursor, Int) -> C,
  create: (A, B, C) -> M
): (Cursor, Int) -> M = { cursor, startIndex ->
  create(
    readA(cursor, startIndex),
    readB(cursor, startIndex + 1),
    readC(cursor, startIndex + 2)
  )
}

internal fun <A, B, C, D, M> read4(
  readA: (Cursor, Int) -> A,
  readB: (Cursor, Int) -> B,
  readC: (Cursor, Int) -> C,
  readD: (Cursor, Int) -> D,
  create: (A, B, C, D) -> M
): (Cursor, Int) -> M = { cursor, startIndex ->
  create(
    readA(cursor, startIndex),
    readB(cursor, startIndex + 1),
    readC(cursor, startIndex + 2),
    readD(cursor, startIndex + 3)
  )
}

internal fun <A, B, C, D, E, M> read5(
  readA: (Cursor, Int) -> A,
  readB: (Cursor, Int) -> B,
  readC: (Cursor, Int) -> C,
  readD: (Cursor, Int) -> D,
  readE: (Cursor, Int) -> E,
  create: (A, B, C, D, E) -> M
): (Cursor, Int) -> M = { cursor, startIndex ->
  create(
    readA(cursor, startIndex),
    readB(cursor, startIndex + 1),
    readC(cursor, startIndex + 2),
    readD(cursor, startIndex + 3),
    readE(cursor, startIndex + 4)
  )
}

internal fun <A, B, C, D, E, F, M> read6(
  readA: (Cursor, Int) -> A,
  readB: (Cursor, Int) -> B,
  readC: (Cursor, Int) -> C,
  readD: (Cursor, Int) -> D,
  readE: (Cursor, Int) -> E,
  readF: (Cursor, Int) -> F,
  create: (A, B, C, D, E, F) -> M
): (Cursor, Int) -> M = { cursor, startIndex ->
  create(
    readA(cursor, startIndex),
    readB(cursor, startIndex + 1),
    readC(cursor, startIndex + 2),
    readD(cursor, startIndex + 3),
    readE(cursor, startIndex + 4),
    readF(cursor, startIndex + 5)
  )
}

internal fun <A, B, C, D, E, F, G, M> read7(
  readA: (Cursor, Int) -> A,
  readB: (Cursor, Int) -> B,
  readC: (Cursor, Int) -> C,
  readD: (Cursor, Int) -> D,
  readE: (Cursor, Int) -> E,
  readF: (Cursor, Int) -> F,
  readG: (Cursor, Int) -> G,
  create: (A, B, C, D, E, F, G) -> M
): (Cursor, Int) -> M = { cursor, startIndex ->
  create(
    readA(cursor, startIndex),
    readB(cursor, startIndex + 1),
    readC(cursor, startIndex + 2),
    readD(cursor, startIndex + 3),
    readE(cursor, startIndex + 4),
    readF(cursor, startIndex + 5),
    readG(cursor, startIndex + 6)
  )
}

internal fun <A, B, C, D, E, F, G, H, M> read8(
  readA: (Cursor, Int) -> A,
  readB: (Cursor, Int) -> B,
  readC: (Cursor, Int) -> C,
  readD: (Cursor, Int) -> D,
  readE: (Cursor, Int) -> E,
  readF: (Cursor, Int) -> F,
  readG: (Cursor, Int) -> G,
  readH: (Cursor, Int) -> H,
  create: (A, B, C, D, E, F, G, H) -> M
): (Cursor, Int) -> M = { cursor, startIndex ->
  create(
    readA(cursor, startIndex),
    readB(cursor, startIndex + 1),
    readC(cursor, startIndex + 2),
    readD(cursor, startIndex + 3),
    readE(cursor, startIndex + 4),
    readF(cursor, startIndex + 5),
    readG(cursor, startIndex + 6),
    readH(cursor, startIndex + 7)
  )
}

internal fun <A, B, C, D, E, F, G, H, I, M> read9(
  readA: (Cursor, Int) -> A,
  readB: (Cursor, Int) -> B,
  readC: (Cursor, Int) -> C,
  readD: (Cursor, Int) -> D,
  readE: (Cursor, Int) -> E,
  readF: (Cursor, Int) -> F,
  readG: (Cursor, Int) -> G,
  readH: (Cursor, Int) -> H,
  readI: (Cursor, Int) -> I,
  create: (A, B, C, D, E, F, G, H, I) -> M
): (Cursor, Int) -> M = { cursor, startIndex ->
  create(
    readA(cursor, startIndex),
    readB(cursor, startIndex + 1),
    readC(cursor, startIndex + 2),
    readD(cursor, startIndex + 3),
    readE(cursor, startIndex + 4),
    readF(cursor, startIndex + 5),
    readG(cursor, startIndex + 6),
    readH(cursor, startIndex + 7),
    readI(cursor, startIndex + 8)
  )
}

internal fun <A, B, M> List<(Cursor, Int) -> Any?>.read2(
  create: (A, B) -> M
) = read2(
  readA = read(index = 0),
  readB = read(index = 1),
  create = create
)

internal fun <A, B, C, M> List<(Cursor, Int) -> Any?>.read3(
  create: (A, B, C) -> M
) = read3(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  create = create
)

internal fun <A, B, C, D, M> List<(Cursor, Int) -> Any?>.read4(
  create: (A, B, C, D) -> M
) = read4(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  readD = read(index = 3),
  create = create
)

internal fun <A, B, C, D, E, M> List<(Cursor, Int) -> Any?>.read5(
  create: (A, B, C, D, E) -> M
) = read5(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  readD = read(index = 3),
  readE = read(index = 4),
  create = create
)

internal fun <A, B, C, D, E, F, M> List<(Cursor, Int) -> Any?>.read6(
  create: (A, B, C, D, E, F) -> M
) = read6(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  readD = read(index = 3),
  readE = read(index = 4),
  readF = read(index = 5),
  create = create
)

internal fun <A, B, C, D, E, F, G, M> List<(Cursor, Int) -> Any?>.read7(
  create: (A, B, C, D, E, F, G) -> M
) = read7(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  readD = read(index = 3),
  readE = read(index = 4),
  readF = read(index = 5),
  readG = read(index = 6),
  create = create
)

internal fun <A, B, C, D, E, F, G, H, M> List<(Cursor, Int) -> Any?>.read8(
  create: (A, B, C, D, E, F, G, H) -> M
) = read8(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  readD = read(index = 3),
  readE = read(index = 4),
  readF = read(index = 5),
  readG = read(index = 6),
  readH = read(index = 7),
  create = create
)

internal fun <A, B, C, D, E, F, G, H, I, M> List<(Cursor, Int) -> Any?>.read9(
  create: (A, B, C, D, E, F, G, H, I) -> M
) = read9(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  readD = read(index = 3),
  readE = read(index = 4),
  readF = read(index = 5),
  readG = read(index = 6),
  readH = read(index = 7),
  readI = read(index = 8),
  create = create
)
