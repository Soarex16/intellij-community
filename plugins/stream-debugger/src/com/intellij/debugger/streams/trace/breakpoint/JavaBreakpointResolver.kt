// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.streams.wrapper.StreamCall
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression

/**
 * @author Shumaf Lovpache
 */
class JavaBreakpointResolver(val psiFile: PsiFile) : BreakpointResolver {
  override fun tryFindBreakpointPlace(streamStep: StreamCall): MethodSignature? {
    val methodCallExpression = psiFile.findElementAt(streamStep.textRange.endOffset)?.prevSibling as? PsiMethodCallExpression ?: return null
    return (methodCallExpression.methodExpression.reference?.resolve() as? PsiMethod)?.methodSignature()
  }
}