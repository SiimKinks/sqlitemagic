@file:Suppress("TestFunctionName")

package com.siimkinks.sqlitemagic

import android.database.Cursor
import com.siimkinks.sqlitemagic.SqlType.BOOLEAN
import com.siimkinks.sqlitemagic.SqlType.INT
import com.siimkinks.sqlitemagic.SqlType.LONG
import com.siimkinks.sqlitemagic.SqlType.STRING
import com.siimkinks.sqlitemagic.internal.RelatedEntities
import com.siimkinks.sqlitemagic.internal.SqlStorage
import com.siimkinks.sqlitemagic.internal.nullBypassParameterEncoding
import com.siimkinks.sqlitemagic.internal.parameterEncoding
import com.siimkinks.sqlitemagic.internal.requiredReadType

internal data class Token(val value: String)

internal val TOKEN_TYPE = requiredReadType(
  storage = SqlStorage.STRING,
  encode = { value: Token -> "db:${value.value}" },
  decode = { value -> Token(value.removePrefix("db:")) },
  encodingNullness = Required
)

internal data class Person(
  val id: Long,
  val name: String?,
  val age: Int,
  val parentId: Long?,
  val token: Token,
  val flag: Boolean?
)

internal class PersonTable(alias: String? = null) : Table<Person>(
  name = "person",
  alias = alias
) {
  val ID = UniqueColumn<Person, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val NAME = Column<Person, String, SqlNullable, String?, String?, SqlNullable, String?>(
    table = this,
    name = "name",
    nullness = SqlNullable,
    parameterEncoding = STRING.nullBypassParameterEncoding(),
    selectedRead = Cursor::getString
  )
  val AGE = Column<Person, Int, Required, Int, Int, Required, Int>(
    table = this,
    name = "age",
    nullness = Required,
    parameterEncoding = INT.parameterEncoding()
  )
  val PARENT_ID = Column(
    table = this,
    name = "parent_id",
    nullness = SqlNullable,
    parameterEncoding = LONG.nullBypassParameterEncoding(),
    selectedRead = nullableRead(Cursor::getLong)
  )
  val TOKEN = Column<Person, Token, Required, Token, Token, Required, Token>(
    table = this,
    name = "token",
    nullness = Required,
    parameterEncoding = TOKEN_TYPE.parameterEncoding()
  )
  val FLAG = Column(
    table = this,
    name = "flag",
    nullness = SqlNullable,
    parameterEncoding = BOOLEAN.nullBypassParameterEncoding(),
    selectedRead = nullableRead { cursor, index -> cursor.getLong(index) != 0L }
  )

  override val entityFields = listOf(ID, NAME, AGE, PARENT_ID, TOKEN, FLAG)

  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = Person(
    id = ID.selectRead(cursor, startIndex),
    name = NAME.selectRead(cursor, startIndex + 1),
    age = AGE.selectRead(cursor, startIndex + 2),
    parentId = PARENT_ID.selectRead(cursor, startIndex + 3),
    token = TOKEN.selectRead(cursor, startIndex + 4),
    flag = FLAG.selectRead(cursor, startIndex + 5)
  )

  override infix fun AS(alias: String) = PersonTable(alias)
}

internal val PERSON = PersonTable()

internal val ada = Person(
  id = 1L,
  name = "Ada",
  age = 36,
  parentId = null,
  token = Token("a"),
  flag = true
)
internal val grace = Person(
  id = 2L,
  name = "Grace",
  age = 85,
  parentId = 1L,
  token = Token("g"),
  flag = false
)
internal val unnamed = Person(
  id = 3L,
  name = null,
  age = 17,
  parentId = 2L,
  token = Token("n"),
  flag = null
)
internal val linus = Person(
  id = 4L,
  name = "Linus",
  age = 17,
  parentId = 4L,
  token = Token("l"),
  flag = true
)

internal val people = listOf(ada, grace, unnamed, linus)

internal fun personDatabase() = BundledSqliteDatabase().apply {
  execSQL(
    "CREATE TABLE person (id INTEGER PRIMARY KEY, name TEXT, age INTEGER NOT NULL, parent_id INTEGER, " +
        "token TEXT NOT NULL, flag INTEGER)"
  )
  execSQL(
    "INSERT INTO person VALUES (1, 'Ada', 36, NULL, 'db:a', 1), (2, 'Grace', 85, 1, 'db:g', 0), " +
        "(3, NULL, 17, 2, 'db:n', NULL), (4, 'Linus', 17, 4, 'db:l', 1)"
  )
}

internal data class Pet(
  val id: Long,
  val owner: Long,
  val name: String,
  val nickname: String?
)

internal class PetTable(alias: String? = null) : Table<Pet>(
  name = "pet",
  alias = alias
) {
  val ID = UniqueColumn<Pet, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "id",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val OWNER = Column<Pet, Long, Required, Long, Long, Required, Long>(
    table = this,
    name = "owner",
    nullness = Required,
    parameterEncoding = LONG.parameterEncoding()
  )
  val NAME = Column<Pet, String, Required, String, String, Required, String>(
    table = this,
    name = "name",
    nullness = Required,
    parameterEncoding = STRING.parameterEncoding()
  )
  val NICKNAME = Column<Pet, String, SqlNullable, String?, String?, SqlNullable, String?>(
    table = this,
    name = "nickname",
    nullness = SqlNullable,
    parameterEncoding = STRING.nullBypassParameterEncoding(),
    selectedRead = Cursor::getString
  )

  override val entityFields = listOf(ID, OWNER, NAME, NICKNAME)

  override fun readEntity(cursor: Cursor, startIndex: Int, related: RelatedEntities?) = Pet(
    id = ID.selectRead(cursor, startIndex),
    owner = OWNER.selectRead(cursor, startIndex + 1),
    name = NAME.selectRead(cursor, startIndex + 2),
    nickname = NICKNAME.selectRead(cursor, startIndex + 3)
  )

  override infix fun AS(alias: String) = PetTable(alias)
}

internal val PET = PetTable()

internal fun petDatabase() = personDatabase().apply {
  execSQL("CREATE TABLE pet (id INTEGER PRIMARY KEY, owner INTEGER NOT NULL, name TEXT NOT NULL, nickname TEXT)")
  execSQL("INSERT INTO pet VALUES (1, 1, 'Rex', NULL), (2, 1, 'Tom', 'Tommy'), (3, 2, 'Kit', NULL)")
}

internal fun DbConnection.persons() = SELECT.FROM(PERSON)
  .ORDER_BY(PERSON.ID.ASC())
  .usingConnection(this)
  .execute()
