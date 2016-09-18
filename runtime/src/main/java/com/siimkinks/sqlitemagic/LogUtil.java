package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import static com.siimkinks.sqlitemagic.DefaultLogger.TAG_SQLITE_MAGIC;

/**
 * Internal logging utility.
 */
public final class LogUtil {

	public static void logInfo(@NonNull String msg, Object... args) {
		if (args.length > 0) msg = String.format(msg, args);
		Log.i(TAG_SQLITE_MAGIC, msg);
	}

	public static void logMandatoryWarning(@NonNull String msg, Object... args) {
		if (args.length > 0) msg = String.format(msg, args);
		Log.w(TAG_SQLITE_MAGIC, msg);
	}

	public static void logQueryTime(long queryTimeInMillis,
	                                @NonNull String[] observedTables,
	                                @NonNull String sql,
	                                @Nullable String[] args) {
		SqliteMagic.LOGGER.logQueryTime(queryTimeInMillis, observedTables, sql, args);
	}

	public static void logDebug(@NonNull String msg, Object... args) {
		if (args.length > 0) msg = String.format(msg, args);
		SqliteMagic.LOGGER.logDebug(msg);
	}

	public static void logWarning(@NonNull String msg, Object... args) {
		if (args.length > 0) msg = String.format(msg, args);
		SqliteMagic.LOGGER.logWarning(msg);
	}

	public static void logError(@NonNull String msg, Object... args) {
		if (args.length > 0) msg = String.format(msg, args);
		SqliteMagic.LOGGER.logError(msg);
	}

	public static void logError(@NonNull Exception e, @NonNull String msg, Object... args) {
		if (args.length > 0) msg = String.format(msg, args);
		SqliteMagic.LOGGER.logError(msg, e);
	}
}
