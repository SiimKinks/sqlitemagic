package com.siimkinks.sqlitemagic.intellij.plugin.processor.method;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.ProblemBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.ProblemEmptyBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.ProblemNewBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.SqliteMagicProblem;
import com.siimkinks.sqlitemagic.intellij.plugin.processor.AbstractProcessor;
import com.siimkinks.sqlitemagic.intellij.plugin.processor.Processor;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiAnnotationUtil;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiClassUtil;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class AbstractMethodProcessor extends AbstractProcessor implements Processor {

  protected AbstractMethodProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
    super(supportedAnnotationClass, supportedClass);
  }

  @NotNull
  @Override
  public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
    List<? super PsiElement> result = new ArrayList<PsiElement>();
    for (PsiMethod psiMethod : PsiClassUtil.collectClassMethodsIntern(psiClass)) {
      PsiAnnotation psiAnnotation = PsiAnnotationUtil.findAnnotation(psiMethod, getSupportedAnnotation());
      if (null != psiAnnotation) {
        if (validate(psiAnnotation, psiMethod, ProblemEmptyBuilder.getInstance())) {
          processIntern(psiMethod, psiAnnotation, result);
        }
      }
    }
    return result;
  }

  @NotNull
  public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
    List<PsiAnnotation> result = new ArrayList<PsiAnnotation>();
    for (PsiMethod psiMethod : PsiClassUtil.collectClassMethodsIntern(psiClass)) {
      PsiAnnotation psiAnnotation = PsiAnnotationUtil.findAnnotation(psiMethod, getSupportedAnnotation());
      if (null != psiAnnotation) {
        result.add(psiAnnotation);
      }
    }
    return result;
  }

  @NotNull
  @Override
  public Collection<SqliteMagicProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
    Collection<SqliteMagicProblem> result = Collections.emptyList();

    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(psiAnnotation, PsiMethod.class);
    if (null != psiMethod) {
      ProblemNewBuilder problemNewBuilder = new ProblemNewBuilder();
      validate(psiAnnotation, psiMethod, problemNewBuilder);
      result = problemNewBuilder.getProblems();
    }

    return result;
  }

  protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiMethod psiMethod, @NotNull ProblemBuilder builder);

  protected abstract void processIntern(PsiMethod psiMethod, PsiAnnotation psiAnnotation, List<? super PsiElement> target);
}
