package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.Author;
import com.siimkinks.sqlitemagic.model.ComplexObjectWithSameLeafs;

import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class ComplexValueWithCreator {
	public static final String TABLE = "complexvaluewithcreator";
	public static final String C_ID = "complexvaluewithcreator.id";
	public static final String C_STRING = "complexvaluewithcreator.string";
	public static final String C_NULLABLE_STRING = "complexvaluewithcreator.nullable_string";
	public static final String C_AUTHOR = "complexvaluewithcreator.author";
	public static final String C_NOT_PERSISTED_AUTHOR = "complexvaluewithcreator.not_persisted_author";
	public static final String C_NULLABLE_AUTHOR = "complexvaluewithcreator.nulable_author";
	public static final String C_COMPLEX_OBJECT_WITH_SAME_LEAFS = "complexvaluewithcreator.complex_object_with_same_leafs";
	public static final String C_NOT_PERSISTED_COMPLEX_OBJECT_WITH_SAME_LEAFS = "complexvaluewithcreator.not_persisted_complex_object_with_same_leafs";
	public static final String C_BUILDER_SIMPLE_VALUE = "complexvaluewithcreator.builder_simple_value";
	public static final String C_NOT_PERSISTED_BUILDER_SIMPLE_VALUE = "complexvaluewithcreator.not_persisted_builder_simple_value";
	public static final String C_NULLABLE_BUILDER_SIMPLE_VALUE = "complexvaluewithcreator.nullable_builder_simple_value";
	public static final String C_CREATOR_SIMPLE_VALUE = "complexvaluewithcreator.creator_simple_value";
	public static final String C_NOT_PERSISTED_CREATOR_SIMPLE_VALUE = "complexvaluewithcreator.not_persisted_creator_simple_value";
	public static final String C_NULLABLE_CREATOR_SIMPLE_VALUE = "complexvaluewithcreator.nullable_creator_simple_value";

	@Id
	abstract long id();

	abstract String string();

	@Nullable
	abstract String nullableString();

	abstract Author author();

	@Column(handleRecursively = false)
	abstract Author notPersistedAuthor();

	@Nullable
	abstract Author nullableAuthor();

	abstract ComplexObjectWithSameLeafs complexObjectWithSameLeafs();

	@Column(handleRecursively = false)
	abstract ComplexObjectWithSameLeafs notPersistedComplexObjectWithSameLeafs();

	abstract SimpleValueWithBuilder builderSimpleValue();

	@Column(handleRecursively = false)
	abstract SimpleValueWithBuilderAndNullableFields notPersistedBuilderSimpleValue();

	@Nullable
	abstract SimpleValueWithBuilderAndNullableFields nullableBuilderSimpleValue();

	abstract SimpleValueWithCreator creatorSimpleValue();

	@Column(handleRecursively = false)
	abstract SimpleValueWithCreatorAndNullableFields notPersistedCreatorSimpleValue();

	@Nullable
	abstract SimpleValueWithCreatorAndNullableFields nullableCreatorSimpleValue();

	public static ComplexValueWithCreator create(long id,
	                                             String string,
	                                             String nullableString,
	                                             Author author,
	                                             Author notPersistedAuthor,
	                                             Author nullableAuthor,
	                                             ComplexObjectWithSameLeafs complexObjectWithSameLeafs,
	                                             ComplexObjectWithSameLeafs notPersistedComplexObjectWithSameLeafs,
	                                             SimpleValueWithBuilder builderSimpleValue,
	                                             SimpleValueWithBuilderAndNullableFields notPersistedBuilderSimpleValue,
	                                             SimpleValueWithBuilderAndNullableFields nullableBuilderSimpleValue,
	                                             SimpleValueWithCreator creatorSimpleValue,
	                                             SimpleValueWithCreatorAndNullableFields notPersistedCreatorSimpleValue,
	                                             SimpleValueWithCreatorAndNullableFields nullableCreatorSimpleValue) {
		return new AutoValue_ComplexValueWithCreator(id, string, nullableString, author, notPersistedAuthor, nullableAuthor, complexObjectWithSameLeafs, notPersistedComplexObjectWithSameLeafs, builderSimpleValue, notPersistedBuilderSimpleValue, nullableBuilderSimpleValue, creatorSimpleValue, notPersistedCreatorSimpleValue, nullableCreatorSimpleValue);
	}

	public static ComplexValueWithCreator newRandom() {
		return create(
				new Random().nextLong(),
				Utils.randomTableName(),
				Utils.randomTableName(),
				Author.newRandom(),
				Author.newRandom(),
				Author.newRandom(),
				ComplexObjectWithSameLeafs.newRandom(),
				ComplexObjectWithSameLeafs.newRandom(),
				SimpleValueWithBuilder.newRandom().build(),
				SimpleValueWithBuilderAndNullableFields.newRandom().build(),
				SimpleValueWithBuilderAndNullableFields.newRandom().build(),
				SimpleValueWithCreator.newRandom(),
				SimpleValueWithCreatorAndNullableFields.newRandom(),
				SimpleValueWithCreatorAndNullableFields.newRandom());
	}

	public boolean equalsWithoutId(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof ComplexValueWithCreator) {
			ComplexValueWithCreator that = (ComplexValueWithCreator) o;
			return (this.string().equals(that.string()))
					&& ((this.nullableString() == null) ? (that.nullableString() == null) : this.nullableString().equals(that.nullableString()))
					&& (this.author().equals(that.author()))
					&& (this.notPersistedAuthor().equals(that.notPersistedAuthor()))
					&& ((this.nullableAuthor() == null) ? (that.nullableAuthor() == null) : this.nullableAuthor().equals(that.nullableAuthor()))
					&& (this.complexObjectWithSameLeafs().equalsWithoutId(that.complexObjectWithSameLeafs()))
					&& (this.notPersistedComplexObjectWithSameLeafs().equals(that.notPersistedComplexObjectWithSameLeafs()))
					&& (this.builderSimpleValue().equalsWithoutId(that.builderSimpleValue()))
					&& (this.notPersistedBuilderSimpleValue().equals(that.notPersistedBuilderSimpleValue()))
					&& ((this.nullableBuilderSimpleValue() == null) ? (that.nullableBuilderSimpleValue() == null) : this.nullableBuilderSimpleValue().equalsWithoutId(that.nullableBuilderSimpleValue()))
					&& (this.creatorSimpleValue().equalsWithoutId(that.creatorSimpleValue()))
					&& (this.notPersistedCreatorSimpleValue().equals(that.notPersistedCreatorSimpleValue()))
					&& ((this.nullableCreatorSimpleValue() == null) ? (that.nullableCreatorSimpleValue() == null) : this.nullableCreatorSimpleValue().equalsWithoutId(that.nullableCreatorSimpleValue()));
		}
		return false;
	}

	public boolean equalsWithoutNotPersistedImmutableObjects(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof ComplexValueWithCreator) {
			ComplexValueWithCreator that = (ComplexValueWithCreator) o;
			return (this.string().equals(that.string()))
					&& ((this.nullableString() == null) ? (that.nullableString() == null) : this.nullableString().equals(that.nullableString()))
					&& (this.author().equals(that.author()))
					&& ((this.nullableAuthor() == null) ? (that.nullableAuthor() == null) : this.nullableAuthor().equals(that.nullableAuthor()))
					&& (this.complexObjectWithSameLeafs().equalsWithoutId(that.complexObjectWithSameLeafs()))
					&& (this.builderSimpleValue().equalsWithoutId(that.builderSimpleValue()))
					&& ((this.nullableBuilderSimpleValue() == null) ? (that.nullableBuilderSimpleValue() == null) : this.nullableBuilderSimpleValue().equalsWithoutId(that.nullableBuilderSimpleValue()))
					&& (this.creatorSimpleValue().equalsWithoutId(that.creatorSimpleValue()))
					&& ((this.nullableCreatorSimpleValue() == null) ? (that.nullableCreatorSimpleValue() == null) : this.nullableCreatorSimpleValue().equalsWithoutId(that.nullableCreatorSimpleValue()));
		}
		return false;
	}
}
