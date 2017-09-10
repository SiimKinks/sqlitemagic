package com.siimkinks.sqlitemagic.model.immutable;

import com.siimkinks.sqlitemagic.model.ProvidesId;

public interface ImmutableEquals extends ProvidesId {
  boolean equalsWithoutId(Object o);
}
