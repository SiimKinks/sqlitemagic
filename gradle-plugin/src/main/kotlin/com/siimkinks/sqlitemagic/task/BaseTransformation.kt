package com.siimkinks.sqlitemagic.task

import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import javassist.Loader
import javassist.bytecode.AnnotationsAttribute
import javassist.bytecode.annotation.Annotation
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import java.io.File
import java.util.*

abstract class BaseTransformation(val destinationDir: File,
                                  val sources: FileCollection,
                                  val classpath: FileCollection,
                                  val debug: Boolean = false) {
  open val LOG = Logging.getLogger(BaseTransformation::class.java)

  protected abstract fun shouldTransform(candidateClass: CtClass): Boolean

  protected abstract fun applyTransform(clazz: CtClass)

  fun exec(): Boolean {
    if (sources.isEmpty) {
      logInfo("No source files")
      return false
    }
    try {
      val loadedClasses = preloadClasses()
      var anyTransformations = false
      for (clazz in loadedClasses) {
        if (processClass(clazz)) {
          anyTransformations = true
        }
      }
      return anyTransformations
    } catch(e: Exception) {
      throw GradleException("Could not execute transformation", e);
    }
  }

  private fun preloadClasses(): List<CtClass> {
    val loadedClasses = LinkedList<CtClass>()
    val pool = AnnotationLoadingClassPool()

    // set up the classpath for the classpool
    for (f in classpath) {
      pool.appendClassPath(f.absolutePath)
    }

    // add the files to process
    for (f in sources) {
      if (!f.isDirectory) {
        loadedClasses.add(loadClassFile(pool, f))
      }
    }

    return loadedClasses
  }

  private fun processClass(clazz: CtClass): Boolean {
    try {
      var transformed = false
      if (shouldTransform(clazz)) {
        clazz.defrost()
        applyTransform(clazz)
        transformed = true
      }
      clazz.writeFile(destinationDir.absolutePath)
      return transformed
    } catch(e: Exception) {
      throw GradleException("An error occurred while trying to process class file ", e)
    }
  }

  private fun loadClassFile(pool: ClassPool, classFile: File): CtClass {
    classFile.inputStream().use {
      return pool.makeClass(it)
    }
  }

  protected fun logInfo(msg: String) {
    if (debug) LOG.info("SQLITEMAGIC -- $msg")
  }

  protected fun logError(msg: String, e: Throwable) {
    LOG.error(msg, e)
  }
}

/**
 * This class loader will load annotations encountered in loaded classes
 * using the pool itself.
 * @see <a href="https://github.com/jboss-javassist/javassist/pull/18">Javassist issue 18</a>
 */
class AnnotationLoadingClassPool : ClassPool(true) {
  override fun getClassLoader(): ClassLoader {
    return Loader(this);
  }
}

const val ANNOTATION_VALUE_NAME = "value"

fun CtMethod.hasAnyAnnotationFrom(annotationClasses: Set<String>): Boolean {
  val methodInfo = this.methodInfo2
  val invisibleAnnotations = methodInfo.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?
  val visibleAnnotations = methodInfo.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?
  if (invisibleAnnotations != null && invisibleAnnotations.containsAnyAnnotation(annotationClasses)) {
    return true
  }
  return visibleAnnotations != null && visibleAnnotations.containsAnyAnnotation(annotationClasses)
}

fun CtMethod.findAnnotation(annotationClass: Class<*>): Annotation? {
  val annotationName = annotationClass.canonicalName
  val methodInfo = methodInfo2
  val invisible = methodInfo.getAttribute(AnnotationsAttribute.invisibleTag) as AnnotationsAttribute?
  val visible = methodInfo.getAttribute(AnnotationsAttribute.visibleTag) as AnnotationsAttribute?
  if (invisible != null) {
    return invisible.getAnnotation(annotationName)
  }
  if (visible != null) {
    return visible.getAnnotation(annotationName)
  }
  return null
}

fun AnnotationsAttribute.containsAnyAnnotation(annotationClasses: Set<String>): Boolean {
  for (annotation in annotations) {
    if (annotationClasses.contains(annotation.typeName)) {
      return true
    }
  }
  return false
}

fun Annotation.getAnnotationElementValue(valueName: String): String? {
  return getMemberValue(valueName)?.toString()?.trim('"') // for string value we don't need leading and trailing "-s
}
