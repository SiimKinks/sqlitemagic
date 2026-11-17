package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.ValueRead
import com.siimkinks.sqlitemagic.internal.sql.ExpressionNode
import com.siimkinks.sqlitemagic.internal.sql.RelationshipPlanner
import com.siimkinks.sqlitemagic.internal.sql.SqlRenderingContext

/**
 * Selection of multiple values: expressions and entities.
 *
 * @param R Kotlin types of the selected values as [Row2]–[Row9], regardless of `mapTo`, with [EntityRows] for an
 *     entity, or [SelectedRow] for the values of `COLUMNS(vararg outputs)`
 * @param T Row type
 */
class RowSelection<R, T> internal constructor(
  internal val values: List<SelectValue<*, *>>,
  private val reader: RowReader<T>,
  override val mappedWithFunction: Boolean = false,
  private val deep: Boolean = false
) : Selection<T>() {
  @Suppress("UNCHECKED_CAST")
  internal val expressions = when {
    values.all { it is SqlExpression<*, *, *> } -> values as List<SqlExpression<*, *, *>>
    else -> null
  }

  private val scalarReader = expressions?.let {
    reader.create(
      values = values,
      layout = ValueLayout(IntArray(values.size) { it })
    )
  }

  override fun render(context: SqlRenderingContext) {
    val layout = context.valueLayout
    context.renderList(count = values.size) { slot ->
      when (val value = values[slot]) {
        is SqlExpression<*, *, *> -> context.renderOutput(value)
        is Table<*> -> {
          context.requireEntityValueNotNullExtended(value)
          context.renderEntityColumns(
            table = value,
            related = layout?.related(slot)
          )
        }
        is NullableEntity<*> -> context.renderEntityColumns(
          table = value.table,
          related = layout?.related(slot)
        )
      }
    }
  }

  override fun forEachField(action: (ExpressionNode.Field) -> Unit) = values.forEach { value ->
    if (value is SqlExpression<*, *, *>) {
      value.node.forEachField(action)
    }
  }

  override fun forEachEntityValue(action: (Table<*>) -> Unit) {
    if (expressions == null) {
      values.forEach { it.entityTable?.let(action) }
    }
  }

  override fun planValues(planner: RelationshipPlanner): ValueLayout? {
    if (expressions != null) {
      return null
    }
    val starts = IntArray(values.size)
    val related = arrayOfNulls<RelatedEntities>(values.size)
    var next = 0
    values.forEachIndexed { slot, value ->
      starts[slot] = next
      next += when (val table = value.entityTable) {
        null -> 1
        else -> {
          val read = planner.entityValueRead(
            table = table,
            startIndex = next,
            deep = deep
          )
          related[slot] = read
          read.width + read.joined.sumOf(RelatedEntities::width)
        }
      }
    }
    return ValueLayout(
      starts = starts,
      related = related
    )
  }

  override fun createExecutionReader(context: SqlRenderingContext) = scalarReader ?: reader.create(
    values = values,
    layout = checkNotNull(context.valueLayout)
  )

  override val exportedValues
    get() = requireNotNull(expressions) {
      "UNION and derived exports take plain values, but this query selects the " +
          "${values.firstNotNullOf(SelectValue<*, *>::entityTable).referenceName} entity"
    }

  internal fun <M> mapped(assemble: (SlotReads) -> ValueRead<M>) = mapped(typedRowReader(assemble))

  internal fun <M> mapped(reader: RowReader<M>) = RowSelection<R, M>(
    values = values,
    reader = reader,
    mappedWithFunction = true,
    deep = deep
  )

  internal fun deep() = RowSelection<R, T>(
    values = values,
    reader = reader,
    mappedWithFunction = mappedWithFunction,
    deep = true
  )
}

internal fun interface RowReader<out T> {
  fun create(values: List<SelectValue<*, *>>, layout: ValueLayout): ExecutionReader<T>
}

internal class ValueLayout(
  val starts: IntArray,
  private val related: Array<RelatedEntities?>? = null
) {
  fun related(slot: Int) = related?.get(slot)
}

