// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.IntermediateOperation
import com.intellij.debugger.streams.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.trace.CallTraceInterpreter
import com.intellij.debugger.streams.trace.IntermediateCallHandler
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.lib.RuntimeIntermediateCallHandler
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.openapi.util.NlsSafe
import com.sun.jdi.ObjectReference

typealias RuntimeIntermediateCallHandlerFactory = (number: Int, call: IntermediateStreamCall, valueManager: ValueManager, time: ObjectReference) -> RuntimeIntermediateCallHandler

/**
 * @author Vitaliy.Bibaev
 */
abstract class IntermediateOperationBase(override val name: @NlsSafe String,
                                         private val handlerFactory: (Int, IntermediateStreamCall, Dsl) -> IntermediateCallHandler,
                                         override val traceInterpreter: CallTraceInterpreter,
                                         override val valuesOrderResolver: ValuesOrderResolver,
                                         private val runtimeHandlerFactory: RuntimeIntermediateCallHandlerFactory? = null) : IntermediateOperation {
  override fun getTraceHandler(callOrder: Int, call: IntermediateStreamCall, dsl: Dsl): IntermediateCallHandler =
    handlerFactory.invoke(callOrder, call, dsl)

  override fun getRuntimeTraceHandler(number: Int,
                                      call: IntermediateStreamCall,
                                      valueManager: ValueManager,
                                      time: ObjectReference): RuntimeIntermediateCallHandler? {
    return runtimeHandlerFactory?.invoke(number, call, valueManager, time)
  }
}