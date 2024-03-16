// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.*
import com.intellij.debugger.streams.trace.breakpoint.lib.RuntimeTerminalCallHandler
import com.intellij.debugger.streams.trace.impl.interpret.ex.UnexpectedValueTypeException
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.intellij.psi.CommonClassNames
import com.sun.jdi.*

/*
  // anyMatch(x -> x < 0)
  .peek(x -> filterMatchPeek0Before.put(time.get(), x))
  .filter(predicate42)
  .peek(x -> filterMatchPeek0After.put(time.get(), x))
  .anyMatch(x -> true)

  // transforms to
  .anyMatch(IntMatcher(before, after, time, predicate))

  потом получаем stream result + info (но для info тут мы не тикали нигде)
*/

/**
 * Example of transformation.
 * Before:
 * ```java
 * .anyMatch(x -> x < 0)
 * ```
 * After:
 * ```java
 * .anyMatch(IntMatcher(before, after, time, x -> x < 0))
 * ```
 * All code related to value collection moved to IntMatcher.
 * So the transformation changed, but semantics preserved.
 *
 * For reference, transformed by evaluate expression tracer:
 * ```java
 * .peek(x -> filterMatchPeek0Before.put(time.get(), x))
 * .filter((x -> x < 0).negate())
 * .peek(x -> filterMatchPeek0After.put(time.get(), x))
 *
 * .allMatch(x -> false);
 * ```
 * */
class MatchRuntimeHandler(private val call: TerminatorStreamCall,
                          private val valueManager: ValueManager,
                          private val time: ObjectReference) : RuntimeTerminalCallHandler {
  private lateinit var beforeValuesMap: ObjectReference
  private lateinit var afterValuesMap: ObjectReference
  private var streamResult: Value? = null

  override fun result(evaluationContextImpl: EvaluationContextImpl): Value = valueManager.watch(evaluationContextImpl) {
    val streamTypeInfo = StreamTypeInfo.forType(call.typeBefore.genericTypeName)
    val beforeFormattedValue = formatMap(beforeValuesMap, streamTypeInfo)
    val afterFormattedValue = formatMap(afterValuesMap, streamTypeInfo)

    val info = array(beforeFormattedValue, afterFormattedValue)
    val result = array(streamResult)

    array(
      array( // interpreter for match operation also requires stream result
        info,
        result
      ),
      result
    )
  }

  override fun transformArguments(evaluationContextImpl: EvaluationContextImpl,
                                  method: Method,
                                  arguments: List<Value?>): List<Value?> = valueManager.watch(evaluationContextImpl) {
    val operatorArgumentTypes = method.argumentTypes()
    require(operatorArgumentTypes.size == 1) { "Match operator should only accept predicate" }

    val predicate = arguments.first()
    val predicateType = operatorArgumentTypes.first()
    val (matcherType, matcherConstructorSignature) = matcherForType(predicateType.name())

    beforeValuesMap = instance(CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP)
    afterValuesMap = instance(CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP)

    val newPredicate = instance(
      matcherType,
      matcherConstructorSignature,
      listOf(beforeValuesMap, afterValuesMap, time, predicate)
    )
    listOf(newPredicate)
  }

  override fun beforeCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? = value

  override fun afterCall(evaluationContextImpl: EvaluationContextImpl, value: Value?): Value? {
    streamResult = value
    return value
  }

  private fun matcherForType(typeName: String): Pair<String, String> = when(typeName) {
    JAVA_UTIL_FUNCTION_PREDICATE -> Pair(OBJECT_MATCHER_CLASS_NAME, OBJECT_MATCHER_CONSTRUCTOR_SIGNATURE)
    JAVA_UTIL_FUNCTION_INT_PREDICATE -> Pair(INT_MATCHER_CLASS_NAME, INT_MATCHER_CONSTRUCTOR_SIGNATURE)
    JAVA_UTIL_FUNCTION_LONG_PREDICATE -> Pair(LONG_MATCHER_CLASS_NAME, LONG_MATCHER_CONSTRUCTOR_SIGNATURE)
    JAVA_UTIL_FUNCTION_DOUBLE_PREDICATE -> Pair(DOUBLE_MATCHER_CLASS_NAME, DOUBLE_MATCHER_CONSTRUCTOR_SIGNATURE)
    else -> throw UnexpectedValueTypeException("Expected Predicate but got $typeName")
  }
}