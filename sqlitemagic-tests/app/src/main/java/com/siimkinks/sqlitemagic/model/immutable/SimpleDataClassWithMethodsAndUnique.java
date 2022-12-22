package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.Collection;
import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Table(persistAll = true, useAccessMethods = true)
public final class SimpleDataClassWithMethodsAndUnique {
  @Id(autoIncrement = false)
  private final long id;
  @Unique
  private final long uniqueVal;
  @Nullable
  private final String string;

  public SimpleDataClassWithMethodsAndUnique(long id, long uniqueVal, @Nullable String string) {
    this.id = id;
    this.uniqueVal = uniqueVal;
    this.string = string;
  }

  public long getId() {
    return id;
  }

  public long getUniqueVal() {
    return uniqueVal;
  }

  @Nullable
  public String getString() {
    return string;
  }

  @NonNull
  @CheckResult
  public SimpleDataClassWithMethodsAndUnique setId(long id) {
    return new SimpleDataClassWithMethodsAndUnique(id, uniqueVal, string);
  }

  @NonNull
  @CheckResult
  public SimpleDataClassWithMethodsAndUnique setUniqueVal(long uniqueVal) {
    return new SimpleDataClassWithMethodsAndUnique(id, uniqueVal, string);
  }

  public static SimpleDataClassWithMethodsAndUnique newRandom() {
    final Random r = new Random();
    return new SimpleDataClassWithMethodsAndUnique(Math.abs(r.nextLong()), r.nextLong(), Utils.randomTableName());
  }

  public static SimpleDataClassWithMethodsAndUnique newRandomWithId(long id) {
    final Random r = new Random();
    return new SimpleDataClassWithMethodsAndUnique(id, r.nextLong(), Utils.randomTableName());
  }

  public SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.BulkInsertBuilder insert(Iterable<SimpleDataClassWithMethodsAndUnique> o) {
    return SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.BulkUpdateBuilder update(Iterable<SimpleDataClassWithMethodsAndUnique> o) {
    return SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.BulkPersistBuilder persist(Iterable<SimpleDataClassWithMethodsAndUnique> o) {
    return SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.BulkDeleteBuilder delete(Collection<SimpleDataClassWithMethodsAndUnique> o) {
    return SqliteMagic_SimpleDataClassWithMethodsAndUnique_Handler.BulkDeleteBuilder.create(o);
  }
}
