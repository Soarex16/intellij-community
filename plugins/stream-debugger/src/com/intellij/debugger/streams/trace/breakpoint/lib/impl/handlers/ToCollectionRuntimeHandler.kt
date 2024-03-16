// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

class ToCollectionRuntimeHandler(call: TerminatorStreamCall,
                                 private val valueManager: ValueManager,
                                 private val time: ObjectReference) : PeekTerminalCallHandler(valueManager, time, call.typeBefore,
                                                                                              call.resultType) {
  override fun result(evaluationContextImpl: EvaluationContextImpl): Value = valueManager.watch(evaluationContextImpl) {
    val (info, result) = rawResult(evaluationContext)
    array(
      array(
        info,
        formatTime(evaluationContext)
      ),
      result
    )
  }

  private fun formatTime(evaluationContext: EvaluationContextImpl): Value = valueManager.watch(evaluationContext) {
    val getTime = time.method("get", "()I")
    val timeValue = getTime.invoke(time, emptyList())
    val timeArr = array("int", 1)
    timeArr.setValue(0, timeValue)
    timeArr
  }
}