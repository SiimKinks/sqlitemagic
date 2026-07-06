package com.siimkinks.sqlitemagic.model;

import androidx.annotation.NonNull;

import com.siimkinks.sqlitemagic.SqliteMagic_ComplexMutableWithUnique_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;

import java.util.Collection;
import java.util.Objects;
import java.util.Random;

@Table(persistAll = true)
public class ComplexMutableWithUnique {
  @Id(autoIncrement = false)
  long id;
  @Unique
  long uniqueVal;
  String string;
  SimpleMutableWithUnique complexVal;
  @Unique
  @NonNull
  SimpleMutableWithUnique complexVal2;

  public ComplexMutableWithUnique() {
  }

  public static ComplexMutableWithUnique newRandom() {
    final ComplexMutableWithUnique val = new ComplexMutableWithUnique();
    fillWithRandomValues(val);
    return val;
  }

  public static void fillWithRandomValues(ComplexMutableWithUnique val) {
    final Random r = new Random();
    val.id = Math.abs(r.nextLong());
    val.uniqueVal = r.nextLong();
    val.string = Utils.randomTableName();
    val.complexVal = SimpleMutableWithUnique.newRandom();
    val.complexVal2 = SimpleMutableWithUnique.newRandom();
  }

  public SqliteMagic_ComplexMutableWithUnique_Handler.InsertBuilder insert() {
    return SqliteMagic_ComplexMutableWithUnique_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_ComplexMutableWithUnique_Handler.UpdateBuilder update() {
    return SqliteMagic_ComplexMutableWithUnique_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_ComplexMutableWithUnique_Handler.PersistBuilder persist() {
    return SqliteMagic_ComplexMutableWithUnique_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_ComplexMutableWithUnique_Handler.DeleteBuilder delete() {
    return SqliteMagic_ComplexMutableWithUnique_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_ComplexMutableWithUnique_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_ComplexMutableWithUnique_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_ComplexMutableWithUnique_Handler.BulkInsertBuilder insert(Iterable<ComplexMutableWithUnique> o) {
    return SqliteMagic_ComplexMutableWithUnique_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_ComplexMutableWithUnique_Handler.BulkUpdateBuilder update(Iterable<ComplexMutableWithUnique> o) {
    return SqliteMagic_ComplexMutableWithUnique_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_ComplexMutableWithUnique_Handler.BulkPersistBuilder persist(Iterable<ComplexMutableWithUnique> o) {
    return SqliteMagic_ComplexMutableWithUnique_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_ComplexMutableWithUnique_Handler.BulkDeleteBuilder delete(Collection<ComplexMutableWithUnique> o) {
    return SqliteMagic_ComplexMutableWithUnique_Handler.BulkDeleteBuilder.create(o);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ComplexMutableWithUnique that = (ComplexMutableWithUnique) o;
    return id == that.id &&
        uniqueVal == that.uniqueVal &&
        Objects.equals(string, that.string) &&
        Objects.equals(complexVal, that.complexVal) &&
        Objects.equals(complexVal2, that.complexVal2);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, uniqueVal, string, complexVal, complexVal2);
  }

  @Override
  public String toString() {
    return "ComplexMutableWithUnique{" +
        "id=" + id +
        ", uniqueVal=" + uniqueVal +
        ", string='" + string + '\'' +
        ", complexVal=" + complexVal +
        ", complexVal2=" + complexVal2 +
        '}';
  }
}
