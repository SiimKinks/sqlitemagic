package lombok.javac.handlers;

import com.google.common.base.Strings;
import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.annotation.internal.Invokes;
import com.siimkinks.sqlitemagic.SqliteMagicProcessor;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Id;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;

import org.mangosdk.spi.ProviderFor;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.lang.model.element.Modifier;

import lombok.core.AST;
import lombok.core.AnnotationValues;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;

import static com.siimkinks.sqlitemagic.Const.DEFAULT_ID_COLUMN_NAME;
import static com.siimkinks.sqlitemagic.Const.DEFAULT_ID_FIELD_NAME;
import static com.siimkinks.sqlitemagic.GlobalConst.ERROR_PROCESSOR_DID_NOT_RUN;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_DELETE;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_INSERT;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_PERSIST;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_BULK_UPDATE;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_DELETE;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_DELETE_TABLE;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_INSERT;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_PERSIST;
import static com.siimkinks.sqlitemagic.util.NameConst.CLASS_UPDATE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_DELETE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_DELETE_TABLE;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_INSERT;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_PERSIST;
import static com.siimkinks.sqlitemagic.util.NameConst.METHOD_UPDATE;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.getCreateInvokeKey;
import static com.siimkinks.sqlitemagic.writer.EntityEnvironment.getGeneratedHandlerClassNameString;
import static lombok.javac.Javac.CTC_BOOLEAN;
import static lombok.javac.Javac.CTC_BYTE;
import static lombok.javac.Javac.CTC_CHAR;
import static lombok.javac.Javac.CTC_DOUBLE;
import static lombok.javac.Javac.CTC_FLOAT;
import static lombok.javac.Javac.CTC_INT;
import static lombok.javac.Javac.CTC_LONG;
import static lombok.javac.Javac.CTC_SHORT;
import static lombok.javac.handlers.HandleTable.CollectionType.COLLECTION;
import static lombok.javac.handlers.HandleTable.CollectionType.ITERABLE;
import static lombok.javac.handlers.JavacHandlerUtil.MemberExistsResult.EXISTS_BY_LOMBOK;
import static lombok.javac.handlers.JavacHandlerUtil.MemberExistsResult.EXISTS_BY_USER;
import static lombok.javac.handlers.JavacHandlerUtil.chainDots;
import static lombok.javac.handlers.JavacHandlerUtil.chainDotsString;
import static lombok.javac.handlers.JavacHandlerUtil.deleteAnnotationIfNeccessary;
import static lombok.javac.handlers.JavacHandlerUtil.deleteImportFromCompilationUnit;
import static lombok.javac.handlers.JavacHandlerUtil.genJavaLangTypeRef;
import static lombok.javac.handlers.JavacHandlerUtil.genTypeRef;
import static lombok.javac.handlers.JavacHandlerUtil.injectField;
import static lombok.javac.handlers.JavacHandlerUtil.injectMethod;
import static lombok.javac.handlers.JavacHandlerUtil.recursiveSetGeneratedBy;

@ProviderFor(JavacAnnotationHandler.class)
public class HandleTable extends JavacAnnotationHandler<Table> {
  public static final int PUBLIC_STATIC_FINAL = Flags.PUBLIC | Flags.STATIC | Flags.FINAL;
  public static final int PUBLIC_STATIC = Flags.PUBLIC | Flags.STATIC;
  public static final int PUBLIC_FINAL = Flags.PUBLIC | Flags.FINAL;

  enum CollectionType {
    ITERABLE(new Func1<JavacNode, JCTree.JCExpression>() {
      @Override
      public JCTree.JCExpression call(JavacNode node) {
        return genJavaLangTypeRef(node, "Iterable");
      }
    }),
    COLLECTION(new Func1<JavacNode, JCTree.JCExpression>() {
      @Override
      public JCTree.JCExpression call(JavacNode node) {
        return chainDots(node, "java", "util", "Collection");
      }
    });

    private final Func1<JavacNode, JCTree.JCExpression> func;

    CollectionType(Func1<JavacNode, JCTree.JCExpression> func) {
      this.func = func;
    }

    public JCTree.JCExpression genericType(JavacNode node) {
      return func.call(node);
    }
  }

  interface Func1<I, R> {
    R call(I input);
  }

