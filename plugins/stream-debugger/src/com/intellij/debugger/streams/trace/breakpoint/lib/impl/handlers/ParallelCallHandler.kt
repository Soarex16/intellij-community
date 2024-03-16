// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Transforms the parallel operator as follows:
 * ```java
 * // before
 * .peek(x -> parallelPeek0Before.put(time.get(), x))
 * // operator call
 * .parallel()
 * // after
 * .sequential()
 * .peek(x -> parallelPeek0After.put(time.incrementAndGet(), x))
 * ```
 */
class ParallelCallHandler(valueManager: ValueManager,
                          time: ObjectReference,
                          typeBefore: GenericType?,
                          typeAfter: GenericType?) : PeekCallHandler(valueManager, time, typeBefore, typeAfter) {
  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = valueManager.watch(evaluationContextImpl) {
    val streamObject = value as? ObjectReference ?: return@watch value
    val sequentializedStream = addSequentialOperator(streamObject)
    super.afterCall(evaluationContextImpl, sequentializedStream)
  }
}