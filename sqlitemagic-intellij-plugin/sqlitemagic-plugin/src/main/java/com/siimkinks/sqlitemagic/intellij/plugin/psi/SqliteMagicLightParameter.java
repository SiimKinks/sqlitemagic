package com.siimkinks.sqlitemagic.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.psi.impl.light.LightParameter;
import com.intellij.psi.impl.light.LightVariableBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;

public class SqliteMagicLightParameter extends LightParameter {
  private String myName;
  private final SqliteMagicLightIdentifier myNameIdentifier;

  public SqliteMagicLightParameter(@NotNull String name, @NotNull PsiType type, PsiElement declarationScope, Language language) {
    super(name, type, declarationScope, language);
    myName = name;
    PsiManager manager = declarationScope.getManager();
    myNameIdentifier = new SqliteMagicLightIdentifier(manager, name);
    ReflectionUtil.setFinalFieldPerReflection(LightVariableBuilder.class, this, LightModifierList.class,
            new SqliteMagicLightModifierList(manager, language));
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(@NotNull String name) {
    myName = name;
    myNameIdentifier.setText(name);
    return this;
  }

  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  public SqliteMagicLightParameter setModifiers(String... modifiers) {
    SqliteMagicLightModifierList modifierList = new SqliteMagicLightModifierList(getManager(), getLanguage(), modifiers);
    ReflectionUtil.setFinalFieldPerReflection(LightVariableBuilder.class, this, LightModifierList.class, modifierList);
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SqliteMagicLightParameter that = (SqliteMagicLightParameter) o;

    return getType().isValid() == that.getType().isValid() && getType().equals(that.getType());
  }

  @Override
  public int hashCode() {
    return getType().hashCode();
  }
}
