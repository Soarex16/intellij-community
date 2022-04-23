// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.collector

import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInstantiationException
import com.intellij.debugger.streams.trace.breakpoint.instance
import com.intellij.psi.CommonClassNames
import com.sun.jdi.ArrayReference
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

const val ATOMIC_INTEGER_CLASS_NAME = "java.util.concurrent.atomic.AtomicInteger"

private const val JAVA_UTIL_FUNCTION_CONSUMER = "java.util.function.Consumer"
private const val JAVA_UTIL_FUNCTION_INT_CONSUMER = "java.util.function.IntConsumer"
private const val JAVA_UTIL_FUNCTION_LONG_CONSUMER = "java.util.function.LongConsumer"
private const val JAVA_UTIL_FUNCTION_DOUBLE_CONSUMER = "java.util.function.DoubleConsumer"

const val OBJECT_COLLECTOR_CLASS_NAME = "com.intellij.debugger.streams.generated.java.collector.ObjectCollector"
const val INT_COLLECTOR_CLASS_NAME = "com.intellij.debugger.streams.generated.java.collector.IntCollector"
const val LONG_COLLECTOR_CLASS_NAME = "com.intellij.debugger.streams.generated.java.collector.LongCollector"
const val DOUBLE_COLLECTOR_CLASS_NAME = "com.intellij.debugger.streams.generated.java.collector.DoubleCollector"

const val OBJECT_COLLECTOR_SOURCE = "/classes/ObjectCollector.java.txt"
const val INT_COLLECTOR_SOURCE = "/classes/IntCollector.java.txt"
const val LONG_COLLECTOR_SOURCE = "/classes/LongCollector.java.txt"
const val DOUBLE_COLLECTOR_SOURCE = "/classes/DoubleCollector.java.txt"

const val COLLECTOR_SIGNATURE = "(Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;)V"

class StreamValuesCollectorFactoryImpl(private val valueManager: ValueManager) : StreamValuesCollectorFactory {
  private val counterObject = valueManager.instance(ATOMIC_INTEGER_CLASS_NAME)

  private val valueStorages: MutableList<ObjectReference> = mutableListOf()
  private var streamResult: Value? = null
  private val elapsedTime: ArrayReference = valueManager.watch {
    array("long", 1)
      .apply { setValue(0, mirror(0L)) }
  }

  override val collectedValues: StreamTraceValues
    get() = if (streamResult == null)
      throw BreakpointTracingException("Stream result was not collected")
    else StreamTraceValues(
      valueStorages,
      streamResult!!,
      counterObject,
      elapsedTime
    )

  override fun getValueCollector(collectorType: String): ObjectReference = valueManager.watch {
    val mapInstance = instance(CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP)
    valueStorages.add(mapInstance)

    val collectorClassName = getCollectorClass(collectorType)
    instance(collectorClassName, COLLECTOR_SIGNATURE, listOf(mapInstance, counterObject))
  }

  override fun collectStreamResult(result: Value) {
    streamResult = result
  }

  private fun getCollectorClass(requestedType: String) = when (requestedType) {
    JAVA_UTIL_FUNCTION_CONSUMER -> OBJECT_COLLECTOR_CLASS_NAME
    JAVA_UTIL_FUNCTION_INT_CONSUMER -> INT_COLLECTOR_CLASS_NAME
    JAVA_UTIL_FUNCTION_LONG_CONSUMER -> LONG_COLLECTOR_CLASS_NAME
    JAVA_UTIL_FUNCTION_DOUBLE_CONSUMER -> DOUBLE_COLLECTOR_CLASS_NAME
    else -> throw ValueInstantiationException(requestedType)
  }
}