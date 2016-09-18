package com.siimkinks.sqlitemagic.exception;

public class DuplicateIdException extends DuplicateException {

	public DuplicateIdException(String fieldName) {
		super(String.format("Duplicate id %s", fieldName));
	}
}
