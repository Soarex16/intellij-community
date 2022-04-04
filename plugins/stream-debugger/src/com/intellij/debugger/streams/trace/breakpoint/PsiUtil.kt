// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.psi.PsiMethod

/**
 * @author Shumaf Lovpache
 */

// Formats psi method into readable string for debugging purposes
internal fun PsiMethod.signatureText(): String {
  val returnTypePart = this.returnType?.canonicalText ?: ""
  val namePart = this.name
  val parametersPart = this.parameterList.parameters.map { it.type.canonicalText }.joinToString(", ")
  return "$returnTypePart $namePart($parametersPart)"
}