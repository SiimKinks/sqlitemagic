package com.siimkinks.sqlitemagic.intellij.plugin.psi;

import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightModifierList;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class SqliteMagicLightModifierList extends LightModifierList {
  private static final Set<String> ALL_MODIFIERS = new HashSet<String>(Arrays.asList(PsiModifier.MODIFIERS));

  private final Map<String, PsiAnnotation> myAnnotations;

  public SqliteMagicLightModifierList(PsiManager manager, final Language language, String... modifiers) {
    super(manager, language, modifiers);
    myAnnotations = new HashMap<String, PsiAnnotation>();
  }

  public void setModifierProperty(@PsiModifier.ModifierConstant @NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
    if (value) {
      addModifier(name);
    } else {
      if (hasModifierProperty(name)) {
        removeModifier(name);
      }
    }
  }

  private void removeModifier(@PsiModifier.ModifierConstant @NotNull @NonNls String name) {
    final Collection<String> myModifiers = collectAllModifiers();
    myModifiers.remove(name);

    clearModifiers();

    for (String modifier : myModifiers) {
      addModifier(modifier);
    }
  }

  private Collection<String> collectAllModifiers() {
    Collection<String> result = new HashSet<String>();
    for (@PsiModifier.ModifierConstant String modifier : ALL_MODIFIERS) {
      if (hasModifierProperty(modifier)) {
        result.add(modifier);
      }
    }
    return result;
  }

  public void checkSetModifierProperty(@PsiModifier.ModifierConstant @NotNull @NonNls String name, boolean value) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  @NotNull
  public PsiAnnotation addAnnotation(@NotNull @NonNls String qualifiedName) {
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    final PsiAnnotation psiAnnotation = elementFactory.createAnnotationFromText('@' + qualifiedName, null);
    myAnnotations.put(qualifiedName, psiAnnotation);
    return psiAnnotation;
  }

  @Override
  public PsiAnnotation findAnnotation(@NotNull String qualifiedName) {
    return myAnnotations.get(qualifiedName);
  }

  @Override
  @NotNull
  public PsiAnnotation[] getAnnotations() {
    PsiAnnotation[] result = PsiAnnotation.EMPTY_ARRAY;
    if (!myAnnotations.isEmpty()) {
      Collection<PsiAnnotation> annotations = myAnnotations.values();
      result = annotations.toArray(new PsiAnnotation[annotations.size()]);
    }
    return result;
  }

  @Override
  public TextRange getTextRange() {
    TextRange r = super.getTextRange();
    return r == null ? TextRange.EMPTY_RANGE : r;
  }

  public String toString() {
    return "SqliteMagicLightModifierList";
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    SqliteMagicLightModifierList that = (SqliteMagicLightModifierList) o;

    if (!myAnnotations.equals(that.myAnnotations)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myAnnotations.hashCode();
  }
}
