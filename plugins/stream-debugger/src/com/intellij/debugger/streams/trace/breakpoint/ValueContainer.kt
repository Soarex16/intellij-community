// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.sun.jdi.*

/**
 * @author Shumaf Lovpache
 */
interface ValueContainer {
  /**
   * @throws InvalidTypeException
   * @throws ClassNotLoadedException
   * @throws IncompatibleThreadStateException
   * @throws InvocationException
   */
  fun createInstance(className: String, constructorSignature: String, args: List<Value>): ObjectReference?

  fun dispose()
}