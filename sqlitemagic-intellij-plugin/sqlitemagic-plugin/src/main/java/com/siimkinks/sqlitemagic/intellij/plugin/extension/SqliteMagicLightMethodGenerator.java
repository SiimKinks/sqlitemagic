package com.siimkinks.sqlitemagic.intellij.plugin.extension;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.ChangeUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeGenerator;
import com.intellij.util.CharTable;
import com.siimkinks.sqlitemagic.intellij.plugin.psi.SqliteMagicLightMethodBuilder;
import org.jetbrains.annotations.Nullable;

public class SqliteMagicLightMethodGenerator implements TreeGenerator {
    @Nullable
    @Override
    public TreeElement generateTreeFor(PsiElement original, CharTable table, PsiManager manager) {
        TreeElement result = null;
        if (original instanceof SqliteMagicLightMethodBuilder) {
            result = ChangeUtil.copyElement((TreeElement) SourceTreeToPsiMap.psiElementToTree(original), table);
        }
        return result;
    }
}
