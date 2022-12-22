package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_ComplexImmutableBuilderWithUnique_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;
import com.siimkinks.sqlitemagic.model.SimpleMutableWithUnique;

import java.util.Collection;
import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class ComplexImmutableBuilderWithUnique {

  ComplexImmutableBuilderWithUnique() {
  }

  @Id(autoIncrement = false)
  public abstract long id();

  @Unique
  public abstract long uniqueVal();

  @Nullable
  public abstract String string();

  @Nullable
  public abstract SimpleMutableWithUnique complexVal();

  @Unique
  @NonNull
  public abstract SimpleMutableWithUnique complexVal2();

  @NonNull
  @CheckResult
  public static Builder builder() {
    return new AutoValue_ComplexImmutableBuilderWithUnique.Builder();
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

    public abstract Builder complexVal(@Nullable SimpleMutableWithUnique complexVal);

    public abstract Builder complexVal2(@NonNull SimpleMutableWithUnique complexVal2);

    @CheckResult
    public abstract ComplexImmutableBuilderWithUnique build();
  }

  public static ComplexImmutableBuilderWithUnique newRandom() {
    final Random r = new Random();
    return builder()
        .id(r.nextLong())
        .uniqueVal(r.nextLong())
        .string(Utils.randomTableName())
        .complexVal(SimpleMutableWithUnique.newRandom())
        .complexVal2(SimpleMutableWithUnique.newRandom())
        .build();
  }

  public SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.BulkInsertBuilder insert(Iterable<ComplexImmutableBuilderWithUnique> o) {
    return SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.BulkUpdateBuilder update(Iterable<ComplexImmutableBuilderWithUnique> o) {
    return SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.BulkPersistBuilder persist(Iterable<ComplexImmutableBuilderWithUnique> o) {
    return SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.BulkDeleteBuilder delete(Collection<ComplexImmutableBuilderWithUnique> o) {
    return SqliteMagic_ComplexImmutableBuilderWithUnique_Handler.BulkDeleteBuilder.create(o);
  }
}