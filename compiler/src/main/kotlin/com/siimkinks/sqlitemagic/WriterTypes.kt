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
import com.siimkinks.sqlitemagic.internal.MutableObjectIntMap
import com.siimkinks.sqlitemagic.internal.MutableScatterMap
import com.siimkinks.sqlitemagic.internal.MutableScatterSet
import com.siimkinks.sqlitemagic.internal.PersistBuilder
import com.siimkinks.sqlitemagic.internal.PersistByColumnBuilder
import com.siimkinks.sqlitemagic.internal.UpdateBuilder
import com.siimkinks.sqlitemagic.internal.UpdateByColumnBuilder
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.asClassName

internal object WriterTypes {
  val GENERATED_DATABASE = GeneratedDatabase::class.asClassName()
  val TABLE = Table::class.asClassName()
  val COLUMN = Column::class.asClassName()
  val COLUMN_VALUE_ADAPTER = ColumnValueAdapter::class.asClassName()
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
  val SQL_UTIL = SqlUtil::class.asClassName()
  val VIEW_DEFINITION = ViewDefinition::class.asClassName()
  val GENERATED_VIEW = GeneratedView::class.asClassName()
  val QUERY_MAPPER = Query.Mapper::class.asClassName()
  val SQLITE_MAGIC = SqliteMagic::class.asClassName()
  val LOG_UTIL = LogUtil::class.asClassName()
  val QUERY_GRAPH_SCOPE = QueryGraphScope::class.asClassName()
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

  val MUTABLE_SCATTER_MAP = MutableScatterMap::class.asClassName()
  val COLUMN_POSITIONS_MAP = MutableObjectIntMap::class
    .asClassName()
    .parameterizedBy(STRING)
  val BIND_VALUES_MAP = MUTABLE_SCATTER_MAP.parameterizedBy(STRING, ANY)
  val STRING_ARRAY = ARRAY.parameterizedBy(STRING)
  val STRING_SCATTER_SET = MutableScatterSet::class
    .asClassName()
    .parameterizedBy(STRING)
  val MUTABLE_INT = MutableInt::class.asClassName()

  val CURSOR = ClassName("android.database", "Cursor")
  val SQLITE_DATABASE = ClassName("androidx.sqlite.db", "SupportSQLiteDatabase")
  val SQL_EXCEPTION = ClassName("android.database", "SQLException")
  val SUPPORT_SQLITE_STATEMENT = ClassName("androidx.sqlite.db", "SupportSQLiteStatement")

  val CHECK_NOT_NULL = MemberName("kotlin", "checkNotNull")

  val UNCHECKED_CAST = AnnotationSpec
    .builder(Suppress::class)
    .addMember("%S", "UNCHECKED_CAST")
    .build()
}
