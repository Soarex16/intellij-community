// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.sun.jdi.request.MethodExitRequest

/**
 * @author Shumaf Lovpache
 */
interface MethodExitBreakpointFactory {
  @Throws(MethodNotFoundException::class)
  fun createIntermediateStepBreakpoint(signature: MethodSignature): MethodExitRequest

  @Throws(MethodNotFoundException::class)
  fun createTerminationOperationBreakpoint(signature: MethodSignature): MethodExitRequest
}