// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.value.transform

import com.intellij.debugger.streams.wrapper.StreamCall
import com.sun.jdi.ThreadReference
import com.sun.jdi.Value

/**
 * @author Shumaf Lovpache
 */
fun interface MethodReturnValueTransformer {
  fun transform(chainStep: StreamCall, thread: ThreadReference, vmValue: Value): Value?
}