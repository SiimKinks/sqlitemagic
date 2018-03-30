package com.siimkinks.sqlitemagic;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.auto.common.MoreElements;
import com.google.auto.common.Visibility;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.element.BaseColumnElement;
import com.siimkinks.sqlitemagic.element.ExtendedTypeElement;
import com.siimkinks.sqlitemagic.element.IndexElement;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.siimkinks.sqlitemagic.element.TransformerElement;
import com.siimkinks.sqlitemagic.element.ViewElement;
import com.siimkinks.sqlitemagic.util.Dual;
import com.squareup.javapoet.TypeName;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor6;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import static com.siimkinks.sqlitemagic.GlobalConst.CLASS_NAME_GENERATED_CLASSES_MANAGER;

@Data
public class Environment {

  private final Messager messager;
  private final Elements elementUtils;
  private final Types typeUtils;
  private final Filer filer;

  // all elements
  @Getter
  private final List<TableElement> allTableElements = new ArrayList<>();
  @Getter
  private final Map<String, Integer> allTableNames = new HashMap<>();

  // processing round elements
  private final Map<String, TableElement> tableElements = new HashMap<>();
  private final Map<String, TableElement> tableElementsByTableName = new HashMap<>();
  private final Map<String, ViewElement> viewElements = new HashMap<>();
  private final List<IndexElement> indexElements = new ArrayList<>();
  private final Map<String, TransformerElement> transformerElements = new HashMap<>();

  @Getter
  private int $processingRounds = 0;
  private boolean hasAutoValueLib = true;
  @Getter
  private String autoValueAnnotationQualifiedName;
  @Getter
  @Setter(AccessLevel.PRIVATE)
  private Class<? extends Annotation> autoValueAnnotation;
  @Getter
  @Setter(AccessLevel.PRIVATE)
  private Class<? extends Annotation> autoValueBuilderAnnotation;
  @Getter
  @Setter
  private String projectDir;
  @Getter
  @Setter
  private boolean debugVariant;
  @Getter
  @Setter
  private String variantName;
  @Getter
  @Setter
  private boolean migrateDebug = true;
  @Getter
  private Integer dbVersion;
  @Getter
  private String dbName;
  @Getter
  @Setter
  @Nullable
  private String submoduleName = null;
  @Getter
  @Setter
  @Nullable
  private List<Dual<TypeElement, String>> submoduleDatabases = null;

  private boolean processingFailed = false;

  public Environment(Messager messager, Elements elementUtils, Types typeUtils, Filer filer) {
    this.messager = messager;
    this.elementUtils = elementUtils;
    this.typeUtils = typeUtils;
    this.filer = filer;
    addDefaultTransformers();
  }

  private void addDefaultTransformers() {
    for (String transformerName : Const.DEFAULT_TRANSFORMERS) {
      final TransformerElement transformer = new TransformerElement(this);
      final Element transformerElement = elementUtils.getTypeElement(transformerName);
      for (Element enclosedElement : transformerElement.getEnclosedElements()) {
        if (enclosedElement.getKind() == ElementKind.METHOD) {
          final ExecutableElement method = (ExecutableElement) enclosedElement;
          Annotation annotation = method.getAnnotation(ObjectToDbValue.class);
          if (annotation != null) {
            transformer.addObjectToDbValueMethod(method);
          }
          annotation = method.getAnnotation(DbValueToObject.class);
          if (annotation != null) {
            transformer.addDbValueToObjectMethod(method);
          }
        }
      }
      addTransformerElement(transformer);
    }
  }

  public void clear() {
    tableElements.clear();
    viewElements.clear();
    indexElements.clear();
    transformerElements.clear();
  }

