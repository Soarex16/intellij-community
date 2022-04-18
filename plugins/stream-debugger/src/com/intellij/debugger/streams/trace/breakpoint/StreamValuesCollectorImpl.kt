// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInstantiationException
import com.intellij.psi.CommonClassNames
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value

val COLLECTOR_CLASS_NAME = "com.intellij.debugger.streams.generated.java.peek.MapCollector"
val ATOMIC_INTEGER_CLASS_NAME = "java.util.concurrent.atomic.AtomicInteger"

class StreamValuesCollectorImpl(private val container: ValueContainer) : StreamValuesCollector {
  private val counterObject = container.createInstance(ATOMIC_INTEGER_CLASS_NAME)
                              ?: throw ValueInstantiationException(ATOMIC_INTEGER_CLASS_NAME)

  private val valueStorages: MutableList<ObjectReference> = mutableListOf()

  override val collectedValues: List<ObjectReference>
    get() = valueStorages

  override var streamResult: Value? = null
    private set

  override fun collectStreamResult(result: Value) {
    streamResult = result
  }

  override fun getValueCollector(collectorType: String): ObjectReference {
    // TODO: в зависимости от сигнатуры метода создавать нужные лямбдочки
    //  Например, для IntStream нужен не просто Consumer<Integer>, а IntConsumer
    //  Или можно использовать java.util.function.IntConsumer.adapt
    val mapInstance = container.createInstance(CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP)
                      ?: throw ValueInstantiationException(CommonClassNames.JAVA_UTIL_LINKED_HASH_MAP)

    valueStorages.add(mapInstance)
    val collectorInstance = container
                              .createInstance(COLLECTOR_CLASS_NAME, "(Ljava/util/Map;Ljava/util/concurrent/atomic/AtomicInteger;)V",
                                              listOf(mapInstance, counterObject))
                            ?: throw ValueInstantiationException(COLLECTOR_CLASS_NAME)

    return collectorInstance
  }
}