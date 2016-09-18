package com.siimkinks.sqlitemagic.util;

import java.util.Collection;

public final class Utils {
	public static boolean isEmpty(Collection<?> collection) {
		return collection == null || collection.isEmpty();
	}
}
