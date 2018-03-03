package com.siimkinks.sqlitemagic;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.siimkinks.sqlitemagic.annotation.Column;
import com.siimkinks.sqlitemagic.annotation.Database;
import com.siimkinks.sqlitemagic.annotation.Index;
import com.siimkinks.sqlitemagic.annotation.SubmoduleDatabase;
import com.siimkinks.sqlitemagic.annotation.Table;
import com.siimkinks.sqlitemagic.annotation.View;
import com.siimkinks.sqlitemagic.annotation.transformer.DbValueToObject;
import com.siimkinks.sqlitemagic.annotation.transformer.ObjectToDbValue;
import com.siimkinks.sqlitemagic.module.CompilerModule;
import com.siimkinks.sqlitemagic.processing.DatabaseConfigurationCollectionStep;
import com.siimkinks.sqlitemagic.processing.GenClassesManagerStep;
import com.siimkinks.sqlitemagic.processing.IndexCollectionStep;
import com.siimkinks.sqlitemagic.processing.ModelCodeGenerationStep;
import com.siimkinks.sqlitemagic.processing.ModelCollectionStep;
import com.siimkinks.sqlitemagic.processing.ProcessingStep;
import com.siimkinks.sqlitemagic.processing.TransformerCodeGenerationStep;
import com.siimkinks.sqlitemagic.processing.TransformerCollectionStep;
import com.siimkinks.sqlitemagic.processing.ViewCodeGenerationStep;
import com.siimkinks.sqlitemagic.processing.ViewCollectionStep;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import dagger.ObjectGraph;

public class BaseProcessor extends AbstractProcessor {
  public static boolean GENERATE_LOGGING = false;
  public static boolean PUBLIC_EXTENSIONS = false;
  static ObjectGraph objectGraph;
  private Environment environment;
  private ImmutableSet<? extends ProcessingStep> processingSteps;

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(
        Database.class.getCanonicalName(),
        SubmoduleDatabase.class.getCanonicalName(),
        Table.class.getCanonicalName(),
        Column.class.getCanonicalName(),
        ObjectToDbValue.class.getCanonicalName(),
        DbValueToObject.class.getCanonicalName(),
        View.class.getCanonicalName(),
        Index.class.getCanonicalName()
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
    objectGraph = createObjectGraph(env);
    parseCompilerArguments(environment, env.getOptions());
    processingSteps = ImmutableSet.<ProcessingStep>builder()
        .addAll(Arrays.asList(
            new DatabaseConfigurationCollectionStep(),
            new TransformerCollectionStep(),
            new TransformerCodeGenerationStep(),
            new ModelCollectionStep(),
            new ModelCodeGenerationStep(),
            new ViewCollectionStep(),
            new ViewCodeGenerationStep(),
            new IndexCollectionStep(),
            new GenClassesManagerStep()
        ))
        .addAll(processingSteps())
        .build();
    Const.init(environment);
  }

  private void parseCompilerArguments(Environment environment, Map<String, String> options) {
    String publicExtensions = options.get("sqlitemagic.kotlin.public.extensions");
    if (publicExtensions != null) {
      PUBLIC_EXTENSIONS = Boolean.valueOf(publicExtensions);
    }
    String generateLogging = options.get("sqlitemagic.generate.logging");
    if (generateLogging != null) {
      GENERATE_LOGGING = Boolean.valueOf(generateLogging);
    }
    String sqliteMagicAutoLib = options.get("sqlitemagic.auto.lib");
    if (Strings.isNullOrEmpty(sqliteMagicAutoLib)) {
      sqliteMagicAutoLib = "com.google.auto.value.AutoValue";
    }
    environment.setAutoValueLib(sqliteMagicAutoLib);
    final String projectDir = options.get("sqlitemagic.project.dir");
    if (!Strings.isNullOrEmpty(projectDir)) {
      environment.setProjectDir(projectDir);
    }
    final String debugVariant = options.get("sqlitemagic.variant.debug");
    if (!Strings.isNullOrEmpty(debugVariant)) {
      environment.setDebugVariant(Boolean.valueOf(debugVariant));
    }
    final String variantDirName = options.get("sqlitemagic.variant.dir.name");
    if (!Strings.isNullOrEmpty(variantDirName)) {
      environment.setVariantDirName(variantDirName);
    }
    String dbVersion = options.get("sqlitemagic.db.version");
    if (!Strings.isNullOrEmpty(dbVersion)) {
      environment.setDbVersion(dbVersion);
    }
    String dbName = options.get("sqlitemagic.db.name");
    if (!Strings.isNullOrEmpty(dbVersion)) {
      environment.setDbName(dbName);
    }
  }

  protected ObjectGraph createObjectGraph(ProcessingEnvironment env) {
    return ObjectGraph.create(new CompilerModule(environment));
  }

  protected Set<ProcessingStep> processingSteps() {
    return Collections.emptySet();
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
