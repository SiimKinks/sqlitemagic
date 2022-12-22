package com.siimkinks.sqlitemagic.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.Collection;
import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@NoArgsConstructor
@EqualsAndHashCode
@ToString
@Table(persistAll = true)
public class ComplexMutableWithUniqueAndNullableId {
  @Id
  @Nullable
  Long id;
  @Unique
  long uniqueVal;
  String string;
  SimpleMutableWithUniqueAndNullableId complexVal;
  @Unique
  @NonNull
  SimpleMutableWithUniqueAndNullableId complexVal2;

  public static ComplexMutableWithUniqueAndNullableId newRandom() {
    final ComplexMutableWithUniqueAndNullableId val = new ComplexMutableWithUniqueAndNullableId();
    fillWithRandomValues(val);
    return val;
  }

  public static void fillWithRandomValues(ComplexMutableWithUniqueAndNullableId val) {
    final Random r = new Random();
    val.id = Math.abs(r.nextLong());
    val.uniqueVal = r.nextLong();
    val.string = Utils.randomTableName();
    val.complexVal = SimpleMutableWithUniqueAndNullableId.newRandom();
    val.complexVal2 = SimpleMutableWithUniqueAndNullableId.newRandom();
  }

  public SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.InsertBuilder insert() {
    return SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.UpdateBuilder update() {
    return SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.PersistBuilder persist() {
    return SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.DeleteBuilder delete() {
    return SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.BulkInsertBuilder insert(Iterable<ComplexMutableWithUniqueAndNullableId> o) {
    return SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.BulkUpdateBuilder update(Iterable<ComplexMutableWithUniqueAndNullableId> o) {
    return SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.BulkPersistBuilder persist(Iterable<ComplexMutableWithUniqueAndNullableId> o) {
    return SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.BulkDeleteBuilder delete(Collection<ComplexMutableWithUniqueAndNullableId> o) {
    return SqliteMagic_ComplexMutableWithUniqueAndNullableId_Handler.BulkDeleteBuilder.create(o);
  }
}
