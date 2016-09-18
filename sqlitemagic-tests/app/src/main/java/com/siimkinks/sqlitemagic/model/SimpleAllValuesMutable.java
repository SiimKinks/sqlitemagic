package com.siimkinks.sqlitemagic.model;

import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Calendar;
import java.util.Date;
import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@NoArgsConstructor
@Table(persistAll = true)
public class SimpleAllValuesMutable {
	@Id
	long id;
	String string;
	short primitiveShort;
	Short boxedShort;
	long primitiveLong;
	Long boxedLong;
	int primitiveInt;
	Integer boxedInteger;
	float primitiveFloat;
	Float boxedFloat;
	double primitiveDouble;
	Double boxedDouble;
	byte primitiveByte;
	Byte boxedByte;
	byte[] primitiveByteArray;
	Byte[] boxedByteArray;
	boolean primitiveBoolean;
	Boolean boxedBoolean;
	Calendar calendar;
	Date utilDate;

	public static SimpleAllValuesMutable newRandom() {
		return fillWithRandomValues(new SimpleAllValuesMutable());
	}

	@NonNull
	private static SimpleAllValuesMutable fillWithRandomValues(@NonNull SimpleAllValuesMutable object) {
		final Random r = new Random();
		object.string = Utils.randomTableName();
		object.primitiveShort = (short) r.nextInt(Short.MAX_VALUE + 1);
		object.boxedShort = (short) r.nextInt(Short.MAX_VALUE + 1);
		object.primitiveLong = r.nextLong();
		object.boxedLong = r.nextLong();
		object.primitiveInt = r.nextInt();
		object.boxedInteger = r.nextInt();
		object.primitiveFloat = r.nextFloat();
		object.boxedFloat = r.nextFloat();
		object.primitiveDouble = r.nextDouble();
		object.boxedDouble = r.nextDouble();
		final byte[] b = new byte[1];
		r.nextBytes(b);
		object.primitiveByte = b[0];
		r.nextBytes(b);
		object.boxedByte = b[0];
		final byte[] byteArray = new byte[4];
		r.nextBytes(byteArray);
		object.primitiveByteArray = byteArray;
		r.nextBytes(byteArray);
		object.boxedByteArray = Utils.toByteArray(byteArray);
		object.primitiveBoolean = r.nextBoolean();
		object.boxedBoolean = r.nextBoolean();
		object.calendar = Calendar.getInstance();
		object.utilDate = new Date(Math.abs(r.nextLong()));
		return object;
	}
}
