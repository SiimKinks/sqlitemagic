package com.siimkinks.sqlitemagic.intellij.plugin.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightIdentifier;

public class SqliteMagicLightIdentifier extends LightIdentifier {
  protected String myText;

  public SqliteMagicLightIdentifier(PsiManager manager, String text) {
    super(manager, text);
    myText = text;
  }

  @Override
  public String getText() {
    return myText;
  }

  public void setText(String text) {
    myText = text;
  }

  @Override
  public PsiElement copy() {
    return new LightIdentifier(getManager(), getText());
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }
}
