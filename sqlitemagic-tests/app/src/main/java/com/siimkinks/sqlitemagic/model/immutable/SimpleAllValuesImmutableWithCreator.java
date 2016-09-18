package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Calendar;
import java.util.Date;

@AutoValue
@Table(persistAll = true)
public abstract class SimpleAllValuesImmutableWithCreator {
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

	public static SimpleAllValuesImmutableWithCreator createWithId(long id,
			String string,
	                                                         short primitiveShort,
	                                                         Short boxedShort,
	                                                         long primitiveLong,
	                                                         Long boxedLong,
	                                                         int primitiveInt,
	                                                         Integer boxedInteger,
	                                                         float primitiveFloat,
	                                                         Float boxedFloat,
	                                                         double primitiveDouble,
	                                                         Double boxedDouble,
	                                                         byte primitiveByte,
	                                                         Byte boxedByte,
	                                                         byte[] primitiveByteArray,
	                                                         boolean primitiveBoolean,
	                                                         Boolean boxedBoolean,
	                                                         Calendar calendar,
	                                                         java.util.Date utilDate) {
		return new AutoValue_SimpleAllValuesImmutableWithCreator(id, string, primitiveShort, boxedShort, primitiveLong, boxedLong, primitiveInt, boxedInteger, primitiveFloat, boxedFloat, primitiveDouble, boxedDouble, primitiveByte, boxedByte, primitiveByteArray, primitiveBoolean, boxedBoolean, calendar, utilDate);
	}

	public static SimpleAllValuesImmutableWithCreator create(String string,
	                                                         short primitiveShort,
	                                                         Short boxedShort,
	                                                         long primitiveLong,
	                                                         Long boxedLong,
	                                                         int primitiveInt,
	                                                         Integer boxedInteger,
	                                                         float primitiveFloat,
	                                                         Float boxedFloat,
	                                                         double primitiveDouble,
	                                                         Double boxedDouble,
	                                                         byte primitiveByte,
	                                                         Byte boxedByte,
	                                                         byte[] primitiveByteArray,
	                                                         boolean primitiveBoolean,
	                                                         Boolean boxedBoolean,
	                                                         Calendar calendar,
	                                                         java.util.Date utilDate) {
		return new AutoValue_SimpleAllValuesImmutableWithCreator(0L, string, primitiveShort, boxedShort, primitiveLong, boxedLong, primitiveInt, boxedInteger, primitiveFloat, boxedFloat, primitiveDouble, boxedDouble, primitiveByte, boxedByte, primitiveByteArray, primitiveBoolean, boxedBoolean, calendar, utilDate);
	}
}