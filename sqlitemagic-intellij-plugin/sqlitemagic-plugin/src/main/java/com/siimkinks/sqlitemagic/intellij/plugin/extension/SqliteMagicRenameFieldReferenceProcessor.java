package com.siimkinks.sqlitemagic.intellij.plugin.extension;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

// TODO implement
public class SqliteMagicRenameFieldReferenceProcessor extends RenameJavaVariableProcessor {

    @Override
    public boolean canProcessElement(@NotNull PsiElement element) {
//        final boolean isPsiJavaField = element instanceof PsiField && StdFileTypes.JAVA.getLanguage().equals(element.getLanguage());
//        if (isPsiJavaField) {
//            final PsiField psiField = (PsiField) element;
//            AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
//            final String accessorFieldName = accessorsInfo.removePrefix(psiField.getName());
//            if (!psiField.getName().equals(accessorFieldName)) {
//                return true;
//            }
//        }
        return false;
    }

    @Override
    public void prepareRenaming(PsiElement element, String newName, Map<PsiElement, String> allRenames) {
//        final PsiField psiField = (PsiField) element;
//        final PsiClass containingClass = psiField.getContainingClass();
//        if (null != containingClass) {
//            final AccessorsInfo accessorsInfo = AccessorsInfo.build(psiField);
//            final String psiFieldName = psiField.getName();
//            final boolean isBoolean = PsiType.BOOLEAN.equals(psiField.getType());
//
//            final String getterName = LombokUtils.toGetterName(accessorsInfo, psiFieldName, isBoolean);
//            final String setterName = LombokUtils.toSetterName(accessorsInfo, psiFieldName, isBoolean);
//
//            final PsiMethod[] psiGetterMethods = containingClass.findMethodsByName(getterName, false);
//            final PsiMethod[] psiSetterMethods = containingClass.findMethodsByName(setterName, false);
//
//            for (PsiMethod psiMethod : psiGetterMethods) {
//                allRenames.put(psiMethod, LombokUtils.toGetterName(accessorsInfo, newName, isBoolean));
//            }
//
//            for (PsiMethod psiMethod : psiSetterMethods) {
//                allRenames.put(psiMethod, LombokUtils.toSetterName(accessorsInfo, newName, isBoolean));
//            }
//        }
    }
}
