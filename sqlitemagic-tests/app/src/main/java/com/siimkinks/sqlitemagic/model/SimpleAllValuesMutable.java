package com.siimkinks.sqlitemagic.model;

import androidx.annotation.NonNull;

import com.siimkinks.sqlitemagic.SqliteMagic_SimpleAllValuesMutable_Handler;
import com.siimkinks.sqlitemagic.Utils;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Random;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@NoArgsConstructor
@Getter
@Setter
@Table(persistAll = true)
public class SimpleAllValuesMutable {
  @Id
  long id;
  String string;
  short primitiveShort;
  Short boxedShort;
  long primitiveLong;
  Long boxedLong;
  int primitiveInt;
  Integer boxedInteger;
  float primitiveFloat;
  Float boxedFloat;
  double primitiveDouble;
  Double boxedDouble;
  byte primitiveByte;
  Byte boxedByte;
  byte[] primitiveByteArray;
  Byte[] boxedByteArray;
  boolean primitiveBoolean;
  Boolean boxedBoolean;
  Calendar calendar;
  Date utilDate;

  public static SimpleAllValuesMutable newRandom() {
    return fillWithRandomValues(new SimpleAllValuesMutable());
  }

  @NonNull
  private static SimpleAllValuesMutable fillWithRandomValues(@NonNull SimpleAllValuesMutable object) {
    final Random r = new Random();
    object.string = Utils.randomTableName();
    object.primitiveShort = (short) r.nextInt(Short.MAX_VALUE + 1);
    object.boxedShort = (short) r.nextInt(Short.MAX_VALUE + 1);
    object.primitiveLong = r.nextLong();
    object.boxedLong = r.nextLong();
    object.primitiveInt = r.nextInt();
    object.boxedInteger = r.nextInt();
    object.primitiveFloat = r.nextFloat();
    object.boxedFloat = r.nextFloat();
    object.primitiveDouble = r.nextDouble();
    object.boxedDouble = r.nextDouble();
    final byte[] b = new byte[1];
    r.nextBytes(b);
    object.primitiveByte = b[0];
    r.nextBytes(b);
    object.boxedByte = b[0];
    final byte[] byteArray = new byte[4];
    r.nextBytes(byteArray);
    object.primitiveByteArray = byteArray;
    r.nextBytes(byteArray);
    object.boxedByteArray = Utils.toByteArray(byteArray);
    object.primitiveBoolean = r.nextBoolean();
    object.boxedBoolean = r.nextBoolean();
    object.calendar = Calendar.getInstance();
    object.utilDate = new Date(Math.abs(r.nextLong()));
    return object;
  }

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public String getString() {
    return string;
  }

  public void setString(String string) {
    this.string = string;
  }

  public short getPrimitiveShort() {
    return primitiveShort;
  }

  public void setPrimitiveShort(short primitiveShort) {
    this.primitiveShort = primitiveShort;
  }

  public Short getBoxedShort() {
    return boxedShort;
  }

  public void setBoxedShort(Short boxedShort) {
    this.boxedShort = boxedShort;
  }

  public long getPrimitiveLong() {
    return primitiveLong;
  }

  public void setPrimitiveLong(long primitiveLong) {
    this.primitiveLong = primitiveLong;
  }

  public Long getBoxedLong() {
    return boxedLong;
  }

  public void setBoxedLong(Long boxedLong) {
    this.boxedLong = boxedLong;
  }

  public int getPrimitiveInt() {
    return primitiveInt;
  }

  public void setPrimitiveInt(int primitiveInt) {
    this.primitiveInt = primitiveInt;
  }

  public Integer getBoxedInteger() {
    return boxedInteger;
  }

  public void setBoxedInteger(Integer boxedInteger) {
    this.boxedInteger = boxedInteger;
  }

  public float getPrimitiveFloat() {
    return primitiveFloat;
  }

  public void setPrimitiveFloat(float primitiveFloat) {
    this.primitiveFloat = primitiveFloat;
  }

  public Float getBoxedFloat() {
    return boxedFloat;
  }

  public void setBoxedFloat(Float boxedFloat) {
    this.boxedFloat = boxedFloat;
  }

