package com.siimkinks.sqlitemagic.processing

import com.google.common.base.Strings
import com.siimkinks.sqlitemagic.BaseProcessor
import com.siimkinks.sqlitemagic.Environment
import com.siimkinks.sqlitemagic.writer.ModelExtensionsWriter
import java.io.File
import javax.annotation.processing.RoundEnvironment
import javax.inject.Inject
import javax.lang.model.element.TypeElement

class ModelExtensionsGenerationStep : ProcessingStep {
  @Inject
  lateinit var environment: Environment
  @Inject
  lateinit var modelExtensionsWriter: ModelExtensionsWriter
  @Inject
  lateinit var generatedSourceTargetDir: File

  init {
    BaseProcessor.inject(this)
  }

  override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
    val writer = modelExtensionsWriter
    environment.tableElements.forEach { tableElement ->
      try {
        writer.writeSource(generatedSourceTargetDir, tableElement)
      } catch(e: Exception) {
        val errMsg = e.message
        environment.error(tableElement.tableElement, errMsg)
        if (Strings.isNullOrEmpty(errMsg)) {
          e.printStackTrace()
        }
        return false
      }
    }
    return true
  }
}