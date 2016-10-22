package com.siimkinks.sqlitemagic.util;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;

import java.util.Arrays;

public final class StringUtil {
  private StringUtil() {
    throw new AssertionError("no instances");
  }

  @NonNull
  @CheckResult
  public static String replaceCamelCaseWithUnderscore(String input) {
    final int length = input.length();
    StringBuilder sb = new StringBuilder(length + 4);
    sb.append(input.charAt(0));
    for (int i = 1; i < length; i++) {
      char c = input.charAt(i);
      if (Character.isUpperCase(c)) {
        c = Character.toLowerCase(c);
        sb.append('_');
      }
      sb.append(c);
    }
    return sb.toString();
  }

  @NonNull
  @CheckResult
  public static String firstCharToUpperCase(String str) {
    char upperCaseFirstLetter = Character.toUpperCase(str.charAt(0));
    return upperCaseFirstLetter + str.substring(1);
  }

  @NonNull
  @CheckResult
  public static String firstCharToLowerCase(String str) {
    char upperCaseFirstLetter = Character.toLowerCase(str.charAt(0));
    return upperCaseFirstLetter + str.substring(1);
  }

  public static void join(@NonNull CharSequence delimiter, @NonNull Object[] tokens, @NonNull StringBuilder stringBuilder) {
    boolean firstTime = true;
    for (Object token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        stringBuilder.append(delimiter);
      }
      stringBuilder.append(token);
    }
  }

  public static void join(@NonNull CharSequence delimiter, @NonNull Iterable tokens, @NonNull StringBuilder stringBuilder) {
    boolean firstTime = true;
    for (Object token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        stringBuilder.append(delimiter);
      }
      stringBuilder.append(token);
    }
  }

  @NonNull
  @CheckResult
  public static String join(@NonNull CharSequence delimiter, @NonNull Object[] tokens) {
    StringBuilder sb = new StringBuilder();
    join(delimiter, tokens, sb);
    return sb.toString();
  }

  @NonNull
  @CheckResult
  public static String join(@NonNull CharSequence delimiter, @NonNull Iterable tokens) {
    StringBuilder sb = new StringBuilder();
    boolean firstTime = true;
    for (Object token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(delimiter);
      }
      sb.append(token);
    }
    return sb.toString();
  }

  @NonNull
  @CheckResult
  public static <T> String join(@NonNull CharSequence delimiter, @NonNull T[] tokens, @NonNull ToStringCallback<T> callback) {
    return join(delimiter, Arrays.asList(tokens), new StringBuilder(), callback);
  }

  @NonNull
  @CheckResult
  public static <T> String join(@NonNull CharSequence delimiter, @NonNull Iterable<T> tokens, @NonNull ToStringCallback<T> callback) {
    return join(delimiter, tokens, new StringBuilder(), callback);
  }

  @NonNull
  @CheckResult
  public static <T> String join(@NonNull CharSequence delimiter, @NonNull Iterable<T> tokens, @NonNull StringBuilder sb, @NonNull ToStringCallback<T> callback) {
    boolean firstTime = true;
    for (T token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(delimiter);
      }
      sb.append(callback.toString(token));
    }
    return sb.toString();
  }

  @NonNull
  @CheckResult
  public static <T> String join(@NonNull CharSequence delimiter, @NonNull Iterable<T> tokens, @NonNull StringBuilder sb, @NonNull AppendCallback<T> callback) {
    boolean firstTime = true;
    for (T token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(delimiter);
      }
      callback.append(sb, token);
    }
    return sb.toString();
  }

  @NonNull
  @CheckResult
  public static String join(@NonNull CharSequence delimiter, @NonNull CharSequence surroundWith, @NonNull Iterable tokens) {
    StringBuilder sb = new StringBuilder();
    boolean firstTime = true;
    for (Object token : tokens) {
      if (firstTime) {
        firstTime = false;
      } else {
        sb.append(delimiter);
      }
      sb.append(surroundWith);
      sb.append(token);
      sb.append(surroundWith);
    }
    return sb.toString();
  }

  public static void append(@NonNull String delimiter, @NonNull String value, int times, @NonNull StringBuilder sb) {
    for (int i = 0; i < times; i++) {
      if (i > 0) {
        sb.append(delimiter);
      }
      sb.append(value);
    }
  }

  public interface ToStringCallback<T> {
    @NonNull
    @CheckResult
    String toString(@NonNull T obj);
  }

  public interface AppendCallback<T> {
    void append(@NonNull StringBuilder sb, @NonNull T obj);
  }
}
