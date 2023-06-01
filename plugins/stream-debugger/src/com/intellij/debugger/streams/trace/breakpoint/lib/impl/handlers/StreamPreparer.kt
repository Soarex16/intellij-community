// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.UNIVERSAL_COLLECTOR_CLASS_NAME
import com.intellij.debugger.streams.trace.breakpoint.UNIVERSAL_COLLECTOR_CONSTRUCTOR_SIGNATURE
import com.intellij.debugger.streams.trace.breakpoint.ValueContext
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.lib.RuntimeSourceCallHandler
import com.sun.jdi.Method
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * Prepares stream for further transformation.
 * First, it transforms source stream to sequential.
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
 *
 * Then it calls
 * ```java
 * .peek(x -> time.incrementAndGet())
 * ```
 * to be able to track time for subsequent operators.
 */
class StreamPreparer(private val valueManager: ValueManager, val time: ObjectReference) : RuntimeSourceCallHandler {
  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = valueManager.watch(evaluationContextImpl) {
    val streamObject = value as? ObjectReference ?: return@watch null
    val sequentializedStream = addSequentialOperator(streamObject)
    val streamWithTicker = addTicker(sequentializedStream)
    streamWithTicker
  }

  private fun ValueContext.addTicker(streamObject: ObjectReference): ObjectReference {
    val ticker = instance(
      UNIVERSAL_COLLECTOR_CLASS_NAME,
      UNIVERSAL_COLLECTOR_CONSTRUCTOR_SIGNATURE,
      listOf(null, time, true.mirror)
    )

    return findPeekMethod(streamObject)
      .invoke(streamObject, listOf(ticker)) as ObjectReference
  }

  private fun findPeekMethod(streamObject: ObjectReference): Method {
    // methodsByName may throw ClassNotPreparedException, but by the time we execute this method,
    // it is guaranteed that the stream class is loaded
    return streamObject.referenceType().methodsByName("peek").single()
  }

  private fun ValueContext.addSequentialOperator(streamObject: ObjectReference): ObjectReference {
    return streamObject
      .method("sequential", "()Ljava/util/stream/BaseStream;")
      .invoke(streamObject) as ObjectReference
  }
}