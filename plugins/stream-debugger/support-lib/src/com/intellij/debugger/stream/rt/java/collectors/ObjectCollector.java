// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.stream.rt.java.collectors;

/**
 * @author Shumaf Lovpache
 * This helper class is loaded by the IntelliJ IDEA stream debugger
 */
class ObjectCollector<T> implements java.util.function.Consumer<T> {
    private final java.util.Map<java.lang.Integer, T> storage;
    private final java.util.concurrent.atomic.AtomicInteger time;
    private final boolean tick;

    ObjectCollector(java.util.Map<Integer, T> storage, java.util.concurrent.atomic.AtomicInteger time, boolean tick) {
        this.storage = storage;
        this.time = time;
        this.tick = tick;
    }

    @Override
    public void accept(T t) {
        storage.put(tick ? time.incrementAndGet() : time.get(), t);
    }
}