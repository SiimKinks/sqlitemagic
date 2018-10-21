package com.siimkinks.sqlitemagic.exception;

import androidx.annotation.NonNull;

public final class OperationFailedException extends RuntimeException {
  public OperationFailedException(@NonNull String message) {
    super(message);
  }

  public OperationFailedException(@NonNull String message, Throwable cause) {
    super(message, cause);
  }
}
