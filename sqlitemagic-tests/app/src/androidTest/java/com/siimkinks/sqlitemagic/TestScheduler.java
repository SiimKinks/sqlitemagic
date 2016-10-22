package com.siimkinks.sqlitemagic;

import java.util.concurrent.TimeUnit;

import rx.Scheduler;
import rx.Subscription;
import rx.functions.Action0;

public final class TestScheduler extends Scheduler {
  private final rx.schedulers.TestScheduler delegate = new rx.schedulers.TestScheduler();
  private boolean runTasksImmediately = true;

  public void runTasksImmediately(boolean runTasksImmediately) {
    this.runTasksImmediately = runTasksImmediately;
  }

  public void triggerActions() {
    delegate.triggerActions();
  }

  @Override
  public Worker createWorker() {
    return new TestWorker();
  }

  class TestWorker extends Worker {
    private final Worker delegateWorker = delegate.createWorker();

    @Override
    public Subscription schedule(Action0 action) {
      Subscription subscription = delegateWorker.schedule(action);
      if (runTasksImmediately) {
        triggerActions();
      }
      return subscription;
    }

    @Override
    public Subscription schedule(Action0 action, long delayTime, TimeUnit unit) {
      Subscription subscription = delegateWorker.schedule(action, delayTime, unit);
      if (runTasksImmediately) {
        triggerActions();
      }
      return subscription;
    }

    @Override
    public void unsubscribe() {
      delegateWorker.unsubscribe();
    }

    @Override
    public boolean isUnsubscribed() {
      return delegateWorker.isUnsubscribed();
    }
  }
}
