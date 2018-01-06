package com.siimkinks.sqlitemagic.transformer;

import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CollectionTransformers {
  @ObjectToDbValue
  public static String listOfStringsToDbValue(List<String> strings) {
    if (strings != null) {
      return join(",", strings);
    }
    return null;
  }

  @DbValueToObject
  public static List<String> dbValueToListOfStrings(String dbObject) {
    if (dbObject != null) {
      return Arrays.asList(dbObject.split(","));
    }
    return null;
  }

  @ObjectToDbValue
  public static String listOfIntsToDbValue(List<Integer> ints) {
    if (ints != null) {
      return join(",", ints);
    }
    return null;
  }

  @DbValueToObject
  public static List<Integer> dbValueToListOfInts(String dbObject) {
    if (dbObject != null) {
      final String[] values = dbObject.split(",");
      final int length = values.length;
      final ArrayList<Integer> output = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        final Integer v = Integer.valueOf(values[i]);
        output.add(v);
      }
      return output;
    }
    return null;
  }

  @ObjectToDbValue
  public static String mapToDbValue(Map<String, String> map) {
    if (map != null) {
      final StringBuilder sb = new StringBuilder();
      boolean first = true;
      for (Map.Entry<String, String> entry : map.entrySet()) {
        if (first) {
          first = false;
        } else {
          sb.append(',');
        }
        sb.append(entry.getKey());
        sb.append(":");
        sb.append(entry.getValue());
      }
      return sb.toString();
    }
    return null;
  }

  @DbValueToObject
  public static Map<String, String> dbValueToMap(String dbObject) {
    if (dbObject != null) {
      final HashMap<String, String> output = new HashMap<>();
      final String[] entires = dbObject.split(",");
      for (int i = 0, size = entires.length; i < size; i++) {
        final String entry = entires[i];
        final String[] keyValue = entry.split(":");
        output.put(keyValue[0], keyValue[1]);
      }
      return output;
    }
    return null;
  }

  public static <T> String join(CharSequence delimiter, List<T> vals) {
    final StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (T val : vals) {
      if (first) {
        first = false;
      } else {
        sb.append(delimiter);
      }
      sb.append(val.toString());
    }
    return sb.toString();
  }
}
