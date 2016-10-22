package com.siimkinks.sqlitemagic.intellij.plugin.inspection;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiTypeElement;
import com.siimkinks.sqlitemagic.intellij.plugin.extension.SqliteMagicProcessorExtensionPoint;
import com.siimkinks.sqlitemagic.intellij.plugin.problem.SqliteMagicProblem;
import com.siimkinks.sqlitemagic.intellij.plugin.processor.Processor;

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

public class SqliteMagicInspection extends BaseJavaLocalInspectionTool {
  private static final Logger LOG = Logger.getInstance(SqliteMagicInspection.class.getName());

  private final Map<String, Collection<Processor>> allProblemHandlers;


  public SqliteMagicInspection() {

    allProblemHandlers = new HashMap<String, Collection<Processor>>();
    for (Processor sqlitemagicInspector : SqliteMagicProcessorExtensionPoint.EP_NAME.getExtensions()) {
      Collection<Processor> inspectorCollection = allProblemHandlers.get(sqlitemagicInspector.getSupportedAnnotation());
      if (null == inspectorCollection) {
        inspectorCollection = new ArrayList<Processor>(2);
        allProblemHandlers.put(sqlitemagicInspector.getSupportedAnnotation(), inspectorCollection);
      }
      inspectorCollection.add(sqlitemagicInspector);

      LOG.debug(String.format("SqliteMagicInspection registered %s inspector", sqlitemagicInspector));
    }
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "SqiteMagic annotations inspection";
  }

  @NotNull
  @Override
  public String getGroupDisplayName() {
    return GroupNames.BUGS_GROUP_NAME;
  }

  @NotNull
  @Override
  public String getShortName() {
    return "SqiteMagic";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new SqliteMagicElementVisitor(holder);
  }

  private class SqliteMagicElementVisitor extends JavaElementVisitor {

    private final ProblemsHolder holder;

    public SqliteMagicElementVisitor(ProblemsHolder holder) {
      this.holder = holder;
    }

    @Override
    public void visitTypeElement(PsiTypeElement type) {
      super.visitTypeElement(type);

    }

    @Override
    public void visitAnnotation(PsiAnnotation annotation) {
      super.visitAnnotation(annotation);

      final String qualifiedName = annotation.getQualifiedName();
      if (StringUtils.isNotBlank(qualifiedName) && allProblemHandlers.containsKey(qualifiedName)) {
        final Collection<SqliteMagicProblem> problems = new HashSet<SqliteMagicProblem>();

        for (Processor inspector : allProblemHandlers.get(qualifiedName)) {
          problems.addAll(inspector.verifyAnnotation(annotation));
        }

        for (SqliteMagicProblem problem : problems) {
          holder.registerProblem(annotation, problem.getMessage(), problem.getHighlightType(), problem.getQuickFixes());
        }
      }
    }
  }
}
