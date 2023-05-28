// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.lib.RuntimeSourceCallHandler
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Transforms source stream to sequential.
 * This trick helps us correctly trace chain in case when qualifier is unknown.
 *
 * Consider following code:
 * ```java
 *   public static void main(String[] args) {
 *     var stream = IntStream
 *       .rangeClosed(1, 100_000)
 *       .parallel()
 *       .map(x -> x + 1);
 *     System.out.println(doubleSum(stream));
 *   }
 *
 *   public static int doubleSum(IntStream s) {
 *     return s.sum();
 *   }
 * ```
 * in this case if we want to trace stream inside `doubleSum` method we will get incorrect
 * results because stream tracing relies on order of operators execution, but stream operations
 * can be executed in any order because it's parallel.
 */
class Sequentializer(private val valueManager: ValueManager) : RuntimeSourceCallHandler {
  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = valueManager.watch(evaluationContextImpl) {
    val streamObject = value as? ObjectReference ?: return@watch null
    val streamType = streamObject.referenceType() as ClassType

    streamType
      .method("sequential", "()Ljava/util/stream/BaseStream;")
      .invoke(streamObject)
  }
}