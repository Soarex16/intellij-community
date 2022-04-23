// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ex.CodeCompilationException
import com.intellij.openapi.compiler.ClassObject

/**
 * @author Shumaf Lovpache
 */

object HelperClassUtils {
  const val STREAM_DEBUGGER_UTILS_CLASS_NAME = "com.intellij.debugger.streams.generated.java.StreamDebuggerUtils"
  const val STREAM_DEBUGGER_UTILS_SOURCE = "/classes/StreamDebuggerUtils.java.txt"

  fun getCompiledHelperClass(context: EvaluationContextImpl, resourceName: String, className: String): ClassObject? = javaClass
    .getResourceAsStream(resourceName).use {
      if (it == null) throw CodeCompilationException("Could not load $resourceName")

      val source = it.bufferedReader().readText()
      return compileJavaCode(className, source, context)
    }

  fun getStreamDebuggerUtilsClass(context: EvaluationContextImpl) = getCompiledHelperClass(
    context,
    STREAM_DEBUGGER_UTILS_SOURCE,
    STREAM_DEBUGGER_UTILS_CLASS_NAME
  )
}