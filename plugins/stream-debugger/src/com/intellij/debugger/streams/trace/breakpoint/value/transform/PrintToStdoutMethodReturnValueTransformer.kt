// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.value.transform

import com.intellij.debugger.streams.wrapper.StreamCall
import com.sun.jdi.*

/**
 * @author Shumaf Lovpache
 */
class PrintToStdoutMethodReturnValueTransformer : MethodReturnValueTransformer {
  override fun transform(chainStep: StreamCall, thread: ThreadReference, vmValue: Value): Value? {
    val (outObject, printlnMethod) = getPrintln(vmValue.virtualMachine()) ?: return null
    val vm = vmValue.virtualMachine()
    outObject.invokeMethod(thread, printlnMethod, listOf(vm.mirrorOf("------sniffing value of method ${chainStep.name}------")), ObjectReference.INVOKE_SINGLE_THREADED)
    outObject.invokeMethod(thread, printlnMethod, listOf(vmValue), ObjectReference.INVOKE_SINGLE_THREADED)
    outObject.invokeMethod(thread, printlnMethod, listOf(vm.mirrorOf("------000000000000000000000000------")), ObjectReference.INVOKE_SINGLE_THREADED)
    return null

    // а когда надо будет делать всякие peek-и, то тоже получается будем ручками
    // на переданном value вызывать peek, и его результат уже возвращать
  }

  private fun getPrintln(vm: VirtualMachine): Pair<ObjectReference, Method>? {
    val systemClass = vm.classesByName("java.lang.System").firstOrNull() ?: return null

    val outStreamField = systemClass.fieldByName("out")
    val outStreamObj = systemClass.getValue(outStreamField) as? ObjectReference ?: return null

    val printlnMethod = outStreamObj.referenceType().methodsByName("println", "(Ljava/lang/Object;)V").firstOrNull() ?: return null

    return Pair(outStreamObj, printlnMethod)
  }
}