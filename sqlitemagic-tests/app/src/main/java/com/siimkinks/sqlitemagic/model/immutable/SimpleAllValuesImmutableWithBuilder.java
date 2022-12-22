package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;

@AutoValue
@Table(persistAll = true)
public abstract class SimpleAllValuesImmutableWithBuilder {
  public static final String TABLE = "simple_all_values_immutable_with_builder";
  public static final String C_ID = "simple_all_values_immutable_with_builder.id";

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

  public SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.BulkInsertBuilder insert(Iterable<SimpleAllValuesImmutableWithBuilder> o) {
    return SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.BulkUpdateBuilder update(Iterable<SimpleAllValuesImmutableWithBuilder> o) {
    return SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.BulkPersistBuilder persist(Iterable<SimpleAllValuesImmutableWithBuilder> o) {
    return SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.BulkDeleteBuilder delete(Collection<SimpleAllValuesImmutableWithBuilder> o) {
    return SqliteMagic_SimpleAllValuesImmutableWithBuilder_Handler.BulkDeleteBuilder.create(o);
  }
}