// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.formatter

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.collector.StreamTraceValues
import com.intellij.debugger.streams.wrapper.StreamChain
import com.sun.jdi.ArrayReference

class StreamTraceFormatterImpl(private val valueManager: ValueManager) : StreamTraceFormatter {
  // TODO: use value formatter factory
  override fun formatTraceResult(chain: StreamChain,
                                 collectedValues: StreamTraceValues,
                                 evaluationContext: EvaluationContextImpl): ArrayReference {
    val intermediateStepsValues = collectedValues.intermediateOperationValues
    return valueManager.watch(evaluationContext) {
      val peekFormatter = PeekTraceFormatter(valueManager, evaluationContext)
      val terminatorFormatter = TerminatorTraceFormatter(valueManager, evaluationContext, collectedValues)
      // Переделать сейчас работает только с терминалом collector
      val infoArray = if (intermediateStepsValues.size == 1) {
        array(
          terminatorFormatter.format(intermediateStepsValues.single(), null)
        )
      }
      else {
        val formattedIntermediateTraces = intermediateStepsValues
          .zipWithNext { prevAfter, currentBefore -> peekFormatter.format(prevAfter, currentBefore) }
        val formattedTerminatorTrace = terminatorFormatter.format(intermediateStepsValues.last(), null)
        array(formattedIntermediateTraces + formattedTerminatorTrace)
      }

      array(
        infoArray,
        array(collectedValues.streamResult), // primitive values would not work (and maybe void, i.e. forEach)
        collectedValues.elapsedTime
      )
    }
  }
}