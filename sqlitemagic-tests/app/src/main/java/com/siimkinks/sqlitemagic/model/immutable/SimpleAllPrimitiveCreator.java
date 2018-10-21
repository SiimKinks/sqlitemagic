package com.siimkinks.sqlitemagic.model.immutable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Random;

@AutoValue
@Table(persistAll = true)
public abstract class SimpleAllPrimitiveCreator {
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

  public static SimpleAllPrimitiveCreator create() {
    return new AutoValue_SimpleAllPrimitiveCreator(
        0L,
        false,
        (byte) 0,
        (short) 0,
        0,
        0L,
        0.0f,
        0.0d
    );
  }

  public static SimpleAllPrimitiveCreator newRandom() {
    final Random r = new Random();
    return new AutoValue_SimpleAllPrimitiveCreator(
        0L,
        r.nextBoolean(),
        (byte) r.nextInt(),
        (short) r.nextInt(),
        r.nextInt(),
        r.nextLong(),
        r.nextFloat(),
        r.nextDouble()
    );
  }
}