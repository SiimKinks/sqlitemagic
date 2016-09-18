package com.siimkinks.sqlitemagic;

import android.app.Application;
import android.support.annotation.NonNull;

import rx.schedulers.Schedulers;

import static com.siimkinks.sqlitemagic.SqliteMagic.DatabaseSetupBuilder.setupDatabase;

/**
 * @author Siim Kinks
 */
public class TestApp extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		initDb(this);
	}

	public static void initDb(@NonNull Application app) {
		SqliteMagic.setLoggingEnabled(true);
		SqliteMagic.init(app, setupDatabase().scheduleRxQueriesOn(Schedulers.immediate()));
	}

}
