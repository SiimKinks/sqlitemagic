package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.siimkinks.sqlitemagic.Utils.ValueParser;
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;
import com.siimkinks.sqlitemagic.internal.StringArraySet;

import java.util.ArrayList;
import java.util.LinkedList;

final class FunctionColumn<
    T, // exact type
    R, // return type (when this column is queried)
    ET, // equivalent type
    P>  // parent table type
    extends NumericColumn<T, R, ET, P> {

  @NonNull
  private final Column[] wrappedColumns;
  @NonNull
  private final String prefix;
  @Nullable
  private final String separator;
  @NonNull
  private final String suffix;

  FunctionColumn(@NonNull Table<P> table,
                 @NonNull @Size(min = 1) Column[] wrappedColumns,
                 @NonNull String prefix,
                 @Nullable String separator,
                 @NonNull String suffix,
                 @NonNull ValueParser<?> valueParser,
                 boolean nullable,
                 @Nullable String alias) {
    super(table, prefix + suffix, false, valueParser, nullable,
        alias != null || wrappedColumns.length > 1 ? alias : wrappedColumns[0].alias);
    this.wrappedColumns = wrappedColumns;
    this.prefix = prefix;
    this.separator = separator;
    this.suffix = suffix;
  }

  FunctionColumn(@NonNull Table<P> table,
                 @NonNull Column wrappedColumn,
                 @NonNull String prefix,
                 @NonNull String suffix,
                 ValueParser<?> valueParser,
                 boolean nullable,
                 @Nullable String alias) {
    this(table, new Column[]{wrappedColumn}, prefix, null, suffix, valueParser, nullable, alias);
  }

  @Override
  void appendSql(@NonNull StringBuilder sb) {
    sb.append(prefix);
    final Column[] wrappedColumns = this.wrappedColumns;
    final int length = this.wrappedColumns.length;
    final String separator = this.separator;
    for (int i = 0; i < length; i++) {
      if (i != 0) {
        sb.append(separator);
      }
      wrappedColumns[i].appendSql(sb);
    }
    sb.append(suffix);
  }

  @Override
  void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    sb.append(prefix);
    final Column[] wrappedColumns = this.wrappedColumns;
    final int length = this.wrappedColumns.length;
    final String separator = this.separator;
    for (int i = 0; i < length; i++) {
      if (i != 0) {
        sb.append(separator);
      }
      wrappedColumns[i].appendSql(sb, systemRenamedTables);
    }
    sb.append(suffix);
  }

  @Override
  void addSelectedTables(@NonNull StringArraySet result) {
    final Column[] wrappedColumns = this.wrappedColumns;
    final int length = this.wrappedColumns.length;
    for (int i = 0; i < length; i++) {
      wrappedColumns[i].addSelectedTables(result);
    }
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    final Column[] wrappedColumns = this.wrappedColumns;
    final int length = this.wrappedColumns.length;
    for (int i = 0; i < length; i++) {
      wrappedColumns[i].addArgs(args);
    }
  }

  @Override
  void addObservedTables(@NonNull ArrayList<String> tables) {
    final Column[] wrappedColumns = this.wrappedColumns;
    final int length = this.wrappedColumns.length;
    for (int i = 0; i < length; i++) {
      wrappedColumns[i].addObservedTables(tables);
    }
  }

  @Override
  int compile(@NonNull SimpleArrayMap<String, Integer> columnPositions, @NonNull StringBuilder compiledCols, int columnOffset) {
    appendSql(compiledCols);
    appendAliasDeclarationIfNeeded(compiledCols);
    putColumnPosition(columnPositions, null, columnOffset, this);
    return columnOffset + 1;
  }

  @Override
  int compile(@NonNull SimpleArrayMap<String, Integer> columnPositions, @NonNull StringBuilder compiledCols, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables, int columnOffset) {
    appendSql(compiledCols, systemRenamedTables);
    appendAliasDeclarationIfNeeded(compiledCols);
    putColumnPosition(columnPositions, null, columnOffset, this);
    return columnOffset + 1;
  }

  @Override
  @NonNull
  @CheckResult
  public FunctionColumn<T, R, ET, P> as(@NonNull String alias) {
    return new FunctionColumn<>(table, wrappedColumns, prefix, separator, suffix, valueParser, nullable, alias);
  }
}
