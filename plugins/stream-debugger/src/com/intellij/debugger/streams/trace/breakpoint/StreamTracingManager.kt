// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.*
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInterceptionException
import com.intellij.debugger.streams.trace.breakpoint.lib.RuntimeHandlerFactory
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.QualifierExpression
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.intellij.openapi.application.runInEdt
import com.intellij.psi.CommonClassNames
import com.intellij.psi.CommonClassNames.JAVA_LANG_THROWABLE
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.sun.jdi.ArrayReference
import com.sun.jdi.Location
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import com.sun.jdi.request.*

typealias ChainEvaluationCallback = (EvaluationContextImpl, Value?) -> Unit

/**
 * @author Shumaf Lovpache
 * Performs async runtime chain modification using JDI.
 * Note that this is a stateful object, so it's scope is the current tracing stream chain.
 */
class StreamTracingManager(private val breakpointFactory: MethodBreakpointFactory,
                           private val breakpointResolver: BreakpointResolver,
                           private val evalContextFactory: EvaluationContextFactory,
                           private val handlerFactory: RuntimeHandlerFactory,
                           private val valueManager: ValueManager,
                           private val debugProcess: JavaDebugProcess,
                           private val chain: StreamChain,
                           private val onEvaluated: ChainEvaluationCallback) {
  private var originalStreamQualifierValue: ObjectReference? = null

  private lateinit var sessionListener: XDebugSessionListener
  private var exceptionGuard: ExceptionRequest? = null

  private var sourceOperationBreakpoint: MethodExitRequest? = null
  private var intermediateOperationsBreakpoints: List<StreamCallRuntimeInfo> = emptyList()
  private lateinit var terminalOperationBreakpoint: StreamCallRuntimeInfo

  // if exception occurred during stream execution here will be thrown exception
  private var exceptionInstance: ObjectReference? = null
  private lateinit var streamLocation: Location
  // it is safe to assume 0 as default value, because if for some reason we did not assign a value,
  // then when looking for a frame, we will simply reach the bottom of the stack
  private var streamSurroundingStackFrameDepth: Int = 0

  fun evaluateChain(evaluationContextImpl: EvaluationContextImpl) {
    val firstRequestor = createRequestors(evaluationContextImpl, chain, breakpointResolver.findBreakpointPlaces(chain))
    firstRequestor.enable()
    initExceptionGuard(evaluationContextImpl)

    val session = debugProcess.session
    sessionListener = createTraceFinishListener(session)
    session.addSessionListener(sessionListener)
    runInEdt { session.resume() }
  }

  private fun initExceptionGuard(evaluationContext: EvaluationContextImpl) {
    val frameProxy = evaluationContext.frameProxy!!
    streamLocation = frameProxy.location()
    streamSurroundingStackFrameDepth = frameProxy.indexFromBottom
    exceptionGuard = breakpointFactory.createExceptionBreakpoint(evaluationContext) { suspendContext, catchLocation, exception ->
      val ctx = suspendContext as SuspendContextImpl
      if (catchLocation == null || !isHandlerInsideStream(ctx, catchLocation)) {
        val context = evalContextFactory.createContext(suspendContext)
        valueManager.watch(context) {
          keep(exception)
          exceptionInstance = exception
        }
        true
      } else {
        false
      }
    }
    exceptionGuard!!.enable()
  }

  // This method should check if exception handler is outside the stream
  private fun isHandlerInsideStream(suspendContext: SuspendContextImpl, catchLocation: Location): Boolean {
    val streamSurroundingFrameIndex = suspendContext.frameCount() - streamSurroundingStackFrameDepth
    val thread = suspendContext.thread!!
    for (frameIndex in 0 until streamSurroundingFrameIndex) {
      val frame = thread.frame(frameIndex)
      // so far we do not handle recursion
      if (frame.location().method() == catchLocation.method()) {
        return true
      }
    }
    return false
  }

  private fun createTraceFinishListener(session: XDebugSession): XDebugSessionListener {
    val currentStackFrame = session.currentStackFrame as? JavaStackFrame
                            ?: throw BreakpointTracingException("Cannot determine current location")
    val outerMethod = currentStackFrame.descriptor.method
    return object : XDebugSessionListener {
      override fun sessionPaused() {
        super.sessionPaused()
        val ctx = debugProcess.session.suspendContext as SuspendContextImpl
        val frame = session.currentStackFrame
        val stackDepth = ctx.frameCount()
        if (stackDepth > streamSurroundingStackFrameDepth) {
          val debugProcess = ctx.debugProcess
          val stepCommand = debugProcess.createStepIntoCommand(ctx, true, null, StepRequest.STEP_MIN)
          debugProcess.session.setSteppingThrough(stepCommand.contextThread)
          debugProcess.managerThread.schedule(stepCommand)
        } else if (frame is JavaStackFrame && frame.descriptor.method == outerMethod) {
          val evaluationContext = evalContextFactory.createContext(ctx)
          disableBreakpointRequests()
          restoreQualifierExpressionValueIfNeeded(evaluationContext, chain)
          debugProcess.session.removeSessionListener(sessionListener)

          val result = getFormattedResult(evaluationContext)
          onEvaluated(evaluationContext, result)
        }
      }
    }
  }

  // returns first breakpoint request for stream chain
  private fun createRequestors(evaluationContext: EvaluationContextImpl,
                               chain: StreamChain,
                               locations: StreamChainBreakpointPlaces): EventRequest {
    val time = valueManager.watch(evaluationContext) {
      instance(ATOMIC_INTEGER_CLASS_NAME)
    }

    val qualifierExpressionBreakpoint = if (locations.qualifierExpressionMethod == null) {
      // if qualifier expression is variable we need to replace it in current stack frame
      replaceQualifierExpressionValue(evaluationContext, chain.qualifierExpression)
      null
    }
    else {
      // if it is a method call, then we set additional breakpoint as for an intermediate operation
      sourceOperationBreakpoint = createSourceOperationRequestor(evaluationContext, locations.qualifierExpressionMethod)
      sourceOperationBreakpoint
    }

    intermediateOperationsBreakpoints = chain
      .intermediateCalls.zip(locations.intermediateStepsMethods)
      .mapIndexed { callOrder, (call, methodSignature) ->
        createIntermediateOperationRequestors(evaluationContext, time, callOrder, call, methodSignature)
      }

    terminalOperationBreakpoint = createTerminalOperationRequestors(evaluationContext, time,
                                                                    chain.intermediateCalls.size,
                                                                    chain.terminationCall,
                                                                    locations.terminationOperationMethod)

    return qualifierExpressionBreakpoint
           ?: intermediateOperationsBreakpoints.firstOrNull()?.methodEntryRequest
           ?: terminalOperationBreakpoint.methodEntryRequest
  }

  private fun createSourceOperationRequestor(evaluationContext: EvaluationContextImpl,
                                             methodSignature: MethodSignature): MethodExitRequest {
    return breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { suspendContext, _, value ->
      enableNextBreakpoint(-1)
      transformIfObjectReference(value) {
        val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
        val handler = handlerFactory.getForSource()
        val nextHandler = getNextCallTransformer(-1)

        nextHandler.beforeCall(context, handler.afterCall(context, it))
      }
    }
  }

  private fun createIntermediateOperationRequestors(evaluationContext: EvaluationContextImpl, time: ObjectReference,
                                                    callOrder: Int, call: IntermediateStreamCall,
                                                    methodSignature: MethodSignature): StreamCallRuntimeInfo {
    val handler = handlerFactory.getForIntermediate(callOrder, call, time)
    // create exit request first to be able to activate it in entry request
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { suspendContext, _, value ->
      enableNextBreakpoint(callOrder)
      val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
      val nextTransformer = getNextCallTransformer(callOrder)
      nextTransformer.beforeCall(context, handler.afterCall(context, value))
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { suspendContext, _, args ->
      exitRequest.enable()
      val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
      handler.transformArguments(context, args)
    }
    return StreamCallRuntimeInfo(handler, entryRequest, exitRequest)
  }

  private fun createTerminalOperationRequestors(evaluationContext: EvaluationContextImpl, time: ObjectReference,
                                                callOrder: Int, call: TerminatorStreamCall,
                                                methodSignature: MethodSignature): StreamCallRuntimeInfo {
    val handler = handlerFactory.getForTermination(callOrder, call, time)
    val exitRequest = breakpointFactory.createMethodExitBreakpoint(evaluationContext, methodSignature) { suspendContext, _, value ->
      val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
      val stepOutRequest = createStepOutRequest(context.suspendContext)
      stepOutRequest.enable()
      handler.afterCall(context, value)
    }
    val entryRequest = breakpointFactory.createMethodEntryBreakpoint(evaluationContext, methodSignature) { suspendContext, _, args ->
      exitRequest.enable()
      val context = evalContextFactory.createContext(checkSuspendContext(suspendContext))
      handler.transformArguments(context, args)
    }
    return StreamCallRuntimeInfo(handler, entryRequest, exitRequest)
  }

  private fun transformIfObjectReference(value: Value?, transformer: (ObjectReference) -> Value?): Value? {
    return if (value != null && value is ObjectReference) {
      transformer(value)
    }
    else {
      value
    }
  }

  private fun disableBreakpointRequests() {
    if (sourceOperationBreakpoint?.isEnabled == true) {
      sourceOperationBreakpoint?.disable()
    }

    for (intermediateStepBreakpoint in intermediateOperationsBreakpoints) {
      if (intermediateStepBreakpoint.methodEntryRequest.isEnabled) {
        intermediateStepBreakpoint.methodEntryRequest.disable()
      }
      if (intermediateStepBreakpoint.methodExitRequest.isEnabled) {
        intermediateStepBreakpoint.methodExitRequest.disable()
      }
    }

    if (terminalOperationBreakpoint.methodEntryRequest.isEnabled) {
      terminalOperationBreakpoint.methodEntryRequest.disable()
    }
    if (terminalOperationBreakpoint.methodExitRequest.isEnabled) {
      terminalOperationBreakpoint.methodExitRequest.disable()
    }
  }

  private fun getFormattedResult(evaluationContext: EvaluationContextImpl): Value {
    return valueManager.watch(evaluationContext) {
      val infos = intermediateOperationsBreakpoints.map { it.handler.result(evaluationContext) }.toMutableList()
      val terminalOperationResult = terminalOperationBreakpoint.handler.result(evaluationContext) as ArrayReference
      infos.add(terminalOperationResult.getValue(0))

      val streamResult = if (exceptionInstance != null) {
        array(JAVA_LANG_THROWABLE, 1).apply {
          setValue(0, exceptionInstance)
        }
      }
      else {
        terminalOperationResult.getValue(1)
      }

      val infoArray = if (infos.isNotEmpty()) {
        array(infos)
      }
      else {
        array(CommonClassNames.JAVA_LANG_OBJECT, 0)
      }

      array(
        infoArray,
        streamResult,
        array(0L.mirror)
      )
    }
  }

  private fun createStepOutRequest(suspendContext: SuspendContextImpl): StepRequest {
    DebuggerManagerThreadImpl.assertIsManagerThread()
    val debugProcess = suspendContext.debugProcess
    val threadRef = suspendContext.thread!!.threadReference
    val req: StepRequest = debugProcess.requestsManager.vmRequestManager.createStepRequest(threadRef, StepRequest.STEP_LINE,
                                                                                           StepRequest.STEP_OUT)
    req.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD)
    return req
  }

  private fun getNextCallTransformer(callNumber: Int): BeforeCallTransformer {
    return if (callNumber + 1 >= intermediateOperationsBreakpoints.size) {
      terminalOperationBreakpoint.handler
    }
    else {
      intermediateOperationsBreakpoints[callNumber + 1].handler
    }
  }

  private fun enableNextBreakpoint(callNumber: Int) {
    if (callNumber + 1 >= intermediateOperationsBreakpoints.size) {
      terminalOperationBreakpoint.methodEntryRequest.enable()
    }
    else {
      intermediateOperationsBreakpoints[callNumber + 1].methodEntryRequest.enable()
    }
  }

  /**
   * NOTE: this approach will work only if the qualifier expression is a simple local variable reference
   * For ex. if qualifier expression is something like this:
   * ```
   * a > 2 ? Stream.of(1, 2, 3) : Stream.iterate(1, x -> x + 1)
   * ```
   * or this:
   * ```
   * obj.field
   * ```
   * we can't determine where we should place breakpoint
   */
  private fun replaceQualifierExpressionValue(evaluationContext: EvaluationContextImpl, qualifierExpression: QualifierExpression) {
    // not null assertion fails only if incorrect evaluationContext passed to method
    val frameProxy = evaluationContext.frameProxy!!
    val qualifierVariable = frameProxy.visibleVariableByName(qualifierExpression.text)
    val qualifierValue = frameProxy.getValue(qualifierVariable) as ObjectReference

    val handler = handlerFactory.getForSource()
    val transformedQualifierValue = handler.afterCall(evaluationContext, qualifierValue)
    frameProxy.setValue(qualifierVariable, transformedQualifierValue)

    if (originalStreamQualifierValue != null)
      throw ValueInterceptionException("Qualifier expression value has already been replaced")

    originalStreamQualifierValue = qualifierValue
  }

  private fun restoreQualifierExpressionValueIfNeeded(evaluationContext: EvaluationContextImpl, streamChain: StreamChain) {
    if (originalStreamQualifierValue == null)
      return

    // not null assertion fails only if incorrect evaluationContext passed to method
    val frameProxy = evaluationContext.frameProxy!!
    val qualifierVariable = frameProxy.visibleVariableByName(streamChain.qualifierExpression.text)
    frameProxy.setValue(qualifierVariable, originalStreamQualifierValue)
  }

  private fun checkSuspendContext(suspendContext: SuspendContext): SuspendContextImpl {
    if (suspendContext !is SuspendContextImpl)
      throw BreakpointTracingException("Cannot trace stream chain because suspend context has unsupported type")

    return suspendContext
  }
}

private data class StreamCallRuntimeInfo(val handler: StreamOperationHandler,
                                         val methodEntryRequest: MethodEntryRequest,
                                         val methodExitRequest: MethodExitRequest)
