// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.lib.impl

import com.intellij.debugger.streams.lib.*
import com.intellij.debugger.streams.resolve.ValuesOrderResolver
import com.intellij.debugger.streams.trace.CallTraceInterpreter
import com.intellij.debugger.streams.trace.IntermediateCallHandler
import com.intellij.debugger.streams.trace.TerminatorCallHandler
import com.intellij.debugger.streams.trace.breakpoint.JavaBreakpointResolver
import com.intellij.debugger.streams.trace.breakpoint.ValueManager
import com.intellij.debugger.streams.trace.breakpoint.lib.*
import com.intellij.debugger.streams.trace.dsl.Dsl
import com.intellij.debugger.streams.wrapper.IntermediateStreamCall
import com.intellij.debugger.streams.wrapper.TerminatorStreamCall
import com.sun.jdi.ObjectReference

/**
 * @author Vitaliy.Bibaev
 */
abstract class LibrarySupportBase(private val compatibleLibrary: UniversalLibrarySupport = LibrarySupportBase.EMPTY) : UniversalLibrarySupport {
  companion object {
    val EMPTY: UniversalLibrarySupport = DefaultLibrarySupport()
  }

  private val mySupportedIntermediateOperations: MutableMap<String, IntermediateOperation> = mutableMapOf()
  private val mySupportedTerminalOperations: MutableMap<String, TerminalOperation> = mutableMapOf()

  final override fun createHandlerFactory(dsl: Dsl): HandlerFactory {
    val compatibleLibraryFactory = compatibleLibrary.createHandlerFactory(dsl)
    return object : HandlerFactory {
      override fun getForIntermediate(number: Int, call: IntermediateStreamCall): IntermediateCallHandler {
        val operation = mySupportedIntermediateOperations[call.name]
        return operation?.getTraceHandler(number, call, dsl)
               ?: compatibleLibraryFactory.getForIntermediate(number, call)
      }

      override fun getForTermination(call: TerminatorStreamCall, resultExpression: String): TerminatorCallHandler {
        val terminalOperation = mySupportedTerminalOperations[call.name]
        return terminalOperation?.getTraceHandler(call, resultExpression, dsl)
               ?: compatibleLibraryFactory.getForTermination(call, resultExpression)
      }
    }
  }

  override fun createRuntimeHandlerFactory(valueManager: ValueManager): RuntimeHandlerFactory {
    // TODO: factory
    // Terminal operations:
    // void forEach(Consumer<? super T> action)
    // void forEachOrdered(Consumer<? super T> action)

    // Object[] toArray()
    // <A> A[] toArray(IntFunction<A[]> generator)
    // T reduce(T identity, BinaryOperator<T> accumulator)
    // <U> U reduce(U identity, BiFunction<U,? super T,U> accumulator, BinaryOperator<U> combiner)
    // <R> R collect(Supplier<R> supplier, BiConsumer<R,? super T> accumulator, BiConsumer<R,R> combiner)
    // <R,A> R collect(Collector<? super T,A,R> collector)
    // long count()
    // default List<T> toList()

    // boolean anyMatch(Predicate<? super T> predicate)
    // boolean allMatch(Predicate<? super T> predicate)
    // boolean noneMatch(Predicate<? super T> predicate)

    // Optional<T> reduce(BinaryOperator<T> accumulator)
    // Optional<T> miсn(Comparator<? super T> comparator)
    // Optional<T> max(Comparator<? super T> comparator)
    // Optional<T> findFirst()
    // Optional<T> findAny()
    //private fun getTerminationOperationFormatter(valueManager: ValueManager, evaluationContext: EvaluationContextImpl, streamCall: StreamCall): TerminationOperationTraceFormatter = when(streamCall.name) {
    //  "findAny", "findFirst", "min", "max", "reduce" -> OptionalTraceFormatter(valueManager, evaluationContext)
    //  "forEach", "forEachOrdered" -> ForEachTraceFormatter(valueManager, evaluationContext)
    //  "anyMatch", "allMatch", "noneMatch" -> TODO("Not implemented")
    //  else -> ToCollectionTraceFormatter(valueManager, evaluationContext)
    //}
    return object : RuntimeHandlerFactory {
      val compatibleRuntimeHandlerFactory = compatibleLibrary.createRuntimeHandlerFactory(valueManager)
      override fun getForSource(): RuntimeSourceCallHandler {
        return compatibleRuntimeHandlerFactory.getForSource()
      }

      override fun getForIntermediate(number: Int, call: IntermediateStreamCall, time: ObjectReference): RuntimeIntermediateCallHandler {
        val operation = mySupportedIntermediateOperations[call.name]
        return operation?.getRuntimeTraceHandler(number, call, valueManager, time)
               ?: compatibleRuntimeHandlerFactory.getForIntermediate(number, call, time)
      }

      override fun getForTermination(number: Int, call: TerminatorStreamCall, time: ObjectReference): RuntimeTerminalCallHandler {
        val terminalOperation = mySupportedTerminalOperations[call.name]
        return terminalOperation?.getRuntimeTraceHandler(number, call, valueManager, time)
               ?: compatibleRuntimeHandlerFactory.getForTermination(number, call, time)
      }
    }
  }

  override val breakpointResolverFactory: BreakpointResolverFactory = BreakpointResolverFactory {
    JavaBreakpointResolver(it)
  }

  final override val interpreterFactory: InterpreterFactory = object : InterpreterFactory {
    override fun getInterpreter(callName: String): CallTraceInterpreter {
      val operation = findOperationByName(callName)
      return operation?.traceInterpreter
             ?: compatibleLibrary.interpreterFactory.getInterpreter(callName)
    }
  }

  final override val resolverFactory: ResolverFactory = object : ResolverFactory {
    override fun getResolver(callName: String): ValuesOrderResolver {
      val operation = findOperationByName(callName)
      return operation?.valuesOrderResolver
             ?: compatibleLibrary.resolverFactory.getResolver(callName)
    }
  }

  protected fun addIntermediateOperationsSupport(vararg operations: IntermediateOperation) {
    operations.forEach { mySupportedIntermediateOperations[it.name] = it }
  }

  protected fun addTerminationOperationsSupport(vararg operations: TerminalOperation) {
    operations.forEach { mySupportedTerminalOperations[it.name] = it }
  }

  private fun findOperationByName(name: String): Operation? =
    mySupportedIntermediateOperations[name] ?: mySupportedTerminalOperations[name]

}