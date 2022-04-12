// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.Patches
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.resolve.add
import com.sun.jdi.*

const val EMPTY_CONSTRUCTOR_SIGNATURE = "()V"

typealias BytecodeFactory = () -> ByteArray?

class ValueContainerImpl(private val myEvalContext: EvaluationContextImpl) : ValueContainer {
  private val myInstantiatedObjects: MutableMap<String, MutableList<ObjectReference>> = mutableMapOf()
  private val myRegisteredBytecodeFactories: MutableMap<String, BytecodeFactory> = mutableMapOf()

  fun registerBytecodeFactory(className: String, factory: BytecodeFactory) {
    myRegisteredBytecodeFactories[className] = factory
  }

  @Throws(InvalidTypeException::class, ClassNotLoadedException::class, IncompatibleThreadStateException::class, InvocationException::class)
  override fun createInstance(className: String, constructorSignature: String, args: List<Value>): ObjectReference? {
    val classType = myEvalContext
                      .loadClassIfAbsent(className) { tryLoadClassBytecode(className) } as? ClassType ?: return null
    val constructorMethod = classType.methodsByName(JVMNameUtil.CONSTRUCTOR_NAME, constructorSignature).first() ?: return null
    constructorMethod.argumentTypeNames().forEach { myEvalContext.loadClass(it) }

    val threadRef = myEvalContext.suspendContext.thread?.threadReference ?: return null
    val instance = classType.newInstance(threadRef, constructorMethod, args, ClassType.INVOKE_SINGLE_THREADED)
    keepValue(className, instance)

    return instance
  }

  private fun tryLoadClassBytecode(className: String): ByteArray? {
    val factory = myRegisteredBytecodeFactories[className] ?: return null
    return factory()
  }

  private fun keepValue(className: String, value: Value) {
    if (value is ObjectReference) {
      if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
        value.disableCollection()
        myInstantiatedObjects.add(className, value)
      }
    }
  }

  override fun dispose() {
    myInstantiatedObjects.forEach { (_, objects) ->
      objects.forEach {
        it.enableCollection()
      }
    }
  }
}

fun ValueContainer.createInstance(className: String) = createInstance(className, EMPTY_CONSTRUCTOR_SIGNATURE, emptyList())