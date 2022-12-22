package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.Collection;
import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleImmutableWithCreatorAndUnique {
  SimpleImmutableWithCreatorAndUnique() {
  }

  @Id(autoIncrement = false)
  public abstract long id();

  @Unique
  public abstract long uniqueVal();

  @Nullable
  public abstract String string();

  @NonNull
  @CheckResult
  public static SimpleImmutableWithCreatorAndUnique create(long id, long uniqueVal, @Nullable String string) {
    return new AutoValue_SimpleImmutableWithCreatorAndUnique(id, uniqueVal, string);
  }

  @NonNull
  @CheckResult
  public SimpleImmutableWithCreatorAndUnique setId(long id) {
    return create(id, uniqueVal(), string());
  }

  @NonNull
  @CheckResult
  public SimpleImmutableWithCreatorAndUnique setUniqueVal(long uniqueVal) {
    return create(id(), uniqueVal, string());
  }

  public static SimpleImmutableWithCreatorAndUnique newRandom() {
    final Random r = new Random();
    return create(Math.abs(r.nextLong()), r.nextLong(), Utils.randomTableName());
  }

  public static SimpleImmutableWithCreatorAndUnique newRandomWithId(long id) {
    final Random r = new Random();
    return create(id, r.nextLong(), Utils.randomTableName());
  }

  public SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.BulkInsertBuilder insert(Iterable<SimpleImmutableWithCreatorAndUnique> o) {
    return SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.BulkUpdateBuilder update(Iterable<SimpleImmutableWithCreatorAndUnique> o) {
    return SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.BulkPersistBuilder persist(Iterable<SimpleImmutableWithCreatorAndUnique> o) {
    return SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.BulkDeleteBuilder delete(Collection<SimpleImmutableWithCreatorAndUnique> o) {
    return SqliteMagic_SimpleImmutableWithCreatorAndUnique_Handler.BulkDeleteBuilder.create(o);
  }
}