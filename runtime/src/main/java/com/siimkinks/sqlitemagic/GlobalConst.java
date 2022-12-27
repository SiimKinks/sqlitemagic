package com.siimkinks.sqlitemagic;

public final class GlobalConst {
  public static final String CLASS_NAME_MAIN_GENERATED_CLASSES_MANAGER = "SqliteMagicDatabase";
  public static final String CLASS_NAME_GENERATED_CLASSES_MANAGER = "GeneratedClassesManager";

  public static final String METHOD_CREATE_SCHEMA = "createSchema";
  public static final String METHOD_CLEAR_DATA = "clearData";
  public static final String METHOD_MIGRATE_VIEWS = "migrateViews";
  public static final String METHOD_GET_DB_VERSION = "getDbVersion";
  public static final String METHOD_GET_DB_NAME = "getDbName";
  public static final String METHOD_CONFIGURE_DATABASE = "configureDatabase";
  public static final String METHOD_GET_SUBMODULE_NAMES = "getSubmoduleNames";
  public static final String METHOD_GET_NR_OF_TABLES = "getNrOfTables";
  public static final String METHOD_COLUMN_FOR_VALUE = "columnForValue";
  public static final String METHOD_IS_DEBUG = "isDebug";

  public static final String ERROR_NOT_INITIALIZED = "Looks like SqliteMagic is not initialized. Please make sure that project is configured correctly";
  public static final String ERROR_UNSUBSCRIBED_UNEXPECTEDLY = "Subscriber unsubscribed unexpectedly";
  public static final String FAILED_TO_INSERT_ERR_MSG = "Failed to insert ";
  public static final String FAILED_TO_PERSIST_ERR_MSG = "Failed to persist ";
  public static final String FAILED_TO_UPDATE_ERR_MSG = "Failed to update ";

  private GlobalConst() {
    throw new AssertionError("no instances");
  }
}
