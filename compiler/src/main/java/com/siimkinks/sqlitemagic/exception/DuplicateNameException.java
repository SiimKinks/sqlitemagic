package com.siimkinks.sqlitemagic.exception;

public class DuplicateNameException extends DuplicateException {

  public DuplicateNameException(String name) {
    super(String.format("Duplicate name %s", name));
  }
}
