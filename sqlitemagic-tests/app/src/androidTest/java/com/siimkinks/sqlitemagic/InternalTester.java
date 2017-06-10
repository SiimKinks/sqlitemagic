package com.siimkinks.sqlitemagic;

import java.util.Set;

import io.reactivex.subjects.PublishSubject;

import static com.google.common.truth.Truth.assertThat;

public final class InternalTester {
  public static void assertTriggersHaveNoObservers() {
    final PublishSubject<Set<String>> triggers = SqliteMagic.getDefaultDbConnection().triggers;
    assertThat(triggers.hasObservers()).isFalse();
  }

  public static void assertTriggersHaveNoObservers(DbConnectionImpl dbConnection) {
    final PublishSubject<Set<String>> triggers = dbConnection.triggers;
    assertThat(triggers.hasObservers()).isFalse();
  }
}
