// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils.equalBySignature
import com.intellij.debugger.ui.breakpoints.FilteredRequestorImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.sun.jdi.Method
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent
import com.sun.jdi.request.InvalidRequestStateException

private val LOG = logger<MethodExitRequestor>()

/**
 * @author Shumaf Lovpache
 */
class MethodExitRequestor(
  project: Project,
  val method: Method,
  val callback: MethodExitCallback
) : FilteredRequestorImpl(project) {
  override fun processLocatableEvent(action: SuspendContextCommandImpl, event: LocatableEvent?): Boolean {
    if (event == null) return false
    val context = action.suspendContext ?: return false

    val currentExecutingMethod = event.location().method()
    if (event !is MethodExitEvent) return false

    if (context.thread?.isSuspended == true && currentExecutingMethod.equalBySignature(method)) {
      try {
        callback.beforeMethodExit(this, context, event)
      }
      catch (e: Throwable) {
        LOG.info(e)
      }
      finally {
        try {
          event.request().disable()
        }
        catch (e: InvalidRequestStateException) {
          LOG.warn(e)
        }
      }
    }

    return false
  }

  override fun getSuspendPolicy(): String = DebuggerSettings.SUSPEND_ALL
}