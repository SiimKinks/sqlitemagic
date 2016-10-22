package com.siimkinks.sqlitemagic.intellij.plugin.provider;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.augment.PsiAugmentProvider;
import com.intellij.psi.impl.source.PsiExtensibleClass;
import com.siimkinks.sqlitemagic.intellij.plugin.extension.SqliteMagicProcessorExtensionPoint;
import com.siimkinks.sqlitemagic.intellij.plugin.extension.UserMapKeys;
import com.siimkinks.sqlitemagic.intellij.plugin.processor.Processor;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiAnnotationUtil;
import com.siimkinks.sqlitemagic.intellij.plugin.util.PsiClassUtil;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

public class SqliteMagicAugmentProvider extends PsiAugmentProvider {
  private static final Logger LOG = Logger.getInstance(SqliteMagicAugmentProvider.class.getName());

  private Collection<String> registeredAnnotationNames;

  public SqliteMagicAugmentProvider() {
    LOG.debug("SqliteMagicAugmentProvider created");
  }

  @NotNull
  @Override
  public <Psi extends PsiElement> List<Psi> getAugments(@NotNull PsiElement element, @NotNull Class<Psi> type) {
    final List<Psi> emptyResult = Collections.emptyList();
    // skip processing during index rebuild
    final Project project = element.getProject();
    if (DumbService.isDumb(project)) {
      return emptyResult;
    }
    // Expecting that we are only augmenting an PsiClass
    // Don't filter !isPhysical elements or code auto completion will not work
    if (!(element instanceof PsiExtensibleClass) || !element.isValid()) {
      return emptyResult;
    }
    // skip processing for other as supported types
    if (type != PsiMethod.class && type != PsiField.class && type != PsiClass.class) {
      return emptyResult;
    }

    final PsiFile containingFile = element.getContainingFile();
    if (containingFile == null) {
      return emptyResult;
    }

    initRegisteredAnnotations();

    final PsiClass psiClass = (PsiClass) element;

    boolean fileOpenInEditor = true;

    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (null != virtualFile) {
      fileOpenInEditor = FileEditorManager.getInstance(project).isFileOpen(virtualFile);
    }

    if (fileOpenInEditor || checkSqliteMagicPresent(psiClass)) {
      return process(type, project, psiClass);
    }

    return emptyResult;
  }

  private boolean checkSqliteMagicPresent(PsiClass psiClass) {
    boolean result = UserMapKeys.isSqliteMagicPossiblePresent(psiClass);
    if (result) {
      result = verifySqliteMagicAnnotationPresent(psiClass);
    }
    UserMapKeys.updateSqliteMagicPresent(psiClass, result);
    return result;
  }

  private void initRegisteredAnnotations() {
    if (null == registeredAnnotationNames) {
      final Collection<String> nameSet = new HashSet<String>();

      for (Processor processor : SqliteMagicProcessorExtensionPoint.EP_NAME.getExtensions()) {
        Class<? extends Annotation> annotationClass = processor.getSupportedAnnotationClass();
        nameSet.add(annotationClass.getSimpleName());
        nameSet.add(annotationClass.getName());
      }

      registeredAnnotationNames = nameSet;
    }
  }

  private <Psi extends PsiElement> List<Psi> process(@NotNull Class<Psi> type, @NotNull Project project, @NotNull PsiClass psiClass) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("Process call for type: %s class: %s", type, psiClass.getQualifiedName()));
    }

    final List<Psi> result = new ArrayList<Psi>();
    for (Processor processor : SqliteMagicProcessorExtensionPoint.EP_NAME.getExtensions()) {
      if (processor.canProduce(type) && processor.isEnabled(project)) {
        result.addAll((Collection<? extends Psi>) processor.process(psiClass));
      }
    }
    return result;
  }

  private boolean verifySqliteMagicAnnotationPresent(@NotNull PsiClass psiClass) {
    if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiClass, registeredAnnotationNames)) {
      return true;
    }
    Collection<PsiField> psiFields = PsiClassUtil.collectClassFieldsIntern(psiClass);
    for (PsiField psiField : psiFields) {
      if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiField, registeredAnnotationNames)) {
        return true;
      }
    }
    Collection<PsiMethod> psiMethods = PsiClassUtil.collectClassMethodsIntern(psiClass);
    for (PsiMethod psiMethod : psiMethods) {
      if (PsiAnnotationUtil.checkAnnotationsSimpleNameExistsIn(psiMethod, registeredAnnotationNames)) {
        return true;
      }
    }
    final PsiElement psiClassParent = psiClass.getParent();
    if (psiClassParent instanceof PsiClass) {
      return verifySqliteMagicAnnotationPresent((PsiClass) psiClassParent);
    }

    return false;
  }
}
