package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_ComplexImmutableCreatorWithUnique_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;
import com.siimkinks.sqlitemagic.model.SimpleMutableWithUnique;

import java.util.Collection;
import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class ComplexImmutableCreatorWithUnique {

  ComplexImmutableCreatorWithUnique() {
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
  public static ComplexImmutableCreatorWithUnique newRandom() {
    final Random r = new Random();
    return new AutoValue_ComplexImmutableCreatorWithUnique(
        r.nextLong(),
        r.nextLong(),
        Utils.randomTableName(),
        SimpleMutableWithUnique.newRandom(),
        SimpleMutableWithUnique.newRandom()
    );
  }

  @NonNull
  @CheckResult
  public static ComplexImmutableCreatorWithUnique create(long id,
                                                         long uniqueVal,
                                                         @Nullable String string,
                                                         @Nullable SimpleMutableWithUnique complexVal1,
                                                         @NonNull SimpleMutableWithUnique complexVal2) {
    return new AutoValue_ComplexImmutableCreatorWithUnique(
        id,
        uniqueVal,
        string,
        complexVal1,
        complexVal2
    );
  }

  public SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.BulkInsertBuilder insert(Iterable<ComplexImmutableCreatorWithUnique> o) {
    return SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.BulkUpdateBuilder update(Iterable<ComplexImmutableCreatorWithUnique> o) {
    return SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.BulkPersistBuilder persist(Iterable<ComplexImmutableCreatorWithUnique> o) {
    return SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.BulkDeleteBuilder delete(Collection<ComplexImmutableCreatorWithUnique> o) {
    return SqliteMagic_ComplexImmutableCreatorWithUnique_Handler.BulkDeleteBuilder.create(o);
  }
}