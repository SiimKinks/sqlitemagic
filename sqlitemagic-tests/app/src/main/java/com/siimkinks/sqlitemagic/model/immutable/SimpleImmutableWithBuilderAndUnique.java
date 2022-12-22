package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.Collection;
import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleImmutableWithBuilderAndUnique {

  SimpleImmutableWithBuilderAndUnique() {
  }

  @Id(autoIncrement = false)
  public abstract long id();

  @Unique
  public abstract long uniqueVal();

  @Nullable
  public abstract String string();

  @NonNull
  @CheckResult
  public static Builder builder() {
    return new AutoValue_SimpleImmutableWithBuilderAndUnique.Builder();
  }

  @NonNull
  @CheckResult
  @IgnoreColumn
  public abstract Builder copy();

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder id(long id);

    public abstract Builder uniqueVal(long uniqueVal);

    public abstract Builder string(@Nullable String string);

    @CheckResult
    public abstract SimpleImmutableWithBuilderAndUnique build();
  }

  public static SimpleImmutableWithBuilderAndUnique newRandom() {
    final Random r = new Random();
    return builder()
        .id(Math.abs(r.nextLong()))
        .uniqueVal(r.nextLong())
        .string(Utils.randomTableName())
        .build();
  }

  public static SimpleImmutableWithBuilderAndUnique newRandomWithId(long id) {
    final Random r = new Random();
    return builder()
        .id(id)
        .uniqueVal(r.nextLong())
        .string(Utils.randomTableName())
        .build();
  }

  public SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.BulkInsertBuilder insert(Iterable<SimpleImmutableWithBuilderAndUnique> o) {
    return SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.BulkUpdateBuilder update(Iterable<SimpleImmutableWithBuilderAndUnique> o) {
    return SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.BulkPersistBuilder persist(Iterable<SimpleImmutableWithBuilderAndUnique> o) {
    return SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.BulkDeleteBuilder delete(Collection<SimpleImmutableWithBuilderAndUnique> o) {
    return SqliteMagic_SimpleImmutableWithBuilderAndUnique_Handler.BulkDeleteBuilder.create(o);
  }
}