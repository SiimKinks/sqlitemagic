package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler;
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
@Table(persistAll = true)
public final class ComplexDataClassWithFieldsAndUnique {
  @Id(autoIncrement = false)
  public final long id;

  @Unique
  public final long uniqueVal;

  public final String string;

  public final SimpleMutableWithUnique complexVal;

  @Unique
  @NonNull
  public final SimpleMutableWithUnique complexVal2;

  public ComplexDataClassWithFieldsAndUnique(long id,
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

  @NonNull
  @CheckResult
  public static ComplexDataClassWithFieldsAndUnique newRandom() {
    final Random r = new Random();
    return new ComplexDataClassWithFieldsAndUnique(
        r.nextLong(),
        r.nextLong(),
        Utils.randomTableName(),
        SimpleMutableWithUnique.newRandom(),
        SimpleMutableWithUnique.newRandom()
    );
  }

  @NonNull
  @CheckResult
  public static ComplexDataClassWithFieldsAndUnique create(long id,
                                                           long uniqueVal,
                                                           @Nullable String string,
                                                           @Nullable SimpleMutableWithUnique complexVal1,
                                                           @NonNull SimpleMutableWithUnique complexVal2) {
    return new ComplexDataClassWithFieldsAndUnique(
        id,
        uniqueVal,
        string,
        complexVal1,
        complexVal2
    );
  }

  public SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.BulkInsertBuilder insert(Iterable<ComplexDataClassWithFieldsAndUnique> o) {
    return SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.BulkUpdateBuilder update(Iterable<ComplexDataClassWithFieldsAndUnique> o) {
    return SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.BulkPersistBuilder persist(Iterable<ComplexDataClassWithFieldsAndUnique> o) {
    return SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.BulkDeleteBuilder delete(Collection<ComplexDataClassWithFieldsAndUnique> o) {
    return SqliteMagic_ComplexDataClassWithFieldsAndUnique_Handler.BulkDeleteBuilder.create(o);
  }
}
