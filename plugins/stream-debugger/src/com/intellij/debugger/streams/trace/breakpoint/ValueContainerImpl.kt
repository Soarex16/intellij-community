// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.Patches
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.resolve.add
import com.sun.jdi.*

const val EMPTY_CONSTRUCTOR_SIGNATURE = "()V"

typealias BytecodeFactory = () -> ByteArray?

class ValueContainerImpl(private val evalContext: EvaluationContextImpl) : ValueContainer {
  private val instantiatedObjects: MutableMap<String, MutableList<ObjectReference>> = mutableMapOf()
  private val registeredBytecodeFactories: MutableMap<String, BytecodeFactory> = mutableMapOf()

  fun registerBytecodeFactory(className: String, factory: BytecodeFactory) {
    registeredBytecodeFactories[className] = factory
  }

  override fun createInstance(className: String, constructorSignature: String, args: List<Value>): ObjectReference? {
    val classType = evalContext
                      .loadClassIfAbsent(className) { tryLoadClassBytecode(className) } as? ClassType ?: return null
    val constructorMethod = classType.methodsByName(JVMNameUtil.CONSTRUCTOR_NAME, constructorSignature).first() ?: return null
    constructorMethod.argumentTypeNames().forEach { evalContext.loadClass(it) }

    val threadRef = evalContext.suspendContext.thread?.threadReference ?: return null
    val instance = classType.newInstance(threadRef, constructorMethod, args, ClassType.INVOKE_SINGLE_THREADED)
    keepValue(className, instance)

    return instance
  }

  private fun tryLoadClassBytecode(className: String): ByteArray? {
    val factory = registeredBytecodeFactories[className] ?: return null
    return factory()
  }

  private fun keepValue(className: String, value: Value) {
    if (value is ObjectReference) {
      if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
        value.disableCollection()
        instantiatedObjects.add(className, value)
      }
    }
  }

  override fun dispose() {
    instantiatedObjects.forEach { (_, objects) ->
      objects.forEach {
        it.enableCollection()
      }
    }
  }
}

fun ValueContainer.createInstance(className: String) = createInstance(className, EMPTY_CONSTRUCTOR_SIGNATURE, emptyList())