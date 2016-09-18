package com.siimkinks.sqlitemagic;

import com.google.common.base.Strings;
import com.google.common.truth.Truth;

import static com.google.common.truth.Truth.assertThat;


public class UnitTestUtil {

	public static final String WILDCARD = "?";
	public static final String RANDOM_TABLE_NAME_REGEX = ".{" + Utils.TABLE_NAME_LEN + "}";

	public static String replaceRandomTableNames(String str) {
		return str.replaceAll("\\.", "\\\\.")
				.replaceAll("\\(", "\\\\(")
				.replaceAll("\\)", "\\\\)")
				.replaceAll("\\+", "\\\\+")
				.replaceAll("\\*", "\\\\*")
				.replaceAll("\\" + WILDCARD, RANDOM_TABLE_NAME_REGEX);
	}

	public static <K, V> void assertSimpleArrayMapsAreEqual(SimpleArrayMap<K, V> actual, SimpleArrayMap<K, V> expected) {
		if (actual == null) {
			assertThat(expected).isNull();
			return;
		}
		if (expected == null) {
			assertThat(actual).isNull();
			return;
		}
		final int size = expected.size();
		assertThat(actual.size()).isEqualTo(size);
		for (int i = 0; i < size; i++) {
			final K key = expected.keyAt(i);
			final V expectedValue = expected.valueAt(i);
			final V actualValue = actual.get(key);
			assertThat(actualValue).isEqualTo(expectedValue);
		}
	}

	public static <K> void assertSimpleArrayMapsAreEqualWithWildcardInValue(SimpleArrayMap<K, String> actual, SimpleArrayMap<K, String> expected) {
		if (actual == null) {
			assertThat(expected).isNull();
			return;
		}
		if (expected == null) {
			assertThat(actual).isNull();
			return;
		}
		final int size = expected.size();
		assertThat(actual.size()).isEqualTo(size);
		for (int i = 0; i < size; i++) {
			final K key = expected.keyAt(i);
			final String expectedValue = expected.valueAt(i);
			final String actualValue = actual.get(key);
			if (expectedValue.contains(WILDCARD)) {
				final String expectedValueRegex = replaceRandomTableNames(expectedValue);
				assertThat(actualValue).isNotNull();
				assertThat(actualValue).matches(expectedValueRegex);
			} else {
				assertThat(actualValue).isEqualTo(expectedValue);
			}
		}
	}

	public static <V> void assertSimpleArrayMapsAreEqualWithWildcardInKey(SimpleArrayMap<String, V> actual, SimpleArrayMap<String, V> expected) {
		if (actual == null) {
			assertThat(expected).isNull();
			return;
		}
		if (expected == null) {
			assertThat(actual).isNull();
			return;
		}
		final int expectedSize = expected.size();
		final int actualSize = actual.size();
		assertThat(actualSize).isEqualTo(expectedSize);
		for (int i = 0; i < expectedSize; i++) {
			final String key = expected.keyAt(i).trim();
			final V expectedValue = expected.valueAt(i);
			final V actualValue = actual.get(key);

			if (actualValue == null && key.contains(WILDCARD)) {
				final String keyWithRegex = replaceRandomTableNames(key);
				boolean foundMatch = false;
				for (int k = 0; k < actualSize; k++) {
					final String actualKey = actual.keyAt(k);
					if (actualKey.matches(keyWithRegex)) {
						final V val = actual.get(actualKey);
						if (val.equals(expectedValue)) {
							foundMatch = true;
							break;
						}
					}
				}
				assertThat(foundMatch).isTrue();
			} else {
				assertThat(actualValue).isEqualTo(expectedValue);
			}
		}
	}

	public static void assertStringsAreEqualOrMatching(String actual, String expected) {
		if (!Strings.isNullOrEmpty(actual) && !Strings.isNullOrEmpty(expected) && !actual.equals(expected)) {
			Truth.assertThat(actual).matches(expected);
		} else {
			Truth.assertThat(actual).isEqualTo(expected);
		}
	}
}
