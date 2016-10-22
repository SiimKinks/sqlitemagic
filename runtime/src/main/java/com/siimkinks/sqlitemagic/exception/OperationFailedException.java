package com.siimkinks.sqlitemagic.exception;

import android.support.annotation.NonNull;

public final class OperationFailedException extends RuntimeException {
  public OperationFailedException(@NonNull String message) {
    super(message);
  }
}
