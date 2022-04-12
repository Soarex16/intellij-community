// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluateExceptionUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.openapi.compiler.ClassObject
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project
import com.sun.jdi.ReferenceType
import com.sun.jdi.event.MethodExitEvent

/**
 * @author Shumaf Lovpache
 */
fun createHelperClass(suspendContext: SuspendContextImpl, event: MethodExitEvent) {
  val source = """
      class MapCollector<T> implements Consumer<T> {
        private final java.util.Map<java.lang.Integer, T> storage;
        private final java.util.concurrent.atomic.AtomicInteger time;

        MapCollector(Map<Integer, T> storage, AtomicInteger time) {
            this.storage = storage;
            this.time = time;
        }

        @Override
        public void accept(T t) {
            storage.put(time.incrementAndGet(), t);
        }
      }
    """.trimIndent()

  // compileClass
}

fun compileClass(project: Project, sourceCode: String): ClassObject? {
  val compilerManager: CompilerManager = CompilerManager.getInstance(project)
  // TODO: научиться компилировать код
  //  compilerManager.compileJavaCode()
  return null
}

fun EvaluationContextImpl.loadClass(className: String): ReferenceType? = debugProcess.loadClass(this, className, classLoader)

fun EvaluationContextImpl.loadClassIfAbsent(className: String, bytesLoader: () -> ByteArray?): ReferenceType? {
  try {
    return try {
      val classLoader = this.classLoader
      this.debugProcess.findClass(this, className, classLoader)
    } catch (e: EvaluateException) {
      if (e.exceptionFromTargetVM!!.type().name() == "java.lang.ClassNotFoundException") {
        val bytes = bytesLoader() ?: return null
        loadAndPrepareClass(this, className, bytes)
      } else {
        throw e
      }
    }
  }
  catch (e: Throwable) {
    return null
  }
}

@Throws(EvaluateException::class)
private fun loadAndPrepareClass(evaluationContext: EvaluationContextImpl, name: String, bytes: ByteArray): ReferenceType? {
  val debugProcess = evaluationContext.debugProcess
  evaluationContext.isAutoLoadClasses = true
  val classLoader = evaluationContext.classLoader
  ClassLoadingUtils.defineClass(name, bytes, evaluationContext, debugProcess, classLoader)
  return try {
    debugProcess.loadClass(evaluationContext, name, classLoader)
  }
  catch (e: Exception) {
    throw EvaluateExceptionUtil.createEvaluateException("Could not load class", e)
  }
}