package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.processing.ModelExtensionsGenerationStep
import com.siimkinks.sqlitemagic.processing.ProcessingStep
import dagger.ObjectGraph
import java.io.File
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.SupportedOptions
import javax.tools.Diagnostic

@SupportedOptions("kapt.kotlin.generated", "generate.kotlin.code")
class SqliteMagicProcessor : BaseProcessor() {
  override fun createObjectGraph(env: ProcessingEnvironment): ObjectGraph {
    val generatedSourceTargetDir = checkNotNull(env.options["kapt.kotlin.generated"]) {
      env.messager.printMessage(Diagnostic.Kind.ERROR, "Can't find the target directory for generated Kotlin files.")
    }
    return super.createObjectGraph(env)
        .plus(KotlinModule(generatedSourceTargetDir = File(generatedSourceTargetDir)))
  }

  override fun processingSteps(): Set<ProcessingStep> = setOf(ModelExtensionsGenerationStep())
}