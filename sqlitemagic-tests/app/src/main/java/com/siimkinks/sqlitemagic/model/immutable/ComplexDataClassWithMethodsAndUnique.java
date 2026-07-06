package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;
import com.siimkinks.sqlitemagic.model.SimpleMutableWithUnique;

import java.util.Collection;
import java.util.Random;

@Table(persistAll = true, useAccessMethods = true)
public record ComplexDataClassWithMethodsAndUnique(
    @Id(autoIncrement = false) long id,
    @Unique long uniqueVal,
    String string,
    SimpleMutableWithUnique complexVal,
    @Unique @NonNull SimpleMutableWithUnique complexVal2
) {
  @NonNull
  @CheckResult
  public static ComplexDataClassWithMethodsAndUnique newRandom() {
    final Random r = new Random();
    return new ComplexDataClassWithMethodsAndUnique(
        r.nextLong(),
        r.nextLong(),
        Utils.randomTableName(),
        SimpleMutableWithUnique.newRandom(),
        SimpleMutableWithUnique.newRandom()
    );
  }

  @NonNull
  @CheckResult
  public static ComplexDataClassWithMethodsAndUnique create(
      long id,
      long uniqueVal,
      @Nullable String string,
      @Nullable SimpleMutableWithUnique complexVal1,
      @NonNull SimpleMutableWithUnique complexVal2
  ) {
    return new ComplexDataClassWithMethodsAndUnique(
        id,
        uniqueVal,
        string,
        complexVal1,
        complexVal2
    );
  }

  public SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.BulkInsertBuilder insert(Iterable<ComplexDataClassWithMethodsAndUnique> o) {
    return SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.BulkUpdateBuilder update(Iterable<ComplexDataClassWithMethodsAndUnique> o) {
    return SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.BulkPersistBuilder persist(Iterable<ComplexDataClassWithMethodsAndUnique> o) {
    return SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.BulkDeleteBuilder delete(Collection<ComplexDataClassWithMethodsAndUnique> o) {
    return SqliteMagic_ComplexDataClassWithMethodsAndUnique_Handler.BulkDeleteBuilder.create(o);
  }
}
