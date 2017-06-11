package com.siimkinks.sqlitemagic

import com.siimkinks.sqlitemagic.processing.ModelExtensionsGenerationStep
import dagger.Module
import dagger.Provides
import java.io.File
import javax.inject.Singleton

@Module(injects = arrayOf(ModelExtensionsGenerationStep::class), complete = false)
open class KotlinModule(private val generatedSourceTargetDir: File) {
  @Provides
  @Singleton
  fun provideTargetDir(): File = generatedSourceTargetDir
}