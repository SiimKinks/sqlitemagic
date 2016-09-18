package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;

import java.util.ArrayList;

final class Expr1 extends Expr {
	@NonNull
	private final String evalArg;

	Expr1(@NonNull Column<?, ?, ?, ?> column, @NonNull String expr, @NonNull String evalArg) {
		super(column, expr);
		this.evalArg = evalArg;
	}

	@Override
	void addArgs(@NonNull ArrayList<String> args) {
		args.add(evalArg);
	}
}
