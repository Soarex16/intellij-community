// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.sun.jdi.*
import com.sun.jdi.request.ExceptionRequest
import com.sun.jdi.request.MethodEntryRequest
import com.sun.jdi.request.MethodExitRequest

/**
 * JDI does not provide mirror for `null`.
 * `null` in debugee VM == `null` in JDI
 * So you should return same value as passed if you don't want to modify
 * */
typealias ReturnValueTransformer = (SuspendContext, Method, Value?) -> Value?
typealias ArgumentsTransformer = (SuspendContext, Method, List<Value?>) -> List<Value?>
typealias ExceptionHandler = (SuspendContext, Location?, ObjectReference) -> Boolean

/**
 * @author Shumaf Lovpache
 * A factory for creating method breakpoints to intercept method calling process
 */
interface MethodBreakpointFactory {
  /**
   * @return disabled method enter request for passed method
   * @throws MethodNotFoundException
   */
  fun createMethodEntryBreakpoint(evaluationContext: EvaluationContextImpl,
                                  signature: MethodSignature,
                                  pauseExecution: Boolean = false,
                                  transformer: ArgumentsTransformer): MethodEntryRequest

  /**
   * @return disabled method exit request for passed method
   * @throws MethodNotFoundException
   */
  fun createMethodExitBreakpoint(evaluationContext: EvaluationContextImpl,
                                 signature: MethodSignature,
                                 pauseExecution: Boolean = false,
                                 transformer: ReturnValueTransformer): MethodExitRequest

  /**
   * @return disabled method exit request for specified exception type
   */
  fun createExceptionBreakpoint(evaluationContext: EvaluationContextImpl,
                                exceptionType: ReferenceType? = null,
                                callback: ExceptionHandler): ExceptionRequest
}