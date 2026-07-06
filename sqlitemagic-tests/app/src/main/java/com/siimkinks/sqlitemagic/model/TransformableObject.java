package com.siimkinks.sqlitemagic.model;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TransformableObject that = (TransformableObject) o;
    return value == that.value;
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  @Override
  public String toString() {
    return "TransformableObject{" +
        "value=" + value +
        '}';
  }
}
