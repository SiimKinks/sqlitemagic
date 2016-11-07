package com.siimkinks.sqlitemagic;

import android.database.SQLException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Utils.ValueParser;
import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;
import com.siimkinks.sqlitemagic.internal.StringArraySet;

import java.util.ArrayList;
import java.util.LinkedList;

final class SelectionColumn<
    T, // exact type
    R, // return type (when this column is queried)
    ET, // equivalent type
    P>  // parent table type
    extends NumericColumn<T, R, ET, P> {

  @NonNull
  private final SelectBuilder<?> selectBuilder;
  @Nullable
  private ArrayList<String> parentObservedTables;

  private SelectionColumn(@NonNull Table<P> table, @NonNull String name, boolean allFromTable,
                          @NonNull ValueParser<?> valueParser, boolean nullable,
                          @Nullable String alias, @NonNull String nameInQuery,
                          @NonNull SelectBuilder<?> selectBuilder) {
    super(table, name, allFromTable, valueParser, nullable, alias, nameInQuery);
    this.selectBuilder = selectBuilder;
  }

  @SuppressWarnings("unchecked")
  @NonNull
  static <T> SelectionColumn<T, T, T, ?> from(@NonNull SelectBuilder<?> selectBuilder,
                                              @NonNull String alias) {
    final Select.SingleColumn<?> columnNode = selectBuilder.columnNode;
    if (selectBuilder.columnsNode != null || columnNode == null) {
      throw new SQLException("Only a single result allowed for a SELECT that is part of a column");
    }
    final Table table = selectBuilder.from.table;
    final Column<?, ?, ?, ?> column = columnNode.column;
    return new SelectionColumn<>(table, alias, false, column.valueParser, column.nullable, alias, alias, selectBuilder);
  }

  @Override
  void appendSql(@NonNull StringBuilder sb) {
    sb.append('(');
    selectBuilder.appendCompiledQuery(sb, parentObservedTables);
    sb.append(')');
  }

  @Override
  void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    sb.append('(');
    selectBuilder.appendCompiledQuery(sb, parentObservedTables);
    sb.append(')');
  }

  @Override
  void addSelectedTables(@NonNull StringArraySet result) {
    // selection column does not add any tables to outer selection as it is returning
    // autonomous column
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    args.addAll(selectBuilder.args);
  }

  @Override
  void addObservedTables(@NonNull ArrayList<String> tables) {
    // defer observed tables adding until sql building, where we might add missing joins,
    // so there will be more tables to add/observe
    this.parentObservedTables = tables;
  }

  @Override
  int compile(@NonNull SimpleArrayMap<String, Integer> columnPositions,
              @NonNull StringBuilder compiledCols,
              int columnOffset) {
    appendSql(compiledCols);
    appendAliasDeclarationIfNeeded(compiledCols);
    putColumnPosition(columnPositions, name, columnOffset, this);
    return columnOffset + 1;
  }

  @Override
  int compile(@NonNull SimpleArrayMap<String, Integer> columnPositions,
              @NonNull StringBuilder compiledCols,
              @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables,
              int columnOffset) {
    appendSql(compiledCols, systemRenamedTables);
    appendAliasDeclarationIfNeeded(compiledCols);
    putColumnPosition(columnPositions, name, columnOffset, this);
    return columnOffset + 1;
  }

  @NonNull
  @Override
  public NumericColumn<T, R, ET, P> as(@NonNull String alias) {
    return new SelectionColumn<>(table, name, allFromTable, valueParser, nullable, alias, nameInQuery, selectBuilder);
  }
}
