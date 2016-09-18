package com.siimkinks.sqlitemagic.intellij.plugin.extension;

import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.java.PsiFieldTreeElement;
import com.intellij.ide.structureView.impl.java.PsiMethodTreeElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.siimkinks.sqlitemagic.intellij.plugin.psi.SqliteMagicLightFieldBuilder;
import com.siimkinks.sqlitemagic.intellij.plugin.psi.SqliteMagicLightMethodBuilder;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

public class SqliteMagicStructureViewExtension implements StructureViewExtension {

    @Override
    public Class<? extends PsiElement> getType() {
        return PsiClass.class;
    }

    @Override
    public StructureViewTreeElement[] getChildren(PsiElement parent) {
        Collection<StructureViewTreeElement> result = new ArrayList<StructureViewTreeElement>();
        final PsiClass psiClass = (PsiClass) parent;

        for (PsiField psiField : psiClass.getFields()) {
            if (psiField instanceof SqliteMagicLightFieldBuilder) {
                result.add(new PsiFieldTreeElement(psiField, false));
            }
        }

        for (PsiMethod psiMethod : psiClass.getMethods()) {
            if (psiMethod instanceof SqliteMagicLightMethodBuilder) {
                result.add(new PsiMethodTreeElement(psiMethod, false));
            }
        }

        if (!result.isEmpty()) {
            return result.toArray(new StructureViewTreeElement[result.size()]);
        } else {
            return StructureViewTreeElement.EMPTY_ARRAY;
        }
    }

    @Nullable
    @Override
    public Object getCurrentEditorElement(Editor editor, PsiElement parent) {
        return null;
    }
}