internal fun <T> typedRowReader(assemble: (SlotReads) -> ValueRead<T>) = RowReader { values, layout ->
  val read = assemble(
    SlotReads(
      reads = List(values.size) { slot -> values[slot].read(layout.related(slot)) },
      starts = layout.starts
    )
  )
  ExecutionReader { read }
}

internal fun List<SqlExpression<*, *, *>>.forEachField(action: (ExpressionNode.Field) -> Unit) = forEach {
  it.node.forEachField(action)
}

internal val SelectValue<*, *>.entityTable
  get() = when (this) {
    is SqlExpression<*, *, *> -> null
    is Table<*> -> this
    is NullableEntity<*> -> table
  }

internal fun SelectValue<*, *>.read(related: RelatedEntities?): ValueRead<Any?> = when (this) {
  is SqlExpression<*, *, *> -> selectRead
  is Table<*> -> ValueRead { statement, index ->
    readEntityAt(
      statement = statement,
      startIndex = index,
      related = related
    )
  }
  is NullableEntity<*> -> {
    val read = checkNotNull(related)
    ValueRead { statement, index ->
      read.readIfPresent(
        statement = statement,
        startIndex = index
      )
    }
  }
}

internal class SlotReads(
  private val reads: List<ValueRead<Any?>>,
  private val starts: IntArray? = null
) {
  @Suppress("UNCHECKED_CAST")
  fun <V> read(slot: Int) = reads[slot] as ValueRead<V>

  fun start(slot: Int) = starts?.get(slot) ?: slot
}

internal fun <A, B, M> read2(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  startB: Int,
  create: (A, B) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + startB)
  )
}

internal fun <A, B, C, M> read3(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  startB: Int,
  startC: Int,
  create: (A, B, C) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + startB),
    readC(statement, startIndex + startC)
  )
}

internal fun <A, B, C, D, M> read4(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  readD: ValueRead<D>,
  startB: Int,
  startC: Int,
  startD: Int,
  create: (A, B, C, D) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + startB),
    readC(statement, startIndex + startC),
    readD(statement, startIndex + startD)
  )
}

internal fun <A, B, C, D, E, M> read5(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  readD: ValueRead<D>,
  readE: ValueRead<E>,
  startB: Int,
  startC: Int,
  startD: Int,
  startE: Int,
  create: (A, B, C, D, E) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + startB),
    readC(statement, startIndex + startC),
    readD(statement, startIndex + startD),
    readE(statement, startIndex + startE)
  )
}

internal fun <A, B, C, D, E, F, M> read6(
  readA: ValueRead<A>,
  readB: ValueRead<B>,
  readC: ValueRead<C>,
  readD: ValueRead<D>,
  readE: ValueRead<E>,
  readF: ValueRead<F>,
  startB: Int,
  startC: Int,
  startD: Int,
  startE: Int,
  startF: Int,
  create: (A, B, C, D, E, F) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + startB),
    readC(statement, startIndex + startC),
    readD(statement, startIndex + startD),
    readE(statement, startIndex + startE),
    readF(statement, startIndex + startF)
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
  startB: Int,
  startC: Int,
  startD: Int,
  startE: Int,
  startF: Int,
  startG: Int,
  create: (A, B, C, D, E, F, G) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + startB),
    readC(statement, startIndex + startC),
    readD(statement, startIndex + startD),
    readE(statement, startIndex + startE),
    readF(statement, startIndex + startF),
    readG(statement, startIndex + startG)
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
  startB: Int,
  startC: Int,
  startD: Int,
  startE: Int,
  startF: Int,
  startG: Int,
  startH: Int,
  create: (A, B, C, D, E, F, G, H) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + startB),
    readC(statement, startIndex + startC),
    readD(statement, startIndex + startD),
    readE(statement, startIndex + startE),
    readF(statement, startIndex + startF),
    readG(statement, startIndex + startG),
    readH(statement, startIndex + startH)
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
  startB: Int,
  startC: Int,
  startD: Int,
  startE: Int,
  startF: Int,
  startG: Int,
  startH: Int,
  startI: Int,
  create: (A, B, C, D, E, F, G, H, I) -> M
) = ValueRead { statement, startIndex ->
  create(
    readA(statement, startIndex),
    readB(statement, startIndex + startB),
    readC(statement, startIndex + startC),
    readD(statement, startIndex + startD),
    readE(statement, startIndex + startE),
    readF(statement, startIndex + startF),
    readG(statement, startIndex + startG),
    readH(statement, startIndex + startH),
    readI(statement, startIndex + startI)
  )
}

