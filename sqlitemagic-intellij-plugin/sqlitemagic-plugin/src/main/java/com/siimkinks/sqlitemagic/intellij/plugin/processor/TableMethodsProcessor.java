package com.siimkinks.sqlitemagic.intellij.plugin.processor;

import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.ProblemBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.processor.clazz.AbstractClassProcessor;
import com.siimkinks.sqlitemagic.intellij.plugin.psi.SqliteMagicLightMethodBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiAnnotationUtil;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiClassUtil;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiMethodUtil;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.siimkinks.sqlitemagic.GlobalConst.*;
import static com.siimkinks.sqlitemagic.intellij.plugin.processor.TableMethodsProcessor.BulkCollection.COLLECTION;
import static com.siimkinks.sqlitemagic.intellij.plugin.processor.TableMethodsProcessor.BulkCollection.ITERABLE;
import static com.siimkinks.sqlitemagic.util.NameConst.*;

public class TableMethodsProcessor extends AbstractClassProcessor {

    enum BulkCollection {
        ITERABLE("java.lang.Iterable"),
        COLLECTION("java.util.Collection");

        private final String qualifiedName;

        BulkCollection(String qualifiedName) {
            this.qualifiedName = qualifiedName;
        }

        private PsiType parameterType(PsiClass psiClass) {
            final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
            return elementFactory.createTypeFromText(qualifiedName + "<" + psiClass.getName() + ">", psiClass);
        }
    }

    protected TableMethodsProcessor() {
        super(Table.class, PsiMethod.class);
    }

    @Override
    protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
        // TODO implement
        return true;
    }

    @Override
    protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
        final Collection<PsiMethod> classMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
        addMethod(psiClass, psiAnnotation, target, classMethods, CLASS_INSERT, METHOD_INSERT);
        addMethod(psiClass, psiAnnotation, target, classMethods, CLASS_UPDATE, METHOD_UPDATE);
        addMethod(psiClass, psiAnnotation, target, classMethods, CLASS_PERSIST, METHOD_PERSIST);
        addMethod(psiClass, psiAnnotation, target, classMethods, CLASS_DELETE, METHOD_DELETE);
        addBulkMethod(psiClass, psiAnnotation, target, classMethods, CLASS_BULK_INSERT, METHOD_INSERT, ITERABLE);
        addBulkMethod(psiClass, psiAnnotation, target, classMethods, CLASS_BULK_UPDATE, METHOD_UPDATE, ITERABLE);
        addBulkMethod(psiClass, psiAnnotation, target, classMethods, CLASS_BULK_PERSIST, METHOD_PERSIST, ITERABLE);
        addBulkMethod(psiClass, psiAnnotation, target, classMethods, CLASS_BULK_DELETE, METHOD_DELETE, COLLECTION);
        addDeleteTableMethod(psiClass, psiAnnotation, target, classMethods);
        addMissingIdIfNeeded(psiClass, psiAnnotation, target, classMethods);
    }

    private void addMethod(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target, Collection<PsiMethod> classMethods, String callClassName, String methodName) {
        if (!PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 0)) {
            target.add(new SqliteMagicLightMethodBuilder(psiClass.getManager(), methodName)
                    .withAnnotation(checkResultAnnotation())
                    .withModifier(PsiModifier.PUBLIC, PsiModifier.FINAL)
                    .withMethodReturnType(getMagicMethodReturnType(psiClass, callClassName))
                    .withBody(defaultMagicMethodBody(psiClass))
                    .withContainingClass(psiClass)
                    .withNavigationElement(psiAnnotation));
        }
    }

    private void addBulkMethod(PsiClass psiClass, PsiAnnotation psiAnnotation, List<? super PsiElement> target, Collection<PsiMethod> classMethods, String callClassName, String methodName, BulkCollection collection) {
        if (!PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 0)) {
            target.add(new SqliteMagicLightMethodBuilder(psiClass.getManager(), methodName)
                    .withAnnotation(checkResultAnnotation())
                    .withModifier(PsiModifier.PUBLIC, PsiModifier.STATIC)
                    .withMethodReturnType(getMagicMethodReturnType(psiClass, callClassName))
                    .withParameter("objects", collection.parameterType(psiClass))
                    .withBody(defaultMagicMethodBody(psiClass))
                    .withContainingClass(psiClass)
                    .withNavigationElement(psiAnnotation));
        }
    }

    private void addDeleteTableMethod(PsiClass psiClass, PsiAnnotation psiAnnotation, List<? super PsiElement> target, Collection<PsiMethod> classMethods) {
        final String methodName = METHOD_DELETE_TABLE;
        if (!PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 0)) {
            target.add(new SqliteMagicLightMethodBuilder(psiClass.getManager(), methodName)
                    .withAnnotation(checkResultAnnotation())
                    .withModifier(PsiModifier.PUBLIC, PsiModifier.STATIC)
                    .withMethodReturnType(getMagicMethodReturnType(psiClass, CLASS_DELETE_TABLE))
                    .withBody(defaultMagicMethodBody(psiClass))
                    .withContainingClass(psiClass)
                    .withNavigationElement(psiAnnotation));
        }
    }

    private void addMissingIdIfNeeded(PsiClass psiClass, PsiAnnotation psiAnnotation, List<? super PsiElement> target, Collection<PsiMethod> classMethods) {
        final String methodName = "id";
        if (!psiClass.hasModifierProperty("abstract")
                && !PsiMethodUtil.hasSimilarMethod(classMethods, methodName, 0)
                && !PsiMethodUtil.hasSimilarMethod(classMethods, "getId", 0)) {
            final Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
            boolean hasIdColumn = false;
            for (PsiField field : psiFields) {
                if (PsiAnnotationUtil.isAnnotatedWith(field, Id.class)) {
                    hasIdColumn = true;
                    break;
                }
            }
            if (!hasIdColumn) {
                target.add(new SqliteMagicLightMethodBuilder(psiClass.getManager(), methodName)
                        .withModifier(PsiModifier.PUBLIC, PsiModifier.FINAL)
                        .withMethodReturnType(PsiType.LONG)
                        .withBody(PsiMethodUtil.createCodeBlockFromText("return this.id", psiClass))
                        .withContainingClass(psiClass)
                        .withNavigationElement(psiAnnotation));
            }
        }
    }

    private PsiCodeBlock defaultMagicMethodBody(PsiClass psiClass) {
        return PsiMethodUtil.createCodeBlockFromText("throw new RuntimeException(\"" + ERROR_PROCESSOR_DID_NOT_RUN + "\")", psiClass);
    }

    private PsiType getMagicMethodReturnType(PsiClass psiClass, String callClassName) {
        final String tableClassName = psiClass.getName();
        final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(psiClass.getProject());
        return elementFactory.createTypeFromText("com.siimkinks.sqlitemagic.entity.Entity" +
                callClassName + "<" + tableClassName + ">", psiClass);
    }

    public static String getGeneratedHandlerClassNameString(String baseClassName) {
        return String.format("SqliteMagic_%s_%s", baseClassName, CLASS_MODEL_HANDLER);
    }

    @NotNull
    private String checkResultAnnotation() {
        return "android.support.annotation.CheckResult";
    }
}
