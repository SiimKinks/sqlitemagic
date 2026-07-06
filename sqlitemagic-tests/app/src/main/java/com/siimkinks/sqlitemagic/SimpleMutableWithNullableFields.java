package com.siimkinks.sqlitemagic;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Collection;
import java.util.Objects;
import java.util.Random;

@Table
public final class SimpleMutableWithNullableFields {
  @Id
  @Column
  public Long id;
  @Nullable
  @Column
  public String nullableString;
  @NonNull
  @Column
  public String nonNullString;

  public SimpleMutableWithNullableFields() {
  }

  public static SimpleMutableWithNullableFields newRandom() {
    final SimpleMutableWithNullableFields obj = new SimpleMutableWithNullableFields();
    fillWithRandomValues(obj);
    return obj;
  }

  public static void fillWithRandomValues(SimpleMutableWithNullableFields obj) {
    final Random r = new Random();
    obj.id = r.nextLong();
    obj.nullableString = Utils.randomTableName();
    obj.nonNullString = Utils.randomTableName();
  }

  public SqliteMagic_SimpleMutableWithNullableFields_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleMutableWithNullableFields_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleMutableWithNullableFields_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleMutableWithNullableFields_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleMutableWithNullableFields_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleMutableWithNullableFields_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleMutableWithNullableFields_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleMutableWithNullableFields_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleMutableWithNullableFields_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleMutableWithNullableFields_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleMutableWithNullableFields_Handler.BulkInsertBuilder insert(Iterable<SimpleMutableWithNullableFields> o) {
    return SqliteMagic_SimpleMutableWithNullableFields_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutableWithNullableFields_Handler.BulkUpdateBuilder update(Iterable<SimpleMutableWithNullableFields> o) {
    return SqliteMagic_SimpleMutableWithNullableFields_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutableWithNullableFields_Handler.BulkPersistBuilder persist(Iterable<SimpleMutableWithNullableFields> o) {
    return SqliteMagic_SimpleMutableWithNullableFields_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutableWithNullableFields_Handler.BulkDeleteBuilder delete(Collection<SimpleMutableWithNullableFields> o) {
    return SqliteMagic_SimpleMutableWithNullableFields_Handler.BulkDeleteBuilder.create(o);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SimpleMutableWithNullableFields that = (SimpleMutableWithNullableFields) o;
    return Objects.equals(id, that.id) &&
        Objects.equals(nullableString, that.nullableString) &&
        Objects.equals(nonNullString, that.nonNullString);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, nullableString, nonNullString);
  }

  @Override
  public String toString() {
    return "SimpleMutableWithNullableFields{" +
        "id=" + id +
        ", nullableString='" + nullableString + '\'' +
        ", nonNullString='" + nonNullString + '\'' +
        '}';
  }
}
