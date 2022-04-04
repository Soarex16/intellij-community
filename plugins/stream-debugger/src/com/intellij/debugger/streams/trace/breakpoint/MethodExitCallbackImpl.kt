// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.ui.breakpoints.FilteredRequestor
import com.intellij.openapi.diagnostic.logger
import com.sun.jdi.event.LocatableEvent
import com.sun.jdi.event.MethodExitEvent

private val LOG = logger<MethodExitCallbackImpl>()

class MethodExitCallbackImpl : MethodExitCallback {
  override fun beforeMethodExit(requestor: FilteredRequestor, suspendContext: SuspendContextImpl, event: MethodExitEvent) {
    TODO("not implemented")
    val vm = event.virtualMachine()

    // This should be checked inside callback
    if (!vm.canGetMethodReturnValues()) {
      LOG.info("Can't modify method return value because vm version (${vm.version()}) does not supports this feature")
    }
  }
}