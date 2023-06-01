// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.trace.breakpoint.lib.impl.handlers

import com.intellij.debugger.streams.trace.breakpoint.DebuggerUtils
import com.intellij.debugger.streams.trace.breakpoint.ValueContext
import com.intellij.debugger.streams.trace.breakpoint.ex.IncorrectValueTypeException
import com.intellij.psi.CommonClassNames
import com.sun.jdi.ArrayReference
import com.sun.jdi.ClassType
import com.sun.jdi.ObjectReference

fun ValueContext.formatMap(valueMap: ObjectReference?, streamTypeInfo: StreamTypeInfo): ArrayReference = if (valueMap == null) {
  emptyResult()
}
else {
  assertIsMap(valueMap)
  val helperClass = getType(DebuggerUtils.STREAM_DEBUGGER_UTILS_CLASS_NAME) as ClassType
  val formatMap = helperClass.method(streamTypeInfo.formatterMethod, "(Ljava/util/Map;)[Ljava/lang/Object;")
  formatMap.invoke(helperClass, listOf(valueMap)) as ArrayReference
}

fun ValueContext.emptyResult(): ArrayReference = array(
  array("int", 0),
  array(CommonClassNames.JAVA_LANG_OBJECT, 0)
)

private fun assertIsMap(value: ObjectReference) {
  if (!com.intellij.debugger.engine.DebuggerUtils.instanceOf(value.type(), CommonClassNames.JAVA_UTIL_MAP)) {
    throw IncorrectValueTypeException(CommonClassNames.JAVA_UTIL_MAP, value.type().name())
  }
}