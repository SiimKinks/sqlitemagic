package com.siimkinks.sqlitemagic.model;

import android.os.Parcel;
import android.os.Parcelable;

public final class NotPersistedModel implements Parcelable {
	String str;

	protected NotPersistedModel(Parcel in) {
		str = in.readString();
	}

	public static final Creator<NotPersistedModel> CREATOR = new Creator<NotPersistedModel>() {
		@Override
		public NotPersistedModel createFromParcel(Parcel in) {
			return new NotPersistedModel(in);
		}

		@Override
		public NotPersistedModel[] newArray(int size) {
			return new NotPersistedModel[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(str);
	}
}
