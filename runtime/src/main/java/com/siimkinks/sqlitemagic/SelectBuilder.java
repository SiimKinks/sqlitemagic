package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;

final class SelectBuilder<S> {
	SqlNode sqlTreeRoot;
	int sqlNodeCount;
	Select.From from;
	Select.Columns columnsNode;
	Select.SingleColumn<?> columnNode;
	final ArrayList<String> args = new ArrayList<>();
	final ArrayList<String> observedTables = new ArrayList<>();
	boolean deep;
	DbConnectionImpl dbConnection = SqliteMagic.getDefaultDbConnection();
	private boolean compiled = false;

	SelectBuilder() {
	}

	void appendCompiledQuery(@NonNull StringBuilder sb, @Nullable ArrayList<String> parentObservedTables) {
		final StringArraySet selectFromTables;
		final boolean select1 = columnNode != null;
		if (select1) {
			selectFromTables = columnNode.preCompileColumns();
		} else {
			selectFromTables = columnsNode.preCompileColumns();
		}
		final SimpleArrayMap<String, String> tableGraphNodeNames = selectFromTables != null ? new SimpleArrayMap<String, String>(selectFromTables.size()) : null;
		//noinspection unchecked
		final Select.From<?, ?, ?> from = this.from;
		final Table<?> table = from.table;
		final SimpleArrayMap<String, LinkedList<String>> systemRenamedTables;
		if (deep) {
			systemRenamedTables = table.addDeepQueryParts(from, selectFromTables, tableGraphNodeNames, select1);
		} else {
			systemRenamedTables = table.addShallowQueryParts(from, selectFromTables, tableGraphNodeNames, select1);
		}
		if (parentObservedTables != null) {
			perfectSelection(from, parentObservedTables, tableGraphNodeNames, null);
		}
		if (systemRenamedTables != null) {
			SqlCreator.appendSql(sqlTreeRoot, systemRenamedTables, sb);
		} else {
			SqlCreator.appendSql(sqlTreeRoot, sb);
		}
	}


	// !!! ordering in this method is important !!!
	@NonNull
	@CheckResult
	<T> CompiledSelect<T, S> build() {
		if (compiled) {
			throw new IllegalStateException("Select statement builder can be compiled only once");
		}
		compiled = true;
		final StringArraySet selectFromTables;
		final boolean select1 = columnNode != null;
		if (select1) {
			selectFromTables = columnNode.preCompileColumns();
		} else {
			selectFromTables = columnsNode.preCompileColumns();
		}
		final SimpleArrayMap<String, String> tableGraphNodeNames = selectFromTables != null ? new SimpleArrayMap<String, String>(selectFromTables.size()) : new SimpleArrayMap<String, String>();
		final SimpleArrayMap<String, LinkedList<String>> systemRenamedTables;
		//noinspection unchecked
		final Select.From<T, ?, ?> from = this.from;
		final Table<T> table = from.table;

		if (deep) {
			systemRenamedTables = table.addDeepQueryParts(from, selectFromTables, tableGraphNodeNames, select1);
		} else {
			systemRenamedTables = table.addShallowQueryParts(from, selectFromTables, tableGraphNodeNames, select1);
		}

		final int argsSize = args.size();

		if (columnNode != null) {
			final String sql = systemRenamedTables != null ?
					SqlCreator.getSql(sqlTreeRoot, sqlNodeCount, systemRenamedTables) :
					SqlCreator.getSql(sqlTreeRoot, sqlNodeCount);
			perfectSelection(from, observedTables, tableGraphNodeNames, null);
			//noinspection unchecked
			return new CompiledSelect1Impl<>(
					sql,
					argsSize > 0 ? args.toArray(new String[argsSize]) : null,
					dbConnection,
					(Column<?, T, ?, ?>) columnNode.column,
					this.observedTables.toArray(new String[this.observedTables.size()])
			);
		}

		final SimpleArrayMap<String, Integer> columnPositions = columnsNode.compileColumns(systemRenamedTables);
		final String sql;
		if (systemRenamedTables != null) {
			sql = SqlCreator.getSql(sqlTreeRoot, sqlNodeCount, systemRenamedTables);
		} else {
			sql = SqlCreator.getSql(sqlTreeRoot, sqlNodeCount);
		}

		final boolean forcedDeepSelection = perfectSelection(from, observedTables, tableGraphNodeNames, columnPositions);
		final boolean fromSelection = columnPositions.isEmpty();
		return new CompiledSelectImpl<>(
				sql,
				argsSize > 0 ? args.toArray(new String[argsSize]) : null,
				table,
				dbConnection,
				this.observedTables.toArray(new String[this.observedTables.size()]),
				fromSelection ? null : columnPositions,
				fromSelection ? null : tableGraphNodeNames,
				deep || forcedDeepSelection
		);
	}

	@SuppressWarnings("unchecked")
	private static boolean perfectSelection(@NonNull Select.From from,
	                                        @NonNull ArrayList<String> observedTables,
	                                        @Nullable SimpleArrayMap<String, String> tableGraphNodeNames,
	                                        @Nullable SimpleArrayMap<String, Integer> columnPositions) {
		final ArrayList<JoinClause> joins = from.joins;
		final int size = joins.size();
		boolean forcedDeepSelection = from.table.perfectSelection(observedTables, tableGraphNodeNames, columnPositions);
		for (int i = 0; i < size; i++) {
			forcedDeepSelection |= joins.get(i).table.perfectSelection(observedTables, tableGraphNodeNames, columnPositions);
		}
		return forcedDeepSelection;
	}
}
