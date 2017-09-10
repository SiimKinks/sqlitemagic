package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.TransformableObject;

import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Table(persistAll = true, useAccessMethods = true)
public final class DataClassWithMethods implements ImmutableEquals {
  @Id
  @Nullable
  private final Long id;
  @NonNull
  private final String stringValue;
  private final boolean aBoolean;
  private final int integer;
  @NonNull
  private final TransformableObject transformableObject;

  public DataClassWithMethods(@Nullable Long id,
                              @NonNull String stringValue,
                              boolean aBoolean, int integer,
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

  @NonNull
  public final String getStringValue() {
    return this.stringValue;
  }

  public final boolean getABoolean() {
    return this.aBoolean;
  }

  public final int getInteger() {
    return this.integer;
  }

  @NonNull
  public final TransformableObject getTransformableObject() {
    return this.transformableObject;
  }

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
}
