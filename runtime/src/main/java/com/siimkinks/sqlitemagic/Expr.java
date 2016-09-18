package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.LinkedList;

/**
 * An SQL expression.
 */
public class Expr {
	@NonNull
	private final Column<?, ?, ?, ?> column;
	@NonNull
	final String expr;

	Expr(@NonNull Column<?, ?, ?, ?> column, @NonNull String expr) {
		this.column = column;
		this.expr = expr;
	}

	void addArgs(@NonNull ArrayList<String> args) {
	}

	void addObservedTables(@NonNull ArrayList<String> tables) {
	}

	void appendToSql(@NonNull StringBuilder sb) {
		column.appendSql(sb);
		sb.append(expr);
	}

	void appendToSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
		column.appendSql(sb, systemRenamedTables);
		sb.append(expr);
	}

	boolean containsColumn(@NonNull Column<?, ?, ?, ?> column) {
		return column.equals(this.column);
	}

	/**
	 * Combine this expression with another one using the AND operator.
	 *
	 * @param expr Expression to combine with
	 * @return Combined expression
	 */
	@NonNull
	@CheckResult
	public final Expr and(@NonNull Expr expr) {
		return new BinaryExpr(" AND ", this, expr);
	}

	/**
	 * Combine this expression with another one using the AND NOT operator combination.
	 *
	 * @param expr Expression to combine with
	 * @return Combined expression
	 */
	@NonNull
	@CheckResult
	public final Expr andNot(@NonNull Expr expr) {
		return new BinaryExpr(" AND NOT ", this, expr);
	}

	/**
	 * Combine this expression with another one using the OR operator.
	 *
	 * @param expr Expression to combine with
	 * @return Combined expression
	 */
	@NonNull
	@CheckResult
	public final Expr or(@NonNull Expr expr) {
		return new BinaryExpr(" OR ", this, expr);
	}

	/**
	 * Combine this expression with another one using the OR NOT operator combination.
	 *
	 * @param expr Expression to combine with
	 * @return Combined expression
	 */
	@NonNull
	@CheckResult
	public final Expr orNot(@NonNull Expr expr) {
		return new BinaryExpr(" OR NOT ", this, expr);
	}
}
