package com.siimkinks.sqlitemagic.fixture.model;

import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Objects;

@Table
public class JavaMutableEntity {
  @Id
  public String id;
  public String value;

  public JavaMutableEntity() {
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof JavaMutableEntity that)) {
      return false;
    }
    return Objects.equals(id, that.id) && Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, value);
  }
}
