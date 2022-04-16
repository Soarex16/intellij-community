// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.streams.trace.breakpoint.ex.MethodNotFoundException
import com.intellij.debugger.streams.trace.breakpoint.ex.ValueInterceptionException
import com.intellij.debugger.streams.wrapper.StreamChain
import com.sun.jdi.Value
import com.sun.jdi.request.MethodExitRequest

/**
 * @author Shumaf Lovpache
 */
interface MethodBreakpointFactory {
  /**
   * Returns previous value of qualifier expression
   * @throws ValueInterceptionException
   */
  fun replaceQualifierExpressionValue(signature: MethodSignature, streamChain: StreamChain): Value

  /**
   * @throws MethodNotFoundException
   * @throws ValueInterceptionException
   */
  fun createProducerStepBreakpoint(signature: MethodSignature): MethodExitRequest

  /**
   * @throws MethodNotFoundException
   * @throws ValueInterceptionException
   */
  fun createIntermediateStepBreakpoint(signature: MethodSignature): MethodExitRequest

  /**
   * @throws MethodNotFoundException
   * @throws ValueInterceptionException
   */
  fun createTerminationOperationBreakpoint(signature: MethodSignature): MethodExitRequest
}