  public double getPrimitiveDouble() {
    return primitiveDouble;
  }

  public void setPrimitiveDouble(double primitiveDouble) {
    this.primitiveDouble = primitiveDouble;
  }

  public Double getBoxedDouble() {
    return boxedDouble;
  }

  public void setBoxedDouble(Double boxedDouble) {
    this.boxedDouble = boxedDouble;
  }

  public byte getPrimitiveByte() {
    return primitiveByte;
  }

  public void setPrimitiveByte(byte primitiveByte) {
    this.primitiveByte = primitiveByte;
  }

  public Byte getBoxedByte() {
    return boxedByte;
  }

  public void setBoxedByte(Byte boxedByte) {
    this.boxedByte = boxedByte;
  }

  public byte[] getPrimitiveByteArray() {
    return primitiveByteArray;
  }

  public void setPrimitiveByteArray(byte[] primitiveByteArray) {
    this.primitiveByteArray = primitiveByteArray;
  }

  public Byte[] getBoxedByteArray() {
    return boxedByteArray;
  }

  public void setBoxedByteArray(Byte[] boxedByteArray) {
    this.boxedByteArray = boxedByteArray;
  }

  public boolean isPrimitiveBoolean() {
    return primitiveBoolean;
  }

  public void setPrimitiveBoolean(boolean primitiveBoolean) {
    this.primitiveBoolean = primitiveBoolean;
  }

  public Boolean getBoxedBoolean() {
    return boxedBoolean;
  }

  public void setBoxedBoolean(Boolean boxedBoolean) {
    this.boxedBoolean = boxedBoolean;
  }

  public Calendar getCalendar() {
    return calendar;
  }

  public void setCalendar(Calendar calendar) {
    this.calendar = calendar;
  }

  public Date getUtilDate() {
    return utilDate;
  }

  public void setUtilDate(Date utilDate) {
    this.utilDate = utilDate;
  }

  public SqliteMagic_SimpleAllValuesMutable_Handler.InsertBuilder insert() {
    return SqliteMagic_SimpleAllValuesMutable_Handler.InsertBuilder.create(this);
  }

  public SqliteMagic_SimpleAllValuesMutable_Handler.UpdateBuilder update() {
    return SqliteMagic_SimpleAllValuesMutable_Handler.UpdateBuilder.create(this);
  }

  public SqliteMagic_SimpleAllValuesMutable_Handler.PersistBuilder persist() {
    return SqliteMagic_SimpleAllValuesMutable_Handler.PersistBuilder.create(this);
  }

  public SqliteMagic_SimpleAllValuesMutable_Handler.DeleteBuilder delete() {
    return SqliteMagic_SimpleAllValuesMutable_Handler.DeleteBuilder.create(this);
  }

  public static SqliteMagic_SimpleAllValuesMutable_Handler.DeleteTableBuilder deleteTable() {
    return SqliteMagic_SimpleAllValuesMutable_Handler.DeleteTableBuilder.create();
  }

  public static SqliteMagic_SimpleAllValuesMutable_Handler.BulkInsertBuilder insert(Iterable<SimpleAllValuesMutable> o) {
    return SqliteMagic_SimpleAllValuesMutable_Handler.BulkInsertBuilder.create(o);
  }

  public static SqliteMagic_SimpleAllValuesMutable_Handler.BulkUpdateBuilder update(Iterable<SimpleAllValuesMutable> o) {
    return SqliteMagic_SimpleAllValuesMutable_Handler.BulkUpdateBuilder.create(o);
  }

  public static SqliteMagic_SimpleAllValuesMutable_Handler.BulkPersistBuilder persist(Iterable<SimpleAllValuesMutable> o) {
    return SqliteMagic_SimpleAllValuesMutable_Handler.BulkPersistBuilder.create(o);
  }

  public static SqliteMagic_SimpleAllValuesMutable_Handler.BulkDeleteBuilder delete(Collection<SimpleAllValuesMutable> o) {
    return SqliteMagic_SimpleAllValuesMutable_Handler.BulkDeleteBuilder.create(o);
  }
}
