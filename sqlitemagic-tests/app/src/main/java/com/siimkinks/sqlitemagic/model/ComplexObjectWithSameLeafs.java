package com.siimkinks.sqlitemagic.model;

import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.immutable.SimpleValueWithBuilder;
import com.siimkinks.sqlitemagic.Utils;

import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
@Table(persistAll = true)
public class ComplexObjectWithSameLeafs {

	public static final String TABLE = "complexobjectwithsameleafs";
	public static final String C_ID = "complexobjectwithsameleafs.id";
	public static final String C_NAME = "complexobjectwithsameleafs.name";
	public static final String C_BOOK = "complexobjectwithsameleafs.book";
	public static final String C_MAGAZINE = "complexobjectwithsameleafs.magazine";
	public static final String C_SIMPLE_VALUE_WITH_BUILDER = "complexobjectwithsameleafs.simple_value_with_builder";
	public static final String C_SIMPLE_VALUE_WITH_BUILDER_DUPLICATE = "complexobjectwithsameleafs.simple_value_with_builder_duplicate";

	@Id
	public long id;
	public String name;
	public SimpleValueWithBuilder simpleValueWithBuilder;
	Book book;
	Magazine magazine;
	public SimpleValueWithBuilder simpleValueWithBuilderDuplicate;

	public static ComplexObjectWithSameLeafs newRandom() {
		final ComplexObjectWithSameLeafs complex = new ComplexObjectWithSameLeafs();
		complex.id = new Random().nextLong();
		complex.name = Utils.randomTableName();
		complex.simpleValueWithBuilder = SimpleValueWithBuilder.newRandom().build();
		complex.book = Book.newRandom();
		complex.magazine = Magazine.newRandom();
		complex.simpleValueWithBuilderDuplicate = SimpleValueWithBuilder.newRandom().build();
		return complex;
	}

	public boolean equalsWithoutId(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ComplexObjectWithSameLeafs that = (ComplexObjectWithSameLeafs) o;

		if (name != null ? !name.equals(that.name) : that.name != null) return false;
		if (simpleValueWithBuilder != null ? !simpleValueWithBuilder.equalsWithoutId(that.simpleValueWithBuilder) : that.simpleValueWithBuilder != null)
			return false;
		if (book != null ? !book.equals(that.book) : that.book != null) return false;
		if (magazine != null ? !magazine.equals(that.magazine) : that.magazine != null)
			return false;
		return !(simpleValueWithBuilderDuplicate != null ? !simpleValueWithBuilderDuplicate.equalsWithoutId(that.simpleValueWithBuilderDuplicate) : that.simpleValueWithBuilderDuplicate != null);

	}
}
