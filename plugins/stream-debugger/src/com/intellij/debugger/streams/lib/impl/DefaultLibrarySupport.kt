// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.*
import com.intellij.debugger.streams.resolve.EmptyResolver
import com.intellij.debugger.streams.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.trace.CallTraceInterpreter
import com.intellij.debugger.streams.trace.IntermediateCallHandler
import com.intellij.debugger.streams.trace.TerminatorCallHandler
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.lib.*
import com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers.NopCallHandler
import com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers.PeekCallHandler
import com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers.PeekTerminalCallHandler
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.trace.impl.handler.unified.PeekTraceHandler
import com.intellij.debugger.streams.trace.impl.handler.unified.TerminatorTraceHandler
import com.intellij.debugger.streams.trace.impl.interpret.SimplePeekCallTraceInterpreter
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.sun.jdi.ObjectReference

class DefaultLibrarySupport : UniversalLibrarySupport {
  override fun createHandlerFactory(dsl: Dsl): HandlerFactory = object : HandlerFactory {
    override fun getForIntermediate(number: Int, call: IntermediateStreamCall): IntermediateCallHandler {
      return PeekTraceHandler(number, call.name, call.typeBefore, call.typeAfter, dsl)
    }

    override fun getForTermination(call: TerminatorStreamCall, resultExpression: String): TerminatorCallHandler {
      return TerminatorTraceHandler(call, dsl)
    }
  }

  override fun createRuntimeHandlerFactory(valueManager: ValueManager): RuntimeHandlerFactory = object : RuntimeHandlerFactory {
    override fun getForSource(): RuntimeSourceCallHandler {
      return NopCallHandler()
    }

    override fun getForIntermediate(number: Int, call: IntermediateStreamCall, time: ObjectReference): RuntimeIntermediateCallHandler {
      return PeekCallHandler(valueManager, number, call.typeBefore, call.typeAfter, time)
    }

    override fun getForTermination(number: Int, call: TerminatorStreamCall, time: ObjectReference): RuntimeTerminalCallHandler {
      return PeekTerminalCallHandler(valueManager, number, time, call.typeBefore, call.resultType)
    }
  }

  override val breakpointResolverFactory: BreakpointResolverFactory
    get() = TODO("Not yet implemented")

  override val interpreterFactory: InterpreterFactory = object : InterpreterFactory {
    override fun getInterpreter(callName: String): CallTraceInterpreter {
      return SimplePeekCallTraceInterpreter()
    }
  }

  override val resolverFactory: ResolverFactory = object : ResolverFactory {
    override fun getResolver(callName: String): ValuesOrderResolver {
      return EmptyResolver()
    }
  }
}