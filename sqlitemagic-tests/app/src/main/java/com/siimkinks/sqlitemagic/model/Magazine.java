package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@Table(persistAll = true)
public class Magazine {

	public static final String TABLE = "magazine";
	public static final String C_ID = "magazine._id";
	public static final String C_NAME = "magazine.name";
	public static final String C_AUTHOR = "magazine.author";

	String name;
	@Column(onDeleteCascade = true)
	Author author;
	int nrOfReleases;

	public static Magazine newRandom() {
		final Magazine magazine = new Magazine();
		fillWithRandomValues(magazine);
		return magazine;
	}

	public static void fillWithRandomValues(Magazine magazine) {
		magazine.name = Utils.randomTableName();
		magazine.author = Author.newRandom();
		magazine.nrOfReleases = new Random().nextInt();
	}
}
