package com.siimkinks.sqlitemagic.model;

import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler;
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
public class SimpleMutableWithUniqueAndNullableId {
  @Id
  @Nullable
  Long id;
  @Unique
  long uniqueVal;
  @Nullable
  String string;

  public long getUniqueVal() {
    return uniqueVal;
  }

  public void setUniqueVal(long uniqueVal) {
    this.uniqueVal = uniqueVal;
  }

  public static SimpleMutableWithUniqueAndNullableId newRandom() {
    final SimpleMutableWithUniqueAndNullableId val = new SimpleMutableWithUniqueAndNullableId();
    fillWithRandomValues(val);
    return val;
  }

  public static void fillWithRandomValues(SimpleMutableWithUniqueAndNullableId val) {
    final Random r = new Random();
    val.uniqueVal = r.nextLong();
    val.string = Utils.randomTableName();
  }

  public SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.BulkInsertBuilder insert(Iterable<SimpleMutableWithUniqueAndNullableId> o) {
    return SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.BulkUpdateBuilder update(Iterable<SimpleMutableWithUniqueAndNullableId> o) {
    return SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.BulkPersistBuilder persist(Iterable<SimpleMutableWithUniqueAndNullableId> o) {
    return SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.BulkDeleteBuilder delete(Collection<SimpleMutableWithUniqueAndNullableId> o) {
    return SqliteMagic_SimpleMutableWithUniqueAndNullableId_Handler.BulkDeleteBuilder.create(o);
  }
}
