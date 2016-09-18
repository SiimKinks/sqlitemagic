package com.siimkinks.sqlitemagic.intellij.plugin.processor;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siimkinks.sqlitemagic.intellij.plugin.processor.Processor;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiAnnotationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public abstract class AbstractProcessor implements Processor {
    /**
     * Anntotation qualified name this processor supports
     */
    private final String supportedAnnotation;
    /**
     * Anntotation class this processor supports
     */
    private final Class<? extends Annotation> supportedAnnotationClass;
    /**
     * Kind of output elements this processor supports
     */
    private final Class<? extends PsiElement> supportedClass;

    /**
     * Constructor for all Lombok-Processors
     *
     * @param supportedAnnotationClass annotation this processor supports
     * @param supportedClass           kind of output elements this processor supports
     */
    protected AbstractProcessor(@NotNull Class<? extends Annotation> supportedAnnotationClass, @NotNull Class<? extends PsiElement> supportedClass) {
        this.supportedAnnotationClass = supportedAnnotationClass;
        this.supportedAnnotation = supportedAnnotationClass.getName();
        this.supportedClass = supportedClass;
    }

    @NotNull
    @Override
    public final String getSupportedAnnotation() {
        return supportedAnnotation;
    }

    @NotNull
    @Override
    public final Class<? extends Annotation> getSupportedAnnotationClass() {
        return supportedAnnotationClass;
    }

    @NotNull
    @Override
    public final Class<? extends PsiElement> getSupportedClass() {
        return supportedClass;
    }

    public boolean acceptAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull Class<? extends PsiElement> type) {
        final String annotationName = StringUtil.notNullize(psiAnnotation.getQualifiedName()).trim();
        return supportedAnnotation.equals(annotationName) && canProduce(type);
    }

    @Override
    public boolean isEnabled(@NotNull Project project) {
        return true;//TODO make it configurable
    }

    @Override
    public boolean canProduce(@NotNull Class<? extends PsiElement> type) {
        return type.isAssignableFrom(supportedClass);
    }

    @NotNull
    public List<? super PsiElement> process(@NotNull PsiClass psiClass) {
        return Collections.emptyList();
    }

    @NotNull
    public abstract Collection<PsiAnnotation> collectProcessedAnnotations(@NotNull PsiClass psiClass);

    @Nullable
    public static Boolean readAnnotation(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass,
                                                         @NotNull String annotationParameter) {
        final boolean result;
        return PsiAnnotationUtil.getDeclaredBooleanAnnotationValue(psiAnnotation, annotationParameter);
    }
}
