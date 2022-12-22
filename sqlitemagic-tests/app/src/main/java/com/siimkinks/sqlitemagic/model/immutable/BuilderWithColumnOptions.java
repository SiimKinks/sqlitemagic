package com.siimkinks.sqlitemagic.model.immutable;

import android.os.Parcelable;

import androidx.annotation.Nullable;

import com.google.auto.value.AutoValue;
import com.siimkinks.sqlitemagic.ParentAbstractClass;
import com.siimkinks.sqlitemagic.ParentInterface;
import com.siimkinks.sqlitemagic.SqliteMagic_BuilderWithColumnOptions_Handler;
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
@Table(persistAll = true, value = BuilderWithColumnOptions.TABLE)
public abstract class BuilderWithColumnOptions extends ParentAbstractClass implements Parcelable, ParentInterface {
  public static final String TABLE = "builder_w_options";
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

  @Override
  public boolean implementThisInterfaceMethod() {
    return false;
  }

  @Override
  public boolean implementThisMethod() {
    return false;
  }

  public static Builder builder() {
    return new AutoValue_BuilderWithColumnOptions.Builder()
        .ignorePrimVal(0L);
  }

  @IgnoreColumn
  public abstract Builder copy();

  public static BuilderWithColumnOptions newRandom() {
    final Random r = new Random();
    return builder()
        .id(r.nextLong())
        .interfaceParentClassColumn(r.nextBoolean())
        .abstractParentClassColumn(r.nextBoolean())
        .notPersistedAuthor(Author.newRandom())
        .inlineRenamedInt(r.nextInt())
        .constantRenamedInt(r.nextInt())
        .uniqueColumn(r.nextInt())
        .build();
  }

  @AutoValue.Builder
  public static abstract class Builder {
    public abstract Builder id(long id);

    public abstract Builder ignoreAuthor(Author ignoreAuthor);

    public abstract Builder ignoreTransformerObject(TransformableObject ignoreTransformerObject);

    public abstract Builder interfaceParentClassColumn(boolean interfaceParentClassColumn);

    public abstract Builder ignoreNotPersistedModel(NotPersistedModel ignoreNotPersistedModel);

    public abstract Builder ignorePrimVal(long ignorePrimVal);

    public abstract Builder abstractParentClassColumn(boolean abstractParentClassColumn);

    public abstract Builder notPersistedAuthor(Author notPersistedAuthor);

    public abstract Builder inlineRenamedInt(int inlineRenamedInt);

    public abstract Builder constantRenamedInt(int constantRenamedInt);

    public abstract Builder uniqueColumn(int uniqueColumn);

    public abstract BuilderWithColumnOptions build();
  }

  public SqliteMagic_BuilderWithColumnOptions_Handler.InsertBuilder insert() {
    return SqliteMagic_BuilderWithColumnOptions_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_BuilderWithColumnOptions_Handler.UpdateBuilder update() {
    return SqliteMagic_BuilderWithColumnOptions_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_BuilderWithColumnOptions_Handler.PersistBuilder persist() {
    return SqliteMagic_BuilderWithColumnOptions_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_BuilderWithColumnOptions_Handler.DeleteBuilder delete() {
    return SqliteMagic_BuilderWithColumnOptions_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_BuilderWithColumnOptions_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_BuilderWithColumnOptions_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_BuilderWithColumnOptions_Handler.BulkInsertBuilder insert(Iterable<BuilderWithColumnOptions> o) {
    return SqliteMagic_BuilderWithColumnOptions_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_BuilderWithColumnOptions_Handler.BulkUpdateBuilder update(Iterable<BuilderWithColumnOptions> o) {
    return SqliteMagic_BuilderWithColumnOptions_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_BuilderWithColumnOptions_Handler.BulkPersistBuilder persist(Iterable<BuilderWithColumnOptions> o) {
    return SqliteMagic_BuilderWithColumnOptions_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_BuilderWithColumnOptions_Handler.BulkDeleteBuilder delete(Collection<BuilderWithColumnOptions> o) {
    return SqliteMagic_BuilderWithColumnOptions_Handler.BulkDeleteBuilder.create(o);
  }
}