  public void error(Element e, String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args), e);
  }

  public void error(String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.ERROR, String.format(msg, args));
  }

  public void warning(Element e, String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.WARNING, String.format(msg, args), e);
  }

  public void debug(Element e, String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.OTHER, String.format(msg, args), e);
  }

  public void debug(String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.OTHER, String.format(msg, args));
  }

  public void warning(String msg, Object... args) {
    messager.printMessage(Diagnostic.Kind.WARNING, "SqliteMagic: " + String.format(msg, args));
  }

  public boolean isSubmodule() {
    return !Strings.isNullOrEmpty(submoduleName);
  }

  public boolean hasSubmodules() {
    return submoduleDatabases != null && !submoduleDatabases.isEmpty();
  }

  public String getGenClassesManagerClassName() {
    return getGenClassesManagerClassName(submoduleName);
  }

  public static String getGenClassesManagerClassName(String moduleName) {
    final String className = CLASS_NAME_GENERATED_CLASSES_MANAGER;
    if (!Strings.isNullOrEmpty(moduleName)) {
      return moduleName + className;
    }
    return className;
  }

  public TableElement getTableElementFor(String qualifiedTypeName) {
    return tableElements.get(qualifiedTypeName);
  }

  public TableElement getTableElementByTableName(String tableName) {
    return tableElementsByTableName.get(tableName);
  }

  public Collection<TableElement> getTableElements() {
    return tableElements.values();
  }

  public void addTableElement(TableElement tableElement) {
    TypeElement tableTypeElement = tableElement.getTableElement();
    String tableQualifiedTypeName = Environment.getQualifiedName(tableTypeElement);
    tableElements.put(tableQualifiedTypeName, tableElement);
    tableElementsByTableName.put(tableElement.getTableName(), tableElement);

    final int tableElementsSoFar = allTableElements.size();
    allTableNames.put(tableElement.getTableName(), tableElementsSoFar);
    allTableElements.add(tableElement);
    tableElement.setTablePos(tableElementsSoFar);
  }

  public ViewElement getViewElementFor(String qualifiedTypeName) {
    return viewElements.get(qualifiedTypeName);
  }

  public Collection<ViewElement> getViewElements() {
    return viewElements.values();
  }

  public void addViewElement(@NonNull ViewElement viewElement) {
    TypeElement tableTypeElement = viewElement.getViewElement();
    String tableQualifiedTypeName = Environment.getQualifiedName(tableTypeElement);
    viewElements.put(tableQualifiedTypeName, viewElement);
  }

  public void addTransformerElement(TransformerElement transformerElement) {
    transformerElements.put(transformerElement.getTypeKey(), transformerElement);
  }

  public boolean hasTransformerFor(ExtendedTypeElement element) {
    return transformerElements.containsKey(element.getTypeKey());
  }

  public TransformerElement getTransformerFor(ExtendedTypeElement element) {
    return transformerElements.get(element.getTypeKey());
  }

  public void addIndexElement(IndexElement indexElement) {
    indexElements.add(indexElement);
  }

  public List<IndexElement> getIndexElements() {
    return indexElements;
  }

  public TypeElement getTypeElement(Element element) {
    final TypeMirror elementType = element.asType();
    final Dual<TypeElement, Boolean> typeElement = getTypeElement(elementType);
    if (typeElement != null) {
      return typeElement.getFirst();
    }
    return null;
  }

  private Dual<TypeElement, Boolean> getTypeElement(final TypeMirror elementType) {
    TypeElement typeElement = elementUtils.getTypeElement(elementType.toString());
    boolean isPrimitive = false;
    if (typeElement == null) {
      try {
        typeElement = typeUtils.boxedClass((PrimitiveType) elementType);
        isPrimitive = true;
      } catch (Exception e) {
        return null;
      }
    }
    return Dual.create(typeElement, isPrimitive);
  }

  private TypeElement getGenericTypeElement(TypeMirror elementType) {
    final String fullElementTypeName = elementType.toString();
    final int firstGenericStart = fullElementTypeName.indexOf('<');
    final String name = fullElementTypeName.substring(0, firstGenericStart);
    return elementUtils.getTypeElement(name);
  }

  public TypeElement getTypeElement(Class<?> cls) {
    return elementUtils.getTypeElement(cls.getCanonicalName());
  }

  public ExtendedTypeElement getAnyTypeElement(Element element) {
    return getAnyTypeElement(element.asType());
  }

  public ExtendedTypeElement getAnyTypeElement(TypeMirror typeMirror) {
    boolean isArrayElement = false;
    boolean isGenericElement = false;
    Dual<TypeElement, Boolean> typeElement = getTypeElement(typeMirror);
    if (typeElement == null) {
      if (typeMirror instanceof ArrayType) {
        try {
          ArrayType arrayType = (ArrayType) typeMirror;
          typeElement = getTypeElement(arrayType.getComponentType());
          isArrayElement = true;
        } catch (Exception e) {
        }
      } else {
        typeElement = Dual.create(getGenericTypeElement(typeMirror), false);
        isGenericElement = true;
      }
    }
    return new ExtendedTypeElement(typeElement, typeMirror, isArrayElement, isGenericElement);
  }

  public ExtendedTypeElement getSupportedSerializedTypeElement(TypeMirror typeMirror) {
    boolean isArrayElement = false;
    boolean isGenericElement = false;
    Dual<TypeElement, Boolean> typeElement = getTypeElement(typeMirror);
    if (typeElement == null) {
      if (typeMirror instanceof ArrayType) {
        ArrayType arrayType = (ArrayType) typeMirror;
        final TypeMirror arrayComponentType = arrayType.getComponentType();
        if (typeUtils.isSameType(arrayComponentType, Const.BYTE_TYPE)) {
          typeElement = getTypeElement(arrayComponentType);
          isArrayElement = true;
        } else {
          return ExtendedTypeElement.EMPTY;
        }
      } else {
        typeElement = Dual.create(getGenericTypeElement(typeMirror), false);
        isGenericElement = true;
      }
    }
    return new ExtendedTypeElement(typeElement, typeMirror, isArrayElement, isGenericElement);
  }

  public static PackageElement getPackage(Element element) {
    if (element.getKind() == ElementKind.PACKAGE) {
      return (PackageElement) element;
    }
    return getPackage(element.getEnclosingElement());
  }

  public static String getQualifiedName(TypeElement element) {
    if (element == null) {
      return "";
    }
    return element.getQualifiedName().toString();
  }

  public static TypeName getTypeName(TypeElement element) {
    return TypeName.get(element.asType());
  }

  public void incrementRound() {
    this.$processingRounds++;
  }

  public boolean hasAutoValueLib() {
    return hasAutoValueLib;
  }

  public String getValueImplementationClassNameString(String abstractClassName) {
    if (!hasAutoValueLib) {
      return abstractClassName;
    }
    return String.format("%s_%s",
        autoValueAnnotation.getSimpleName(),
        abstractClassName);
  }

  public void setAutoValueLib(String annotation) {
    try {
      autoValueAnnotationQualifiedName = annotation;
      autoValueAnnotation = (Class<? extends Annotation>) Class.forName(annotation);
      autoValueBuilderAnnotation = (Class<? extends Annotation>) Class.forName(annotation + "$Builder");
    } catch (ClassNotFoundException e) {
      hasAutoValueLib = false;
    }
  }

  public void setDbVersion(String dbVersion) {
    this.dbVersion = Integer.valueOf(dbVersion);
  }

  public void setDbName(String dbName) {
    this.dbName = dbName;
  }

  public boolean isJavaBaseObject(TypeMirror type) {
    return typeUtils.isSameType(type, Const.OBJECT_TYPE) || type.getKind() == TypeKind.NONE;
  }

  public ImmutableSet<VariableElement> getLocalAndInheritedColumnFields(TypeElement type) {
    SetMultimap<String, VariableElement> fieldMap = LinkedHashMultimap.create();
    getLocalAndInheritedColumnFields(getPackage(type), type, fieldMap);
    return ImmutableSet.copyOf(fieldMap.values());
  }

  private static void getLocalAndInheritedColumnFields(PackageElement pkg,
                                                       TypeElement type,
                                                       SetMultimap<String, VariableElement> fields) {
    for (TypeMirror superInterface : type.getInterfaces()) {
      getLocalAndInheritedColumnFields(pkg, asTypeElement(superInterface), fields);
    }
    if (type.getSuperclass().getKind() != TypeKind.NONE) {
      // Visit the superclass after superinterfaces so we will always see the implementation of a
      // method after any interfaces that declared it.
      getLocalAndInheritedColumnFields(pkg, asTypeElement(type.getSuperclass()), fields);
    }
    for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
      final Set<Modifier> modifiers = field.getModifiers();
      if (!modifiers.contains(Modifier.STATIC)) {
        fields.put(field.getSimpleName().toString(), field);
      }
    }
  }

  /**
   * Returns the set of all non-private methods from {@code type}, including methods that it
   * inherits from its ancestors. Inherited methods that are overridden are not included in the
   * result. So if {@code type} defines {@code public String toString()}, the returned set will
   * contain that method, but not the {@code toString()} method defined by {@code Object}.
   */
  public ImmutableSet<ExecutableElement> getLocalAndInheritedColumnMethods(TypeElement type) {
    Set<ExecutableElement> methods = getLocalAndInheritedMethods(type);

    final Iterator<ExecutableElement> iterator = methods.iterator();
    while (iterator.hasNext()) {
      final ExecutableElement method = iterator.next();
      if (!method.getModifiers().contains(Modifier.ABSTRACT)
          || method.getReturnType().getKind() == TypeKind.VOID
          || !method.getParameters().isEmpty()) {
        iterator.remove();
      }
    }
    return ImmutableSet.copyOf(methods);
  }

  @NonNull
  public Set<ExecutableElement> getLocalAndInheritedMethods(TypeElement type) {
    SetMultimap<String, ExecutableElement> methodMap = LinkedHashMultimap.create();
    getLocalAndInheritedMethods(getPackage(type), type, methodMap);
    // Find methods that are overridden. We do this using `Elements.overrides`, which means
    // that it is inherently a quadratic operation, since we have to compare every method against
    // every other method. We reduce the performance impact by (a) grouping methods by name, since
    // a method cannot override another method with a different name, and (b) making sure that
    // methods in ancestor types precede those in descendant types, which means we only have to
    // check a method against the ones that follow it in that order.
    Set<ExecutableElement> overridden = new LinkedHashSet<>();
    final Elements elementUtils = this.elementUtils;
    for (String methodName : methodMap.keySet()) {
      List<ExecutableElement> methodList = ImmutableList.copyOf(methodMap.get(methodName));
      for (int i = 0; i < methodList.size(); i++) {
        ExecutableElement methodI = methodList.get(i);
        for (int j = i + 1; j < methodList.size(); j++) {
          ExecutableElement methodJ = methodList.get(j);
          if (elementUtils.overrides(methodJ, methodI, type)) {
            overridden.add(methodI);
          }
        }
      }
    }
    Set<ExecutableElement> methods = new LinkedHashSet<>(methodMap.values());
    methods.removeAll(overridden);
    return methods;
  }

  private static void getLocalAndInheritedMethods(PackageElement pkg,
                                                  TypeElement type,
                                                  SetMultimap<String, ExecutableElement> methods) {
    for (TypeMirror superInterface : type.getInterfaces()) {
      final TypeElement superInterfaceElement = asTypeElement(superInterface);
      final String interfaceName = superInterfaceElement.getSimpleName().toString();
      if (interfaceName.startsWith("Parcelable")) continue;

      getLocalAndInheritedMethods(pkg, superInterfaceElement, methods);
    }
    if (type.getSuperclass().getKind() != TypeKind.NONE) {
      // Visit the superclass after superinterfaces so we will always see the implementation of a
      // method after any interfaces that declared it.
      getLocalAndInheritedMethods(pkg, asTypeElement(type.getSuperclass()), methods);
    }
    for (ExecutableElement method : ElementFilter.methodsIn(type.getEnclosedElements())) {
      if (!method.getModifiers().contains(Modifier.STATIC)
          && visibleFromPackage(method, pkg)) {
        methods.put(method.getSimpleName().toString(), method);
      }
    }
  }

  private static boolean visibleFromPackage(Element element, PackageElement pkg) {
    // We use Visibility.ofElement rather than .effectiveVisibilityOfElement because it doesn't
    // really matter whether the containing class is visible. If you inherit a public method
    // then you have a public method, regardless of whether you inherit it from a public class.
    Visibility visibility = Visibility.ofElement(element);
    switch (visibility) {
      case PRIVATE:
        return false;
      case DEFAULT:
        return getPackage(element).equals(pkg);
      default:
        return true;
    }
  }

  public static Element asElement(TypeMirror typeMirror) {
    return typeMirror.accept(AS_ELEMENT_VISITOR, null);
  }

  private static final TypeVisitor<Element, Void> AS_ELEMENT_VISITOR =
      new SimpleTypeVisitor6<Element, Void>() {
        @Override
        protected Element defaultAction(TypeMirror e, Void p) {
          throw new IllegalArgumentException(e + "cannot be converted to an Element");
        }

        @Override
        public Element visitDeclared(DeclaredType t, Void p) {
          return t.asElement();
        }

        @Override
        public Element visitError(ErrorType t, Void p) {
          return t.asElement();
        }

        @Override
        public Element visitTypeVariable(TypeVariable t, Void p) {
          return t.asElement();
        }
      };

  public static TypeElement asTypeElement(TypeMirror mirror) {
    return MoreElements.asType(asElement(mirror));
  }

  public boolean isValidDataClass(@NonNull List<? extends Element> enclosedElements,
                                  @NonNull List<? extends BaseColumnElement> allColumns) {
    for (Element enclosedElement : enclosedElements) {
      if (enclosedElement.getKind() == ElementKind.CONSTRUCTOR) {
        ExecutableElement constructor = (ExecutableElement) enclosedElement;
        final List<? extends VariableElement> constructorParams = constructor.getParameters();
        if (constructorParams.size() != allColumns.size()) {
          return false;
        }
        final Types typeUtils = getTypeUtils();
        final Iterator<? extends BaseColumnElement> columnsIterator = allColumns.iterator();
        for (VariableElement param : constructorParams) {
          final BaseColumnElement column = columnsIterator.next();
          if (!typeUtils.isSameType(param.asType(), column.getDeserializedType().getTypeMirror())) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }
}
