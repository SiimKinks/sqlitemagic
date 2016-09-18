package com.siimkinks.sqlitemagic.transformer;

import android.text.TextUtils;

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.annotation.transformer.Transformer;

@Transformer
public final class ArrayTransformer {
	@ObjectToDbValue
	public static String objectToDbValue(Integer[] javaObject) {
		if (javaObject != null) {
			return TextUtils.join(",", javaObject);
		}
		return null;
	}

	@DbValueToObject
	public static Integer[] dbValueToObject(String dbObject) {
		if (dbObject != null) {
			final String[] vals = dbObject.split("\\,");
			final int length = vals.length;
			final Integer[] output = new Integer[length];
			for (int i = 0; i < length; i++) {
				output[i] = Integer.valueOf(vals[i]);
			}
			return output;
		}
		return null;
	}
}
