package com.siimkinks.sqlitemagic.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightParameterListBuilder;

import java.util.Arrays;

public class SqliteMagicLightParameterListBuilder extends LightParameterListBuilder {
  public SqliteMagicLightParameterListBuilder(PsiManager manager, Language language) {
    super(manager, language);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SqliteMagicLightParameterListBuilder that = (SqliteMagicLightParameterListBuilder) o;

    return Arrays.equals(getParameters(), that.getParameters());
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(getParameters());
  }
}
