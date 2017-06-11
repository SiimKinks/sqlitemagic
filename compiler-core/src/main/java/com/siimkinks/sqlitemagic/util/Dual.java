package com.siimkinks.sqlitemagic.util;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Siim Kinks
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Dual<T1, T2> {
  private final T1 first;
  private final T2 second;

  public static <T1, T2> Dual<T1, T2> create(T1 first, T2 second) {
    return new Dual<T1, T2>(first, second);
  }

  public static <T1, T2> Dual<T1, T2> empty() {
    return create(null, null);
  }
}
