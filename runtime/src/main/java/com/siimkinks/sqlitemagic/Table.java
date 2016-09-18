package com.siimkinks.sqlitemagic;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Size;

import com.siimkinks.sqlitemagic.util.MutableInt;

import java.util.ArrayList;
import java.util.LinkedList;

import rx.Subscription;

/**
 * Metadata of a table in a database.
 *
 * @param <T> Table Java object type
 */
public class Table<T> {
	static final Table<?> ANONYMOUS_TABLE = new Table<>("", null, 1);

	@NonNull
	final String name;
	@Nullable
	final String alias;
	@NonNull
	final String nameInQuery;
	final int nrOfColumns;
	final boolean hasAlias;
	private final Column<?, ?, ?, T> selectAllColumn;

	Table(@NonNull String name, @Nullable String alias, int nrOfColumns) {
		this.name = name;
		this.alias = alias;
		this.nrOfColumns = nrOfColumns;
		final boolean hasAlias = alias != null;
		this.hasAlias = hasAlias;
		this.nameInQuery = hasAlias ? alias : name;
		this.selectAllColumn = new Column<>(this, "*", true, null, false, null);
	}

	void appendToSqlFromClause(@NonNull StringBuilder sb) {
		sb.append(name);
		if (hasAlias) {
			sb.append(" AS ")
					.append(alias);
		}
	}

	/**
	 * This method gives table class the opportunity to perfect selection at select statement
	 * build time.
	 *
	 * @param observedTables      Tables that are being selected
	 * @param tableGraphNodeNames Selection graph node names
	 * @param columnPositions     Column positions in the selection
	 * @return Whether selection should be deep.
	 */
	boolean perfectSelection(@NonNull ArrayList<String> observedTables,
	                         @Nullable SimpleArrayMap<String, String> tableGraphNodeNames,
	                         @Nullable SimpleArrayMap<String, Integer> columnPositions) {
		if (!observedTables.contains(name)) {
			observedTables.add(name);
		}
		return false;
	}

	@NonNull
	final Table<T> internalAlias(@NonNull String alias) {
		return new Table<>(name, alias, nrOfColumns);
	}

	/**
	 * Create an alias for this table.
	 *
	 * @param alias The alias name
	 * @return New table with provided alias.
	 */
	@NonNull
	@CheckResult
	public Table<T> as(@NonNull String alias) {
		return new Table<>(name, alias, nrOfColumns);
	}

	/**
	 * All columns from this table ("*").
	 *
	 * @return Column that represents all columns in this table
	 */
	@NonNull
	public final Column<?, ?, ?, T> all() {
		return selectAllColumn;
	}

	/**
	 * Create join "ON" clause.
	 *
	 * @param expr Expression to use in join "ON" clause
	 * @return Join clause
	 */
	@NonNull
	@CheckResult
	public final JoinClause on(@NonNull final Expr expr) {
		return new JoinClause(this, "", "ON ") {
			@Override
			void appendSql(@NonNull StringBuilder sb) {
				super.appendSql(sb);
				expr.appendToSql(sb);
			}

			@Override
			void appendSql(@NonNull StringBuilder sb, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
				super.appendSql(sb, systemRenamedTables);
				expr.appendToSql(sb, systemRenamedTables);
			}

			@Override
			boolean containsColumn(@NonNull Column<?, ?, ?, ?> column) {
				return expr.containsColumn(column);
			}

			@Override
			void addArgs(@NonNull ArrayList<String> args) {
				super.addArgs(args);
				expr.addArgs(args);
			}
		};
	}

	/**
	 * Create join "USING" clause.
	 * <p>
	 * Each of the columns specified must exist in the datasets to both the left and right
	 * of the join-operator. For each pair of columns, the expression "lhs.X = rhs.X"
	 * is evaluated for each row of the cartesian product as a boolean expression.
	 * Only rows for which all such expressions evaluates to true are included from the
	 * result set. When comparing values as a result of a USING clause, the normal rules
	 * for handling affinities, collation sequences and NULL values in comparisons apply.
	 * The column from the dataset on the left-hand side of the join-operator is considered
	 * to be on the left-hand side of the comparison operator (=) for the purposes of
	 * collation sequence and affinity precedence.
	 * <p>
	 * For each pair of columns identified by a USING clause, the column from the
	 * right-hand dataset is omitted from the joined dataset. This is the only difference
	 * between a USING clause and its equivalent ON constraint.
	 *
	 * @param columns Columns to use in the USING clause
	 * @return Join clause
	 */
	@NonNull
	@CheckResult
	public final JoinClause using(@NonNull @Size(min = 1) final Column... columns) {
		final int colLen = columns.length;
		final StringBuilder sb = new StringBuilder(8 + colLen * 12);
		sb.append("USING (");
		for (int i = 0; i < colLen; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(columns[i].name);
		}
		sb.append(')');
		return new JoinClause(this, "", sb.toString()) {
			@Override
			boolean containsColumn(@NonNull Column<?, ?, ?, ?> column) {
				for (int i = 0; i < colLen; i++) {
					if (columns[i].equals(column)) {
						return true;
					}
				}
				return false;
			}
		};
	}

	@Nullable
	SimpleArrayMap<String, LinkedList<String>> addDeepQueryParts(@NonNull Select.From from,
	                                                             @Nullable StringArraySet selectFromTables,
	                                                             @Nullable SimpleArrayMap<String, String> tableGraphNodeNames,
	                                                             boolean select1) {
		return null;
	}

	@Nullable
	SimpleArrayMap<String, LinkedList<String>> addShallowQueryParts(@NonNull Select.From from,
	                                                                @Nullable StringArraySet selectFromTables,
	                                                                @Nullable SimpleArrayMap<String, String> tableGraphNodeNames,
	                                                                boolean select1) {
		return null;
	}

	@NonNull
	ArrayList<T> allFromCursor(@NonNull FastCursor cursor,
	                           @Nullable SimpleArrayMap<String, Integer> columnPositions,
	                           SimpleArrayMap<String, String> tableGraphNodeNames,
	                           boolean queryDeep,
	                           @NonNull Subscription subscription) {
		throw new RuntimeException("not implemented");
	}

	@Nullable
	T firstFromCursor(@NonNull FastCursor cursor,
	                  @Nullable SimpleArrayMap<String, Integer> columnPositions,
	                  SimpleArrayMap<String, String> tableGraphNodeNames,
	                  boolean queryDeep) {
		throw new RuntimeException("not implemented");
	}

	@NonNull
	T fromCurrentCursorPosition(@NonNull FastCursor cursor,
	                            @Nullable SimpleArrayMap<String, Integer> columnPositions,
	                            SimpleArrayMap<String, String> tableGraphNodeNames,
	                            boolean queryDeep,
	                            @NonNull MutableInt columnOffset) {
		throw new RuntimeException("not implemented");
	}

	final boolean baseNameEquals(Object o) {
		if (this == o) return true;
		if (o == null) return false;

		final Table<?> table;
		try {
			table = (Table<?>) o;
		} catch (ClassCastException e) {
			return false;
		}

		return name.equals(table.name);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null) return false;

		final Table<?> table;
		try {
			table = (Table<?>) o;
		} catch (ClassCastException e) {
			return false;
		}

		return nameInQuery.equals(table.nameInQuery);
	}

	@Override
	public int hashCode() {
		return nameInQuery.hashCode();
	}
}
