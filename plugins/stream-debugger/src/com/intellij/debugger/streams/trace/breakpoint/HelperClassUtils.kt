// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

/**
 * @author Shumaf Lovpache
 */

object HelperClassUtils {
  const val STREAM_DEBUGGER_UTILS_CLASS_NAME = "com.intellij.debugger.streams.generated.java.StreamDebuggerUtils"
  const val STREAM_DEBUGGER_UTILS_CLASS_FILE = "/classes/compiled/StreamDebuggerUtils.class"

  fun getCompiledClass(resourceName: String): ByteArray? = javaClass
    .getResourceAsStream(resourceName)
    ?.readAllBytes()

  fun getStreamDebuggerUtilsClass() = getCompiledClass(
    STREAM_DEBUGGER_UTILS_CLASS_FILE
  )
}