  public static final Map<JavacTreeMaker.TypeTag, String> TYPE_MAP;

  static {
    Map<JavacTreeMaker.TypeTag, String> m = new HashMap<>();
    m.put(CTC_INT, "Integer");
    m.put(CTC_DOUBLE, "Double");
    m.put(CTC_FLOAT, "Float");
    m.put(CTC_SHORT, "Short");
    m.put(CTC_BYTE, "Byte");
    m.put(CTC_LONG, "Long");
    m.put(CTC_BOOLEAN, "Boolean");
    m.put(CTC_CHAR, "Character");
    TYPE_MAP = Collections.unmodifiableMap(m);
  }

  @Inject
  Environment environment;

  public HandleTable() {
    SqliteMagicProcessor.inject(this);
  }

  @Override
  public void handle(AnnotationValues<Table> annotation, JCTree.JCAnnotation ast, JavacNode annotationNode) {
    if (environment.isProcessingFailed()) {
      return;
    }
    Table tableInstance = annotation.getInstance();

    deleteAnnotationIfNeccessary(annotationNode, Table.class);
    deleteImportFromCompilationUnit(annotationNode, Table.class.getCanonicalName());

    JavacNode tableNode = annotationNode.up();
    final JavacTreeMaker maker = tableNode.getTreeMaker();

    final String tableName = getTableName(tableInstance, tableNode);
    final TableElement tableElement = environment.getTableElementByTableName(tableName);
    generateMagicMethods(maker, tableNode);
    generateMissingIdIfNeeded(maker, tableNode, tableElement);
  }

  private void generateMissingIdIfNeeded(JavacTreeMaker maker, JavacNode tableNode, TableElement tableElement) {
    // ignore autovalue elements
    if (((JCTree.JCClassDecl) tableNode.get()).mods.getFlags().contains(Modifier.ABSTRACT))
      return;

    // ignore if @Id column is already present
    if (tableElement.hasId()) return;

    final JCTree.JCAnnotation idAnnotation = maker.Annotation(
        chainDotsString(tableNode, Id.class.getCanonicalName()),
        List.<JCTree.JCExpression>nil());
    final JCTree.JCAnnotation columnAnnotation = maker.Annotation(
        chainDotsString(tableNode, Column.class.getCanonicalName()),
        List.<JCTree.JCExpression>of(maker.Literal(DEFAULT_ID_COLUMN_NAME)));
    final JCTree.JCModifiers mods = maker.Modifiers(0, List.of(idAnnotation, columnAnnotation));
    final Name name = tableNode.toName(DEFAULT_ID_FIELD_NAME);
    final JCTree.JCPrimitiveTypeTree longType = maker.TypeIdent(CTC_LONG);
    JCTree.JCVariableDecl newField = maker.VarDef(mods, name, longType, null);
    injectField(tableNode, recursiveSetGeneratedBy(newField, tableNode.get(), tableNode.getContext()));

    // ignore getter method generation if it exists
    final JavacHandlerUtil.MemberExistsResult existsResult = JavacHandlerUtil.methodExists(DEFAULT_ID_FIELD_NAME, tableNode, 0);
    if (existsResult == EXISTS_BY_USER || existsResult == EXISTS_BY_LOMBOK) return;

    final JCTree.JCModifiers methodMods = maker.Modifiers(PUBLIC_FINAL);
    final Name methodName = tableNode.toName(DEFAULT_ID_FIELD_NAME);
    final JCTree.JCStatement statement = maker.Return(maker.Ident(name));
    final JCTree.JCBlock body = maker.Block(0, List.of(statement));
    final JCTree.JCMethodDecl method = maker.MethodDef(methodMods, methodName, longType,
        List.<JCTree.JCTypeParameter>nil(),
        List.<JCTree.JCVariableDecl>nil(),
        List.<JCTree.JCExpression>nil(),
        body,
        null);
    injectMethod(tableNode, recursiveSetGeneratedBy(method, tableNode.get(), tableNode.getContext()));
  }

