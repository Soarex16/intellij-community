// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.sun.jdi.*

/**
 * @author Shumaf Lovpache
 */
interface ValueContext {
  val evaluationContext: EvaluationContextImpl

  /**
   * @throws InvalidTypeException
   * @throws ClassNotLoadedException
   * @throws IncompatibleThreadStateException
   * @throws InvocationException
   */
  fun instance(className: String, constructorSignature: String, args: List<Value>): ObjectReference

  fun instance(className: String): ObjectReference = instance(className, EMPTY_CONSTRUCTOR_SIGNATURE, emptyList())

  fun mirror(value: Int): IntegerValue
  fun mirror(value: Byte): ByteValue
  fun mirror(value: Char): CharValue
  fun mirror(value: Float): FloatValue
  fun mirror(value: Long): LongValue
  fun mirror(value: Short): ShortValue
  fun mirror(value: Double): DoubleValue
  fun mirror(value: Boolean): BooleanValue
  fun mirror(value: String): StringReference
  fun mirror(): VoidValue

  fun getType(className: String): ReferenceType
  fun defineClass(className: String, bytesLoader: BytecodeFactory): ClassType

  fun array(componentType: String, size: Int): ArrayReference
  fun array(vararg values: Value): ArrayReference
  fun array(values: List<Value>): ArrayReference

  fun ObjectReference.method(name: String, signature: String): Method
  fun ReferenceType.method(name: String, signature: String): Method

  fun invoke(cls: ClassType, method: Method, arguments: List<Value>): Value
  fun invoke(obj: ObjectReference, method: Method, arguments: List<Value>): Value

  fun keep(value: Value)
}