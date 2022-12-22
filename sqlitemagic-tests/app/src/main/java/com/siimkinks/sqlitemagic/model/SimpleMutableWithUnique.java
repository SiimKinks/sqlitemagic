package com.siimkinks.sqlitemagic.model;

import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_SimpleMutableWithUnique_Handler;
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
public class SimpleMutableWithUnique {
  @Id(autoIncrement = false)
  long id;
  @Unique
  long uniqueVal;
  @Nullable
  String string;

  public long getId() {
    return id;
  }

  @Nullable
  public String getString() {
    return string;
  }

  public void setString(@Nullable String string) {
    this.string = string;
  }

  public void setUniqueVal(long uniqueVal) {
    this.uniqueVal = uniqueVal;
  }

  public static SimpleMutableWithUnique newRandom() {
    final SimpleMutableWithUnique val = new SimpleMutableWithUnique();
    fillWithRandomValues(val);
    return val;
  }

  public static void fillWithRandomValues(SimpleMutableWithUnique val) {
    final Random r = new Random();
    val.id = Math.abs(r.nextLong());
    val.uniqueVal = r.nextLong();
    val.string = Utils.randomTableName();
  }

  public SqliteMagic_SimpleMutableWithUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleMutableWithUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleMutableWithUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleMutableWithUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleMutableWithUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleMutableWithUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleMutableWithUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleMutableWithUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleMutableWithUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleMutableWithUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleMutableWithUnique_Handler.BulkInsertBuilder insert(Iterable<SimpleMutableWithUnique> o) {
    return SqliteMagic_SimpleMutableWithUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutableWithUnique_Handler.BulkUpdateBuilder update(Iterable<SimpleMutableWithUnique> o) {
    return SqliteMagic_SimpleMutableWithUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutableWithUnique_Handler.BulkPersistBuilder persist(Iterable<SimpleMutableWithUnique> o) {
    return SqliteMagic_SimpleMutableWithUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleMutableWithUnique_Handler.BulkDeleteBuilder delete(Collection<SimpleMutableWithUnique> o) {
    return SqliteMagic_SimpleMutableWithUnique_Handler.BulkDeleteBuilder.create(o);
  }
}
