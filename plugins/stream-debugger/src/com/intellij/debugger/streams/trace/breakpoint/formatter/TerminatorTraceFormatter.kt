// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.formatter

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.collector.StreamTraceValues
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

class TerminatorTraceFormatter(private val valueManager: ValueManager,
                               private val evaluationContext: EvaluationContextImpl,
                               private val collectedValues: StreamTraceValues) : PeekTraceFormatter(valueManager, evaluationContext) {
  /**
   * Converts the result of the intermediate operation to the following format:
   * ```
   * var beforeArray = // same as in PeekTraceFormatter
   * var afterArray = // same as in PeekTraceFormatter
   * new java.lang.Object[] { beforeArray, afterArray, time };
   * ```
   */
  override fun format(beforeValues: Value?, afterValues: Value?): Value = valueManager.watch(evaluationContext) {
    val before = super.formatMap(beforeValues)
    val after = super.formatMap(afterValues)

    val formattedTime = formatTime(collectedValues.time)
    array(
      array(before, after),
      formattedTime
    )
  }

  private fun formatTime(time: ObjectReference): Value = valueManager.watch(evaluationContext) {
    val getTime = time.method("get", "()I")
    val timeValue = invoke(time, getTime, emptyList())
    val timeArr = array("int", 1)
    timeArr.setValue(0, timeValue)
    timeArr
  }
}