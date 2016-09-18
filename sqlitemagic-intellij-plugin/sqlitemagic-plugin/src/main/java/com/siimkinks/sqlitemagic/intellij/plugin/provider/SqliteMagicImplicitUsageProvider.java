package com.siimkinks.sqlitemagic.intellij.plugin.provider;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.ImplicitUsageProvider;
import com.siimkinks.sqlitemagic.annotation.Table;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class SqliteMagicImplicitUsageProvider implements ImplicitUsageProvider {
    private static final List<Class<? extends Annotation>> ANNOTATIONS = new ArrayList<Class<? extends Annotation>>() {
        {
            add(Table.class);
        }
    };

    private static final Collection<String> FIELD_ANNOTATIONS = new HashSet<String>();
    private static final Collection<String> METHOD_ANNOTATIONS = new HashSet<String>();
    private static final Collection<String> CLASS_ANNOTATIONS = new HashSet<String>();

    public SqliteMagicImplicitUsageProvider() {
        for (Class<? extends Annotation> annotation : ANNOTATIONS) {
            final EnumSet<ElementType> elementTypes = EnumSet.copyOf(Arrays.asList(annotation.getAnnotation(Target.class).value()));
            if (elementTypes.contains(ElementType.FIELD)) {
                FIELD_ANNOTATIONS.add(annotation.getName());
            }
            if (elementTypes.contains(ElementType.METHOD) || elementTypes.contains(ElementType.CONSTRUCTOR)) {
                METHOD_ANNOTATIONS.add(annotation.getName());
            }
            if (elementTypes.contains(ElementType.TYPE)) {
                CLASS_ANNOTATIONS.add(annotation.getName());
            }
        }
    }

    @Override
    public boolean isImplicitUsage(PsiElement element) {
        return checkUsage(element);
    }

    @Override
    public boolean isImplicitRead(PsiElement element) {
        return checkUsage(element);
    }

    @Override
    public boolean isImplicitWrite(PsiElement element) {
        return checkUsage(element);
    }

    private boolean checkUsage(PsiElement element) {
        boolean result = false;
        if (element instanceof PsiField) {
            result = checkAnnotations((PsiModifierListOwner) element, FIELD_ANNOTATIONS);
        } else if (element instanceof PsiMethod) {
            result = checkAnnotations((PsiModifierListOwner) element, METHOD_ANNOTATIONS);
        }
        return result;
    }

    private boolean checkAnnotations(PsiModifierListOwner element, Collection<String> annotations) {
        boolean result;
        result = AnnotationUtil.isAnnotated(element, annotations);
        if (!result) {
            final PsiClass containingClass = ((PsiMember) element).getContainingClass();
            if (null != containingClass) {
                result = AnnotationUtil.isAnnotated(containingClass, CLASS_ANNOTATIONS);
            }
        }
        return result;
    }
}
