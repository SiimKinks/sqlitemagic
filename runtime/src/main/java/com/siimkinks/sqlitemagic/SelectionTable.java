package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.util.ArrayList;
import java.util.Collections;

final class SelectionTable<T> extends Table<T> {
  @NonNull
  private final String compiledSql;
  @Nullable
  private final String[] args;
  @NonNull
  private final String[] observedTables;
  private final Query.Mapper<T> mapper;

  private SelectionTable(@NonNull String compiledSql,
                         @Nullable String alias,
                         @Nullable String[] args,
                         @NonNull String[] observedTables,
                         Query.Mapper<T> mapper) {
    super("", alias, 0);
    this.compiledSql = compiledSql;
    this.args = args;
    this.mapper = mapper;
    this.observedTables = observedTables;
  }

  @SuppressWarnings("unchecked")
  static <T> SelectionTable<T> from(@NonNull SelectBuilder<?> selectTableBuilder,
                                    @Nullable String alias) {
    final String compiledSql;
    final String[] args;
    final Query.Mapper<T> mapper;
    final String[] observedTables;
    final CompiledSelect<Object, ?> compiledSelect = selectTableBuilder.build();
    if (compiledSelect instanceof CompiledSelect1Impl<?, ?>) {
      final CompiledSelect1Impl cs = (CompiledSelect1Impl) compiledSelect;
      compiledSql = cs.sql;
      args = cs.args;
      mapper = cs.mapper;
      observedTables = cs.observedTables;
    } else if (compiledSelect instanceof CompiledSelectImpl<?, ?>) {
      final CompiledSelectImpl cs = (CompiledSelectImpl) compiledSelect;
      compiledSql = cs.sql;
      args = cs.args;
      mapper = cs.mapper;
      observedTables = cs.observedTables;
    } else throw new RuntimeException("Unknown compiled select");
    return new SelectionTable<>(compiledSql, alias, args, observedTables, mapper);
  }

  void linkWithOuterSelect(@NonNull SelectBuilder<?> outerSelectBuilder) {
    if (args != null) {
      Collections.addAll(outerSelectBuilder.args, args);
    }
  }

  @Override
  void appendToSqlFromClause(@NonNull StringBuilder sb) {
    sb.append('(');
    sb.append(compiledSql);
    sb.append(')');
    if (hasAlias) {
      sb.append(" AS ")
          .append(alias);
    }
  }

  @Override
  boolean perfectSelection(@NonNull ArrayList<String> observedTables, @Nullable SimpleArrayMap<String, String> tableGraphNodeNames, @Nullable SimpleArrayMap<String, Integer> columnPositions) {
    final String[] subqueryObservedTables = this.observedTables;
    final int length = subqueryObservedTables.length;
    for (int i = 0; i < length; i++) {
      final String tableName = subqueryObservedTables[i];
      if (!observedTables.contains(tableName)) {
        observedTables.add(tableName);
      }
    }
    return false;
  }

  @NonNull
  @Override
  Query.Mapper<T> mapper(@Nullable SimpleArrayMap<String, Integer> columnPositions, SimpleArrayMap<String, String> tableGraphNodeNames, boolean queryDeep) {
    return mapper;
  }

  @NonNull
  @Override
  public Table<T> as(@NonNull String alias) {
    return new SelectionTable<>(compiledSql, alias, args, observedTables, mapper);
  }
}
