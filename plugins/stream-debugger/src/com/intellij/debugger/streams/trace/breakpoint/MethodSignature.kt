// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.TypeConversionUtil
import com.sun.jdi.Method

data class MethodSignature(val containingClass: String, val name: String, val argumentTypes: List<String>, val returnType: String) {
  val arguments: String
    get() = argumentTypes.joinToString(", ")

  override fun toString() = "$returnType $containingClass.$name($arguments)"
}

internal fun Method.methodSignature() = MethodSignature(
  this.declaringType().name(),
  name(),
  argumentTypeNames(),
  returnTypeName()
)

internal fun PsiMethod.methodSignature() = MethodSignature(
  containingClass?.qualifiedName ?: "",
  name,
  parameterList.parameters.map { param -> TypeConversionUtil.erasure(param.type)?.canonicalText!! },
  TypeConversionUtil.erasure(returnType)?.canonicalText ?: ""
)