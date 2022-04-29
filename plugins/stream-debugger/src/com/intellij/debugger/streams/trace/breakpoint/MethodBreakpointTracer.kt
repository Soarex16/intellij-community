// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.streams.StreamDebuggerBundle
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.TraceResultInterpreter
import com.intellij.debugger.streams.trace.TracingCallback
import com.intellij.debugger.streams.trace.breakpoint.HelperClassUtils.getCompiledClass
import com.intellij.debugger.streams.trace.breakpoint.collector.*
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointPlaceNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointTracingException
import com.intellij.debugger.streams.trace.breakpoint.formatter.StreamTraceFormatter
import com.intellij.debugger.streams.trace.breakpoint.formatter.StreamTraceFormatterImpl
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.XDebugSession

private val LOG = logger<MethodBreakpointTracer>()

/**
 * @author Shumaf Lovpache
 */
class MethodBreakpointTracer(private val session: XDebugSession,
                             private val breakpointResolver: BreakpointResolver,
                             private val resultInterpreter: TraceResultInterpreter) : StreamTracer {
  override fun trace(chain: StreamChain, callback: TracingCallback) {
    val xDebugProcess = session.debugProcess as? JavaDebugProcess ?: return
    val runTraceCommand = object : DebuggerCommandImpl(PrioritizedTask.Priority.NORMAL) {
      override fun action() {
        trace(xDebugProcess, chain, callback)
      }
    }
    val debuggerManagerThread = xDebugProcess.debuggerSession.process.managerThread
    debuggerManagerThread.schedule(runTraceCommand)
  }

  private fun trace(debugProcess: JavaDebugProcess, chain: StreamChain, callback: TracingCallback) {
    val context = createEvaluationContext(debugProcess)
    val valueManager: ValueManager = createValueManager(context)
    val valuesCollector: StreamValuesCollectorFactory = StreamValuesCollectorFactoryImpl(valueManager)
    val traceFormatter: StreamTraceFormatter = StreamTraceFormatterImpl(valueManager)

    val executionCallback = TracingCallbackWrapper(chain, callback, resultInterpreter, traceFormatter)
    val locations = try {
      breakpointResolver.findBreakpointPlaces(chain)
    }
    catch (e: BreakpointPlaceNotFoundException) {
      executionCallback.breakpointSetupFailed(e)
      return
    }

    // TODO: Abstract by language here?
    val breakpointFactory: MethodBreakpointFactory = JavaMethodBreakpointFactory(context, valuesCollector, executionCallback, chain)

    try {
      setStreamBreakpoints(breakpointFactory, locations)

      // TODO: после трассировки проверять, что брейкпоинты,
      //  которые мы расставили были подчищены. Также полезно
      //  рассмотреть как поведет себя control flow в случае
      //  завершения дебага, не будет ли течь память

      // session.stepOver(false) // TODO: тут надо что-то более понятное юзеру придумать, а не сразу шагать

      // TODO: посмотреть куда будут вываливаться исключения, созданные в коллбеках брейкпоинтов

      // TODO: execute
      return
    }
    catch (e: BreakpointTracingException) {
      executionCallback.tracingSetupFailed(e)
      valueManager.dispose()
      LOG.error(e)
    }
  }

  private fun setStreamBreakpoints(breakpointFactory: MethodBreakpointFactory, locations: StreamChainBreakpointPlaces) {
    val qualifierExpressionBreakpoint = if (locations.qualifierExpressionMethod == null) {
      // if qualifier expression is variable we need to replace it in current stack frame
      val qualifierExpressionValue = breakpointFactory.replaceQualifierExpressionValue()
      null // TODO: восстанавливать значение qualifierExpression после вычисления стрима
    }
    else {
      // set additional breakpoint as for an intermediate operation
      breakpointFactory
        .createProducerStepBreakpoint(locations.qualifierExpressionMethod)
    }

    val intermediateStepsBreakpoints = locations.intermediateStepsMethods.map {
      breakpointFactory.createIntermediateStepBreakpoint(it)
    }
    val terminationOperationBreakpoint = breakpointFactory
      .createTerminationOperationBreakpoint(locations.terminationOperationMethod)

    qualifierExpressionBreakpoint?.enable()
    intermediateStepsBreakpoints.forEach { it.enable() }
    terminationOperationBreakpoint.enable()
  }

  private fun createValueManager(context: EvaluationContextImpl): ValueManager {
    val container = ValueManagerImpl(context)
    container.registerBytecodeFactory(OBJECT_COLLECTOR_CLASS_NAME) {
      getCompiledClass(OBJECT_COLLECTOR_CLASS_FILE)
    }
    container.registerBytecodeFactory(INT_COLLECTOR_CLASS_NAME) {
      getCompiledClass(INT_COLLECTOR_CLASS_FILE)
    }
    container.registerBytecodeFactory(LONG_COLLECTOR_CLASS_NAME) {
      getCompiledClass(LONG_COLLECTOR_CLASS_FILE)
    }
    container.registerBytecodeFactory(DOUBLE_COLLECTOR_CLASS_NAME) {
      getCompiledClass(DOUBLE_COLLECTOR_CLASS_FILE)
    }
    return container
  }

  private fun createEvaluationContext(debugProcess: JavaDebugProcess): EvaluationContextImpl {
    val process = debugProcess.debuggerSession.process
    val suspendContext = process.suspendManager.pausedContext
    val currentStackFrameProxy = suspendContext.frameProxy
    val ctx = EvaluationContextImpl(suspendContext, currentStackFrameProxy)
      .withAutoLoadClasses(true)
    // explicitly setting class loader because we don't want to modify user's class loader
    ctx.classLoader = ClassLoadingUtils.getClassLoader(ctx, process)
    return ctx
  }

  private class TracingCallbackWrapper(
    private val chain: StreamChain,
    private val tracingCallback: TracingCallback,
    private val resultInterpreter: TraceResultInterpreter,
    private val traceFormatter: StreamTraceFormatter
  ) : StreamExecutionCallback {
    override fun evaluated(collectedValues: StreamTraceValues, context: EvaluationContextImpl) {
      val formattedResult = traceFormatter.formatTraceResult(chain, collectedValues)
      val interpretedResult = try {
        resultInterpreter.interpret(chain, formattedResult)
      }
      catch (t: Throwable) {
        // TODO: change trace expression field
        runInEdt {
          tracingCallback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.cannot.interpret.result", t.message!!))
        }
        throw t
      }
      // TODO: clear value manager when window closed
      runInEdt { tracingCallback.evaluated(interpretedResult, context) }
    }

    override fun breakpointSetupFailed(e: Throwable) = runInEdt {
      tracingCallback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.cannot.find.places.for.breakpoints"))
      LOG.error(e)
    }

    override fun tracingSetupFailed(e: Throwable) = runInEdt {
      tracingCallback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.cannot.initialize.breakpoints"))
      LOG.error(e)
    }

    override fun streamExecutionFailed(e: Throwable) = runInEdt {
      tracingCallback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.exception.occurred.during.stream.execution"))
      LOG.error(e)
    }
  }
}

