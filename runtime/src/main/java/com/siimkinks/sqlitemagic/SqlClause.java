package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.LinkedList;

abstract class SqlClause {
  abstract void appendSql(@NonNull StringBuilder sb);

  abstract void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables);
}
