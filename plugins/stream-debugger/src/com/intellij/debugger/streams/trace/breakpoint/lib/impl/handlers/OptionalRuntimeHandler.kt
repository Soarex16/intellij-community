// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.*
import com.intellij.debugger.streams.trace.impl.interpret.ex.UnexpectedValueTypeException
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.sun.jdi.ObjectReference
import com.sun.jdi.ReferenceType
import com.sun.jdi.Value

class OptionalRuntimeHandler(call: TerminatorStreamCall,
                             private val valueManager: ValueManager,
                             time: ObjectReference) : PeekTerminalCallHandler(valueManager, time, call.typeBefore,
                                                                              call.resultType) {

  /**
   * ```java
   * info[i] = new java.lang.Object[] {
   *     new java.lang.Object[] { beforeArray, afterArray },
   *     new java.lang.Object[] {
   *         new boolean[] { evaluationResult[0].isPresent() },
   *         new int[] { evaluationResult[0].orElse(0) }
   *     }
   * };
   *
   * myRes = new java.lang.Object[] { info, streamResult /* Optional */, elapsedTime };
   * ```
   */
  override fun result(evaluationContextImpl: EvaluationContextImpl): Value = valueManager.watch(evaluationContextImpl) {
    val (beforeAfter, wrappedResult) = rawResult(evaluationContext)
    val streamResult = wrappedResult.getValue(0) as ObjectReference
    assertIsOptional(streamResult)

    val isPresent = streamResult
      .method("isPresent", "()Z")
      .invoke(streamResult, emptyList())

    val unwrappedOptional = unwrapOptionalOrDefault(streamResult)
    val info = array(
      beforeAfter,
      array(
        array(isPresent),
        array(unwrappedOptional)
      )
    )

    array(
      info,
      wrappedResult
    )
  }

  private fun ValueContext.unwrapOptionalOrDefault(streamResult: ObjectReference): Value? {
    val optionalType = streamResult.referenceType()
    val orElseMethod = streamResult.method("orElse", orElseSignature(optionalType.name()))
    val orElseArg = orElseMethod.argumentTypes().first().defaultValue()
    return orElseMethod.invoke(streamResult, listOf(orElseArg))
  }

  private fun orElseSignature(optionalTypeName: String) = when (optionalTypeName) {
    JAVA_UTIL_OPTIONAL -> "(Ljava/lang/Object;)Ljava/lang/Object;"
    JAVA_UTIL_OPTIONAL_INT -> "(I)I"
    JAVA_UTIL_OPTIONAL_LONG -> "(J)J"
    JAVA_UTIL_OPTIONAL_DOUBLE -> "(D)D"
    else -> throw UnexpectedValueTypeException("Expected Optional but got $optionalTypeName")
  }

  private fun isOptional(optionalType: ReferenceType) = when (optionalType.name()) {
    JAVA_UTIL_OPTIONAL, JAVA_UTIL_OPTIONAL_INT, JAVA_UTIL_OPTIONAL_LONG, JAVA_UTIL_OPTIONAL_DOUBLE -> true
    else -> false
  }

  private fun assertIsOptional(value: Value?) {
    if (value is ObjectReference && isOptional(value.referenceType())) return
    throw UnexpectedValueTypeException("Optional expected. But ${value?.type()?.name()} received")
  }
}