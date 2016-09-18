package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * An SQL join clause.
 */
public class JoinClause extends SqlClause {
	@NonNull
	final Table<?> table;
	@NonNull
	String operator;
	@Nullable
	final String constraint;

	JoinClause(@NonNull Table<?> table, @NonNull String operator, @Nullable String constraint) {
		this.table = table;
		this.operator = operator;
		this.constraint = constraint;
	}

	@Override
	void appendSql(@NonNull StringBuilder sb) {
		sb.append(operator)
				.append(' ');
		this.table.appendToSqlFromClause(sb);
		final String constraint = this.constraint;
		if (constraint != null && constraint.length() > 0) {
			sb.append(' ')
					.append(constraint);
		}
	}

	@Override
	void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
		sb.append(operator)
				.append(' ');
		this.table.appendToSqlFromClause(sb);
		final String constraint = this.constraint;
		if (constraint != null && constraint.length() > 0) {
			sb.append(' ')
					.append(constraint);
		}
	}

	@NonNull
	final String tableNameInQuery() {
		return table.nameInQuery;
	}

	final boolean tableHasAlias() {
		return table.hasAlias;
	}

	boolean containsColumn(@NonNull Column<?, ?, ?, ?> column) {
		return true;
	}

	void addArgs(@NonNull ArrayList<String> args) {
	}

	static int indexOf(@NonNull Table<?> table,
	                   @NonNull ArrayList<JoinClause> array,
	                   @NonNull Column<?, ?, ?, ?> joinedOnColumn) {
		if (array.isEmpty()) {
			return -1;
		}

		final int len = array.size();
		for (int i = 0; i < len; i++) {
			final JoinClause joinClause = array.get(i);
			if (joinClause == null) continue;
			if (joinClause.table.baseNameEquals(table)) {
				if (joinClause.containsColumn(joinedOnColumn)) {
					return i;
				}
			}
		}
		return -1;
	}
}
