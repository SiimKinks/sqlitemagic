package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.Author;

import java.util.Random;

@AutoValue
@Table(persistAll = true)
public abstract class CreatorMagazine implements ImmutableEquals {
	public static final String TABLE = "creatormagazine";
	public static final String C_ID = "creatormagazine.id";
	public static final String C_NAME = "creatormagazine.name";
	public static final String C_AUTHOR = "creatormagazine.author";
	public static final String C_SIMPLE_VALUE_WITH_BUILDER = "creatormagazine.simplevaluewithbuilder";
	public static final String C_SIMPLE_VALUE_WITH_CREATOR = "creatormagazine.simplevaluewithcreator";

	@Id
	public abstract long id();
	@Nullable
	public abstract String name();
	@Nullable
	public abstract Author author();
	@Nullable
	public abstract SimpleValueWithBuilder simpleValueWithBuilder();
	@Nullable
	public abstract SimpleValueWithCreator simpleValueWithCreator();

	public static CreatorMagazine create(long id,
	                                     String name,
	                                     Author author,
	                                     SimpleValueWithBuilder simpleValueWithBuilder,
	                                     SimpleValueWithCreator simpleValueWithCreator) {
		return new AutoValue_CreatorMagazine(id, name, author, simpleValueWithBuilder, simpleValueWithCreator);
	}

	public boolean equalsWithoutId(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof CreatorMagazine) {
			CreatorMagazine that = (CreatorMagazine) o;
			return ((this.name() == null) ? (that.name() == null) : this.name().equals(that.name()))
					&& ((this.author() == null) ? (that.author() == null) : this.author().equals(that.author()))
					&& ((this.simpleValueWithBuilder() == null) ? (that.simpleValueWithBuilder() == null) : this.simpleValueWithBuilder().equalsWithoutId(that.simpleValueWithBuilder()))
					&& ((this.simpleValueWithCreator() == null) ? (that.simpleValueWithCreator() == null) : this.simpleValueWithCreator().equalsWithoutId(that.simpleValueWithCreator()));
		}
		return false;
	}

	public static CreatorMagazine newRandom() {
		return new AutoValue_CreatorMagazine(
				new Random().nextLong(),
				Utils.randomTableName(),
				Author.newRandom(),
				SimpleValueWithBuilder.newRandom().build(),
				SimpleValueWithCreator.newRandom()
		);
	}
}