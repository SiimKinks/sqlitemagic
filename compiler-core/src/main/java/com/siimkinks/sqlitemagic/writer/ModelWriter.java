package com.siimkinks.sqlitemagic.writer;

import com.siimkinks.sqlitemagic.Environment;
import com.siimkinks.sqlitemagic.WriterUtil;
import com.siimkinks.sqlitemagic.element.TableElement;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;

import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.inject.Singleton;

import androidx.annotation.NonNull;

import static com.siimkinks.sqlitemagic.Const.CLASS_MODIFIERS;

@Singleton
public class ModelWriter {

  public static final String ENTITY_VARIABLE = "entity";
  public static final String DB_CONNECTION_VARIABLE = "dbConnection";
  public static final String MANAGER_VARIABLE = "manager";
  public static final String TRANSACTION_VARIABLE = "transaction";
  public static final String EMITTER_VARIABLE = "emitter";
  public static final String DISPOSABLE_VARIABLE = "disposable";
  public static final String OBJECTS_VARIABLE = "objects";
  public static final String CONFLICT_ALGORITHM_VARIABLE = "conflictAlgorithm";
  public static final String OPERATION_BY_COLUMNS_VARIABLE = "operationByColumns";
  public static final String UPDATE_BY_COLUMN_VARIABLE = "updateByColumn";
  public static final String STATEMENT_VARIABLE = "stm";
  public static final String INSERT_STATEMENT_VARIABLE = "insertStm";
  public static final String UPDATE_STATEMENT_VARIABLE = "updateStm";
  public static final String OPERATION_HELPER_VARIABLE = "opHelper";
  public static final String MODULE_NAME_VARIABLE = "moduleName";
  @NonNull
  private final Environment environment;

  @Inject
  public ModelWriter(@NonNull Environment environment) {
    this.environment = environment;
  }

  private final ModelPartGenerator[] modelPartGenerators = new ModelPartGenerator[]{
      new ModelPersistingGenerator(),
      new ModelRetrievingGenerator(),
      new ModelDeletingGenerator()
  };

  public void writeSource(Filer filer, TableElement tableElement) throws IOException {
    final TypeName tableElementTypeName = tableElement.getTableElementTypeName();
    final EntityEnvironment entityEnvironment = new EntityEnvironment(tableElement, tableElementTypeName);
    final TypeSpec.Builder daoClassBuilder = TypeSpec.classBuilder(entityEnvironment.getDaoClassNameString())
        .addModifiers(CLASS_MODIFIERS);
    final TypeSpec.Builder handlerClassBuilder = TypeSpec.classBuilder(entityEnvironment.getHandlerClassNameString())
        .addModifiers(CLASS_MODIFIERS);

    for (ModelPartGenerator generator : modelPartGenerators) {
      generator.write(daoClassBuilder, handlerClassBuilder, entityEnvironment);
    }

    WriterUtil.writeSource(filer, daoClassBuilder.build(), tableElement.getPackageName());
    WriterUtil.writeSource(filer, handlerClassBuilder.build());

    ColumnClassWriter.from(tableElement, environment, false).write(filer);
    StructureWriter.from(entityEnvironment, environment).write(filer);
  }
}
