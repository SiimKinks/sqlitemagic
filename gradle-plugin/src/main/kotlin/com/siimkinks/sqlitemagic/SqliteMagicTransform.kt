package com.siimkinks.sqlitemagic

import com.android.build.api.transform.*
import com.android.build.api.transform.QualifiedContent.DefaultContentType.CLASSES
import com.android.build.api.transform.QualifiedContent.Scope.*
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.api.BaseVariant
import com.siimkinks.sqlitemagic.task.InvokeTransformation
import org.gradle.api.Project
import org.gradle.api.ProjectConfigurationException
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.compile.AbstractCompile
import org.zeroturnaround.zip.ZipUtil
import java.io.File
import java.util.*

class SqliteMagicTransform(private val project: Project,
                           private val sqlitemagic: SqliteMagicPluginExtension,
                           private val androidExtension: BaseExtension) : Transform() {
  private val javaCompileTasks: HashMap<Pair<String, String>, AbstractCompile> = HashMap()
  private val jarContentType = setOf(CLASSES)
  private val jarScope = mutableSetOf(EXTERNAL_LIBRARIES)
  private val variants = ArrayList<BaseVariant>()

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
      val classpath = transformInvocation.classpath(projectFilesOutput)
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
    val classpath = transformInvocation.classpath(projectFilesOutput)
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

  private fun TransformInvocation.classpath(outputDir: File): FileCollection = classpath(context, outputDir, referencedInputs)

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
  private fun classpath(context: Context, outputDir: File, referencedInputs: Collection<TransformInput>): FileCollection {
    val variant = getVariant(context, outputDir) ?: throw ProjectConfigurationException("Missing variant for output dir: $outputDir", null)

    var classpathFiles = variant.javaCompile.classpath
    for (input in referencedInputs) {
      classpathFiles += project.files(*input.directoryInputs.map(DirectoryInput::getFile).toTypedArray())
    }

    // bootClasspath isn't set until the last possible moment because it's expensive to look
    // up the android sdk path.
    val bootClasspath = variant.javaCompile.options.bootClasspath
    if (bootClasspath != null) {
      classpathFiles += project.files(*bootClasspath.split(File.pathSeparator).toTypedArray())
    } else {
      // If this is null it means the javaCompile task didn't need to run, however, we still
      // need to run but can't without the bootClasspath. Just fail and ask the user to rebuild.
      throw ProjectConfigurationException ("Unable to obtain the bootClasspath. This may happen if " +
          "your javaCompile tasks didn't run but sqlitemagic did. You must rebuild your project or " +
          "otherwise force javaCompile to run.", null)
    }

    return classpathFiles
  }

  private fun getVariant(context: Context, outputDir: File): BaseVariant? {
    try {
      val variantName = context.variantName
      for (variant in variants) {
        if (variant.name == variantName) {
          return variant
        }
      }
    } catch (e: NoSuchMethodError) {
      // Extract the variant from the output path assuming it's in the form like:
      // - '*/intermediates/transforms/sqlitemagic/<VARIANT>
      // - '*/intermediates/transforms/sqlitemagic/<VARIANT>/folders/1/1/sqlitemagic
      // This will no longer be needed when the transform api supports per-variant transforms
      val parts = outputDir.toURI().path.split("/intermediates/transforms/$name/|/folders/[0-9]+".toRegex())
      if (parts.size < 2) {
        throw ProjectConfigurationException("Could not extract variant from output dir: $outputDir", null)
      }
      val variantPath = parts[1]
      for (variant in variants) {
        if (variant.dirName == variantPath) {
          return variant
        }
      }
    }
    return null
  }

  fun putJavaCompileTask(variant: BaseVariant) {
    variants.add(variant)
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