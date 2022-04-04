// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.engine.requests.RequestManagerImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.debugger.ui.breakpoints.FilteredRequestor
import com.intellij.openapi.diagnostic.logger
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.TypeConversionUtil
import com.intellij.util.containers.JBIterable
import com.sun.jdi.ClassNotPreparedException
import com.sun.jdi.Method
import com.sun.jdi.VirtualMachine
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.request.MethodExitRequest
import kotlin.streams.asSequence
import kotlin.streams.asStream

private val LOG = logger<DebuggerUtils>()

fun interface MethodExitCallback {
  fun beforeMethodExit(requestor: FilteredRequestor, suspendContext: SuspendContextImpl, event: MethodExitEvent)
}

/**
 * @author Shumaf Lovpache
 */
object DebuggerUtils {
  public fun runInDebuggerThread(debugProcess: DebugProcessImpl, action: () -> Unit) {
    val command = object : DebuggerCommandImpl(PrioritizedTask.Priority.NORMAL) {
      override fun action() {
        action()
      }
    }

    val managerThread = debugProcess.managerThread
    if (DebuggerManagerThreadImpl.isManagerThread()) {
      managerThread.invoke(command)
    }
    else {
      managerThread.schedule(command)
    }
  }

  fun createMethodExitBreakpoint(process: JavaDebugProcess, psiMethod: PsiMethod, callback: MethodExitCallback): FilteredRequestor? {
    val vmMethod = findVmMethod(process, psiMethod) ?: return null

    val javaDebuggerSession = process.debuggerSession
    val debugProcess = javaDebuggerSession.process

    val requestor = MethodExitRequestor(debugProcess.project, vmMethod, callback)
    enableMethodExitRequest(debugProcess, vmMethod, requestor)
    return requestor
  }

  private fun enableMethodExitRequest(debugProcess: DebugProcessImpl, vmMethod: Method, requestor: FilteredRequestor) {
    val requestManager: RequestManagerImpl = debugProcess.requestsManager ?: return
    val methodExitRequest: MethodExitRequest = requestManager.createMethodExitRequest(requestor)
    methodExitRequest.addClassFilter(vmMethod.declaringType())
    methodExitRequest.enable()
  }

  private fun findVmMethod(process: JavaDebugProcess, psiMethod: PsiMethod): Method? {
    val javaDebuggerSession = process.debuggerSession
    val debugProcess = javaDebuggerSession.process
    val vm: VirtualMachine = debugProcess.virtualMachineProxy.virtualMachine

    val fqClassName = psiMethod.containingClass?.qualifiedName
    val vmClass = vm.classesByName(fqClassName).firstOrNull()
    if (vmClass == null) {
      LOG.info("Class $fqClassName not found by jvm")
      return null
    }

    try {
      val vmMethod = vmClass.methods().findByPsiMethodSignature(psiMethod) // TODO: methodsByName(name, jni like signature)
      if (vmMethod == null) {
        LOG.info("Can not find method with signature ${psiMethod.signatureText()} in $fqClassName")
        return null
      }

      return vmMethod
    }
    catch (e: ClassNotPreparedException) {
      LOG.warn("Failed to retreive $fqClassName method because class not yet been prepared.", e)
    }

    return null
  }

  internal fun Method?.equalBySignature(other: Method): Boolean = this != null && this.name() == other.name()
                                                                  && this.returnTypeName() == other.returnTypeName()
                                                                  && this.argumentTypeNames() == other.argumentTypeNames()

  private fun List<Method>.findByPsiMethodSignature(psiMethod: PsiMethod) = this.find {
    it.name() == psiMethod.name
    && it.returnTypeName() == TypeConversionUtil.erasure(psiMethod.returnType)?.canonicalText
    && it.argumentTypeNames() == psiMethod.parameterList.parameters
      .map { param -> TypeConversionUtil.erasure(param.type)?.canonicalText }
  }
}