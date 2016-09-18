package com.siimkinks.sqlitemagic.model.immutable;

import android.support.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.model.TransformableObject;

import java.util.Random;

@Table(persistAll = true)
@AutoValue
public abstract class SimpleValueWithBuilder implements ImmutableEquals {
	public static final String TABLE = "simplevaluewithbuilder";
	public static final String C_ID = "simplevaluewithbuilder.id";
	public static final String C_STRING_VALUE = "simplevaluewithbuilder.string_value";

	@Id
	@Nullable
	public abstract Long id();
	public abstract String stringValue();
	abstract Boolean boxedBoolean();
	public abstract boolean aBoolean();
	public abstract int integer();
	abstract TransformableObject transformableObject();

	public static Builder builder() {
		return new AutoValue_SimpleValueWithBuilder.Builder();
	}

	@AutoValue.Builder
	public static abstract class Builder {
		public abstract Builder id(@Nullable Long id);
		public abstract Builder stringValue(String stringValue);
		public abstract Builder boxedBoolean(Boolean boxedBoolean);
		public abstract Builder aBoolean(boolean aBoolean);
		public abstract Builder integer(int integer);
		public abstract Builder transformableObject(TransformableObject transformableObject);

		public abstract SimpleValueWithBuilder build();
	}

	public SimpleValueWithBuilder.Builder copy() {
		return new AutoValue_SimpleValueWithBuilder.Builder(this);
	}

	public static SimpleValueWithBuilder.Builder newRandom() {
		final Random random = new Random();
		return SimpleValueWithBuilder.builder()
				.stringValue(Utils.randomTableName())
				.boxedBoolean(random.nextBoolean())
				.aBoolean(random.nextBoolean())
				.integer(random.nextInt())
				.transformableObject(new TransformableObject(random.nextInt()));
	}

	public boolean equalsWithoutId(Object o) {
		if (o == this) {
			return true;
		}
		if (o instanceof SimpleValueWithBuilder) {
			SimpleValueWithBuilder that = (SimpleValueWithBuilder) o;
			return (this.stringValue().equals(that.stringValue()))
					&& (this.boxedBoolean().equals(that.boxedBoolean()))
					&& (this.aBoolean() == that.aBoolean())
					&& (this.integer() == that.integer())
					&& (this.transformableObject().equals(that.transformableObject()));
		}
		return false;
	}
}
