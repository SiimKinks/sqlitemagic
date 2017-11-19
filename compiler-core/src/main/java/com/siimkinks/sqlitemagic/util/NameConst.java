package com.siimkinks.sqlitemagic.util;

public final class NameConst {
  public static final String PACKAGE_ROOT = "com.siimkinks.sqlitemagic";

  public static final String CLASS_MODEL_DAO = "Dao";
  public static final String CLASS_MODEL_HANDLER = "Handler";
  public static final String CLASS_TABLE_STRUCTURE = "Table";
  public static final String CLASS_INSERT = "InsertBuilder";
  public static final String CLASS_BULK_INSERT = "BulkInsertBuilder";
  public static final String CLASS_UPDATE = "UpdateBuilder";
  public static final String CLASS_BULK_UPDATE = "BulkUpdateBuilder";
  public static final String CLASS_PERSIST = "PersistBuilder";
  public static final String CLASS_BULK_PERSIST = "BulkPersistBuilder";
  public static final String CLASS_DELETE = "DeleteBuilder";
  public static final String CLASS_BULK_DELETE = "BulkDeleteBuilder";
  public static final String CLASS_DELETE_TABLE = "DeleteTableBuilder";

  public static final String METHOD_CREATE = "create";
  public static final String METHOD_EXECUTE = "execute";
  public static final String METHOD_OBSERVE = "observe";
  public static final String METHOD_CONNECTION_PROVIDER = "usingConnection";

  public static final String FIELD_VIEW_QUERY = "QUERY";
  public static final String FIELD_INSERT_SQL = "INSERT_SQL";
  public static final String FIELD_UPDATE_SQL = "UPDATE_SQL";
  public static final String FIELD_TABLE_SCHEMA = "TABLE_SCHEMA";

  public static final String METHOD_NEW_INSTANCE_WITH_ONLY_ID = "newInstanceWithOnlyId";
  public static final String METHOD_ADD_SHALLOW_QUERY_PARTS = "addShallowQueryParts";
  public static final String METHOD_ADD_SHALLOW_QUERY_PARTS_INTERNAL = "addShallowQueryPartsInternal";
  public static final String METHOD_ADD_DEEP_QUERY_PARTS = "addDeepQueryParts";
  public static final String METHOD_ADD_DEEP_QUERY_PARTS_INTERNAL = "addDeepQueryPartsInternal";
  public static final String METHOD_MAPPER = "mapper";
  public static final String METHOD_FULL_OBJECT_FROM_CURSOR_POSITION = "fullObjectFromCursorPosition";
  public static final String METHOD_SHALLOW_OBJECT_FROM_CURSOR_POSITION = "shallowObjectFromCursorPosition";
  public static final String METHOD_SET_ID = "setId";
  public static final String METHOD_GET_ID = "getId";
  public static final String METHOD_SET_CONFLICT_ALGORITHM = "conflictAlgorithm";
  public static final String METHOD_BY_COLUMN = "byColumn";
  public static final String METHOD_SET_IGNORE_NULL_VALUES = "ignoreNullValues";
  public static final String METHOD_IS_UNIQUE_COLUMN_NULL = "isUniqueColumnNull";
  public static final String METHOD_BIND_UNIQUE_COLUMN = "bindUniqueColumn";
  public static final String METHOD_BIND_TO_UPDATE_STATEMENT = "bindToUpdateStatement";
  public static final String METHOD_BIND_TO_UPDATE_STATEMENT_WITH_COMPLEX_COLUMNS = "bindToUpdateStatementWithComplexColumns";
  public static final String METHOD_BIND_TO_INSERT_STATEMENT = "bindToInsertStatement";
  public static final String METHOD_BIND_TO_NOT_NULL = "bindNotNull";
  public static final String METHOD_DELETE = "delete";
  public static final String METHOD_DELETE_TABLE = "deleteTable";
  public static final String METHOD_GET_INSERT_STATEMENT = "getInsertStatement";
  public static final String METHOD_INTERNAL_INSERT = "internalInsert";
  public static final String METHOD_INSERT = "insert";
  public static final String METHOD_GET_UPDATE_STATEMENT = "getUpdateStatement";
  public static final String METHOD_UPDATE = "update";
  public static final String METHOD_INTERNAL_UPDATE = "internalUpdate";
  public static final String METHOD_PERSIST = "persist";
  public static final String METHOD_INTERNAL_PERSIST = "internalPersist";
  public static final String METHOD_INTERNAL_PERSIST_IGNORING_NULL_VALUES = "internalPersistIgnoringNullValues";
  public static final String METHOD_CALL_INTERNAL_PERSIST_ON_COMPLEX_COLUMNS = "callInternalPersistsOnComplexColumns";
  public static final String METHOD_CALL_INTERNAL_PERSIST_IGNORING_NULL_VALUES_ON_COMPLEX_COLUMNS = "callInternalPersistsIgnoringNullValuesOnComplexColumns";
  public static final String METHOD_CALL_INTERNAL_INSERT_ON_COMPLEX_COLUMNS = "callInternalInsertsOnComplexColumns";
  public static final String METHOD_CALL_INTERNAL_UPDATE_ON_COMPLEX_COLUMNS = "callInternalUpdatesOnComplexColumns";
}
