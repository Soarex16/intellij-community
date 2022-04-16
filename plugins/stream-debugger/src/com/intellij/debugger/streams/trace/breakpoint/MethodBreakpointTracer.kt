// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.impl.ClassLoadingUtils
import com.intellij.debugger.streams.StreamDebuggerBundle
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.TraceResultInterpreter
import com.intellij.debugger.streams.trace.TracingCallback
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.runInDebuggerThread
import com.intellij.debugger.streams.trace.breakpoint.ex.ArgumentTypeMismatchException
import com.intellij.debugger.streams.trace.breakpoint.ex.BreakpointPlaceNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInstantiationException
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.ArrayReference

private val LOG = logger<MethodBreakpointTracer>()

/**
 * @author Shumaf Lovpache
 */
class MethodBreakpointTracer(private val mySession: XDebugSession,
                             private val myBreakpointResolver: BreakpointResolver,
                             private val myResultInterpreter: TraceResultInterpreter) : StreamTracer {
  override fun trace(chain: StreamChain, callback: TracingCallback) {
    val executionCallback = object : StreamExecutionCallback {
      override fun evaluated(collectedValues: StreamValuesCollector, context: EvaluationContextImpl) {
        // TODO: transform maps to arrays

        // create evaluation result from collected values and dispose StreamValuesCollector later
        //val arrayClass = vm.classesByName("java.lang.Object[]").first() as ArrayType
        //val reference = arrayClass.newInstance(2)
        //val storage = storages.first()
        //reference.setValue(0, storage)
        //reference.setValue(1, storage)
        val evaluationResult: ArrayReference = null as ArrayReference // TODO: implement
        LOG.info("stream chain evaluated")
        val interpretedResult = try {
          myResultInterpreter.interpret(chain, evaluationResult)
        }
        catch (t: Throwable) {
          // TODO: change trace expression field
          callback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.cannot.interpret.result", t.message!!))
          throw t
        }
        callback.evaluated(interpretedResult, context)
      }

      override fun breakpointSetupFailed(e: Throwable) {
        callback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.cannot.find.places.for.breakpoints"))
        LOG.error(e)
      }

      override fun tracingSetupFailed(e: Throwable) {
        callback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.cannot.initialize.breakpoints"))
        LOG.error(e)
      }

      override fun streamExecutionFailed(e: Throwable) {
        callback.evaluationFailed("", StreamDebuggerBundle.message("evaluation.failed.exception.occurred.during.stream.execution"))
        LOG.error(e)
      }
    }

    val xDebugProcess = mySession.debugProcess as? JavaDebugProcess ?: return
    runInDebuggerThread(xDebugProcess.debuggerSession.process) {
      trace(xDebugProcess, chain, executionCallback)
    }
  }

  private fun trace(debugProcess: JavaDebugProcess, chain: StreamChain, callback: StreamExecutionCallback) {
    val breakpointPlaces = try {
      myBreakpointResolver.findBreakpointPlaces(chain)
    }
    catch (e: BreakpointPlaceNotFoundException) {
      callback.breakpointSetupFailed(e)
      return
    }

    val context = createEvaluationContext(debugProcess)
    val valueContainer: ValueContainer = createValueContainer(context)

    try {
      val valuesCollector: StreamValuesCollector = StreamValuesCollectorImpl(valueContainer)

      // TODO: Abstract by language here?
      val breakpointFactory: MethodBreakpointFactory = MethodBreakpointFactoryImpl(context, valuesCollector, callback)

      val qualifierExpressionBreakpoint = if (breakpointPlaces.qualifierExpressionMethod == null) {
        // if qualifier expression is variable we need to replace it in current stack frame
        val stepSignature = breakpointPlaces.intermediateStepsMethods.firstOrNull()
                            ?: breakpointPlaces.terminationOperationMethod
        val qualifierExpressionValue = breakpointFactory.replaceQualifierExpressionValue(stepSignature, chain)
        null // TODO: восстанавливать значение qualifierExpression после вычисления стрима
      } else {
        // set additional breakpoint as for an intermediate operation
        breakpointFactory
          .createProducerStepBreakpoint(breakpointPlaces.qualifierExpressionMethod)
      }

      val intermediateStepsBreakpoints = breakpointPlaces.intermediateStepsMethods.map {
        breakpointFactory.createIntermediateStepBreakpoint(it)
      }
      val terminationOperationBreakpoint = breakpointFactory
        .createTerminationOperationBreakpoint(breakpointPlaces.terminationOperationMethod)

      qualifierExpressionBreakpoint?.enable()
      intermediateStepsBreakpoints.forEach { it.enable() }
      terminationOperationBreakpoint.enable()

      // TODO: после трассировки проверять, что брейкпоинты,
      //  которые мы расставили были подчищены. Также полезно
      //  рассмотреть как поведет себя control flow в случае
      //  завершения дебага, не будет ли течь память

      // mySession.stepOver(false) // TODO: тут надо что-то более понятное юзеру придумать, а не сразу шагать
    }
    catch (e: ValueInstantiationException) {
      callback.tracingSetupFailed(e)
      valueContainer.dispose()
      LOG.error("Cannot create value of type ${e.type}", e)
    }
    catch (e: MethodNotFoundException) {
      callback.tracingSetupFailed(e)
      valueContainer.dispose()
      LOG.error("Cannot find some methods in target VM", e)
    }
    catch (e: ArgumentTypeMismatchException) {
      callback.streamExecutionFailed(e)
      valueContainer.dispose()
      LOG.error("Cannot find some methods in target VM", e)
    }
    catch (e: Throwable) {
      callback.streamExecutionFailed(e)
      valueContainer.dispose()
    }
  }

  private fun createValueContainer(context: EvaluationContextImpl): ValueContainer {
    val container = ValueContainerImpl(context)
    container.registerBytecodeFactory(COLLECTOR_CLASS_NAME) {
      javaClass
        .getResourceAsStream("/classes/MapCollector.clazz").use {
          it?.readBytes()
        }
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
}

