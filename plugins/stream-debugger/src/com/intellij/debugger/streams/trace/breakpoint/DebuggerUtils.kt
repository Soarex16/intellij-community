// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.impl.PrioritizedTask
import com.intellij.openapi.diagnostic.logger
import com.sun.jdi.ClassNotPreparedException
import com.sun.jdi.Method

private val LOG = logger<DebuggerUtils>()

/**
 * @author Shumaf Lovpache
 */
object DebuggerUtils {
  fun runInDebuggerThread(debugProcess: DebugProcessImpl, action: () -> Unit) {
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

  fun findVmMethod(evaluationContext: EvaluationContextImpl, signature: MethodSignature): Method? {
    val fqClassName = signature.containingClass
    val vmClass = evaluationContext.loadClass(fqClassName)
    if (vmClass == null) {
      LOG.info("Class $fqClassName not found by jvm")
      return null
    }

    try {
      val vmMethod = vmClass.methodsByName(signature.name).findByPsiMethodSignature(signature)
      if (vmMethod == null) {
        LOG.info("Can not find method with signature ${signature} in $fqClassName")
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

  private fun List<Method>.findByPsiMethodSignature(signature: MethodSignature) = this.find {
    it.methodSignature() == signature
  }
}