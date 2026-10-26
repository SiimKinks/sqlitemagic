package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.utils.PlainKotlinCompilation
import org.intellij.lang.annotations.Language

internal fun compileExpressionConsumer(
  @Language("kotlin") body: String,
  imports: String = ""
) = PlainKotlinCompilation.compile(
  fileName = "ExpressionConsumer.kt",
  contents = """
    @file:OptIn(InternalSqliteMagicApi::class)

    package downstream.expression

    import com.siimkinks.sqlitemagic.AND
    import com.siimkinks.sqlitemagic.AND_NOT
    import com.siimkinks.sqlitemagic.BETWEEN
    import com.siimkinks.sqlitemagic.Column
    import com.siimkinks.sqlitemagic.DbConnection
    import com.siimkinks.sqlitemagic.EncodableRawSqlExpression
    import com.siimkinks.sqlitemagic.EncodableSqlExpression
    import com.siimkinks.sqlitemagic.EncodableSqlRangeStart
    import com.siimkinks.sqlitemagic.GREATER_OR_EQUAL
    import com.siimkinks.sqlitemagic.GREATER_THAN
    import com.siimkinks.sqlitemagic.IN
    import com.siimkinks.sqlitemagic.IS
    import com.siimkinks.sqlitemagic.IS_NOT
    import com.siimkinks.sqlitemagic.LESS_OR_EQUAL
    import com.siimkinks.sqlitemagic.LESS_THAN
    import com.siimkinks.sqlitemagic.LikeSqlExpression
    import com.siimkinks.sqlitemagic.NOT_BETWEEN
    import com.siimkinks.sqlitemagic.NOT_IN
    import com.siimkinks.sqlitemagic.NULL
    import com.siimkinks.sqlitemagic.OR
    import com.siimkinks.sqlitemagic.OR_NOT
    import com.siimkinks.sqlitemagic.RawSqlExpression
    import com.siimkinks.sqlitemagic.Required
    import com.siimkinks.sqlitemagic.Row2
    import com.siimkinks.sqlitemagic.SELECT
    import com.siimkinks.sqlitemagic.ScalarSelection
    import com.siimkinks.sqlitemagic.SelectFromStage
    import com.siimkinks.sqlitemagic.SelectHavingStage
    import com.siimkinks.sqlitemagic.SelectWhereStage
    import com.siimkinks.sqlitemagic.SqlExpression
    import com.siimkinks.sqlitemagic.SqlNullable
    import com.siimkinks.sqlitemagic.SqlType
    import com.siimkinks.sqlitemagic.Table
    import com.siimkinks.sqlitemagic.UniqueColumn
    import com.siimkinks.sqlitemagic.asLiteral
    import com.siimkinks.sqlitemagic.asValue
    import com.siimkinks.sqlitemagic.div
    import com.siimkinks.sqlitemagic.expr
    import com.siimkinks.sqlitemagic.internal.InternalSqliteMagicApi
    import com.siimkinks.sqlitemagic.internal.SqlStorage
    import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
    import com.siimkinks.sqlitemagic.internal.parameterEncoding
    import com.siimkinks.sqlitemagic.internal.requiredReadType
    import com.siimkinks.sqlitemagic.minus
    import com.siimkinks.sqlitemagic.not
    import com.siimkinks.sqlitemagic.plus
    import com.siimkinks.sqlitemagic.readAs
    import com.siimkinks.sqlitemagic.rem
    import com.siimkinks.sqlitemagic.times
    import com.siimkinks.sqlitemagic.unaryMinus
    import typewitness.Exact
    import typewitness.exactType
    $imports

    class Person

    data class Token(val value: String)

    val TOKEN_TYPE = requiredReadType(
      storage = SqlStorage.STRING,
      encode = { value: Token -> "db:" + value.value },
      decode = { value: String -> Token(value.removePrefix("db:")) },
      encodingNullness = Required
    )

    val DECODED_TYPE = requiredReadType(
      storage = SqlStorage.STRING,
      encode = { value: Token? -> value?.value ?: "absent" },
      decode = { value: String ->
        when (value) {
          "absent" -> null
          else -> Token(value)
        }
      },
      encodingNullness = Required
    )

    class PersonTable private constructor(
      alias: String?
    ) : Table<Person>(name = "person", alias = alias) {
      val ID = UniqueColumn<Person, Long, Required, Long, Long, Required, Long>(
        table = this,
        name = "id",
        nullness = Required,
        parameterEncoding = SqlType.LONG.parameterEncoding()
      )
      val NAME = Column<Person, String, SqlNullable, String?, String?, SqlNullable, String?>(
        table = this,
        name = "name",
        nullness = SqlNullable,
        parameterEncoding = SqlType.STRING.nullBypassParameterEncoding(),
        selectedRead = { cursor, index -> cursor.getString(index) }
      )
      val AGE = Column<Person, Int, Required, Int, Int, Required, Int>(
        table = this,
        name = "age",
        nullness = Required,
        parameterEncoding = SqlType.INT.parameterEncoding()
      )
      val PARENT_ID = Column<Person, Long, SqlNullable, Long?, Long?, SqlNullable, Long?>(
        table = this,
        name = "parent_id",
        nullness = SqlNullable,
        parameterEncoding = SqlType.LONG.nullBypassParameterEncoding()
      )
      val TOKEN = Column<Person, Token, Required, Token, Token, Required, Token>(
        table = this,
        name = "token",
        nullness = Required,
        parameterEncoding = TOKEN_TYPE.parameterEncoding()
      )
      val DECODED = Column<Person, Token?, Required, Token?, Token?, Required, Token?>(
        table = this,
        name = "decoded",
        nullness = Required,
        parameterEncoding = DECODED_TYPE.parameterEncoding()
      )

      override infix fun AS(alias: String) = PersonTable(alias)

      companion object {
        val PERSON = PersonTable(null)
      }
    }

    val PERSON = PersonTable.PERSON

    class Pet

    class PetTable private constructor(
      alias: String?
    ) : Table<Pet>(name = "pet", alias = alias) {
      val ID = UniqueColumn<Pet, Long, Required, Long, Long, Required, Long>(
        table = this,
        name = "id",
        nullness = Required,
        parameterEncoding = SqlType.LONG.parameterEncoding()
      )
      val OWNER = Column<Pet, Long, Required, Long, Long, Required, Long>(
        table = this,
        name = "owner",
        nullness = Required,
        parameterEncoding = SqlType.LONG.parameterEncoding()
      )
      val NAME = Column<Pet, String, Required, String, String, Required, String>(
        table = this,
        name = "name",
        nullness = Required,
        parameterEncoding = SqlType.STRING.parameterEncoding()
      )
      val NICKNAME = Column<Pet, String, SqlNullable, String?, String?, SqlNullable, String?>(
        table = this,
        name = "nickname",
        nullness = SqlNullable,
        parameterEncoding = SqlType.STRING.nullBypassParameterEncoding()
      )

      override infix fun AS(alias: String) = PetTable(alias)

      companion object {
        val PET = PetTable(null)
      }
    }

    val PET = PetTable.PET

    $body
  """
)
