package com.siimkinks.sqlitemagic;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.LinkedList;

import androidx.annotation.NonNull;

abstract class SqlClause {
  abstract void appendSql(@NonNull StringBuilder sb);

  abstract void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables);
}
