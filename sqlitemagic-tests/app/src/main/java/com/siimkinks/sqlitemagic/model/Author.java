package com.siimkinks.sqlitemagic.model;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable;

import com.siimkinks.sqlitemagic.SqliteMagic_Author_Handler.BulkDeleteBuilder;
import com.siimkinks.sqlitemagic.SqliteMagic_Author_Handler.BulkInsertBuilder;
import com.siimkinks.sqlitemagic.SqliteMagic_Author_Handler.BulkPersistBuilder;
import com.siimkinks.sqlitemagic.SqliteMagic_Author_Handler.BulkUpdateBuilder;
import com.siimkinks.sqlitemagic.SqliteMagic_Author_Handler.DeleteBuilder;
import com.siimkinks.sqlitemagic.SqliteMagic_Author_Handler.DeleteTableBuilder;
import com.siimkinks.sqlitemagic.SqliteMagic_Author_Handler.InsertBuilder;
import com.siimkinks.sqlitemagic.SqliteMagic_Author_Handler.PersistBuilder;
import com.siimkinks.sqlitemagic.SqliteMagic_Author_Handler.UpdateBuilder;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Collection;
import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

// TODO use magic generated methods and remove unnecessary code
@Table
@NoArgsConstructor
@ToString
@EqualsAndHashCode
public class Author implements Parcelable, ProvidesId {
  public static final String TABLE = "author";
  public static final String C_ID = "author.id";
  public static final String C_NAME = "author.name";
  public static final String C_BOXED_BOOLEAN = "author.boxed_boolean";
  public static final String C_PRIMITIVE_BOOLEAN = "author.primitive_boolean";

  @Id
  @Column
  public Long id;
  @Column
  public String name;
  @Column
  public Boolean boxedBoolean;
  @Column
  public boolean primitiveBoolean;

  public Author(String name, Boolean boxedBoolean, boolean primitiveBoolean) {
    this.name = name;
    this.boxedBoolean = boxedBoolean;
    this.primitiveBoolean = primitiveBoolean;
  }

  protected Author(Parcel in) {
    name = in.readString();
    primitiveBoolean = in.readByte() != 0;
  }

  public static Author newRandom() {
    final Author author = new Author();
    fillWithRandomValues(author);
    return author;
  }

  public static void fillWithRandomValues(Author author) {
    final Random r = new Random();
    author.id = r.nextLong();
    author.name = Utils.randomTableName();
    author.boxedBoolean = r.nextBoolean();
    author.primitiveBoolean = r.nextBoolean();
  }

  public static Author getFromCursorPosition(Cursor cursor) {
    final int idIndex = cursor.getColumnIndex(C_ID);
    final int nameIndex = cursor.getColumnIndex(C_NAME);
    final int boxedBooleanIndex = cursor.getColumnIndex(C_BOXED_BOOLEAN);
    final Author tmp = new Author();
    tmp.id = cursor.isNull(idIndex) ? null : cursor.getLong(idIndex);
    tmp.name = cursor.isNull(nameIndex) ? null : cursor.getString(nameIndex);
    tmp.boxedBoolean = cursor.isNull(boxedBooleanIndex) ? null : cursor.getInt(boxedBooleanIndex) == 1;
    tmp.primitiveBoolean = cursor.getInt(cursor.getColumnIndex(C_PRIMITIVE_BOOLEAN)) == 1;
    return tmp;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeString(name);
    dest.writeByte((byte) (primitiveBoolean ? 1 : 0));
  }

  public static final Creator<Author> CREATOR = new Creator<Author>() {
    @Override
    public Author createFromParcel(Parcel in) {
      return new Author(in);
    }

    @Override
    public Author[] newArray(int size) {
      return new Author[size];
    }
  };

  @Override
  public Long provideId() {
    return id;
  }

  public InsertBuilder insert() {
    return InsertBuilder.create(this);
  }

  public UpdateBuilder update() {
    return UpdateBuilder.create(this);
  }

  public PersistBuilder persist() {
    return PersistBuilder.create(this);
  }

  public DeleteBuilder delete() {
    return DeleteBuilder.create(this);
  }

  public static DeleteTableBuilder deleteTable() {
    return DeleteTableBuilder.create();
  }

  public static BulkInsertBuilder insert(Iterable<Author> o) {
    return BulkInsertBuilder.create(o);
  }

  public static BulkUpdateBuilder update(Iterable<Author> o) {
    return BulkUpdateBuilder.create(o);
  }

  public static BulkPersistBuilder persist(Iterable<Author> o) {
    return BulkPersistBuilder.create(o);
  }

  public static BulkDeleteBuilder delete(Collection<Author> o) {
    return BulkDeleteBuilder.create(o);
  }
}
