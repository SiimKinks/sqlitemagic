package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.siimkinks.sqlitemagic.SqliteMagic_DataClassWithNullableMethods_Handler;
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
@Table(persistAll = true, useAccessMethods = true)
public final class DataClassWithNullableMethods implements ImmutableEquals {
  @Id
  @Nullable
  private final Long id;
  @Nullable
  private final String stringValue;
  @Nullable
  private final Boolean aBoolean;
  private final int integer;
  @NonNull
  private final TransformableObject transformableObject;

  public DataClassWithNullableMethods(@Nullable Long id,
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

  @Nullable
  public final Long getId() {
    return this.id;
  }

  @Nullable
  public final String getStringValue() {
    return this.stringValue;
  }

  @Nullable
  public final Boolean getABoolean() {
    return this.aBoolean;
  }

  public final int getInteger() {
    return this.integer;
  }

  @NonNull
  public final TransformableObject getTransformableObject() {
    return this.transformableObject;
  }

  public static DataClassWithNullableMethods newRandom() {
    return newRandom(null);
  }

  public static DataClassWithNullableMethods newRandom(Long id) {
    final Random random = new Random();
    return new DataClassWithNullableMethods(
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
    if (o instanceof DataClassWithNullableMethods) {
      DataClassWithNullableMethods that = (DataClassWithNullableMethods) o;
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

  public SqliteMagic_DataClassWithNullableMethods_Handler.InsertBuilder insert() {
    return SqliteMagic_DataClassWithNullableMethods_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_DataClassWithNullableMethods_Handler.UpdateBuilder update() {
    return SqliteMagic_DataClassWithNullableMethods_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_DataClassWithNullableMethods_Handler.PersistBuilder persist() {
    return SqliteMagic_DataClassWithNullableMethods_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_DataClassWithNullableMethods_Handler.DeleteBuilder delete() {
    return SqliteMagic_DataClassWithNullableMethods_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_DataClassWithNullableMethods_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_DataClassWithNullableMethods_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_DataClassWithNullableMethods_Handler.BulkInsertBuilder insert(Iterable<DataClassWithNullableMethods> o) {
    return SqliteMagic_DataClassWithNullableMethods_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithNullableMethods_Handler.BulkUpdateBuilder update(Iterable<DataClassWithNullableMethods> o) {
    return SqliteMagic_DataClassWithNullableMethods_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithNullableMethods_Handler.BulkPersistBuilder persist(Iterable<DataClassWithNullableMethods> o) {
    return SqliteMagic_DataClassWithNullableMethods_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_DataClassWithNullableMethods_Handler.BulkDeleteBuilder delete(Collection<DataClassWithNullableMethods> o) {
    return SqliteMagic_DataClassWithNullableMethods_Handler.BulkDeleteBuilder.create(o);
  }
}
