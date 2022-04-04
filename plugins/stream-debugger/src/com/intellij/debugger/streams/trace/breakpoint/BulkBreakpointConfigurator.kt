// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.runInDebuggerThread
import com.intellij.debugger.streams.trace.breakpoint.value.transform.MethodReturnValueTransformer
import com.intellij.debugger.streams.trace.breakpoint.value.transform.PrintToStdoutMethodReturnValueTransformer
import com.intellij.debugger.streams.wrapper.StreamCall
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.debugger.ui.breakpoints.FilteredRequestor
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.Value
import com.sun.jdi.event.MethodExitEvent

private val LOG = logger<BulkBreakpointConfigurator>()

/**
 * @author Shumaf Lovpache
 */
class BulkBreakpointConfigurator : BreakpointConfigurator {
  override fun setBreakpoints(process: JavaDebugProcess, chain: StreamChain, chainEvaluatedCallback: ChainEvaluatedCallback) {
    val intermediateMethods = chain.intermediateCalls
      .map { findStreamCallMethod(process.session, it) }

    if (null in intermediateMethods) {
      LOG.info("Cannot find declarations for some methods in stream chain")
      return
    }

    val terminationMethod = findStreamCallMethod(process.session, chain.terminationCall)

    if (terminationMethod == null) {
      LOG.info("Cannot find declarations for termination method in stream chain")
      return
    }

    runInDebuggerThread(process.debuggerSession.process) {
      val identityValueModifier: (Value?) -> Value? = { value ->
        LOG.info("Modifying value of type ${value?.type()?.name()}")
        value
      }

      val valueTransformer: MethodReturnValueTransformer = PrintToStdoutMethodReturnValueTransformer()

      // TODO: создаем какую-то машинерию и все такое тут или выносим отдельно? Лучше вынести отдельно и пробрасывать сюда

      val intermediateStepsRequestors = chain.intermediateCalls.zip(intermediateMethods).map {
        DebuggerUtils.createMethodExitBreakpoint(process, it.second!!, applyReturnValueTranformer(it.first, valueTransformer))
      }

      DebuggerUtils.createMethodExitBreakpoint(process, terminationMethod) { requestor, suspendContext, ev ->
        // TODO: у терминального оператора по идее надо что-то другое сделать
        //  а вообще было бы неплохо высунуть наружу это, т. е. делегировать возню с терминальный/нетерминальный
        //  операцией и какой именно метод вызывается (может туда peek не подойдет или что-то такое) в MethodReturnValueTransformer
        applyReturnValueTranformer(chain.terminationCall, valueTransformer)(requestor, suspendContext, ev)

        chainEvaluatedCallback.onChainEvaluated()

        val debugProcess = suspendContext.debugProcess
        intermediateStepsRequestors.forEach {
          debugProcess.requestsManager.deleteRequest(it)
        }
        debugProcess.requestsManager.deleteRequest(requestor)
      }
    }
  }
}

// TODO: надо куда-то вкорячить это
fun applyReturnValueTranformer(chainStep: StreamCall, valueTransformer: MethodReturnValueTransformer) = transformerCallback@{
  requestor: FilteredRequestor, suspendContext: SuspendContextImpl, event: MethodExitEvent ->

  // TODO: сюда хочется вснуть вызов какого-то красивого DSL для манипулирования значениями
  //  У DSL хочется, чтобы можно было создавать и в какое-то место складывать переменные разных типов
  val threadProxy = suspendContext.thread ?: return@transformerCallback

  val originalReturnValue = try {
    event.returnValue()
  }
  catch (e: UnsupportedOperationException) {
    val vm = event.virtualMachine()
    LOG.info("Return value interception is not supported in ${vm.name()} ${vm.version()}", e)
    return@transformerCallback
  }

  val replacedReturnValue = valueTransformer
                              .transform(chainStep, threadProxy.threadReference, originalReturnValue) ?: return@transformerCallback

  // TODO: ClassNotLoadedException, IncompatibleThreadStateException, InvalidTypeException
  threadProxy.forceEarlyReturn(replacedReturnValue)
}

fun findStreamCallMethod(session: XDebugSession, step: StreamCall): PsiMethod? {
  val currentFile = session.currentPosition?.file ?: return null

  val psiManager = PsiManager.getInstance(session.project)
  val psiFile = psiManager.findFile(currentFile) ?: return null

  val methodCallExpression = psiFile.findElementAt(step.textRange.endOffset)?.prevSibling as? PsiMethodCallExpression ?: return null
  return methodCallExpression.methodExpression.reference?.resolve() as? PsiMethod
}