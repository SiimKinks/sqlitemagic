package com.siimkinks.sqlitemagic.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightReferenceListBuilder;

public class SqliteMagicLightReferenceListBuilder extends LightReferenceListBuilder {

  public SqliteMagicLightReferenceListBuilder(PsiManager manager, Language language, Role role) {
    super(manager, language, role);
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }
}
