package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteStatement;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;

final class FunctionCopyColumn<
		T, // exact type
		R, // return type (when this column is queried)
		ET, // equivalent type
		P>  // parent table type
		extends NumericColumn<T, R, ET, P> {

	@NonNull
	private final Column<T, R, ET, P> wrappedColumn;
	@NonNull
	private final String prefix;
	private final char suffix;

	FunctionCopyColumn(@NonNull Table<P> table,
	                   @NonNull Column<T, R, ET, P> wrappedColumn,
	                   @NonNull String prefix,
	                   char suffix,
	                   boolean nullable,
	                   @Nullable String alias) {
		super(table, prefix + wrappedColumn.nameInQuery + suffix, wrappedColumn.allFromTable,
				wrappedColumn.valueParser, nullable, alias != null ? alias : wrappedColumn.alias);
		this.wrappedColumn = wrappedColumn;
		this.prefix = prefix;
		this.suffix = suffix;
	}

	@NonNull
	@Override
	String toSqlArg(@NonNull T val) {
		return wrappedColumn.toSqlArg(val);
	}

	@Nullable
	@Override
	<V> V getFromCursor(@NonNull FastCursor cursor) {
		return wrappedColumn.getFromCursor(cursor);
	}

	@Nullable
	@Override
	<V> V getFromStatement(@NonNull SQLiteStatement stm) {
		return wrappedColumn.getFromStatement(stm);
	}

	@Override
	void appendSql(@NonNull StringBuilder sb) {
		sb.append(prefix);
		wrappedColumn.appendSql(sb);
		sb.append(suffix);
	}

	@Override
	void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
		sb.append(prefix);
		wrappedColumn.appendSql(sb, systemRenamedTables);
		sb.append(suffix);
	}

	@Override
	void addSelectedTables(@NonNull StringArraySet result) {
		wrappedColumn.addSelectedTables(result);
	}

	@Override
	void addArgs(@NonNull ArrayList<String> args) {
		wrappedColumn.addArgs(args);
	}

	@Override
	void addObservedTables(@NonNull ArrayList<String> tables) {
		wrappedColumn.addObservedTables(tables);
	}

	@Override
	int compile(@NonNull SimpleArrayMap<String, Integer> columnPositions,
	            @NonNull StringBuilder compiledCols,
	            int columnOffset) {
		appendSql(compiledCols);
		appendAliasDeclarationIfNeeded(compiledCols);
		putColumnPosition(columnPositions, name, columnOffset, this);
		return columnOffset + 1;
	}

	@Override
	int compile(@NonNull SimpleArrayMap<String, Integer> columnPositions,
	            @NonNull StringBuilder compiledCols,
	            @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables,
	            int columnOffset) {
		appendSql(compiledCols, systemRenamedTables);
		appendAliasDeclarationIfNeeded(compiledCols);
		putColumnPosition(columnPositions, name, columnOffset, this);
		return columnOffset + 1;
	}

	@Override
	@NonNull
	@CheckResult
	public FunctionCopyColumn<T, R, ET, P> as(@NonNull String alias) {
		return new FunctionCopyColumn<>(table, wrappedColumn, prefix, suffix, nullable, alias);
	}
}
