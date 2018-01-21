package com.siimkinks.sqlitemagic;

public final class GlobalConst {
  public static final String CLASS_NAME_GENERATED_CLASSES_MANAGER = "GeneratedClassesManager";
  public static final String CLASS_GENERATED_CLASSES_MANAGER = "com.siimkinks.sqlitemagic." + CLASS_NAME_GENERATED_CLASSES_MANAGER;

  public static final String METHOD_CREATE_TABLES = "createTables";
  public static final String METHOD_CLEAR_DATA = "clearData";
  public static final String METHOD_GET_DB_VERSION = "getDbVersion";
  public static final String METHOD_GET_DB_NAME = "getDbName";
  public static final String METHOD_CONFIGURE_DATABASE = "configureDatabase";
  public static final String METHOD_GET_SUBMODULE_NAMES = "getSubmoduleNames";
  public static final String METHOD_GET_NR_OF_TABLES = "getNrOfTables";
  public static final String METHOD_COLUMN_FOR_VALUE = "columnForValue";

  public static final String INVOCATION_METHOD_CREATE_TABLES = CLASS_GENERATED_CLASSES_MANAGER + "#" + METHOD_CREATE_TABLES;
  public static final String INVOCATION_METHOD_CLEAR_DATA = CLASS_GENERATED_CLASSES_MANAGER + "#" + METHOD_CLEAR_DATA;
  public static final String INVOCATION_METHOD_GET_DB_VERSION = CLASS_GENERATED_CLASSES_MANAGER + "#" + METHOD_GET_DB_VERSION;
  public static final String INVOCATION_METHOD_GET_DB_NAME = CLASS_GENERATED_CLASSES_MANAGER + "#" + METHOD_GET_DB_NAME;
  public static final String INVOCATION_METHOD_CONFIGURE_DATABASE = CLASS_GENERATED_CLASSES_MANAGER + "#" + METHOD_CONFIGURE_DATABASE;
  public static final String INVOCATION_METHOD_GET_SUBMODULE_NAMES = CLASS_GENERATED_CLASSES_MANAGER + "#" + METHOD_GET_SUBMODULE_NAMES;
  public static final String INVOCATION_METHOD_GET_NR_OF_TABLES = CLASS_GENERATED_CLASSES_MANAGER + "#" + METHOD_GET_NR_OF_TABLES;
  public static final String INVOCATION_METHOD_COLUMN_FOR_VALUE = CLASS_GENERATED_CLASSES_MANAGER + "#" + METHOD_COLUMN_FOR_VALUE;

  public static final String ERROR_PROCESSOR_DID_NOT_RUN = "Looks like SqliteMagic gradle plugin processor did not run. Please make sure that project is configured correctly";
  public static final String ERROR_UNSUBSCRIBED_UNEXPECTEDLY = "Subscriber unsubscribed unexpectedly";
  public static final String FAILED_TO_INSERT_ERR_MSG = "Failed to insert ";
  public static final String FAILED_TO_PERSIST_ERR_MSG = "Failed to persist ";
  public static final String FAILED_TO_UPDATE_ERR_MSG = "Failed to update ";

  private GlobalConst() {
    throw new AssertionError("no instances");
  }
}
