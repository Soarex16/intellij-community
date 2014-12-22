/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class InferenceFromSourceUtil {
  static boolean shouldInferFromSource(@NotNull PsiMethod method) {
    if (isLibraryCode(method) ||
        method.hasModifierProperty(PsiModifier.ABSTRACT) ||
        PsiUtil.canBeOverriden(method) ||
        method.getBody() == null) {
      return false;
    }
    
    if (method.hasModifierProperty(PsiModifier.STATIC)) return true;

    return !isUnusedInAnonymousClass(method);
  }

  private static boolean isUnusedInAnonymousClass(@NotNull PsiMethod method) {
    PsiClass containingClass = method.getContainingClass();
    return containingClass instanceof PsiAnonymousClass &&
           MethodReferencesSearch.search(method, new LocalSearchScope(containingClass), false).findFirst() == null;
  }

  private static boolean isLibraryCode(@NotNull PsiMethod method) {
    if (method instanceof PsiCompiledElement) return true;
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(method);
    return virtualFile != null && FileIndexFacade.getInstance(method.getProject()).isInLibrarySource(virtualFile);
  }
}
