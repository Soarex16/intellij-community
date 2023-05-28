// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.streams.trace.breakpoint.lib.RuntimeTraceHandler

/**
 * @author Shumaf Lovpache
 */
interface StreamOperationHandler : RuntimeTraceHandler, BeforeCallTransformer, CallTransformer, AfterCallTransformer