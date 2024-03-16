// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.streams.trace.breakpoint.ValueContext
import com.sun.jdi.ObjectReference

fun ValueContext.addSequentialOperator(streamObject: ObjectReference): ObjectReference {
  return streamObject
    .method("sequential", "()Ljava/util/stream/BaseStream;")
    .invoke(streamObject) as ObjectReference
}