internal fun <A, B, M> SlotReads.read2(
  create: (A, B) -> M
) = read2(
  readA = read(slot = 0),
  readB = read(slot = 1),
  startB = start(slot = 1),
  create = create
)

internal fun <A, B, C, M> SlotReads.read3(
  create: (A, B, C) -> M
) = read3(
  readA = read(slot = 0),
  readB = read(slot = 1),
  readC = read(slot = 2),
  startB = start(slot = 1),
  startC = start(slot = 2),
  create = create
)

internal fun <A, B, C, D, M> SlotReads.read4(
  create: (A, B, C, D) -> M
) = read4(
  readA = read(slot = 0),
  readB = read(slot = 1),
  readC = read(slot = 2),
  readD = read(slot = 3),
  startB = start(slot = 1),
  startC = start(slot = 2),
  startD = start(slot = 3),
  create = create
)

internal fun <A, B, C, D, E, M> SlotReads.read5(
  create: (A, B, C, D, E) -> M
) = read5(
  readA = read(slot = 0),
  readB = read(slot = 1),
  readC = read(slot = 2),
  readD = read(slot = 3),
  readE = read(slot = 4),
  startB = start(slot = 1),
  startC = start(slot = 2),
  startD = start(slot = 3),
  startE = start(slot = 4),
  create = create
)

internal fun <A, B, C, D, E, F, M> SlotReads.read6(
  create: (A, B, C, D, E, F) -> M
) = read6(
  readA = read(slot = 0),
  readB = read(slot = 1),
  readC = read(slot = 2),
  readD = read(slot = 3),
  readE = read(slot = 4),
  readF = read(slot = 5),
  startB = start(slot = 1),
  startC = start(slot = 2),
  startD = start(slot = 3),
  startE = start(slot = 4),
  startF = start(slot = 5),
  create = create
)

internal fun <A, B, C, D, E, F, G, M> SlotReads.read7(
  create: (A, B, C, D, E, F, G) -> M
) = read7(
  readA = read(slot = 0),
  readB = read(slot = 1),
  readC = read(slot = 2),
  readD = read(slot = 3),
  readE = read(slot = 4),
  readF = read(slot = 5),
  readG = read(slot = 6),
  startB = start(slot = 1),
  startC = start(slot = 2),
  startD = start(slot = 3),
  startE = start(slot = 4),
  startF = start(slot = 5),
  startG = start(slot = 6),
  create = create
)

internal fun <A, B, C, D, E, F, G, H, M> SlotReads.read8(
  create: (A, B, C, D, E, F, G, H) -> M
) = read8(
  readA = read(slot = 0),
  readB = read(slot = 1),
  readC = read(slot = 2),
  readD = read(slot = 3),
  readE = read(slot = 4),
  readF = read(slot = 5),
  readG = read(slot = 6),
  readH = read(slot = 7),
  startB = start(slot = 1),
  startC = start(slot = 2),
  startD = start(slot = 3),
  startE = start(slot = 4),
  startF = start(slot = 5),
  startG = start(slot = 6),
  startH = start(slot = 7),
  create = create
)

internal fun <A, B, C, D, E, F, G, H, I, M> SlotReads.read9(
  create: (A, B, C, D, E, F, G, H, I) -> M
) = read9(
  readA = read(slot = 0),
  readB = read(slot = 1),
  readC = read(slot = 2),
  readD = read(slot = 3),
  readE = read(slot = 4),
  readF = read(slot = 5),
  readG = read(slot = 6),
  readH = read(slot = 7),
  readI = read(slot = 8),
  startB = start(slot = 1),
  startC = start(slot = 2),
  startD = start(slot = 3),
  startE = start(slot = 4),
  startF = start(slot = 5),
  startG = start(slot = 6),
  startH = start(slot = 7),
  startI = start(slot = 8),
  create = create
)