  private void generateMagicMethods(JavacTreeMaker maker, JavacNode tableElement) {
    final Set<String> allStaticMethodNames = findAllStaticMethodNames(tableElement);
    final Set<String> allMethodNames = findAllMethodNames(tableElement);
    final String tableClassName = tableElement.getName();
    generateMethod(maker, tableElement, allMethodNames, METHOD_INSERT, CLASS_INSERT, tableClassName);
    generateMethod(maker, tableElement, allMethodNames, METHOD_UPDATE, CLASS_UPDATE, tableClassName);
    generateMethod(maker, tableElement, allMethodNames, METHOD_PERSIST, CLASS_PERSIST, tableClassName);
    generateMethod(maker, tableElement, allMethodNames, METHOD_DELETE, CLASS_DELETE, tableClassName);
    generateBulkMethod(maker, tableElement, allStaticMethodNames, METHOD_INSERT, CLASS_BULK_INSERT, tableClassName, ITERABLE);
    generateBulkMethod(maker, tableElement, allStaticMethodNames, METHOD_UPDATE, CLASS_BULK_UPDATE, tableClassName, ITERABLE);
    generateBulkMethod(maker, tableElement, allStaticMethodNames, METHOD_PERSIST, CLASS_BULK_PERSIST, tableClassName, ITERABLE);
    generateBulkMethod(maker, tableElement, allStaticMethodNames, METHOD_DELETE, CLASS_BULK_DELETE, tableClassName, COLLECTION);
    generateDeleteTable(maker, tableElement, allStaticMethodNames, tableClassName);
  }

  private void generateMethod(JavacTreeMaker maker, JavacNode tableElement, Set<String> allMethodNames, String methodName, String callClassName, String tableClassName) {
    final String invokeKey = getCreateInvokeKey(callClassName, tableClassName);
    if (!allMethodNames.contains(methodName)) {
      final JCTree.JCAssign value = maker.Assign(maker.Ident(tableElement.toName("value")),
          maker.Literal(invokeKey));
      final JCTree.JCAssign useThisAsOnlyParam = maker.Assign(maker.Ident(tableElement.toName("useThisAsOnlyParam")),
          maker.Literal(true));
      final JCTree.JCAnnotation invokesAnnotation = maker.Annotation(chainDotsString(tableElement, Invokes.class.getCanonicalName()),
          List.<JCTree.JCExpression>of(value, useThisAsOnlyParam));
      final JCTree.JCModifiers mods = maker.Modifiers(PUBLIC_FINAL, List.of(invokesAnnotation));
      final Name name = tableElement.toName(methodName);
      final JCTree.JCExpression returnType = getMagicMethodReturnType(tableElement, callClassName, tableClassName);
      final JCTree.JCBlock body = defaultMagicMethodBody(maker, tableElement);
      final JCTree.JCMethodDecl method = maker.MethodDef(mods, name, returnType,
          List.<JCTree.JCTypeParameter>nil(),
          List.<JCTree.JCVariableDecl>nil(),
          List.<JCTree.JCExpression>nil(),
          body,
          null);
      injectMethod(tableElement, recursiveSetGeneratedBy(method, tableElement.get(), tableElement.getContext()));
    }
  }

  private void generateBulkMethod(JavacTreeMaker maker, JavacNode tableElement,
                                  Set<String> allStaticMethodNames, String methodName,
                                  String callClassName, String tableClassName,
                                  CollectionType collectionType) {
    final String invokeKey = getCreateInvokeKey(callClassName, tableClassName);
    if (!allStaticMethodNames.contains(methodName)) {
      final JCTree.JCAnnotation invokesAnnotation = maker.Annotation(chainDotsString(tableElement, Invokes.class.getCanonicalName()),
          List.<JCTree.JCExpression>of(maker.Literal(invokeKey)));
      final JCTree.JCModifiers mods = maker.Modifiers(PUBLIC_STATIC, List.of(invokesAnnotation));
      final Name name = tableElement.toName(methodName);
      final JCTree.JCExpression returnType = getMagicMethodReturnType(tableElement, callClassName, tableClassName);
      final JCTree.JCExpression tableClassType = chainDotsString(tableElement, tableElement.getPackageDeclaration() + "." + tableClassName);
      final JCTree.JCExpression paramType = maker.TypeApply(collectionType.genericType(tableElement), List.of(tableClassType));
      long flags = JavacHandlerUtil.addFinalIfNeeded(Flags.PARAMETER, tableElement.getContext());
      final JCTree.JCVariableDecl param = maker.VarDef(maker.Modifiers(flags), tableElement.toName("o"), paramType, null);
      final JCTree.JCBlock body = defaultMagicMethodBody(maker, tableElement);
      final JCTree.JCMethodDecl method = maker.MethodDef(mods, name, returnType,
          List.<JCTree.JCTypeParameter>nil(),
          List.of(param),
          List.<JCTree.JCExpression>nil(),
          body,
          null);
      injectMethod(tableElement, recursiveSetGeneratedBy(method, tableElement.get(), tableElement.getContext()));
    }
  }

