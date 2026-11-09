package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.ValueRead
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Selection of multiple columns.
 *
 * @param T Row type
 */
class RowSelection<T> internal constructor(
  internal val outputs: List<SqlExpression<*, *, *>>,
  private val reader: ExecutionReader<T>,
  override val mappedWithFunction: Boolean = false
) : Selection<T>() {
  override fun render(context: SqlRenderingContext) = context.renderList(
    items = outputs,
    render = context::renderOutput
  )

  override fun forEachField(action: (ExpressionNode.Field) -> Unit) = outputs.forEachField(action)

  override fun createExecutionReader(context: SqlRenderingContext) = reader

  override val exportedValues get() = outputs

  internal val columnReads get() = outputs.map(SqlExpression<*, *, *>::selectRead)

  internal fun <M> mapped(read: ValueRead<M>) = mapped(reader = { read })

  internal fun <M> mapped(reader: ExecutionReader<M>) = RowSelection(
    outputs = outputs,
    reader = reader,
    mappedWithFunction = true
  )
}

internal fun List<SqlExpression<*, *, *>>.forEachField(action: (ExpressionNode.Field) -> Unit) = forEach {
  it.node.forEachField(action)
}

@Suppress("UNCHECKED_CAST")
private fun <V> List<ValueRead<Any?>>.read(index: Int) = this[index] as ValueRead<V>

internal fun <A, B, M> read2(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  create: (A, B) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + 1)
  )
}

internal fun <A, B, C, M> read3(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  create: (A, B, C) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + 1),
    readC(statement, startIndex + 2)
  )
}

internal fun <A, B, C, D, M> read4(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  readD: ValueRead<D>,
  create: (A, B, C, D) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + 1),
    readC(statement, startIndex + 2),
    readD(statement, startIndex + 3)
  )
}

internal fun <A, B, C, D, E, M> read5(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  readD: ValueRead<D>,
  readE: ValueRead<E>,
  create: (A, B, C, D, E) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + 1),
    readC(statement, startIndex + 2),
    readD(statement, startIndex + 3),
    readE(statement, startIndex + 4)
  )
}

internal fun <A, B, C, D, E, F, M> read6(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  readD: ValueRead<D>,
  readE: ValueRead<E>,
  readF: ValueRead<F>,
  create: (A, B, C, D, E, F) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + 1),
    readC(statement, startIndex + 2),
    readD(statement, startIndex + 3),
    readE(statement, startIndex + 4),
    readF(statement, startIndex + 5)
  )
}

internal fun <A, B, C, D, E, F, G, M> read7(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  readD: ValueRead<D>,
  readE: ValueRead<E>,
  readF: ValueRead<F>,
  readG: ValueRead<G>,
  create: (A, B, C, D, E, F, G) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + 1),
    readC(statement, startIndex + 2),
    readD(statement, startIndex + 3),
    readE(statement, startIndex + 4),
    readF(statement, startIndex + 5),
    readG(statement, startIndex + 6)
  )
}

internal fun <A, B, C, D, E, F, G, H, M> read8(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  readD: ValueRead<D>,
  readE: ValueRead<E>,
  readF: ValueRead<F>,
  readG: ValueRead<G>,
  readH: ValueRead<H>,
  create: (A, B, C, D, E, F, G, H) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + 1),
    readC(statement, startIndex + 2),
    readD(statement, startIndex + 3),
    readE(statement, startIndex + 4),
    readF(statement, startIndex + 5),
    readG(statement, startIndex + 6),
    readH(statement, startIndex + 7)
  )
}

internal fun <A, B, C, D, E, F, G, H, I, M> read9(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  readD: ValueRead<D>,
  readE: ValueRead<E>,
  readF: ValueRead<F>,
  readG: ValueRead<G>,
  readH: ValueRead<H>,
  readI: ValueRead<I>,
  create: (A, B, C, D, E, F, G, H, I) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + 1),
    readC(statement, startIndex + 2),
    readD(statement, startIndex + 3),
    readE(statement, startIndex + 4),
    readF(statement, startIndex + 5),
    readG(statement, startIndex + 6),
    readH(statement, startIndex + 7),
    readI(statement, startIndex + 8)
  )
}

internal fun <A, B, M> List<ValueRead<Any?>>.read2(
  create: (A, B) -> M
) = read2(
  readA = read(index = 0),
  readB = read(index = 1),
  create = create
)

internal fun <A, B, C, M> List<ValueRead<Any?>>.read3(
  create: (A, B, C) -> M
) = read3(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  create = create
)

internal fun <A, B, C, D, M> List<ValueRead<Any?>>.read4(
  create: (A, B, C, D) -> M
) = read4(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  readD = read(index = 3),
  create = create
)

internal fun <A, B, C, D, E, M> List<ValueRead<Any?>>.read5(
  create: (A, B, C, D, E) -> M
) = read5(
  readA = read(index = 0),
  readB = read(index = 1),
  readC = read(index = 2),
  readD = read(index = 3),
  readE = read(index = 4),
  create = create
)

internal fun <A, B, C, D, E, F, M> List<ValueRead<Any?>>.read6(
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

internal fun <A, B, C, D, E, F, G, M> List<ValueRead<Any?>>.read7(
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

internal fun <A, B, C, D, E, F, G, H, M> List<ValueRead<Any?>>.read8(
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

internal fun <A, B, C, D, E, F, G, H, I, M> List<ValueRead<Any?>>.read9(
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
