// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.streams.trace.StreamTracer
import com.intellij.debugger.streams.trace.TraceResultInterpreter
import com.intellij.debugger.streams.trace.TracingCallback
import com.intellij.debugger.streams.trace.breakpoint.value.transform.MethodReturnValueTransformer
import com.intellij.debugger.streams.trace.breakpoint.value.transform.PrintToStdoutMethodReturnValueTransformer
import com.intellij.debugger.streams.wrapper.StreamChain
import com.intellij.openapi.diagnostic.logger
import com.intellij.xdebugger.XDebugSession

private val LOG = logger<MethodBreakpointTracer>()

/**
 * @author Shumaf Lovpache
 */
class MethodBreakpointTracer(val mySession: XDebugSession,
                             val breakpointConfigurator: BreakpointConfigurator,
                             val myResultInterpreter: TraceResultInterpreter) : StreamTracer {
  override fun trace(chain: StreamChain, callback: TracingCallback) {
    val xDebugProcess = mySession.debugProcess as? JavaDebugProcess ?: return

    // TODO: create objects for tracer

    breakpointConfigurator.setBreakpoints(xDebugProcess, chain) {
      LOG.info("stream chain evaluated")
    }
    mySession.resume()
  }
}

