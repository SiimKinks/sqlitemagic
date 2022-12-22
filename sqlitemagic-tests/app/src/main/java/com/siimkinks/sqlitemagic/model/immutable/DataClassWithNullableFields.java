package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_DataClassWithNullableFields_Handler;
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
public final class DataClassWithNullableFields implements ImmutableEquals {
  @Id
  @Nullable
  public final Long id;
  @Nullable
  public final String stringValue;
  @Nullable
  public final Boolean aBoolean;
  public final int integer;
  @NonNull
  public final TransformableObject transformableObject;

  public DataClassWithNullableFields(@Nullable Long id,
                                     @Nullable String stringValue,
                                     @Nullable Boolean aBoolean,
                                     int integer,
                                     @NonNull TransformableObject transformableObject) {
    this.id = id;
    this.stringValue = stringValue;
    this.aBoolean = aBoolean;
    this.integer = integer;
    this.transformableObject = transformableObject;
  }

  public static DataClassWithNullableFields newRandom() {
    return newRandom(null);
  }

  public static DataClassWithNullableFields newRandom(Long id) {
    final Random random = new Random();
    return new DataClassWithNullableFields(
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
    if (o instanceof DataClassWithNullableFields) {
      DataClassWithNullableFields that = (DataClassWithNullableFields) o;
      return ((this.stringValue == null) ? (that.stringValue == null) : this.stringValue.equals(that.stringValue))
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

  public SqliteMagic_DataClassWithNullableFields_Handler.InsertBuilder insert() {
    return SqliteMagic_DataClassWithNullableFields_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_DataClassWithNullableFields_Handler.UpdateBuilder update() {
    return SqliteMagic_DataClassWithNullableFields_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_DataClassWithNullableFields_Handler.PersistBuilder persist() {
    return SqliteMagic_DataClassWithNullableFields_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_DataClassWithNullableFields_Handler.DeleteBuilder delete() {
    return SqliteMagic_DataClassWithNullableFields_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_DataClassWithNullableFields_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_DataClassWithNullableFields_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_DataClassWithNullableFields_Handler.BulkInsertBuilder insert(Iterable<DataClassWithNullableFields> o) {
    return SqliteMagic_DataClassWithNullableFields_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithNullableFields_Handler.BulkUpdateBuilder update(Iterable<DataClassWithNullableFields> o) {
    return SqliteMagic_DataClassWithNullableFields_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithNullableFields_Handler.BulkPersistBuilder persist(Iterable<DataClassWithNullableFields> o) {
    return SqliteMagic_DataClassWithNullableFields_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithNullableFields_Handler.BulkDeleteBuilder delete(Collection<DataClassWithNullableFields> o) {
    return SqliteMagic_DataClassWithNullableFields_Handler.BulkDeleteBuilder.create(o);
  }
}
