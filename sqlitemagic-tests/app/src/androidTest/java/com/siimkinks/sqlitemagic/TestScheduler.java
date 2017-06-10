package com.siimkinks.sqlitemagic;

import java.util.concurrent.TimeUnit;

import io.reactivex.Scheduler;
import io.reactivex.disposables.Disposable;

public final class TestScheduler extends Scheduler {
  private final io.reactivex.schedulers.TestScheduler delegate = new io.reactivex.schedulers.TestScheduler();
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
    public Disposable schedule(Runnable action) {
      Disposable disposable = delegateWorker.schedule(action);
      if (runTasksImmediately) {
        triggerActions();
      }
      return disposable;
    }

    @Override
    public Disposable schedule(Runnable action, long delayTime, TimeUnit unit) {
      Disposable disposable = delegateWorker.schedule(action, delayTime, unit);
      if (runTasksImmediately) {
        triggerActions();
      }
      return disposable;
    }

    @Override
    public void dispose() {
      delegateWorker.dispose();
    }

    @Override
    public boolean isDisposed() {
      return delegateWorker.isDisposed();
    }
  }
}
