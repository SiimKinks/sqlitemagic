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

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Table(persistAll = true, useAccessMethods = true)
public final class ComplexDataClassWithMethodsAndUnique {
  @Id(autoIncrement = false)
  private final long id;

  @Unique
  private final long uniqueVal;

  private final String string;

  private final SimpleMutableWithUnique complexVal;

  @Unique
  @NonNull
  private final SimpleMutableWithUnique complexVal2;

  public ComplexDataClassWithMethodsAndUnique(long id,
                                              long uniqueVal,
                                              String string,
                                              SimpleMutableWithUnique complexVal,
                                              SimpleMutableWithUnique complexVal2) {
    this.id = id;
    this.uniqueVal = uniqueVal;
    this.string = string;
    this.complexVal = complexVal;
    this.complexVal2 = complexVal2;
  }

  public long getId() {
    return id;
  }

  public long getUniqueVal() {
    return uniqueVal;
  }

  public String getString() {
    return string;
  }

  public SimpleMutableWithUnique getComplexVal() {
    return complexVal;
  }

  public SimpleMutableWithUnique getComplexVal2() {
    return complexVal2;
  }

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
  public static ComplexDataClassWithMethodsAndUnique create(long id,
                                                            long uniqueVal,
                                                            @Nullable String string,
                                                            @Nullable SimpleMutableWithUnique complexVal1,
                                                            @NonNull SimpleMutableWithUnique complexVal2) {
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
