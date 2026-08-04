package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityBulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityDeleteTableBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertBuilder
import com.siimkinks.sqlitemagic.entity.EntityInsertResult
import com.siimkinks.sqlitemagic.entity.EntityPersistBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistByColumnBuilder
import com.siimkinks.sqlitemagic.entity.EntityPersistResult
import com.siimkinks.sqlitemagic.entity.EntityUpdateBuilder
import com.siimkinks.sqlitemagic.entity.EntityUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.exception.OperationFailedException
import com.siimkinks.sqlitemagic.internal.MutableInt
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.StringArraySet
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.asClassName
import java.util.concurrent.CancellationException

internal object WriterTypes {
  val TABLE = Table::class.asClassName()
  val COLUMN = Column::class.asClassName()
  val NUMERIC_COLUMN = NumericColumn::class.asClassName()
  val UNIQUE_COLUMN = UniqueColumn::class.asClassName()
  val UNIQUE_NUMERIC_COLUMN = UniqueNumericColumn::class.asClassName()
  val COMPLEX_COLUMN = ComplexColumn::class.asClassName()
  val COMPLEX_NUMERIC_COLUMN = ComplexNumericColumn::class.asClassName()
  val BOOLEAN_COLUMN = BooleanColumn::class.asClassName()
  val UNIQUE = Unique::class.asClassName()
  val NULLABLE = Nullable::class.asClassName()
  val NOT_NULLABLE = NotNullable::class.asClassName()
  val UTILS = Utils::class.asClassName()
  val QUERY_MAPPER = Query.Mapper::class.asClassName()
  val SELECT_FROM_RAW = Select.From::class.asClassName()
  val SELECT_FROM = SELECT_FROM_RAW.parameterizedBy(STAR, STAR, STAR, STAR)
  val DB_CONNECTION = DbConnection::class.asClassName()
  val DB_CONNECTION_IMPL = DbConnectionImpl::class.asClassName()
  val SQLITE_MAGIC = SqliteMagic::class.asClassName()
  val CONFLICT_ALGORITHM = ConflictAlgorithm::class.asClassName()
  val LOG_UTIL = LogUtil::class.asClassName()
  val JOIN_CLAUSE = JoinClause::class.asClassName()
  val VALUE_PARSER = Utils.ValueParser::class
    .asClassName()
    .parameterizedBy(STAR)

  val ENTITY_INSERT_BUILDER = EntityInsertBuilder::class.asClassName()
  val ENTITY_UPDATE_BUILDER = EntityUpdateBuilder::class.asClassName()
  val ENTITY_UPDATE_BY_COLUMN_BUILDER = EntityUpdateByColumnBuilder::class.asClassName()
  val ENTITY_PERSIST_BUILDER = EntityPersistBuilder::class.asClassName()
  val ENTITY_PERSIST_BY_COLUMN_BUILDER = EntityPersistByColumnBuilder::class.asClassName()
  val ENTITY_DELETE_BUILDER = EntityDeleteBuilder::class.asClassName()
  val ENTITY_DELETE_BY_COLUMN_BUILDER = EntityDeleteByColumnBuilder::class.asClassName()
  val ENTITY_DELETE_TABLE_BUILDER = EntityDeleteTableBuilder::class.asClassName()
  val ENTITY_BULK_INSERT_BUILDER = EntityBulkInsertBuilder::class.asClassName()
  val ENTITY_BULK_UPDATE_BUILDER = EntityBulkUpdateBuilder::class.asClassName()
  val ENTITY_BULK_UPDATE_BY_COLUMN_BUILDER = EntityBulkUpdateByColumnBuilder::class.asClassName()
  val ENTITY_BULK_PERSIST_BUILDER = EntityBulkPersistBuilder::class.asClassName()
  val ENTITY_BULK_PERSIST_BY_COLUMN_BUILDER = EntityBulkPersistByColumnBuilder::class.asClassName()
  val ENTITY_BULK_DELETE_BUILDER = EntityBulkDeleteBuilder::class.asClassName()
  val ENTITY_BULK_DELETE_BY_COLUMN_BUILDER = EntityBulkDeleteByColumnBuilder::class.asClassName()
  val ENTITY_INSERT_RESULT = EntityInsertResult::class.asClassName()
  val ENTITY_PERSIST_RESULT = EntityPersistResult::class.asClassName()
  val OPERATION_FAILED_EXCEPTION = OperationFailedException::class.asClassName()
  val ENTITY_DB_MANAGER = EntityDbManager::class.asClassName()
  val OPERATION_HELPER = OperationHelper::class.asClassName()
  val VARIABLE_ARGS_OPERATION_HELPER = VariableArgsOperationHelper::class.asClassName()

  val SIMPLE_ARRAY_MAP = SimpleArrayMap::class.asClassName()
  val BIND_VALUES_MAP = SIMPLE_ARRAY_MAP.parameterizedBy(STRING, ANY)
  val STRING_ARRAY_SET = StringArraySet::class.asClassName()
  val MUTABLE_INT = MutableInt::class.asClassName()
  val ARRAY_LIST = ClassName("java.util", "ArrayList")
  val LINKED_LIST = ClassName("java.util", "LinkedList")
  val PAIR = Pair::class.asClassName()
  val SYSTEM_RENAMED_TABLES = SIMPLE_ARRAY_MAP.parameterizedBy(
    STRING,
    LINKED_LIST.parameterizedBy(STRING)
  )

  val CURSOR = ClassName("android.database", "Cursor")
  val SQLITE_DATABASE = ClassName("android.database.sqlite", "SQLiteDatabase")
  val SQL_EXCEPTION = ClassName("android.database", "SQLException")
  val SUPPORT_SQLITE_STATEMENT = ClassName("androidx.sqlite.db", "SupportSQLiteStatement")

  val SINGLE = ClassName("io.reactivex", "Single")
  val COMPLETABLE = ClassName("io.reactivex", "Completable")
  val CANCELLATION_EXCEPTION = CancellationException::class.asClassName()

  val UNCHECKED_CAST = AnnotationSpec
    .builder(Suppress::class)
    .addMember("%S", "UNCHECKED_CAST")
    .build()
}
