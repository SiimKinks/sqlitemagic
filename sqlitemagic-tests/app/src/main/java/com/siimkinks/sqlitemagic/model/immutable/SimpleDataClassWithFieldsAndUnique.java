package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler;
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
@Table(persistAll = true)
public final class SimpleDataClassWithFieldsAndUnique {
  @Id(autoIncrement = false)
  public final long id;
  @Unique
  public final long uniqueVal;
  @Nullable
  public final String string;

  public SimpleDataClassWithFieldsAndUnique(long id, long uniqueVal, @Nullable String string) {
    this.id = id;
    this.uniqueVal = uniqueVal;
    this.string = string;
  }

  @NonNull
  @CheckResult
  public SimpleDataClassWithFieldsAndUnique setId(long id) {
    return new SimpleDataClassWithFieldsAndUnique(id, uniqueVal, string);
  }

  @NonNull
  @CheckResult
  public SimpleDataClassWithFieldsAndUnique setUniqueVal(long uniqueVal) {
    return new SimpleDataClassWithFieldsAndUnique(id, uniqueVal, string);
  }

  public static SimpleDataClassWithFieldsAndUnique newRandom() {
    final Random r = new Random();
    return new SimpleDataClassWithFieldsAndUnique(Math.abs(r.nextLong()), r.nextLong(), Utils.randomTableName());
  }

  public static SimpleDataClassWithFieldsAndUnique newRandomWithId(long id) {
    final Random r = new Random();
    return new SimpleDataClassWithFieldsAndUnique(id, r.nextLong(), Utils.randomTableName());
  }

  public SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.BulkInsertBuilder insert(Iterable<SimpleDataClassWithFieldsAndUnique> o) {
    return SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.BulkUpdateBuilder update(Iterable<SimpleDataClassWithFieldsAndUnique> o) {
    return SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.BulkPersistBuilder persist(Iterable<SimpleDataClassWithFieldsAndUnique> o) {
    return SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.BulkDeleteBuilder delete(Collection<SimpleDataClassWithFieldsAndUnique> o) {
    return SqliteMagic_SimpleDataClassWithFieldsAndUnique_Handler.BulkDeleteBuilder.create(o);
  }
}
