package com.siimkinks.sqlitemagic;

import android.database.sqlite.SQLiteStatement;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.siimkinks.sqlitemagic.internal.SimpleArrayMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Random;

import static com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_BYTES;
import static com.siimkinks.sqlitemagic.internal.ContainerHelpers.EMPTY_PRIMITIVE_BYTES;

/**
 * Internal utility functions.
 */
public final class Utils {
  private static final Random RANDOM = new Random();
  private static final char[] CHAR_SET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
  @VisibleForTesting
  static final int TABLE_NAME_LEN = 6;

  private Utils() {
    throw new AssertionError("no instances");
  }

  @NonNull
  @CheckResult
  public static String randomTableName() {
    final Random r = RANDOM;
    final char[] charSet = CHAR_SET;
    final int charSetLen = charSet.length;
    final char buf[] = new char[TABLE_NAME_LEN];
    for (int i = 0; i < TABLE_NAME_LEN; i++) {
      buf[i] = charSet[r.nextInt(charSetLen)];
    }
    return new String(buf, 0, TABLE_NAME_LEN);
  }

  @NonNull
  @CheckResult
  public static String addTableAlias(@NonNull Table<?> table, @NonNull SimpleArrayMap<String, LinkedList<String>> systemRenamedTables) {
    LinkedList<String> aliases = systemRenamedTables.get(table.name);
    final String nameInQuery = table.nameInQuery;
    if (aliases == null) {
      aliases = new LinkedList<>();
      aliases.add(nameInQuery);
      systemRenamedTables.put(table.name, aliases);
    } else {
      aliases.add(nameInQuery);
    }
    return nameInQuery;
  }

  @Nullable
  @CheckResult
  public static byte[] toByteArray(@Nullable Byte[] array) {
    if (array == null) {
      return null;
    } else if (array.length == 0) {
      return EMPTY_PRIMITIVE_BYTES;
    } else {
      byte[] result = new byte[array.length];

      for (int i = 0, size = array.length; i < size; ++i) {
        result[i] = array[i];
      }
      return result;
    }
  }

  @Nullable
  @CheckResult
  public static Byte[] toByteArray(@Nullable byte[] array) {
    if (array == null) {
      return null;
    } else if (array.length == 0) {
      return EMPTY_BYTES;
    } else {
      Byte[] result = new Byte[array.length];

      for (int i = 0, size = array.length; i < size; ++i) {
        result[i] = array[i];
      }
      return result;
    }
  }

  @NonNull
  @CheckResult
  static <V extends Number> String numericConstantToSqlString(@NonNull V val) {
    final String strVal = val.toString();
    if (val.intValue() < 0) {
      return '(' + strVal + ')';
    }
    return strVal;
  }

  static <V extends Number> ValueParser parserForNumberType(V val) {
    if (val instanceof Long) {
      return LONG_PARSER;
    } else if (val instanceof Integer) {
      return INTEGER_PARSER;
    } else if (val instanceof Short) {
      return SHORT_PARSER;
    } else if (val instanceof Double) {
      return DOUBLE_PARSER;
    } else if (val instanceof Float) {
      return FLOAT_PARSER;
    } else if (val instanceof Byte) {
      return BYTE_PARSER;
    }
    return LONG_PARSER;
  }

  static final ValueParser<String> STRING_PARSER = new ValueParser<String>() {
    @Override
    public String parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getString(0);
    }

