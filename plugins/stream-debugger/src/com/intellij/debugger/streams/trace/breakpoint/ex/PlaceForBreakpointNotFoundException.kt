// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint.ex

import com.intellij.debugger.streams.wrapper.StreamCall

/**
 * @author Shumaf Lovpache
 */
class PlaceForBreakpointNotFoundException(streamCall: StreamCall) : RuntimeException("Cannot find declarations for methods ${streamCall.name} in stream chain")
