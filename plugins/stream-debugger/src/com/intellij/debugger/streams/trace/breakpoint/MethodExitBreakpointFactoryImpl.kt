// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.findVmMethod
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.openapi.diagnostic.logger
import com.sun.jdi.*
import com.sun.jdi.request.MethodExitRequest

private val LOG = logger<MethodExitBreakpointFactoryImpl>()

/**
 * Creates breakpoints for specified methods
 * @author Shumaf Lovpache
 */
class MethodExitBreakpointFactoryImpl(private val myEvaluationContext: EvaluationContextImpl,
                                      private val myValuesCollector: StreamValuesCollector,
                                      private val callback: StreamExecutionCallback) : MethodExitBreakpointFactory {

  @Throws(MethodNotFoundException::class)
  override fun createIntermediateStepBreakpoint(signature: MethodSignature): MethodExitRequest {
    val storage = myValuesCollector.getValueCollector()

    return createBreakpointRequest(signature) { _, value ->
      if (value !is ObjectReference) return@createBreakpointRequest null // TODO: log unsuccessful intercepting

      val peekSignature = "(Ljava/util/function/Consumer;)Ljava/util/stream/Stream;"
      val valueType = value.referenceType() as ClassType
      val peekMethod = DebuggerUtils.findMethod(valueType, "peek", peekSignature)
                       ?: throw MethodNotFoundException("peek", peekSignature, valueType.name())

      return@createBreakpointRequest myEvaluationContext.debugProcess
        .invokeInstanceMethod(myEvaluationContext, value, peekMethod, listOf(storage), 0, true)
    }
  }

  @Throws(MethodNotFoundException::class)
  override fun createTerminationOperationBreakpoint(signature: MethodSignature): MethodExitRequest {
    return createBreakpointRequest(signature) { suspendContext, value ->
      myValuesCollector.collectStreamResult(value)
      callback.evaluated(myValuesCollector, myEvaluationContext)
      return@createBreakpointRequest null
    }
  }

  /**
   * Returns null when method with specified [signature] cannot be found in target VM
   */
  @Throws(MethodNotFoundException::class)
  private fun createBreakpointRequest(signature: MethodSignature, transformer: (SuspendContext, Value) -> Value?): MethodExitRequest {
    val vmMethod = findVmMethod(myEvaluationContext, signature) ?: throw MethodNotFoundException(signature)
    val requestor = MethodExitRequestor(myEvaluationContext.project, vmMethod) { requestor, suspendContext, event ->
      suspendContext.debugProcess.requestsManager.deleteRequest(requestor)

      val threadProxy = suspendContext.thread ?: return@MethodExitRequestor

      val originalReturnValue = try {
        event.returnValue()
      }
      catch (e: UnsupportedOperationException) {
        val vm = event.virtualMachine()
        LOG.info("Return value interception is not supported in ${vm.name()} ${vm.version()}", e)
        return@MethodExitRequestor
      }

      val replacedReturnValue = try {
        transformer(suspendContext, originalReturnValue)
      }
      catch (e: Throwable) {
        LOG.info("Error occurred during ${signature} method return value modification", e)
        null
      } ?: return@MethodExitRequestor

      try {
        threadProxy.forceEarlyReturn(replacedReturnValue)
      }
      catch (e: ClassNotLoadedException) {
        callback.breakpointSetupFailed(e)
        LOG.info("Class for type ${replacedReturnValue.type().name()} has not been loaded yet", e)
      }
      catch (e: IncompatibleThreadStateException) {
        callback.breakpointSetupFailed(e)
        LOG.info("Current thread is not suspended", e)
      }
      catch (e: InvalidTypeException) {
        callback.breakpointSetupFailed(e)
        LOG.info("Could not cast value of type ${replacedReturnValue.type().name()} to ${originalReturnValue.type().name()}", e)
      }
    }

    val request = myEvaluationContext.debugProcess.requestsManager.createMethodExitRequest(requestor)
    request.addClassFilter(vmMethod.declaringType())

    return request
  }
}