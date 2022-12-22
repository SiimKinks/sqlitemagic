package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_DataClassWithFields_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.TransformableObject;

import java.util.Collection;
import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Table(persistAll = true)
public final class DataClassWithFields implements ImmutableEquals {
  @Id
  @Nullable
  public final Long id;
  @NonNull
  public final String stringValue;
  public final boolean aBoolean;
  public final int integer;
  @NonNull
  public final TransformableObject transformableObject;

  public DataClassWithFields(@Nullable Long id,
                             @NonNull String stringValue,
                             boolean aBoolean,
                             int integer,
                             @NonNull TransformableObject transformableObject) {
    this.id = id;
    this.stringValue = stringValue;
    this.aBoolean = aBoolean;
    this.integer = integer;
    this.transformableObject = transformableObject;
  }

  public static DataClassWithFields newRandom() {
    return newRandom(null);
  }

  public static DataClassWithFields newRandom(Long id) {
    final Random random = new Random();
    return new DataClassWithFields(
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
    if (o instanceof DataClassWithFields) {
      DataClassWithFields that = (DataClassWithFields) o;
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

  public SqliteMagic_DataClassWithFields_Handler.InsertBuilder insert() {
    return SqliteMagic_DataClassWithFields_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_DataClassWithFields_Handler.UpdateBuilder update() {
    return SqliteMagic_DataClassWithFields_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_DataClassWithFields_Handler.PersistBuilder persist() {
    return SqliteMagic_DataClassWithFields_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_DataClassWithFields_Handler.DeleteBuilder delete() {
    return SqliteMagic_DataClassWithFields_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_DataClassWithFields_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_DataClassWithFields_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_DataClassWithFields_Handler.BulkInsertBuilder insert(Iterable<DataClassWithFields> o) {
    return SqliteMagic_DataClassWithFields_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithFields_Handler.BulkUpdateBuilder update(Iterable<DataClassWithFields> o) {
    return SqliteMagic_DataClassWithFields_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithFields_Handler.BulkPersistBuilder persist(Iterable<DataClassWithFields> o) {
    return SqliteMagic_DataClassWithFields_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithFields_Handler.BulkDeleteBuilder delete(Collection<DataClassWithFields> o) {
    return SqliteMagic_DataClassWithFields_Handler.BulkDeleteBuilder.create(o);
  }
}
