package com.siimkinks.sqlitemagic;

import com.google.auto.service.AutoService;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.transformer.Transformer;
import com.siimkinks.sqlitemagic.module.CompilerModule;
import com.siimkinks.sqlitemagic.processing.GenClassesManagerStep;
import com.siimkinks.sqlitemagic.processing.ModelCodeGenerationStep;
import com.siimkinks.sqlitemagic.processing.ModelCollectionStep;
import com.siimkinks.sqlitemagic.processing.ProcessingStep;
import com.siimkinks.sqlitemagic.processing.TransformerCodeGenerationStep;
import com.siimkinks.sqlitemagic.processing.TransformerCollectionStep;
import com.siimkinks.sqlitemagic.processing.ViewCodeGenerationStep;
import com.siimkinks.sqlitemagic.processing.ViewCollectionStep;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import dagger.ObjectGraph;

@AutoService(Processor.class)
public class SqliteMagicProcessor extends AbstractProcessor {

  public static final String KEY_SQLITE_MAGIC_AUTO_LIB = "SQLITE_MAGIC_AUTO_LIB";
  public static final String KEY_SQLITE_MAGIC_GENERATE_LOGGING = "SQLITE_MAGIC_GENERATE_LOGGING";
  public static final String KEY_SQLITE_MAGIC_DB_VERSION = "SQLITE_MAGIC_DB_VERSION";
  public static final String KEY_SQLITE_MAGIC_DB_NAME = "SQLITE_MAGIC_DB_NAME";
  public static boolean GENERATE_LOGGING = false;
  private static ObjectGraph objectGraph;
  private Environment environment;
  private ImmutableSet<? extends ProcessingStep> processingSteps;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        Table.class.getCanonicalName(),
        Column.class.getCanonicalName(),
        Transformer.class.getCanonicalName(),
        View.class.getCanonicalName()
    );
  }

  @Override
  public synchronized void init(ProcessingEnvironment env) {
    super.init(env);
    environment = new Environment(
        env.getMessager(),
        env.getElementUtils(),
        env.getTypeUtils(),
        env.getFiler()
    );
    objectGraph = ObjectGraph.create(new CompilerModule(environment));
    initSettingsFromGradlePlugin(environment);
    processingSteps = ImmutableSet.of(
        new TransformerCollectionStep(),
        new TransformerCodeGenerationStep(),
        new ModelCollectionStep(),
        new ModelCodeGenerationStep(),
        new ViewCollectionStep(),
        new ViewCodeGenerationStep(),
        new GenClassesManagerStep()
    );
    Const.init(environment);
  }

  private void initSettingsFromGradlePlugin(Environment environment) {
    String generateLogging = System.getProperty(KEY_SQLITE_MAGIC_GENERATE_LOGGING);
    if (generateLogging != null) {
      GENERATE_LOGGING = Boolean.valueOf(generateLogging);
    }
    String sqliteMagicAutoLib = System.getProperty(KEY_SQLITE_MAGIC_AUTO_LIB);
    if (Strings.isNullOrEmpty(sqliteMagicAutoLib)) {
      throw new RuntimeException("Missing AutoValue lib config");
    }
    environment.setAutoValueLib(sqliteMagicAutoLib);
    String dbVersion = System.getProperty(KEY_SQLITE_MAGIC_DB_VERSION);
    if (!Strings.isNullOrEmpty(dbVersion)) {
      environment.setDbVersion(dbVersion);
    }
    String dbName = System.getProperty(KEY_SQLITE_MAGIC_DB_NAME);
    if (!Strings.isNullOrEmpty(dbVersion)) {
      environment.setDbName(dbName);
    }
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if (environment.isProcessingFailed()) {
      return false;
    }
    environment.incrementRound();
    for (ProcessingStep processingStep : processingSteps) {
      if (!processingStep.process(annotations, roundEnv)) {
        environment.setProcessingFailed(true);
        return false;
      }
    }
    environment.clear();
    return false;
  }

  public static void inject(Object target) {
    objectGraph.inject(target);
  }
}
