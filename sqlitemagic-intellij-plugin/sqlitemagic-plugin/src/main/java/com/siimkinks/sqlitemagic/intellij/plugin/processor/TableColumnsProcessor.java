package com.siimkinks.sqlitemagic.intellij.plugin.processor;

import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.ProblemBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.processor.clazz.AbstractClassProcessor;
import com.siimkinks.sqlitemagic.intellij.plugin.psi.SqliteMagicLightFieldBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiAnnotationUtil;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiClassUtil;

import org.jetbrains.annotations.NotNull;


public class TableColumnsProcessor extends AbstractClassProcessor {

    protected TableColumnsProcessor() {
        super(Table.class, PsiField.class);
    }

    @Override
    protected boolean validate(@NotNull PsiAnnotation psiAnnotation, @NotNull PsiClass psiClass, @NotNull ProblemBuilder builder) {
        // TODO implement
        return true;
    }

    @Override
    protected void generatePsiElements(@NotNull PsiClass psiClass, @NotNull PsiAnnotation psiAnnotation, @NotNull List<? super PsiElement> target) {
        boolean hasIdColumn = false;
        for (PsiField field : PsiClassUtil.collectClassFieldsIntern(psiClass)) {
            if (PsiAnnotationUtil.isAnnotatedWith(field, Id.class)) {
                hasIdColumn = true;
                break;
            }
        }

        if (!hasIdColumn && !psiClass.hasModifierProperty("abstract")) {
            target.add(new SqliteMagicLightFieldBuilder(psiClass.getManager(), "id", PsiType.LONG)
                    .withContainingClass(psiClass)
                    .withNavigationElement(psiAnnotation));
        }
    }
}
