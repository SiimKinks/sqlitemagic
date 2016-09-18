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
public abstract class ComplexValueWithBuilder {
	public static final String TABLE = "complexvaluewithbuilder";
	public static final String C_ID = "complexvaluewithbuilder.id";
	public static final String C_STRING = "complexvaluewithbuilder.string";
	public static final String C_NULLABLE_STRING = "complexvaluewithbuilder.nullable_string";
	public static final String C_AUTHOR = "complexvaluewithbuilder.author";
	public static final String C_NOT_PERSISTED_AUTHOR = "complexvaluewithbuilder.not_persisted_author";
	public static final String C_NULLABLE_AUTHOR = "complexvaluewithbuilder.nulable_author";
	public static final String C_COMPLEX_OBJECT_WITH_SAME_LEAFS = "complexvaluewithbuilder.complex_object_with_same_leafs";
	public static final String C_NOT_PERSISTED_COMPLEX_OBJECT_WITH_SAME_LEAFS = "complexvaluewithbuilder.not_persisted_complex_object_with_same_leafs";
	public static final String C_BUILDER_SIMPLE_VALUE = "complexvaluewithbuilder.builder_simple_value";
	public static final String C_NOT_PERSISTED_BUILDER_SIMPLE_VALUE = "complexvaluewithbuilder.not_persisted_builder_simple_value";
	public static final String C_NULLABLE_BUILDER_SIMPLE_VALUE = "complexvaluewithbuilder.nullable_builder_simple_value";
	public static final String C_CREATOR_SIMPLE_VALUE = "complexvaluewithbuilder.creator_simple_value";
	public static final String C_NOT_PERSISTED_CREATOR_SIMPLE_VALUE = "complexvaluewithbuilder.not_persisted_creator_simple_value";
	public static final String C_NULLABLE_CREATOR_SIMPLE_VALUE = "complexvaluewithbuilder.nullable_creator_simple_value";

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

	public static ComplexValueWithBuilder.Builder builder() {
		return new AutoValue_ComplexValueWithBuilder.Builder();
	}

	public ComplexValueWithBuilder.Builder copy() {
		return new AutoValue_ComplexValueWithBuilder.Builder(this);
	}

	@AutoValue.Builder
	public abstract static class Builder {
		abstract Builder id(long id);

		abstract Builder string(String string);

		abstract Builder nullableString(String string);

		abstract Builder author(Author author);

		abstract Builder notPersistedAuthor(Author author);

		abstract Builder nullableAuthor(Author author);

		abstract Builder complexObjectWithSameLeafs(ComplexObjectWithSameLeafs complexObjectWithSameLeafs);

		abstract Builder notPersistedComplexObjectWithSameLeafs(ComplexObjectWithSameLeafs notPersistedComplexObjectWithSameLeafs);

		abstract Builder builderSimpleValue(SimpleValueWithBuilder simpleValueWithBuilder);

		abstract Builder notPersistedBuilderSimpleValue(SimpleValueWithBuilderAndNullableFields simpleValueWithBuilder);

		abstract Builder nullableBuilderSimpleValue(SimpleValueWithBuilderAndNullableFields simpleValueWithBuilder);

		abstract Builder creatorSimpleValue(SimpleValueWithCreator simpleValueWithCreator);

		abstract Builder notPersistedCreatorSimpleValue(SimpleValueWithCreatorAndNullableFields simpleValueWithCreator);

		abstract Builder nullableCreatorSimpleValue(SimpleValueWithCreatorAndNullableFields simpleValueWithCreator);

		abstract ComplexValueWithBuilder build();
	}

	public static ComplexValueWithBuilder.Builder newRandom() {
		return builder()
				.id(new Random().nextLong())
				.string(Utils.randomTableName())
				.nullableString(Utils.randomTableName())
				.author(Author.newRandom())
				.notPersistedAuthor(Author.newRandom())
				.nullableAuthor(Author.newRandom())
				.complexObjectWithSameLeafs(ComplexObjectWithSameLeafs.newRandom())
				.notPersistedComplexObjectWithSameLeafs(ComplexObjectWithSameLeafs.newRandom())
				.builderSimpleValue(SimpleValueWithBuilder.newRandom().build())
				.notPersistedBuilderSimpleValue(SimpleValueWithBuilderAndNullableFields.newRandom().build())
				.nullableBuilderSimpleValue(SimpleValueWithBuilderAndNullableFields.newRandom().build())
				.creatorSimpleValue(SimpleValueWithCreator.newRandom())
				.notPersistedCreatorSimpleValue(SimpleValueWithCreatorAndNullableFields.newRandom())
				.nullableCreatorSimpleValue(SimpleValueWithCreatorAndNullableFields.newRandom());
	}

	public boolean equalsWithoutId(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof ComplexValueWithBuilder) {
			ComplexValueWithBuilder that = (ComplexValueWithBuilder) o;
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
		if (o instanceof ComplexValueWithBuilder) {
			ComplexValueWithBuilder that = (ComplexValueWithBuilder) o;
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
