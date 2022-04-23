// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.SuspendContext
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.findVmMethod
import com.intellij.debugger.streams.trace.breakpoint.collector.StreamValuesCollectorFactory
import com.intellij.debugger.streams.trace.breakpoint.ex.ArgumentTypeMismatchException
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInterceptionException
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.CommonClassNames.JAVA_UTIL_FUNCTION_CONSUMER
import com.sun.jdi.*
import com.sun.jdi.request.MethodExitRequest
import kotlin.reflect.KClass

private val LOG = logger<JavaMethodBreakpointFactory>()

/**
 * @author Shumaf Lovpache
 */
class JavaMethodBreakpointFactory(private val evaluationContext: EvaluationContextImpl,
                                  private val collectorFactory: StreamValuesCollectorFactory,
                                  private val executionCallback: StreamExecutionCallback,
                                  private val streamChain: StreamChain) : MethodBreakpointFactory {

  private var originalStreamQualifierValue: ObjectReference? = null

  override fun replaceQualifierExpressionValue() {
    val collector = collectorFactory.getValueCollector(JAVA_UTIL_FUNCTION_CONSUMER)

    // TODO: !! is bad
    val frameProxy = evaluationContext.suspendContext.frameProxy!!
    val qualifierVariable = frameProxy.visibleVariableByName(streamChain.qualifierExpression.text)
    val qualifierValue = frameProxy.getValue(qualifierVariable) as ObjectReference
    frameProxy.setValue(qualifierVariable, wrapWithPeek(qualifierValue, collector))

    if (originalStreamQualifierValue != null)
      throw ValueInterceptionException("Qualifier expression value has already been replaced")

    originalStreamQualifierValue = qualifierValue
  }

  override fun restoreQualifierExpressionValueIfNeeded() {
    if (originalStreamQualifierValue == null)
      return

    // TODO: !! is bad
    val frameProxy = evaluationContext.suspendContext.frameProxy!!
    val qualifierVariable = frameProxy.visibleVariableByName(streamChain.qualifierExpression.text)
    frameProxy.setValue(qualifierVariable, originalStreamQualifierValue)
  }

  override fun createProducerStepBreakpoint(signature: MethodSignature): MethodExitRequest =
    createIntermediateStepBreakpoint(signature)

  override fun createIntermediateStepBreakpoint(signature: MethodSignature): MethodExitRequest {
    val collector = collectorFactory.getValueCollector(JAVA_UTIL_FUNCTION_CONSUMER)

    return createMethodExitBreakpointRequest(signature) { _, _, value ->
      if (value !is ObjectReference) createTypeMismatchException(value, ObjectReference::class)

      return@createMethodExitBreakpointRequest wrapWithPeek(value, collector)
    }
  }

  override fun createTerminationOperationBreakpoint(signature: MethodSignature): MethodExitRequest {
    return createMethodExitBreakpointRequest(signature) { _, _, value ->
      // TODO: не учтен случай, когда результат стрима void
      collectorFactory.collectStreamResult(value)
      executionCallback.evaluated(collectorFactory.collectedValues, evaluationContext)
      return@createMethodExitBreakpointRequest null
    }
  }

  private fun checkStreamMethodArguments(vmMethod: Method, actualArgs: List<Value>): Boolean =
    vmMethod.argumentTypes().size == actualArgs.size && vmMethod.argumentTypes()
      .zip(actualArgs).all { (expectedArgType, actualArg) -> DebuggerUtils.instanceOf(actualArg.type(), expectedArgType.name()) }

  private fun wrapWithPeek(value: ObjectReference, collector: Value): Value {
    val peekArgs = listOf(collector)

    val peekSignature = "(Ljava/util/function/Consumer;)Ljava/util/stream/Stream;"
    val valueType = value.referenceType() as ClassType
    val peekMethod = DebuggerUtils.findMethod(valueType, "peek", peekSignature)
                     ?: throw MethodNotFoundException("peek", peekSignature, valueType.name())

    if (!checkStreamMethodArguments(peekMethod, peekArgs)) throw ArgumentTypeMismatchException(peekMethod, peekArgs)

    return evaluationContext.debugProcess
      .invokeInstanceMethod(evaluationContext, value, peekMethod, peekArgs, 0, true)
  }

  /**
   * Returns null when method with specified [signature] cannot be found in target VM
   */
  private fun createMethodExitBreakpointRequest(signature: MethodSignature,
                                                transformer: (SuspendContext, Method, Value) -> Value?): MethodExitRequest {
    val vmMethod = findVmMethod(evaluationContext, signature) ?: throw MethodNotFoundException(signature)
    val requestor = MethodExitRequestor(evaluationContext.project, vmMethod) { requestor, suspendContext, event ->
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
        transformer(suspendContext, event.method(), originalReturnValue)
      }
      catch (e: Throwable) {
        LOG.info("Error occurred during ${signature} method return value modification", e)
        null
      } ?: return@MethodExitRequestor

      try {
        threadProxy.forceEarlyReturn(replacedReturnValue)
      }
      catch (e: ClassNotLoadedException) {
        executionCallback.breakpointSetupFailed(e)
        LOG.info("Class for type ${replacedReturnValue.type().name()} has not been loaded yet", e)
      }
      catch (e: IncompatibleThreadStateException) {
        executionCallback.breakpointSetupFailed(e)
        LOG.info("Current thread is not suspended", e)
      }
      catch (e: InvalidTypeException) {
        executionCallback.breakpointSetupFailed(e)
        LOG.info("Could not cast value of type ${replacedReturnValue.type().name()} to ${originalReturnValue.type().name()}", e)
      }
    }

    val request = evaluationContext.debugProcess.requestsManager.createMethodExitRequest(requestor)
    request.addClassFilter(vmMethod.declaringType())

    return request
  }

  private fun createTypeMismatchException(value: Value, expectedType: KClass<ObjectReference>): Nothing = throw ValueInterceptionException(
    "Cannot modify value because it is of type ${value.type().name()} " +
    "(expected value of type ${expectedType.simpleName}) "
  )
}