package com.siimkinks.sqlitemagic.intellij.plugin.processor.clazz;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.ProblemBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.ProblemEmptyBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.ProblemNewBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.SqliteMagicProblem;
import com.siimkinks.sqlitemagic.intellij.plugin.processor.AbstractProcessor;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiAnnotationUtil;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiClassUtil;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiMethodUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.*;

public abstract class AbstractClassProcessor extends AbstractProcessor implements ClassProcessor {

    protected AbstractClassProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
        super(supportedAnnotationClass, supportedClass);
    }

    @NotNull
    @Override
    public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
        List<? super PsiElement> result = Collections.emptyList();

        PsiAnnotation psiAnnotation = PsiAnnotationUtil.findAnnotation(psiClass, getSupportedAnnotation());
        if (null != psiAnnotation) {
            if (validate(psiAnnotation, psiClass, ProblemEmptyBuilder.getInstance())) {
                result = new ArrayList<PsiElement>();
                generatePsiElements(psiClass, psiAnnotation, result);
            }
        }
        return result;
    }

    @NotNull
    public Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass) {
        Collection<PsiAnnotation> result = new ArrayList<PsiAnnotation>();
        PsiAnnotation psiAnnotation = PsiAnnotationUtil.findAnnotation(psiClass, getSupportedAnnotation());
        if (null != psiAnnotation) {
            result.add(psiAnnotation);
        }
        return result;
    }

    @NotNull
    @Override
    public Collection<SqliteMagicProblem> verifyAnnotation(@NotNull PsiAnnotation psiAnnotation) {
        Collection<SqliteMagicProblem> result = Collections.emptyList();
        // check first for fields, methods and filter it out, because PsiClass is parent of all annotations and will match other parents too
        PsiElement psiElement = PsiTreeUtil.getParentOfType(psiAnnotation, PsiField.class, PsiMethod.class, PsiClass.class);
        if (psiElement instanceof PsiClass) {
            ProblemNewBuilder problemNewBuilder = new ProblemNewBuilder();
            validate(psiAnnotation, (PsiClass) psiElement, problemNewBuilder);
            result = problemNewBuilder.getProblems();
        }

        return result;
    }

    protected abstract boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder);

    protected abstract void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target);

    protected Collection<PsiField> filterFields(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, boolean filterTransient) {

        final Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);

        final Collection<PsiField> result = new ArrayList<PsiField>(psiFields.size());

        for (PsiField classField : psiFields) {
            if (classField.hasModifierProperty(PsiModifier.STATIC) || (filterTransient && classField.hasModifierProperty(PsiModifier.TRANSIENT))) {
                continue;
            }
            result.add(classField);
        }
        return result;
    }

    protected Collection<String> makeSet(@Nullable Collection<String> exclude) {
        if (null == exclude || exclude.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<String>(exclude);
    }
}
