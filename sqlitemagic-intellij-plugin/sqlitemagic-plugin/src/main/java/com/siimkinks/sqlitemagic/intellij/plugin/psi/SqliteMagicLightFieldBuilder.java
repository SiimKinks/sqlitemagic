package com.siimkinks.sqlitemagic.intellij.plugin.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.IncorrectOperationException;
import com.siimkinks.sqlitemagic.intellij.plugin.icon.SqliteMagicIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SqliteMagicLightFieldBuilder extends LightFieldBuilder {
  protected String myName;
  protected final SqliteMagicLightIdentifier myNameIdentifier;

  public SqliteMagicLightFieldBuilder(@NotNull PsiManager manager, @NotNull String name, @NotNull PsiType type) {
    super(manager, name, type);
    myName = name;
    myNameIdentifier = new SqliteMagicLightIdentifier(manager, name);
    setBaseIcon(SqliteMagicIcons.FIELD_ICON);
  }

  public SqliteMagicLightFieldBuilder withContainingClass(PsiClass psiClass) {
    setContainingClass(psiClass);
    return this;
  }

  public SqliteMagicLightFieldBuilder withModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String modifier) {
    ((LightModifierList) getModifierList()).addModifier(modifier);
    return this;
  }

  public SqliteMagicLightFieldBuilder withNavigationElement(PsiElement navigationElement) {
    setNavigationElement(navigationElement);
    return this;
  }

  public SqliteMagicLightFieldBuilder withInitialValue(PsiExpression initializer) {
    setInitializer(initializer);
    return this;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public PsiElement setName(@NotNull String name) {
    myName = name;
    myNameIdentifier.setText(myName);
    return this;
  }

  @NotNull
  @Override
  public PsiIdentifier getNameIdentifier() {
    return myNameIdentifier;
  }

  public String toString() {
    return "SqliteMagicLightFieldBuilder: " + getName();
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    // just add new element to the containing class
    final PsiClass containingClass = getContainingClass();
    if (null != containingClass) {
      CheckUtil.checkWritable(containingClass);
      return containingClass.add(newElement);
    }
    return null;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  @Override
  public void delete() throws IncorrectOperationException {
    // simple do nothing
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    // simple do nothing
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    if (another instanceof SqliteMagicLightFieldBuilder) {
      final SqliteMagicLightFieldBuilder anotherLightField = (SqliteMagicLightFieldBuilder) another;

      boolean stillEquivalent = getName().equals(anotherLightField.getName()) &&
          getType().equals(anotherLightField.getType());

      if (stillEquivalent) {
        final PsiClass containingClass = getContainingClass();
        final PsiClass anotherContainingClass = anotherLightField.getContainingClass();

        stillEquivalent = (null == containingClass && null == anotherContainingClass) ||
            (null != containingClass && containingClass.isEquivalentTo(anotherContainingClass));
      }

      return stillEquivalent;
    } else {
      return super.isEquivalentTo(another);
    }
  }
}
