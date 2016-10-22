package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import java.util.LinkedList;

final class SqlCreator {
  @NonNull
  static String getSql(@NonNull SqlNode sqlNode, int sqlNodeCount) {
    final StringBuilder stringBuilder = new StringBuilder(sqlNodeCount * 20);
    appendSql(sqlNode, stringBuilder);
    return stringBuilder.toString();
  }

  static void appendSql(@NonNull SqlNode sqlNode, @NonNull StringBuilder stringBuilder) {
    final SqlNode parent = sqlNode.parent;
    if (parent != null) {
      appendSql(parent, stringBuilder);
    } else {
      appendNodeSqlPart(sqlNode, stringBuilder);
      return;
    }
    appendNodeSqlPart(sqlNode, stringBuilder);
  }

  private static void appendNodeSqlPart(@NonNull SqlNode sqlNode, @NonNull StringBuilder stringBuilder) {
    sqlNode.appendSql(stringBuilder);
    stringBuilder.append(' ');
  }

  @NonNull
  static String getSql(@NonNull SqlNode sqlNode,
                       int sqlNodeCount,
                       @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    final StringBuilder stringBuilder = new StringBuilder(sqlNodeCount * 20);
    appendSql(sqlNode, systemRenamedTables, stringBuilder);
    return stringBuilder.toString();
  }

  static void appendSql(@NonNull SqlNode sqlNode,
                        @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables,
                        @NonNull StringBuilder stringBuilder) {
    final SqlNode parent = sqlNode.parent;
    if (parent != null) {
      appendSql(parent, systemRenamedTables, stringBuilder);
    } else {
      appendNodeSqlPart(sqlNode, stringBuilder, systemRenamedTables);
      return;
    }
    appendNodeSqlPart(sqlNode, stringBuilder, systemRenamedTables);
  }

  private static void appendNodeSqlPart(@NonNull SqlNode sqlNode, @NonNull StringBuilder stringBuilder,
                                        @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    sqlNode.appendSql(stringBuilder, systemRenamedTables);
    stringBuilder.append(' ');
  }
}
