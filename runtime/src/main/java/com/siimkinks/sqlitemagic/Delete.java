package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

/**
 * Builder for SQL DELETE statement.
 */
public final class Delete extends DeleteSqlNode {
	Delete() {
		super(null);
	}

	@Override
	protected void appendSql(@NonNull StringBuilder sb) {
		sb.append("DELETE");
	}

	/**
	 * Create a new builder for SQL DELETE statement.
	 * <p>
	 * Note that {@code table} param must be one of annotation processor generated table
	 * objects that corresponds to table in a database.<br>
	 * Example:
	 * <pre>{@code
	 * import static com.example.model.AuthorTable.AUTHOR;
	 *
	 * // [...]
	 *
	 * Delete.from(AUTHOR)
	 *       .where(AUTHOR.NAME.is("George"));
	 * }</pre>
	 *
	 * @param table Table to delete from. This param must be one of annotation processor
	 *              generated table objects that corresponds to table in a database
	 * @param <T>   Table object type
	 * @return A new builder for SQL DELETE statement
	 */
	@CheckResult
	public static <T> From<T> from(@NonNull Table<T> table) {
		return new From<>(new Delete(), table);
	}

	/**
	 * Builder for SQL DELETE statement.
	 */
	public static final class From<T> extends ExecutableNode {
		@NonNull
		final Table<T> table;

		From(@NonNull Delete parent, @NonNull Table<T> table) {
			super(parent);
			this.table = table;
			deleteBuilder.from = this;
		}

		@Override
		protected void appendSql(@NonNull StringBuilder sb) {
			sb.append("FROM ");
			table.appendToSqlFromClause(sb);
		}

		/**
		 * Define SQL DELETE statement WHERE clause.
		 *
		 * @param expr WHERE clause expression
		 * @return A builder for SQL DELETE statement
		 */
		@CheckResult
		public Where where(@NonNull Expr expr) {
			return new Where(this, expr);
		}
	}

	/**
	 * Builder for SQL DELETE statement.
	 */
	public static final class Where extends ExecutableNode {
		@NonNull
		private final Expr expr;

		Where(@NonNull DeleteSqlNode parent, @NonNull Expr expr) {
			super(parent);
			this.expr = expr;
			expr.addArgs(deleteBuilder.args);
		}

		@Override
		protected void appendSql(@NonNull StringBuilder sb) {
			sb.append("WHERE ");
			expr.appendToSql(sb);
		}
	}
}
