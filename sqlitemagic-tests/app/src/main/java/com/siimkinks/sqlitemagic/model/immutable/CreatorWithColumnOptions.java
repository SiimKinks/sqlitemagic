package com.siimkinks.sqlitemagic.model.immutable;

import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.ParentAbstractClass;
import com.siimkinks.sqlitemagic.ParentInterface;
import com.siimkinks.sqlitemagic.SqliteMagic_CreatorWithColumnOptions_Handler;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.IgnoreColumn;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.Unique;
import com.siimkinks.sqlitemagic.model.Author;
import com.siimkinks.sqlitemagic.model.NotPersistedModel;
import com.siimkinks.sqlitemagic.model.TransformableObject;

import java.util.Collection;
import java.util.Random;

@AutoValue
@Table(persistAll = true, value = CreatorWithColumnOptions.TABLE)
public abstract class CreatorWithColumnOptions extends ParentAbstractClass implements Parcelable, ParentInterface {
  public static final String TABLE = "creator_w_options";
  public static final String CONST_INT = "const_int";

  @Id(autoIncrement = false)
  public abstract long id();

  @Nullable
  @IgnoreColumn
  public abstract Author ignoreAuthor();

  @Column(handleRecursively = false)
  public abstract Author notPersistedAuthor();

  @Column("inline_int")
  public abstract int inlineRenamedInt();

  @Column(CONST_INT)
  public abstract int constantRenamedInt();

  @Unique
  public abstract int uniqueColumn();

  @Nullable
  @IgnoreColumn
  public abstract TransformableObject ignoreTransformerObject();

  @Nullable
  @IgnoreColumn
  public abstract NotPersistedModel ignoreNotPersistedModel();

  @IgnoreColumn
  public abstract long ignorePrimVal();

  public static CreatorWithColumnOptions newRandom() {
    final Random r = new Random();
    return new AutoValue_CreatorWithColumnOptions(
        r.nextBoolean(),
        r.nextBoolean(),
        r.nextLong(),
        Author.newRandom(),
        Author.newRandom(),
        r.nextInt(),
        r.nextInt(),
        r.nextInt(),
        null,
        null,
        r.nextLong());
  }

  public static CreatorWithColumnOptions newRandomWithUniqueColumn(int uniqueColumn) {
    final Random r = new Random();
    return new AutoValue_CreatorWithColumnOptions(
        r.nextBoolean(),
        r.nextBoolean(),
        r.nextLong(),
        Author.newRandom(),
        Author.newRandom(),
        r.nextInt(),
        r.nextInt(),
        uniqueColumn,
        null,
        null,
        r.nextLong());
  }

  public CreatorWithColumnOptions minimalCopy() {
    final Author author = new Author();
    author.id = notPersistedAuthor().id;
    return new AutoValue_CreatorWithColumnOptions(
        interfaceParentClassColumn(),
        abstractParentClassColumn(),
        id(),
        null,
        author,
        inlineRenamedInt(),
        constantRenamedInt(),
        uniqueColumn(),
        null,
        null,
        0L);
  }

  public CreatorWithColumnOptions setId(long id) {
    return new AutoValue_CreatorWithColumnOptions(
        interfaceParentClassColumn(),
        abstractParentClassColumn(),
        id,
        ignoreAuthor(),
        notPersistedAuthor(),
        inlineRenamedInt(),
        constantRenamedInt(),
        uniqueColumn(),
        ignoreTransformerObject(),
        ignoreNotPersistedModel(),
        ignorePrimVal()
    );
  }

  public CreatorWithColumnOptions setUniqueColumn(int uniqueColumn) {
    return new AutoValue_CreatorWithColumnOptions(
        interfaceParentClassColumn(),
        abstractParentClassColumn(),
        id(),
        ignoreAuthor(),
        notPersistedAuthor(),
        inlineRenamedInt(),
        constantRenamedInt(),
        uniqueColumn,
        ignoreTransformerObject(),
        ignoreNotPersistedModel(),
        ignorePrimVal()
    );
  }

  @Override
  public boolean implementThisInterfaceMethod() {
    return false;
  }

  @Override
  public boolean implementThisMethod() {
    return false;
  }

  public SqliteMagic_CreatorWithColumnOptions_Handler.InsertBuilder insert() {
    return SqliteMagic_CreatorWithColumnOptions_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_CreatorWithColumnOptions_Handler.UpdateBuilder update() {
    return SqliteMagic_CreatorWithColumnOptions_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_CreatorWithColumnOptions_Handler.PersistBuilder persist() {
    return SqliteMagic_CreatorWithColumnOptions_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_CreatorWithColumnOptions_Handler.DeleteBuilder delete() {
    return SqliteMagic_CreatorWithColumnOptions_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_CreatorWithColumnOptions_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_CreatorWithColumnOptions_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_CreatorWithColumnOptions_Handler.BulkInsertBuilder insert(Iterable<CreatorWithColumnOptions> o) {
    return SqliteMagic_CreatorWithColumnOptions_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_CreatorWithColumnOptions_Handler.BulkUpdateBuilder update(Iterable<CreatorWithColumnOptions> o) {
    return SqliteMagic_CreatorWithColumnOptions_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_CreatorWithColumnOptions_Handler.BulkPersistBuilder persist(Iterable<CreatorWithColumnOptions> o) {
    return SqliteMagic_CreatorWithColumnOptions_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_CreatorWithColumnOptions_Handler.BulkDeleteBuilder delete(Collection<CreatorWithColumnOptions> o) {
    return SqliteMagic_CreatorWithColumnOptions_Handler.BulkDeleteBuilder.create(o);
  }
}