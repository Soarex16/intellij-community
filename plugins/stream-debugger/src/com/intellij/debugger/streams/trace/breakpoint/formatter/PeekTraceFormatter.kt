// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.formatter

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.HelperClassUtils.STREAM_DEBUGGER_UTILS_CLASS_NAME
import com.intellij.debugger.streams.trace.breakpoint.HelperClassUtils.getStreamDebuggerUtilsClass
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.ex.IncorrectValueTypeException
import com.intellij.psi.CommonClassNames.JAVA_UTIL_MAP
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

open class PeekTraceFormatter(private val valueManager: ValueManager,
                              private val evaluationContext: EvaluationContextImpl) : TraceFormatter {
  init {
    valueManager.defineClass(STREAM_DEBUGGER_UTILS_CLASS_NAME) {
      getStreamDebuggerUtilsClass()
    }
  }

  /**
   * Converts the result of the intermediate operation to the following format:
   * ```
   * var beforeArray = java.lang.Object[] { keys, values };
   * var afterArray = java.lang.Object[] { keys, values };
   * new java.lang.Object[] { beforeArray, afterArray };
   * ```
   */
  override fun format(beforeValues: Value?, afterValues: Value?): Value = valueManager.watch(evaluationContext) {
    val before = formatMap(beforeValues)
    val after = formatMap(afterValues)
    array(before, after)
  }

  protected fun formatMap(values: Value?) = valueManager.watch(evaluationContext) {
    if (values == null) {
      emptyResult()
    }
    else {
      checkType(values)
      getMapKeysAndValues(values as ObjectReference)
    }
  }

  private fun emptyResult(): ArrayReference = valueManager.watch(evaluationContext) {
    array(
      array("int", 0),
      array()
    )
  }

  private fun checkType(value: Value) {
    if (!(value is ObjectReference && DebuggerUtils.instanceOf(value.type(), JAVA_UTIL_MAP))) {
      throw IncorrectValueTypeException(JAVA_UTIL_MAP, value.type().name())
    }
  }

  private fun getMapKeysAndValues(valueMap: ObjectReference): ArrayReference = valueManager.watch(evaluationContext) {
    val helperClass = getType(STREAM_DEBUGGER_UTILS_CLASS_NAME) as ClassType
    val formatMap = helperClass.method("formatMap", "(Ljava/util/Map;)[Ljava/lang/Object;")
    invoke(helperClass, formatMap, listOf(valueMap)) as ArrayReference
  }
}