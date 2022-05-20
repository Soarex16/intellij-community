// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.collector

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

/**
 * @author Shumaf Lovpache
 */
interface StreamValuesCollectorFactory {
  fun getForIntermediate(evaluationContext: EvaluationContextImpl, collectorType: String): ValueCollector

  fun getForTermination(evaluationContext: EvaluationContextImpl): ValueCollector

  val collectedValues: StreamTraceValues
}

data class StreamTraceValues(
  val intermediateOperationValues: List<ObjectReference>,
  val streamResult: Value,
  val time: ObjectReference,
  val elapsedTime: Value
)