package com.siimkinks.sqlitemagic

import com.android.build.api.transform.*
import com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import com.android.build.api.transform.QualifiedContent.Scope.*
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.BaseVariant
import com.siimkinks.sqlitemagic.task.InvokeTransformation
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.AbstractCompile
import org.zeroturnaround.zip.ZipUtil
import java.io.File
import java.util.*

class SqliteMagicTransform(val project: Project,
                           val sqlitemagic: SqliteMagicPluginExtension,
                           val androidExtension: BaseExtension) : Transform() {
  private val javaCompileTasks: HashMap<Pair<String, String>, AbstractCompile> = HashMap()
  val jarContentType = setOf(CLASSES)
  val jarScope = mutableSetOf(EXTERNAL_LIBRARIES)

  override fun transform(transformInvocation: TransformInvocation) {
    super.transform(transformInvocation)
    val projectFilesOutput = transformInvocation.outputProvider.getContentLocation(name, setOf(CLASSES), mutableSetOf(PROJECT), Format.DIRECTORY)

    transformInvocation.inputs
        .filter { it.directoryInputs.isNotEmpty() }
        .forEach { transformProjectFiles(it, projectFilesOutput, transformInvocation) }

    transformInvocation.inputs
        .filter { it.jarInputs.isNotEmpty() }
        .forEach { transformExternalLibraries(it, projectFilesOutput, transformInvocation) }
  }

  private fun transformProjectFiles(input: TransformInput,
                                    projectFilesOutput: File,
                                    transformInvocation: TransformInvocation) {
    val incremental = transformInvocation.isIncremental
    input.directoryInputs.forEach {
      val classpath = transformInvocation.classpath(it.file)
          .plus(project.files(it.file))
      val sources =
          if (incremental) {
            it.changedFiles
                .filter { it.value != Status.REMOVED }
                .map { it.key.absolutePath }
                .toList()
          } else {
            it.file.getAllClassFilePaths()
          }

      InvokeTransformation(
          destinationDir = projectFilesOutput,
          classpath = classpath,
          sources = project.files(sources),
          debug = sqlitemagic.debugBytecodeProcessor)
          .exec()
    }
  }

  private fun transformExternalLibraries(input: TransformInput,
                                         projectFilesOutput: File,
                                         transformInvocation: TransformInvocation) {
    filterJarInput(input, transformInvocation)
        .forEach { it.transform(transformInvocation, projectFilesOutput) }
  }

  private fun JarInput.transform(transformInvocation: TransformInvocation,
                                 projectFilesOutput: File) {
    val tmpDir = File(transformInvocation.context.temporaryDir, name)
    if (tmpDir.exists()) {
      tmpDir.deleteRecursively()
    }
    val extractDir = File(tmpDir, "in")
    val outputDir = File(tmpDir, "out")
    val jarOutput = transformInvocation.jarOutput(name)
    project.copy {
      it.from(project.zipTree(file))
      it.into(project.file(extractDir))
    }
    val classpath = transformInvocation.classpath(jarOutput)
        .plus(project.files(projectFilesOutput))
    val anyTransformations = InvokeTransformation(
        destinationDir = outputDir,
        classpath = classpath,
        sources = project.files(extractDir.getAllClassFilePaths()),
        debug = sqlitemagic.debugBytecodeProcessor)
        .exec()

    if (!anyTransformations) {
      copyToOutput(transformInvocation)
      return
    }

    val outputFiles = outputDir.list().toSet()
    extractDir.listFiles()
        .forEach {
          if (it.name !in outputFiles) {
            it.copyRecursively(File(outputDir, it.name))
          }
        }

    try {
      jarOutput.createNewFile()
      ZipUtil.pack(outputDir, jarOutput)
    } catch (e: Throwable) {
      // ignore
    }
  }

  private fun filterJarInput(input: TransformInput, transformInvocation: TransformInvocation): Collection<JarInput> {
    val incremental = transformInvocation.isIncremental
    return if (incremental) input.jarInputs.filter { filterByStatus(it) } else input.jarInputs
  }

  private fun filterByStatus(input: JarInput) = input.status != Status.REMOVED && input.status != Status.NOTCHANGED

  private fun TransformInvocation.jarOutput(name: String) = outputProvider.getContentLocation(name, jarContentType, jarScope, Format.JAR)

  private fun TransformInvocation.classpath(inputFile: File): FileCollection = classpath(inputFile, referencedInputs)

  private fun JarInput.copyToOutput(transformInvocation: TransformInvocation) {
    val destination = transformInvocation.jarOutput(name)
    destination.delete()
    file.copyRecursively(destination)
  }

  private fun Collection<JarInput>.copyAllToOutput(transformInvocation: TransformInvocation, predicate: ((JarInput) -> Boolean)? = null) {
    if (predicate != null) {
      for (value in this) {
        if (predicate(value)) {
          value.copyToOutput(transformInvocation)
        }
      }
    } else {
      for (value in this) {
        value.copyToOutput(transformInvocation)
      }
    }
  }

  private fun File.getAllClassFilePaths(): List<String> = walkTopDown()
      .filter { !it.isDirectory && it.name.endsWith(".class") }
      .map { it.absolutePath }
      .toList()

  /**
   * Classpath getting from Retrolambda
   */
  private fun classpath(inputFile: File, referencedInputs: Collection<TransformInput>): FileCollection {
    var buildName = inputFile.name
    var flavorName = inputFile.parentFile.name

    // If either one starts with a number or is 'folders', it's probably the result of a transform, keep moving
    // up the dir structure until we find the right folders.
    // Yes I know this is bad, but hopefully per-variant transforms will land soon.
    var current = inputFile
    while (buildName[0].isDigit() || flavorName[0].isDigit()
        || buildName == "folders" || flavorName == "folders"
        || buildName == "jars" || flavorName == "jars") {
      current = current.parentFile
      buildName = current.name
      flavorName = current.parentFile.name
    }

    val compileTask = javaCompileTasks[Pair(flavorName, buildName)] ?: javaCompileTasks[Pair("", buildName)]!!
    var classPathFiles = compileTask.classpath
        .plus(project.files(androidExtension.bootClasspath))
    referencedInputs.forEach { classPathFiles = classPathFiles.plus(project.files(it.directoryInputs.forEach { it.file })) }

    return classPathFiles
  }

  fun putJavaCompileTask(variant: BaseVariant) {
    javaCompileTasks.put(Pair(variant.flavorName, variant.buildType.name), variant.javaCompile)
  }

  override fun getScopes(): MutableSet<QualifiedContent.Scope> {
    return mutableSetOf(PROJECT, SUB_PROJECTS, EXTERNAL_LIBRARIES)
  }

  override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
    return mutableSetOf(CLASSES)
  }

  override fun getName(): String {
    return "sqlitemagic"
  }

  override fun isIncremental(): Boolean {
    return true
  }
}