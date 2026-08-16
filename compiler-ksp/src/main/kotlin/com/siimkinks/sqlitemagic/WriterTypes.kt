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
import com.siimkinks.sqlitemagic.internal.BulkDeleteBuilder
import com.siimkinks.sqlitemagic.internal.BulkDeleteByColumnBuilder
import com.siimkinks.sqlitemagic.internal.BulkInsertBuilder
import com.siimkinks.sqlitemagic.internal.BulkPersistBuilder
import com.siimkinks.sqlitemagic.internal.BulkPersistByColumnBuilder
import com.siimkinks.sqlitemagic.internal.BulkUpdateBuilder
import com.siimkinks.sqlitemagic.internal.BulkUpdateByColumnBuilder
import com.siimkinks.sqlitemagic.internal.DeleteBuilder
import com.siimkinks.sqlitemagic.internal.DeleteByColumnBuilder
import com.siimkinks.sqlitemagic.internal.DeleteTableBuilder
import com.siimkinks.sqlitemagic.internal.EntityAdapter
import com.siimkinks.sqlitemagic.internal.EntityDefaultIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityGeneratedIdAdapter
import com.siimkinks.sqlitemagic.internal.EntityIdentityAdapter
import com.siimkinks.sqlitemagic.internal.EntityIdentityStatementBinder
import com.siimkinks.sqlitemagic.internal.EntityRecursiveAdapter
import com.siimkinks.sqlitemagic.internal.EntityRelationshipOperations
import com.siimkinks.sqlitemagic.internal.EntityStatementBinder
import com.siimkinks.sqlitemagic.internal.GeneratedEntityIdentity
import com.siimkinks.sqlitemagic.internal.InsertBuilder
import com.siimkinks.sqlitemagic.internal.MutableInt
import com.siimkinks.sqlitemagic.internal.PersistBuilder
import com.siimkinks.sqlitemagic.internal.PersistByColumnBuilder
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap
import com.siimkinks.sqlitemagic.internal.StringArraySet
import com.siimkinks.sqlitemagic.internal.UpdateBuilder
import com.siimkinks.sqlitemagic.internal.UpdateByColumnBuilder
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.asClassName
import java.util.concurrent.CancellationException

internal object WriterTypes {
  val GENERATED_DATABASE = GeneratedDatabase::class.asClassName()
  val TABLE = Table::class.asClassName()
  val COLUMN = Column::class.asClassName()
  val CHAR_SEQUENCE = CharSequence::class.asClassName()
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
  val QUERY_ALIAS_CONTEXT = QueryAliasContext::class.asClassName()
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
  val INSERT_BUILDER = InsertBuilder::class.asClassName()
  val BULK_INSERT_BUILDER = BulkInsertBuilder::class.asClassName()
  val UPDATE_BUILDER = UpdateBuilder::class.asClassName()
  val UPDATE_BY_COLUMN_BUILDER = UpdateByColumnBuilder::class.asClassName()
  val BULK_UPDATE_BUILDER = BulkUpdateBuilder::class.asClassName()
  val BULK_UPDATE_BY_COLUMN_BUILDER = BulkUpdateByColumnBuilder::class.asClassName()
  val PERSIST_BUILDER = PersistBuilder::class.asClassName()
  val PERSIST_BY_COLUMN_BUILDER = PersistByColumnBuilder::class.asClassName()
  val BULK_PERSIST_BUILDER = BulkPersistBuilder::class.asClassName()
  val BULK_PERSIST_BY_COLUMN_BUILDER = BulkPersistByColumnBuilder::class.asClassName()
  val DELETE_BUILDER = DeleteBuilder::class.asClassName()
  val DELETE_BY_COLUMN_BUILDER = DeleteByColumnBuilder::class.asClassName()
  val BULK_DELETE_BUILDER = BulkDeleteBuilder::class.asClassName()
  val BULK_DELETE_BY_COLUMN_BUILDER = BulkDeleteByColumnBuilder::class.asClassName()
  val DELETE_TABLE_BUILDER = DeleteTableBuilder::class.asClassName()
  val ENTITY_INSERT_RESULT = EntityInsertResult::class.asClassName()
  val ENTITY_PERSIST_RESULT = EntityPersistResult::class.asClassName()
  val ENTITY_ADAPTER = EntityAdapter::class.asClassName()
  val ENTITY_IDENTITY_ADAPTER = EntityIdentityAdapter::class.asClassName()
  val ENTITY_DEFAULT_IDENTITY_ADAPTER = EntityDefaultIdentityAdapter::class.asClassName()
  val ENTITY_GENERATED_ID_ADAPTER = EntityGeneratedIdAdapter::class.asClassName()
  val ENTITY_STATEMENT_BINDER = EntityStatementBinder::class.asClassName()
  val ENTITY_IDENTITY_STATEMENT_BINDER = EntityIdentityStatementBinder::class.asClassName()
  val ENTITY_RECURSIVE_ADAPTER = EntityRecursiveAdapter::class.asClassName()
  val ENTITY_RELATIONSHIP_OPERATIONS = EntityRelationshipOperations::class.asClassName()
  val GENERATED_ENTITY_IDENTITY = GeneratedEntityIdentity::class.asClassName()
  val OPERATION_FAILED_EXCEPTION = OperationFailedException::class.asClassName()

  val SIMPLE_ARRAY_MAP = SimpleArrayMap::class.asClassName()
  val BIND_VALUES_MAP = SIMPLE_ARRAY_MAP.parameterizedBy(STRING, ANY)
  val STRING_ARRAY = ARRAY.parameterizedBy(STRING)
  val STRING_ARRAY_SET = StringArraySet::class.asClassName()
  val MUTABLE_INT = MutableInt::class.asClassName()
  val ARRAY_LIST = ClassName("java.util", "ArrayList")
  val LINKED_LIST = ClassName("java.util", "LinkedList")
  val SYSTEM_RENAMED_TABLES = SIMPLE_ARRAY_MAP.parameterizedBy(
    STRING,
    LINKED_LIST.parameterizedBy(STRING)
  )

  val CURSOR = ClassName("android.database", "Cursor")
  val SQLITE_DATABASE = ClassName("androidx.sqlite.db", "SupportSQLiteDatabase")
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
