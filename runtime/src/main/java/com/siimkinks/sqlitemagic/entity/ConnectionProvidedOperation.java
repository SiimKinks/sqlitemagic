package com.siimkinks.sqlitemagic.entity;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import com.siimkinks.sqlitemagic.DbConnection;

public interface ConnectionProvidedOperation<R> {
	/**
	 * Configure this operation to use provided connection for database operation.
	 *
	 * @param connection Database connection
	 * @return Operation builder
	 */
	@NonNull
	@CheckResult
	R usingConnection(@NonNull DbConnection connection);
}
