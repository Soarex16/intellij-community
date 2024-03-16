// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.STREAM_DEBUGGER_UTILS_CLASS_NAME
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.*
import com.intellij.debugger.streams.trace.breakpoint.ex.IncorrectValueTypeException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInstantiationException
import com.intellij.debugger.streams.trace.breakpoint.lib.RuntimeIntermediateCallHandler
import com.intellij.debugger.streams.trace.breakpoint.lib.RuntimeTerminalCallHandler
import com.intellij.debugger.streams.trace.impl.handler.type.GenericType
import com.intellij.psi.CommonClassNames
import com.sun.jdi.*

open class PeekCallHandler(protected val valueManager: ValueManager,
                           protected val time: ObjectReference,
                           protected val typeBefore: GenericType?,
                           protected val typeAfter: GenericType?) : RuntimeIntermediateCallHandler, RuntimeTerminalCallHandler {
  private var beforeValuesMap: ObjectReference? = null
  private var afterValuesMap: ObjectReference? = null

  /**
   * Converts the result of the intermediate operation to the following format:
   * ```
   * var beforeArray = java.lang.Object[] { keys, values };
   * var afterArray = java.lang.Object[] { keys, values };
   * new java.lang.Object[] { beforeArray, afterArray };
   * ```
   */
  override fun result(evaluationContextImpl: EvaluationContextImpl): Value = valueManager.watch(evaluationContextImpl) {
    val beforeFormattedValue = if (typeBefore == null || beforeValuesMap == null) {
      emptyResult()
    }
    else {
      formatMap(beforeValuesMap, StreamTypeInfo.forType(typeBefore.genericTypeName))
    }

    val afterFormattedValue = if (typeAfter == null || afterValuesMap == null) {
      emptyResult()
    }
    else {
      formatMap(afterValuesMap, StreamTypeInfo.forType(typeAfter.genericTypeName))
    }
    array(
      beforeFormattedValue,
      afterFormattedValue
    )
  }

  override fun beforeCall(evaluationContextImpl: EvaluationContextImpl,
                          value: Value?): Value? = valueManager.watch(evaluationContextImpl) {
    if (typeBefore != null && value is ObjectReference) {
      beforeValuesMap = instance(CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP)
      val streamTypeInfo = StreamTypeInfo.forType(typeBefore.genericTypeName)
      createInterceptor(value, streamTypeInfo, beforeValuesMap!!, false)
    }
    else {
      value
    }
  }

  override fun afterCall(evaluationContextImpl: EvaluationContextImpl,
                         value: Value?): Value? = valueManager.watch(evaluationContextImpl) {
    if (typeAfter != null && value is ObjectReference) {
      afterValuesMap = instance(CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP)
      val streamTypeInfo = StreamTypeInfo.forType(typeAfter.genericTypeName)
      createInterceptor(value, streamTypeInfo, afterValuesMap!!, true)
    }
    else {
      value
    }
  }

  private fun ValueContext.createInterceptor(chainInstance: ObjectReference,
                                             streamTypeInfo: StreamTypeInfo,
                                             valuesMap: ObjectReference,
                                             tick: Boolean): ObjectReference {
    val collectorMirror = instance(UNIVERSAL_COLLECTOR_CLASS_NAME, UNIVERSAL_COLLECTOR_CONSTRUCTOR_SIGNATURE, listOf(valuesMap, time, tick.mirror))
    val peekArgs = listOf(collectorMirror)

    return chainInstance
      .method("peek", streamTypeInfo.peekSignature)
      .invoke(chainInstance, peekArgs) as ObjectReference
  }



  override fun transformArguments(evaluationContextImpl: EvaluationContextImpl, method: Method, arguments: List<Value?>): List<Value?> = arguments
}

enum class StreamTypeInfo(val type: String, val peekSignature: String, val formatterMethod: String) {
  ObjectStream(
    CommonClassNames.JAVA_UTIL_STREAM_STREAM,
    OBJECT_CONSUMER_SIGNATURE,
    "formatObjectMap"
  ),
  IntStream(
    CommonClassNames.JAVA_UTIL_STREAM_INT_STREAM,
    INT_CONSUMER_SIGNATURE,
    "formatIntMap"
  ),
  LongStream(
    CommonClassNames.JAVA_UTIL_STREAM_LONG_STREAM,
    LONG_CONSUMER_SIGNATURE,
    "formatLongMap"
  ),
  DoubleStream(
    CommonClassNames.JAVA_UTIL_STREAM_DOUBLE_STREAM,
    DOUBLE_CONSUMER_SIGNATURE,
    "formatDoubleMap"
  );

  companion object {
    fun forType(type: String?): StreamTypeInfo = when (type) {
      CommonClassNames.JAVA_LANG_OBJECT -> ObjectStream
      CommonClassNames.JAVA_LANG_INTEGER -> IntStream
      CommonClassNames.JAVA_LANG_LONG -> LongStream
      CommonClassNames.JAVA_LANG_DOUBLE -> DoubleStream
      else -> throw ValueInstantiationException("Could not get collector for stream of type $type")
    }
  }
}
