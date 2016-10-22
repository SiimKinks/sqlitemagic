package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Calendar;
import java.util.Date;

@AutoValue
@Table(persistAll = true)
public abstract class SimpleAllValuesImmutableWithBuilder {
  public static final String TABLE = "simpleallvaluesimmutablewithbuilder";
  public static final String C_ID = "simpleallvaluesimmutablewithbuilder.id";

  @Id
  @Nullable
  abstract Long id();

  @Nullable
  abstract String string();

  abstract short primitiveShort();

  @Nullable
  abstract Short boxedShort();

  abstract long primitiveLong();

  @Nullable
  abstract Long boxedLong();

  abstract int primitiveInt();

  @Nullable
  abstract Integer boxedInteger();

  abstract float primitiveFloat();

  @Nullable
  abstract Float boxedFloat();

  abstract double primitiveDouble();

  @Nullable
  abstract Double boxedDouble();

  abstract byte primitiveByte();

  @Nullable
  abstract Byte boxedByte();

  @SuppressWarnings("mutable")
  abstract byte[] primitiveByteArray();

  abstract boolean primitiveBoolean();

  @Nullable
  abstract Boolean boxedBoolean();

  @Nullable
  abstract Calendar calendar();

  @Nullable
  abstract Date utilDate();

  public static Builder builder() {
    return new AutoValue_SimpleAllValuesImmutableWithBuilder.Builder();
  }

  @AutoValue.Builder
  public static abstract class Builder {
    abstract Builder id(@Nullable Long id);

    abstract Builder string(String string);

    abstract Builder primitiveShort(short aShort);

    abstract Builder boxedShort(Short boxedShort);

    abstract Builder primitiveLong(long aLong);

    abstract Builder boxedLong(Long aLong);

    abstract Builder primitiveInt(int a);

    abstract Builder boxedInteger(Integer a);

    abstract Builder primitiveFloat(float a);

    abstract Builder boxedFloat(Float a);

    abstract Builder primitiveDouble(double a);

    abstract Builder boxedDouble(Double a);

    abstract Builder primitiveByte(byte a);

    abstract Builder boxedByte(Byte a);

    abstract Builder primitiveByteArray(byte[] bytes);

    abstract Builder primitiveBoolean(boolean a);

    abstract Builder boxedBoolean(Boolean a);

    abstract Builder calendar(Calendar calendar);

    abstract Builder utilDate(Date date);

    public abstract SimpleAllValuesImmutableWithBuilder build();
  }
}