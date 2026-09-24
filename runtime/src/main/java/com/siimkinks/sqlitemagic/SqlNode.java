package com.siimkinks.sqlitemagic;

import androidx.annotation.Nullable;

public abstract class SqlNode extends SqlClause {
  @Nullable
  final SqlNode parent;

  SqlNode(@Nullable SqlNode parent) {
    this.parent = parent;
  }
}
