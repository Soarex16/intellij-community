// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.Patches
import com.intellij.debugger.engine.JVMNameUtil
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.resolve.add
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInstantiationException
import com.sun.jdi.*

const val EMPTY_CONSTRUCTOR_SIGNATURE = "()V"

typealias BytecodeFactory = () -> ByteArray?

class ValueManagerImpl(override val evaluationContext: EvaluationContextImpl) : ValueManager, ValueContext {
  private val instantiatedObjects: MutableMap<String, MutableList<ObjectReference>> = mutableMapOf()
  private val registeredBytecodeFactories: MutableMap<String, BytecodeFactory> = mutableMapOf()

  fun registerBytecodeFactory(className: String, factory: BytecodeFactory) {
    registeredBytecodeFactories[className] = factory
  }

  private fun tryLoadClassBytecode(className: String): ByteArray {
    val factory = registeredBytecodeFactories[className] ?: throw ValueInstantiationException(className)
    return factory() ?: throw ValueInstantiationException(className)
  }

  override fun <R> watch(init: ValueContext.() -> R): R = this.init()

  override fun dispose() {
    instantiatedObjects.forEach { (_, objects) ->
      objects.forEach {
        it.enableCollection()
      }
    }
  }

  override fun instance(className: String, constructorSignature: String, args: List<Value>): ObjectReference {
    val instance = when (val type = getType(className)) {
      // Array type don't have constructor method
      is ArrayType -> {
        val arrayLength = args.first() as IntegerValue
        type.newInstance(arrayLength.value())
      }
      is ClassType -> {
        val constructorMethod = type.methodsByName(JVMNameUtil.CONSTRUCTOR_NAME, constructorSignature).first()
                                ?: throw ValueInstantiationException(className)
        constructorMethod.prepareArguments(evaluationContext)
        val threadRef = evaluationContext.suspendContext.thread?.threadReference ?: throw ValueInstantiationException(className)
        type.newInstance(threadRef, constructorMethod, args, ClassType.INVOKE_SINGLE_THREADED)
      }
      else -> throw ValueInstantiationException(type.name())
    }

    keep(instance)
    return instance
  }

  override fun mirror(value: Int): IntegerValue = evaluationContext.debugProcess.virtualMachineProxy.mirrorOf(value)

  override fun mirror(value: Byte): ByteValue = evaluationContext.debugProcess.virtualMachineProxy.mirrorOf(value)

  override fun mirror(value: Char): CharValue = evaluationContext.debugProcess.virtualMachineProxy.mirrorOf(value)

  override fun mirror(value: Float): FloatValue = evaluationContext.debugProcess.virtualMachineProxy.mirrorOf(value)

  override fun mirror(value: Long): LongValue = evaluationContext.debugProcess.virtualMachineProxy.mirrorOf(value)

  override fun mirror(value: Short): ShortValue = evaluationContext.debugProcess.virtualMachineProxy.mirrorOf(value)

  override fun mirror(value: Double): DoubleValue = evaluationContext.debugProcess.virtualMachineProxy.mirrorOf(value)

  override fun mirror(value: Boolean): BooleanValue = evaluationContext.debugProcess.virtualMachineProxy.mirrorOf(value)

  override fun mirror(value: String): StringReference = evaluationContext.debugProcess.virtualMachineProxy.mirrorOf(value).also { keep(it) }

  override fun mirror(): VoidValue = evaluationContext.debugProcess.virtualMachineProxy.mirrorOfVoid()

  override fun getType(className: String): ReferenceType = evaluationContext.loadClassIfAbsent(className) { tryLoadClassBytecode(className) }
                                                           ?: throw ValueInstantiationException(className)

  override fun defineClass(className: String, bytesLoader: BytecodeFactory): ClassType {
    registerBytecodeFactory(className, bytesLoader)
    return getType(className) as ClassType
  }

  override fun array(componentType: String, size: Int): ArrayReference {
    val arrayClassName = "$componentType[]"
    val vm = evaluationContext.debugProcess.virtualMachineProxy.virtualMachine
    val arraySize = vm.mirrorOf(size)
    return instance(arrayClassName, "(I)V", listOf(arraySize)) as ArrayReference
  }

  override fun array(vararg values: Value): ArrayReference = array(values.toList())

  override fun array(values: List<Value>): ArrayReference {
    val array = array("java.lang.Object", values.size)
    array.values = values
    return array
  }

  override fun ObjectReference.method(name: String, signature: String): Method = referenceType().method(name, signature)

  override fun ReferenceType.method(name: String, signature: String): Method = methodsByName(name, signature)
     .firstOrNull().also { it?.prepareArguments(evaluationContext) } ?: throw MethodNotFoundException(name, signature, this.name())

  override fun invoke(cls: ClassType, method: Method, arguments: List<Value>): Value = evaluationContext.debugProcess
    .invokeMethod(evaluationContext, cls, method, arguments, 0, true)

  override fun invoke(obj: ObjectReference, method: Method, arguments: List<Value>): Value = evaluationContext.debugProcess
    .invokeInstanceMethod(evaluationContext, obj, method, arguments, 0, true)

  override fun keep(value: Value) {
    if (value is ObjectReference) {
      val className = value.referenceType().name()
      if (!Patches.IBM_JDK_DISABLE_COLLECTION_BUG) {
        value.disableCollection()
        instantiatedObjects.add(className, value)
      }
    }
  }
}

fun ValueManager.instance(className: String): ObjectReference = watch { instance(className) }