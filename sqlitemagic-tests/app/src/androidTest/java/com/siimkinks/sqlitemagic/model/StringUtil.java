package com.siimkinks.sqlitemagic.model;

import java.util.List;

public final class StringUtil {
	private StringUtil() {
		//no instance
	}

	public static <T> String join(CharSequence delimiter, List<T> vals) {
		final StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (T val : vals) {
			if (first) {
				first = false;
			} else {
				sb.append(delimiter);
			}
			sb.append(val.toString());
		}
		return sb.toString();
	}
}
