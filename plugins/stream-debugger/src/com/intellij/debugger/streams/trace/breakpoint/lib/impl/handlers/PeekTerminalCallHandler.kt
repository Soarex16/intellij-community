// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueContext
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.lib.RuntimeTerminalCallHandler
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.psi.CommonClassNames
import com.sun.jdi.ArrayReference
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import com.sun.jdi.VoidValue

open class PeekTerminalCallHandler(private val valueManager: ValueManager,
                                   time: ObjectReference,
                                   typeBefore: GenericType?,
                                   typeAfter: GenericType?) : RuntimeTerminalCallHandler {

  private val peekCallHandler = PeekCallHandler(valueManager, time, typeBefore, typeAfter)
  private var streamResult: Value? = null

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value = valueManager.watch(evaluationContextImpl) {
    val (info, result) = rawResult(evaluationContext)
    array(info, result)
  }

  protected fun ValueContext.rawResult(evaluationContextImpl: EvaluationContextImpl): Pair<Value, ArrayReference> {
    val wrappedStreamResult = if (streamResult is VoidValue) {
      array(CommonClassNames.JAVA_LANG_OBJECT, 1)
    }
    else {
      array(streamResult)
    }
    return Pair(peekCallHandler.result(evaluationContextImpl), wrappedStreamResult)
  }

  override fun beforeCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = peekCallHandler.beforeCall(evaluationContextImpl, value)

  override fun transformArguments(evaluationContextImpl: EvaluationContextImpl,
                                  method: Method,
                                  arguments: List<Value?>): List<Value?> = peekCallHandler.transformArguments(evaluationContextImpl, method, arguments)

  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? {
    streamResult = value
    return value
  }
}