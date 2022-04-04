// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.streams.wrapper.StreamChain

/**
 * @author Shumaf Lovpache
 */
interface BreakpointConfigurator {
  /**
   * Хотим предусмотреть возможность подмены алгоритма расстановки брейкпоинтов
   * На текущий момент идей две:
   * - ставим последовательно после каждого метода
   * - ставим все в начале исполнения цепочки, снимаем в конце выполнения цепочки
   *
   * TODO: продумать как пробрасывать свой модификатор значений
   */

  /**
   * Вызываем перед заходом в цепочку, чтобы настроить брейкпоинты
   */
  fun setBreakpoints(process: JavaDebugProcess, chain: StreamChain, chainEvaluatedCallback: ChainEvaluatedCallback)
}