package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.SqliteMagic_SimpleAllPrimitiveBuilder_Handler;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Collection;
import java.util.Random;

@AutoValue
@Table(persistAll = true)
public abstract class SimpleAllPrimitiveBuilder {
  @Id
  @Nullable
  public abstract Long id();

  public abstract boolean bool();

  public abstract byte aByte();

  public abstract short aShort();

  public abstract int integer();

  public abstract long aLong();

  public abstract float aFloat();

  public abstract double aDouble();

  public static Builder builder() {
    return new AutoValue_SimpleAllPrimitiveBuilder.Builder()
        .bool(false)
        .aByte((byte) 0)
        .aShort((short) 0)
        .integer(0)
        .aLong(0L)
        .aFloat(0.0f)
        .aDouble(0.0d);
  }

  public static SimpleAllPrimitiveBuilder newRandom() {
    final Random r = new Random();
    return builder()
        .bool(r.nextBoolean())
        .aByte((byte) r.nextInt())
        .aShort((short) r.nextInt())
        .integer(r.nextInt())
        .aLong(r.nextLong())
        .aFloat(r.nextFloat())
        .aDouble(r.nextDouble())
        .build();
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder id(@Nullable Long id);

    public abstract Builder bool(boolean bool);

    public abstract Builder aByte(byte aByte);

    public abstract Builder aShort(short aShort);

    public abstract Builder integer(int integer);

    public abstract Builder aLong(long aLong);

    public abstract Builder aFloat(float aFloat);

    public abstract Builder aDouble(double aDouble);

    public abstract SimpleAllPrimitiveBuilder build();
  }

  public SqliteMagic_SimpleAllPrimitiveBuilder_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleAllPrimitiveBuilder_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleAllPrimitiveBuilder_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleAllPrimitiveBuilder_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleAllPrimitiveBuilder_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleAllPrimitiveBuilder_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleAllPrimitiveBuilder_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleAllPrimitiveBuilder_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleAllPrimitiveBuilder_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleAllPrimitiveBuilder_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleAllPrimitiveBuilder_Handler.BulkInsertBuilder insert(Iterable<SimpleAllPrimitiveBuilder> o) {
    return SqliteMagic_SimpleAllPrimitiveBuilder_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleAllPrimitiveBuilder_Handler.BulkUpdateBuilder update(Iterable<SimpleAllPrimitiveBuilder> o) {
    return SqliteMagic_SimpleAllPrimitiveBuilder_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleAllPrimitiveBuilder_Handler.BulkPersistBuilder persist(Iterable<SimpleAllPrimitiveBuilder> o) {
    return SqliteMagic_SimpleAllPrimitiveBuilder_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleAllPrimitiveBuilder_Handler.BulkDeleteBuilder delete(Collection<SimpleAllPrimitiveBuilder> o) {
    return SqliteMagic_SimpleAllPrimitiveBuilder_Handler.BulkDeleteBuilder.create(o);
  }
}