package com.siimkinks.sqlitemagic.processing;

import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.BaseProcessor;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.Database;
import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase;
import com.siimkinks.sqlitemagic.util.Dual;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;

import static com.siimkinks.sqlitemagic.Environment.getGenClassesManagerClassName;
import static com.siimkinks.sqlitemagic.util.NameConst.PACKAGE_ROOT;
import static com.siimkinks.sqlitemagic.util.StringUtil.firstCharToUpperCase;

public class DatabaseConfigurationCollectionStep implements ProcessingStep {
  private static final String ERR_SINGLE_SUBMODULE_DB_ALLOWED = String
      .format("Only one element per module can be annotated with @%s", SubmoduleDatabase.class.getSimpleName());
  private static final String ERR_SINGLE_DB_ALLOWED = String
      .format("Only one element per module can be annotated with @%s", Database.class.getSimpleName());

  @Inject
  Environment environment;

  public DatabaseConfigurationCollectionStep() {
    BaseProcessor.inject(this);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    final Set<? extends Element> submoduleDatabaseElements = roundEnv.getElementsAnnotatedWith(SubmoduleDatabase.class);
    if (!parseSubmodules(submoduleDatabaseElements)) return false;

    final Set<? extends Element> databaseElements = roundEnv.getElementsAnnotatedWith(Database.class);
    if (!submoduleDatabaseElements.isEmpty() && !databaseElements.isEmpty()) {
      environment.error("Module can have either @%s or @%s annotated element, but not both",
          Database.class.getSimpleName(), SubmoduleDatabase.class.getSimpleName());
      return false;
    }
    if (databaseElements.size() > 1) {
      environment.error(ERR_SINGLE_DB_ALLOWED);
      return false;
    }
    for (Element element : databaseElements) {
      final Database database = element.getAnnotation(Database.class);
      if (environment.getSubmoduleDatabases() == null) {
        final List<Dual<TypeElement, String>> submodules = getSubmodules(environment, database);
        if (!submodules.isEmpty()) {
          environment.setSubmoduleDatabases(submodules);
        }
      } else {
        environment.error(element, ERR_SINGLE_DB_ALLOWED);
        return false;
      }
      final String dbName = database.name();
      if (!Strings.isNullOrEmpty(dbName)) {
        environment.setDbName("\"" + dbName + "\"");
      }
      final int version = database.version();
      if (version >= 0) {
        environment.setDbVersion(Integer.toString(version));
      }
    }

    return true;
  }

  private boolean parseSubmodules(Set<? extends Element> submoduleDatabaseElements) {
    if (submoduleDatabaseElements.size() > 1) {
      environment.error(ERR_SINGLE_SUBMODULE_DB_ALLOWED);
      return false;
    }
    for (Element element : submoduleDatabaseElements) {
      if (environment.getModuleName() == null) {
        final SubmoduleDatabase submoduleDatabase = element.getAnnotation(SubmoduleDatabase.class);
        final String moduleName = submoduleDatabase.value();
        if (Strings.isNullOrEmpty(moduleName)) {
          environment.error(element, "Submodule name cannot be empty or null");
        }
        environment.setModuleName(firstCharToUpperCase(moduleName));
      } else {
        environment.error(element, ERR_SINGLE_SUBMODULE_DB_ALLOWED);
        return false;
      }
    }
    return true;
  }

  private static List<Dual<TypeElement, String>> getSubmodules(Environment environment, Database databaseAnnotation) {
    final ArrayList<Dual<TypeElement, String>> result = new ArrayList<>();
    final Elements elementUtils = environment.getElementUtils();
    try {
      for (Class<?> clazz : databaseAnnotation.submodules()) {
        final TypeElement annotatedTypeElement = environment.getTypeElement(clazz);
        final Dual<TypeElement, String> managerElement = getManagerTypeElement(elementUtils, annotatedTypeElement);
        result.add(managerElement);
      }
    } catch (MirroredTypesException e) {
      for (TypeMirror typeMirror : e.getTypeMirrors()) {
        final TypeElement annotatedTypeElement = elementUtils.getTypeElement(typeMirror.toString());
        final Dual<TypeElement, String> managerElement = getManagerTypeElement(elementUtils, annotatedTypeElement);
        result.add(managerElement);
      }
    }
    return result;
  }

  private static Dual<TypeElement, String> getManagerTypeElement(Elements elementUtils, TypeElement annotatedTypeElement) {
    final SubmoduleDatabase submoduleDatabase = annotatedTypeElement.getAnnotation(SubmoduleDatabase.class);
    final String moduleName = firstCharToUpperCase(submoduleDatabase.value());
    final String managerClassName = getGenClassesManagerClassName(moduleName);
    return Dual.create(
        elementUtils.getTypeElement(PACKAGE_ROOT + "." + managerClassName),
        moduleName);
  }
}