  private void generateDeleteTable(JavacTreeMaker maker, JavacNode tableElement, Set<String> allStaticMethodNames, String tableClassName) {
    final String callClassName = CLASS_DELETE_TABLE;
    final String methodName = METHOD_DELETE_TABLE;
    final String invokeKey = getCreateInvokeKey(callClassName, tableClassName);
    if (!allStaticMethodNames.contains(methodName)) {
      final JCTree.JCAnnotation invokesAnnotation = maker.Annotation(chainDotsString(tableElement, Invokes.class.getCanonicalName()),
          List.<JCTree.JCExpression>of(maker.Literal(invokeKey)));
      final JCTree.JCModifiers mods = maker.Modifiers(PUBLIC_STATIC, List.of(invokesAnnotation));
      final Name name = tableElement.toName(methodName);
      final JCTree.JCExpression returnType = getMagicMethodReturnType(tableElement, callClassName, tableClassName);
      final JCTree.JCBlock body = defaultMagicMethodBody(maker, tableElement);
      final JCTree.JCMethodDecl method = maker.MethodDef(mods, name, returnType,
          List.<JCTree.JCTypeParameter>nil(),
          List.<JCTree.JCVariableDecl>nil(),
          List.<JCTree.JCExpression>nil(),
          body,
          null);
      injectMethod(tableElement, recursiveSetGeneratedBy(method, tableElement.get(), tableElement.getContext()));
    }
  }

  private JCTree.JCExpression getMagicMethodReturnType(JavacNode tableElement, String callClassName, String tableClassName) {
    return chainDots(tableElement, new String[]{
        "com", "siimkinks", "sqlitemagic",
        getGeneratedHandlerClassNameString(tableClassName),
        callClassName
    });
  }

  private static JCTree.JCBlock defaultMagicMethodBody(JavacTreeMaker maker, JavacNode tableElement) {
    JCTree.JCExpression exType = genTypeRef(tableElement, RuntimeException.class.getCanonicalName());
    JCTree.JCExpression exception = maker.NewClass(null, List.<JCTree.JCExpression>nil(), exType, List.<JCTree.JCExpression>of(maker.Literal(ERROR_PROCESSOR_DID_NOT_RUN)), null);
    final JCTree.JCStatement statement = maker.Throw(exception);
    return maker.Block(0, List.of(statement));
  }

  private String getTableName(Table tableAnnotation, JavacNode tableElement) {
    final String definedTableName = tableAnnotation.value();
    if (!Strings.isNullOrEmpty(definedTableName)) {
      return definedTableName;
    }
    return tableElement.getName().toLowerCase();
  }

  private static Set<String> findAllStaticMethodNames(JavacNode typeNode) {
    Set<String> methodNames = new LinkedHashSet<>();
    for (JavacNode child : typeNode.down()) {
      if (child.getKind() != AST.Kind.METHOD) continue;
      JCTree.JCMethodDecl methodDecl = (JCTree.JCMethodDecl) child.get();
      long methodFlags = methodDecl.mods.flags;
      //Take static methods
      if ((methodFlags & Flags.STATIC) != 0) {
        methodNames.add(child.getName());
      }
    }
    return methodNames;
  }

  private Set<String> findAllMethodNames(JavacNode typeNode) {
    Set<String> methodNames = new LinkedHashSet<>();
    for (JavacNode child : typeNode.down()) {
      if (child.getKind() != AST.Kind.METHOD) continue;
      JCTree.JCMethodDecl methodDecl = (JCTree.JCMethodDecl) child.get();
      long methodFlags = methodDecl.mods.flags;
      //Skip static methods
      if ((methodFlags & Flags.STATIC) != 0) continue;
      methodNames.add(child.getName());
    }
    return methodNames;
  }
}
