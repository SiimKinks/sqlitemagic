package com.siimkinks.sqlitemagic.util;

import android.support.annotation.CheckResult;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Siim Kinks
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FormatData {

	private final String format;
	private final Object[] args;

	public static FormatData create(String format, Object... args) {
		return new FormatData(format, args);
	}

	@CheckResult
	public String formatInto(String stm) {
		return String.format(stm, format);
	}

	public Object[] getWithOtherArgsBefore(Object... otherArgs) {
		final int otherArgsLen = otherArgs.length;
		final int argsLen = args.length;
		Object[] output = new Object[argsLen + otherArgsLen];
		System.arraycopy(otherArgs, 0, output, 0, otherArgsLen);
		System.arraycopy(args, 0, output, otherArgsLen, argsLen);
		return output;
	}

	public Object[] getWithOtherArgsAfter(Object... otherArgs) {
		final int otherArgsLen = otherArgs.length;
		final int argsLen = args.length;
		Object[] output = new Object[argsLen + otherArgsLen];
		System.arraycopy(args, 0, output, 0, argsLen);
		System.arraycopy(otherArgs, 0, output, argsLen, otherArgsLen);
		return output;
	}

	@CheckResult
	public Intermediate getArgsBetween(Object... beforeArgs) {
		return new Intermediate(format, args, beforeArgs);
	}

	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static class Intermediate {
		final String format;
		final Object[] args;
		final Object[] beforeArgs;

		public Object[] and(Object... afterArgs) {
			final int argsLen = args.length;
			final int beforeArgsLen = beforeArgs.length;
			final int afterArgsLen = afterArgs.length;
			final Object[] output = new Object[argsLen + beforeArgsLen + afterArgsLen];
			System.arraycopy(beforeArgs, 0, output, 0, beforeArgsLen);
			System.arraycopy(args, 0, output, beforeArgsLen, argsLen);
			System.arraycopy(afterArgs, 0, output, argsLen + beforeArgsLen, afterArgsLen);
			return output;
		}
	}
}
