package com.siimkinks.sqlitemagic;

import android.support.annotation.Nullable;

abstract class SqlNode extends SqlClause {
  @Nullable
  final SqlNode parent;

  SqlNode(@Nullable SqlNode parent) {
    this.parent = parent;
  }
}