    @Override
    public String parseFromStatement(@NonNull SQLiteStatement statement) {
      return statement.simpleQueryForString();
    }
  };

  static final ValueParser<Long> LONG_PARSER = new ValueParser<Long>() {
    @Override
    public Long parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getLong(0);
    }

    @Override
    public Long parseFromStatement(@NonNull SQLiteStatement statement) {
      return statement.simpleQueryForLong();
    }
  };

  static final ValueParser<Long> NULLABLE_LONG_PARSER = new ValueParser<Long>() {
    @Override
    public Long parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getLong(0);
    }

    @Override
    public Long parseFromStatement(@NonNull SQLiteStatement statement) {
      final String rawVal = statement.simpleQueryForString();
      if (rawVal != null) {
        return Long.valueOf(rawVal);
      }
      return null;
    }
  };

  static final ValueParser<Integer> INTEGER_PARSER = new ValueParser<Integer>() {
    @Override
    public Integer parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getInt(0);
    }

    @Override
    public Integer parseFromStatement(@NonNull SQLiteStatement statement) {
      return (int) statement.simpleQueryForLong();
    }
  };

  static final ValueParser<Integer> NULLABLE_INTEGER_PARSER = new ValueParser<Integer>() {
    @Override
    public Integer parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getInt(0);
    }

    @Override
    public Integer parseFromStatement(@NonNull SQLiteStatement statement) {
      final String rawVal = statement.simpleQueryForString();
      if (rawVal != null) {
        return Integer.valueOf(rawVal);
      }
      return null;
    }
  };

  static final ValueParser<Short> SHORT_PARSER = new ValueParser<Short>() {
    @Override
    public Short parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getShort(0);
    }

    @Override
    public Short parseFromStatement(@NonNull SQLiteStatement statement) {
      return (short) statement.simpleQueryForLong();
    }
  };

  static final ValueParser<Short> NULLABLE_SHORT_PARSER = new ValueParser<Short>() {
    @Override
    public Short parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getShort(0);
    }

    @Override
    public Short parseFromStatement(@NonNull SQLiteStatement statement) {
      final String rawVal = statement.simpleQueryForString();
      if (rawVal != null) {
        return Short.valueOf(rawVal);
      }
      return null;
    }
  };

  static final ValueParser<Double> DOUBLE_PARSER = new ValueParser<Double>() {
    @Override
    public Double parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getDouble(0);
    }

    @Override
    public Double parseFromStatement(@NonNull SQLiteStatement statement) {
      final String rawVal = statement.simpleQueryForString();
      if (rawVal != null) {
        return Double.parseDouble(rawVal);
      }
      return null;
    }
  };

  static final ValueParser<Float> FLOAT_PARSER = new ValueParser<Float>() {
    @Override
    public Float parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getFloat(0);
    }

    @Override
    public Float parseFromStatement(@NonNull SQLiteStatement statement) {
      final String rawVal = statement.simpleQueryForString();
      if (rawVal != null) {
        return Float.parseFloat(rawVal);
      }
      return null;
    }
  };

  static final ValueParser<Byte> BYTE_PARSER = new ValueParser<Byte>() {
    @Override
    public Byte parseFromCursor(@NonNull FastCursor fastCursor) {
      return (byte) fastCursor.getLong(0);
    }

    @Override
    public Byte parseFromStatement(@NonNull SQLiteStatement statement) {
      return (byte) statement.simpleQueryForLong();
    }
  };

  static final ValueParser<Byte> NULLABLE_BYTE_PARSER = new ValueParser<Byte>() {
    @Override
    public Byte parseFromCursor(@NonNull FastCursor fastCursor) {
      return (byte) fastCursor.getLong(0);
    }

    @Override
    public Byte parseFromStatement(@NonNull SQLiteStatement statement) {
      final String rawVal = statement.simpleQueryForString();
      if (rawVal != null) {
        return Byte.valueOf(rawVal);
      }
      return null;
    }
  };

  static final ValueParser<byte[]> UNBOXED_BYTE_ARRAY_PARSER = new ValueParser<byte[]>() {
    @Override
    public byte[] parseFromCursor(@NonNull FastCursor fastCursor) {
      return fastCursor.getBlob(0);
    }

    @Override
    public byte[] parseFromStatement(@NonNull SQLiteStatement statement) {
      final ParcelFileDescriptor pfd = statement.simpleQueryForBlobFileDescriptor();
      if (pfd == null) {
        return null;
      }
      final AutoCloseInputStream in = new AutoCloseInputStream(pfd);
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try {
        final byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        try {
          in.close();
        } catch (IOException e) {
          // ignore
        }
      }
      return out.toByteArray();
    }
  };

  static final ValueParser<Byte[]> BOXED_BYTE_ARRAY_PARSER = new ValueParser<Byte[]>() {
    @Override
    public Byte[] parseFromCursor(@NonNull FastCursor fastCursor) {
      return toByteArray(fastCursor.getBlob(0));
    }

    @Override
    public Byte[] parseFromStatement(@NonNull SQLiteStatement statement) {
      final ParcelFileDescriptor pfd = statement.simpleQueryForBlobFileDescriptor();
      if (pfd == null) {
        return null;
      }
      final AutoCloseInputStream in = new AutoCloseInputStream(pfd);
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      try {
        final byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      } finally {
        try {
          in.close();
        } catch (IOException e) {
          // ignore
        }
      }
      // this is bad, mkay
      return toByteArray(out.toByteArray());
    }
  };

  interface ValueParser<T> {
    T parseFromCursor(@NonNull FastCursor fastCursor);

    T parseFromStatement(@NonNull SQLiteStatement statement);
  }
}
