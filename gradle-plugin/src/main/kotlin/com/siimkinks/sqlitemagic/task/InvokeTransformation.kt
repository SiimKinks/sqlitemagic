package com.siimkinks.sqlitemagic.task

import com.siimkinks.sqlitemagic.annotation.internal.Invokes
import javassist.CtClass
import javassist.CtMethod
import javassist.bytecode.annotation.Annotation
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import java.io.File

class InvokeTransformation(destinationDir: File,
						   sources: FileCollection,
						   classpath: FileCollection,
						   debug: Boolean = false) : BaseTransformation(destinationDir, sources, classpath, debug) {
	val METHOD_ANNOTATIONS = setOf<String>(Invokes::class.java.canonicalName)
	override val LOG = Logging.getLogger(InvokeTransformation::class.java)

	override fun shouldTransform(candidateClass: CtClass): Boolean {
		return candidateClass.methods.any { it.hasAnyAnnotationFrom(METHOD_ANNOTATIONS) }
	}

	override fun applyTransform(clazz: CtClass) {
		logInfo("Transforming ${clazz.name}")
		clazz.methods.forEach {
			val invokes = it.findAnnotation(Invokes::class.java)
			if (invokes != null) {
				handleInvocation(clazz, it, invokes)
			}
		}
	}

	private fun handleInvocation(clazz: CtClass, method: CtMethod, invokesAnnotation: Annotation) {
		val value = invokesAnnotation.getAnnotationElementValue(ANNOTATION_VALUE_NAME) ?: return
		val pos = value.indexOf("#")
		val targetMethod = value.substring(pos + 1)
		val targetClass = value.substring(0, pos)
		logInfo("Invoker ${method.name} invokes targetMethod $targetMethod of class $targetClass")
		try {
			val body = StringBuilder()
			val returnType = method.returnType
			if (!returnType.name.equals("void", ignoreCase = true)) {
				body.append("return ")
			}
			body.append(targetClass)
			body.append('.')
			body.append(targetMethod)
			body.append('(')
			if (invokesAnnotation.getAnnotationElementValue("useThisAsOnlyParam")?.toBoolean() ?: false) {
				body.append("this")
			} else {
				body.append("$$")
			}
			body.append(");")
			method.setBody(body.toString())
		} catch (e: Exception) {
			val errMsg = e.message
			if (errMsg != null && errMsg.contains("class is frozen")) {
				logInfo("Not transforming frozen class ${clazz.name}; errMsg=$errMsg")
			} else {
				logError("Transformation failed for class ${clazz.name}", e)
			}
		}

	}
}