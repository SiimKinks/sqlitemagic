package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_DataClassWithMethods_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.TransformableObject;

import java.util.Collection;
import java.util.Random;

@Table(persistAll = true, useAccessMethods = true)
public record DataClassWithMethods(
    @Id @Nullable Long id,
    @NonNull String stringValue,
    boolean aBoolean,
    int integer,
    @NonNull TransformableObject transformableObject
) implements ImmutableEquals {
  public static DataClassWithMethods newRandom() {
    return newRandom(null);
  }

  public static DataClassWithMethods newRandom(Long id) {
    final Random random = new Random();
    return new DataClassWithMethods(
        id == null ? 0L : id,
        Utils.randomTableName(),
        random.nextBoolean(),
        random.nextInt(),
        new TransformableObject(random.nextInt())
    );
  }

  public boolean equalsWithoutId(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof DataClassWithMethods) {
      DataClassWithMethods that = (DataClassWithMethods) o;
      return (this.stringValue.equals(that.stringValue))
          && (this.aBoolean == that.aBoolean)
          && (this.integer == that.integer)
          && (this.transformableObject.equals(that.transformableObject));
    }
    return false;
  }

  @Override
  public Long provideId() {
    return id;
  }

  public SqliteMagic_DataClassWithMethods_Handler.InsertBuilder insert() {
    return SqliteMagic_DataClassWithMethods_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_DataClassWithMethods_Handler.UpdateBuilder update() {
    return SqliteMagic_DataClassWithMethods_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_DataClassWithMethods_Handler.PersistBuilder persist() {
    return SqliteMagic_DataClassWithMethods_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_DataClassWithMethods_Handler.DeleteBuilder delete() {
    return SqliteMagic_DataClassWithMethods_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_DataClassWithMethods_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_DataClassWithMethods_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_DataClassWithMethods_Handler.BulkInsertBuilder insert(Iterable<DataClassWithMethods> o) {
    return SqliteMagic_DataClassWithMethods_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithMethods_Handler.BulkUpdateBuilder update(Iterable<DataClassWithMethods> o) {
    return SqliteMagic_DataClassWithMethods_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithMethods_Handler.BulkPersistBuilder persist(Iterable<DataClassWithMethods> o) {
    return SqliteMagic_DataClassWithMethods_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithMethods_Handler.BulkDeleteBuilder delete(Collection<DataClassWithMethods> o) {
    return SqliteMagic_DataClassWithMethods_Handler.BulkDeleteBuilder.create(o);
  }
}
