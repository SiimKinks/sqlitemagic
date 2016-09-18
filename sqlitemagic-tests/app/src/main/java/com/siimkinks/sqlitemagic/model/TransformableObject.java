package com.siimkinks.sqlitemagic.model;

import android.os.Parcel;
import android.os.Parcelable;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TransformableObject implements Parcelable {
	public int value;

	public TransformableObject(int value) {
		this.value = value;
	}

	protected TransformableObject(Parcel in) {
		value = in.readInt();
	}

	public static final Creator<TransformableObject> CREATOR = new Creator<TransformableObject>() {
		@Override
		public TransformableObject createFromParcel(Parcel in) {
			return new TransformableObject(in);
		}

		@Override
		public TransformableObject[] newArray(int size) {
			return new TransformableObject[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeInt(value);
	}
}
