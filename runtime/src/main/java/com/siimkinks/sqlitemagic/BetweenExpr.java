package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;

final class BetweenExpr extends Expr {
  @Nullable
  private final String firstVal;
  @Nullable
  private final String secondVal;
  @Nullable
  private final Column<?, ?, ?, ?> firstColumn;
  @Nullable
  private final Column<?, ?, ?, ?> secondColumn;

  BetweenExpr(Column<?, ?, ?, ?> column,
              @Nullable String firstVal,
              @Nullable String secondVal,
              @Nullable Column<?, ?, ?, ?> firstColumn,
              @Nullable Column<?, ?, ?, ?> secondColumn,
              boolean not) {
    super(column, not ? " NOT " : " ");
    this.firstVal = firstVal;
    this.secondVal = secondVal;
    this.firstColumn = firstColumn;
    this.secondColumn = secondColumn;
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb) {
    super.appendToSql(sb);
    sb.append("BETWEEN ");
    if (firstVal != null) {
      sb.append('?');
    } else {
      if (firstColumn == null) {
        throw new IllegalStateException("Missing BETWEEN statement first value");
      }
      firstColumn.appendSql(sb);
    }
    sb.append(" AND ");
    if (secondVal != null) {
      sb.append('?');
    } else {
      if (secondColumn == null) {
        throw new IllegalStateException("Missing BETWEEN statement second value");
      }
      secondColumn.appendSql(sb);
    }
  }

  @Override
  void appendToSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    super.appendToSql(sb, systemRenamedTables);
    sb.append("BETWEEN ");
    if (firstVal != null) {
      sb.append('?');
    } else {
      if (firstColumn == null) {
        throw new IllegalStateException("Missing BETWEEN statement first value");
      }
      firstColumn.appendSql(sb, systemRenamedTables);
    }
    sb.append(" AND ");
    if (secondVal != null) {
      sb.append('?');
    } else {
      if (secondColumn == null) {
        throw new IllegalStateException("Missing BETWEEN statement second value");
      }
      secondColumn.appendSql(sb, systemRenamedTables);
    }
  }

  @Override
  void addArgs(@NonNull ArrayList<String> args) {
    super.addArgs(args);
    if (firstVal != null) {
      args.add(firstVal);
    }
    if (secondVal != null) {
      args.add(secondVal);
    }
  }

  @Override
  boolean containsColumn(@NonNull Column<?, ?, ?, ?> column) {
    return super.containsColumn(column)
        || firstColumn != null && column.equals(firstColumn)
        || secondColumn != null && column.equals(secondColumn);
  }
}
