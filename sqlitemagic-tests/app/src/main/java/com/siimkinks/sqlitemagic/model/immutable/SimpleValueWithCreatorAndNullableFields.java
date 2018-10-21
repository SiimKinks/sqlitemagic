package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleValueWithCreatorAndNullableFields implements ImmutableEquals {
  public static final String TABLE = "simple_value_with_creator_and_nullable_fields";
  public static final String C_ID = "simple_value_with_creator_and_nullable_fields.id";
  public static final String C_STRING = "simple_value_with_creator_and_nullable_fields.string";
  public static final String C_BOXED_BOOLEAN = "simple_value_with_creator_and_nullable_fields.boxed_boolean";
  public static final String C_BOXED_INTEGER = "simple_value_with_creator_and_nullable_fields.boxed_integer";

  @Id
  @Nullable
  public abstract Long id();

  @Nullable
  public abstract String string();

  @Nullable
  public abstract Boolean boxedBoolean();

  @Nullable
  public abstract Integer boxedInteger();

  public static SimpleValueWithCreatorAndNullableFields createWithId(Long id,
                                                                     String string,
                                                                     Boolean boxedBoolean,
                                                                     Integer boxedInteger) {
    return new AutoValue_SimpleValueWithCreatorAndNullableFields(id, string, boxedBoolean, boxedInteger);
  }

  public static SimpleValueWithCreatorAndNullableFields create(String string,
                                                               Boolean boxedBoolean,
                                                               Integer boxedInteger) {
    return new AutoValue_SimpleValueWithCreatorAndNullableFields(null, string, boxedBoolean, boxedInteger);
  }

  public static SimpleValueWithCreatorAndNullableFields nullSomeColumns(Long id, Integer boxedInteger) {
    return new AutoValue_SimpleValueWithCreatorAndNullableFields(id, null, null, boxedInteger);
  }

  public static SimpleValueWithCreatorAndNullableFields newRandom() {
    final Random r = new Random();
    return createWithId(
        r.nextLong(),
        Utils.randomTableName(),
        r.nextBoolean(),
        r.nextInt()
    );
  }

  public static SimpleValueWithCreatorAndNullableFields newRandomWithId(Long id) {
    final Random r = new Random();
    return createWithId(
        id,
        Utils.randomTableName(),
        r.nextBoolean(),
        r.nextInt()
    );
  }

  public boolean equalsWithoutId(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof SimpleValueWithCreatorAndNullableFields) {
      SimpleValueWithCreatorAndNullableFields that = (SimpleValueWithCreatorAndNullableFields) o;
      return ((this.string() == null) ? (that.string() == null) : this.string().equals(that.string()))
          && ((this.boxedBoolean() == null) ? (that.boxedBoolean() == null) : this.boxedBoolean().equals(that.boxedBoolean()))
          && ((this.boxedInteger() == null) ? (that.boxedInteger() == null) : this.boxedInteger().equals(that.boxedInteger()));
    }
    return false;
  }

  @Override
  public Long provideId() {
    return id();
  }
}
