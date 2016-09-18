package com.siimkinks.sqlitemagic.transformer;

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.annotation.transformer.Transformer;
import com.siimkinks.sqlitemagic.model.Author;
import com.siimkinks.sqlitemagic.model.Book;

@Transformer
public final class TransformerWithGenerics<T extends Author> {
	@ObjectToDbValue
	public static Integer objectToDbValue(TransformableObject javaObject) {
		return javaObject.value;
	}

	@DbValueToObject
	public static TransformableObject dbValueToObject(Integer dbObject) {
		return new TransformableObject(dbObject);
	}

	@Transformer
	public static final class InnerTransformerWithGenerics<V extends Book> {
		@ObjectToDbValue
		public static Integer objectToDbValue(TransformableObject2 javaObject) {
			return javaObject.value;
		}

		@DbValueToObject
		public static TransformableObject2 dbValueToObject(Integer dbObject) {
			return new TransformableObject2(dbObject);
		}

		@Transformer
		public static final class DoubleInnerTransformer {
			@ObjectToDbValue
			public static Integer objectToDbValue(TransformableObject3 javaObject) {
				return javaObject.value;
			}

			@DbValueToObject
			public static TransformableObject3 dbValueToObject(Integer dbObject) {
				return new TransformableObject3(dbObject);
			}
		}
	}

	@Transformer
	public static final class TransformerForObjectWithGenerics {
		@ObjectToDbValue
		public static String objectToDbValue(TransformableObjectWithGenerics javaObject) {
			return javaObject.value.toString();
		}

		@DbValueToObject
		public static TransformableObjectWithGenerics dbValueToObject(String dbObject) {
			return new TransformableObjectWithGenerics(null);
		}
	}

	public static final class TransformableObject {
		public int value;

		public TransformableObject(int value) {
			this.value = value;
		}
	}

	public static final class TransformableObject2 {
		public int value;

		public TransformableObject2(int value) {
			this.value = value;
		}
	}

	public static final class TransformableObject3 {
		public int value;

		public TransformableObject3(int value) {
			this.value = value;
		}
	}

	public static final class TransformableObjectWithGenerics<T> {
		public T value;

		public TransformableObjectWithGenerics(T value) {
			this.value = value;
		}
	}
}
